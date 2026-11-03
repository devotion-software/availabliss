package dev.joeherbert.availabliss.core.calendar

import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.joeherbert.availabliss.core.auth.TokenStore
import dev.joeherbert.availabliss.core.common.DateTimes
import dev.joeherbert.availabliss.core.data.AppPreferences
import dev.joeherbert.availabliss.core.data.CalendarsRepository
import dev.joeherbert.availabliss.core.network.ApiError
import dev.joeherbert.availabliss.core.network.ApiResult
import dev.joeherbert.availabliss.core.network.model.CalendarConnection
import dev.joeherbert.availabliss.core.network.model.DeviceBusy
import dev.joeherbert.availabliss.core.network.model.DeviceCalendar
import dev.joeherbert.availabliss.core.network.model.DeviceSync
import dev.joeherbert.availabliss.core.network.onSuccess
import dev.joeherbert.availabliss.core.push.deviceName
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Outcome of a device calendar upload. */
sealed interface DeviceSyncResult {
    data class Synced(val connection: CalendarConnection, val busyCount: Int) : DeviceSyncResult
    /** Every range is the same as the server already has: nothing was sent. */
    data object Unchanged : DeviceSyncResult
    /** Not signed in, or READ_CALENDAR not granted and nothing was uploaded before: nothing to do. */
    data object Skipped : DeviceSyncResult
    /** READ_CALENDAR was revoked: this phone's calendars and busy time were removed from the server. */
    data object Cleared : DeviceSyncResult
    data class Failed(val error: ApiError) : DeviceSyncResult
}

/** What a sync run should do, given the session, permission and upload history. */
internal enum class DeviceSyncAction { Skip, Upload, Clear }

/**
 * Without READ_CALENDAR there is nothing new to read, but the last upload (years of
 * busy time) would otherwise stay on the server and keep the user busy to their
 * groups, so a revoked permission clears it.
 */
internal fun deviceSyncAction(signedIn: Boolean, hasPermission: Boolean, lastSyncAt: Long?): DeviceSyncAction = when {
    !signedIn -> DeviceSyncAction.Skip
    hasPermission -> DeviceSyncAction.Upload
    lastSyncAt != null -> DeviceSyncAction.Clear
    else -> DeviceSyncAction.Skip
}

/** What one range of the last upload sent: its fingerprint and when (epoch ms). */
@Serializable
internal data class UploadedRange(val hash: String, val at: Long)

/**
 * Whether a range with fingerprint [hash] can be skipped: the last upload of it sent
 * exactly this, less than [maxAge] ago (a backstop in case the server's copy was lost).
 */
internal fun isUnchanged(hash: String, last: UploadedRange?, now: Long, maxAge: Long): Boolean =
    last != null && last.hash == hash && now - last.at in 0 until maxAge

/**
 * The ranges a sync from [start] to at least [horizon] is uploaded in: up to the first
 * fixed boundary, then whole [DeviceCalendarSync.RANGE_DAYS]-day ranges between fixed
 * boundaries until one reaches [horizon]. Fixed boundaries keep the later ranges (and
 * so their fingerprints) the same from day to day.
 */
internal fun syncRanges(start: Instant, horizon: Instant): List<Pair<Instant, Instant>> {
    val step = Duration.ofDays(DeviceCalendarSync.RANGE_DAYS).seconds
    val bounds = mutableListOf(start)
    var next = Math.floorDiv(start.epochSecond, step) * step + step
    do {
        bounds += Instant.ofEpochSecond(next)
        next += step
    } while (bounds.last() < horizon)
    return bounds.zipWithNext()
}

/**
 * Uploads busy time ranges (never titles) from the device calendars the user marked
 * as busy, as far ahead as the server asks (`CalendarConnection.syncUntil`, three
 * years at first), via `PUT /calendars/device`.
 *
 * The window is sent in fixed ranges (see [syncRanges]) and only ranges that changed
 * are re-sent (see [isUnchanged]); the near one goes last, marked as the end of the
 * sync, so the server tells the user's groups once.
 */
@Singleton
class DeviceCalendarSync @Inject constructor(
    @ApplicationContext private val context: Context,
    private val reader: DeviceCalendarReader,
    private val prefs: AppPreferences,
    private val calendars: CalendarsRepository,
    private val tokenStore: TokenStore,
    private val json: Json,
) {
    private val mutex = Mutex()

    /** Device calendar ids that count as busy: the user's choice, or sensible defaults. */
    suspend fun busyCalendarIds(available: List<DeviceCalendarInfo>): Set<String> =
        prefs.busyDeviceCalendarIds.first()
            ?: available.filter { it.busyByDefault }.map { it.externalId }.toSet()

    suspend fun setBusyCalendarIds(ids: Set<String>) = prefs.setBusyDeviceCalendarIds(ids)

    /** [force] uploads even when nothing changed (the user tapped Sync). */
    suspend fun sync(force: Boolean = false): DeviceSyncResult = mutex.withLock { syncLocked(force) }

    private suspend fun syncLocked(force: Boolean): DeviceSyncResult =
        when (deviceSyncAction(tokenStore.token() != null, reader.hasReadPermission(), prefs.lastDeviceSyncAt.first())) {
            DeviceSyncAction.Skip -> DeviceSyncResult.Skipped
            DeviceSyncAction.Clear -> clear()
            DeviceSyncAction.Upload -> upload(force)
        }

    /**
     * Uploads an empty calendar list for this device. The server drops calendars
     * missing from an upload, and their busy time with them (past and future), so this
     * removes everything this phone shared without needing the connection id, which
     * `PUT /calendars/device` resolves from [AppPreferences.deviceId].
     */
    private suspend fun clear(): DeviceSyncResult {
        val today = DateTimes.today().atStartOfDay(DateTimes.zone).toInstant()
        val payload = DeviceSync(
            deviceId = prefs.deviceId(),
            deviceName = deviceName(context),
            rangeStart = DateTimes.toIso(today),
            rangeEnd = DateTimes.toIso(today.plus(Duration.ofDays(1))),
            calendars = emptyList(),
            busy = emptyList(),
        )
        return when (val result = calendars.uploadDeviceBusy(payload).onSuccess { prefs.clearLastDeviceSyncAt() }) {
            is ApiResult.Success -> DeviceSyncResult.Cleared
            is ApiResult.Failure -> DeviceSyncResult.Failed(result.error)
        }
    }

    private suspend fun upload(force: Boolean, again: Boolean = true): DeviceSyncResult {
        val available = reader.calendars()
        val busyIds = busyCalendarIds(available)
        val busyCalendarIds = available.filter { it.externalId in busyIds }.map { it.id }
        // Start of today so events earlier today still count.
        val start = DateTimes.today().atStartOfDay(DateTimes.zone).toInstant()
        val ranges = syncRanges(start, horizon(start))
        val coveredUntil = DateTimes.toIso(ranges.last().second)
        val deviceCalendars = available.map {
            DeviceCalendar(externalId = it.externalId, name = it.name.take(120), color = it.color, busy = it.externalId in busyIds)
        }
        val uploaded = loadUploaded()
        val now = System.currentTimeMillis()

        class Item(val key: String, val payload: DeviceSync, val hash: String, val current: Boolean)
        val items = ranges.mapIndexed { index, (from, to) ->
            val payload = DeviceSync(
                deviceId = prefs.deviceId(),
                deviceName = deviceName(context),
                rangeStart = DateTimes.toIso(from),
                rangeEnd = DateTimes.toIso(to),
                calendars = deviceCalendars,
                busy = reader.busyRanges(busyCalendarIds, from, to).take(MAX_RANGES).map {
                    DeviceBusy(calendarExternalId = it.calendarId.toString(), start = DateTimes.toIso(it.start), end = DateTimes.toIso(it.end))
                },
            )
            val key = from.epochSecond.toString()
            val hash = fingerprint(payload)
            val maxAge = if (index == 0) MAX_UNCHANGED_AGE_MS else MAX_FAR_UNCHANGED_AGE_MS
            Item(key, payload, hash, current = !force && isUnchanged(hash, uploaded[key], now, maxAge))
        }
        val far = items.drop(1).filterNot { it.current }
        val near = items.first()
        if (near.current && far.isEmpty()) return DeviceSyncResult.Unchanged

        // Later ranges first; the near one goes last, even unchanged, so groups refresh once.
        val keys = items.map { it.key }.toSet()
        val sent = uploaded.filterKeys { it in keys }.toMutableMap()
        var connection: CalendarConnection? = null
        var busyCount = 0
        for ((index, item) in (far + near).withIndex()) {
            val payload = item.payload.copy(coveredUntil = coveredUntil, more = index < far.size)
            when (val result = calendars.uploadDeviceBusy(payload)) {
                is ApiResult.Success -> {
                    connection = result.value
                    busyCount += payload.busy.size
                    sent[item.key] = UploadedRange(item.hash, System.currentTimeMillis())
                    saveUploaded(sent)
                }
                is ApiResult.Failure -> return DeviceSyncResult.Failed(result.error)
            }
        }
        prefs.setLastDeviceSyncAt(now)

        // The server wants more than this sync read (a group now plans further ahead): go again.
        val asked = connection?.syncUntil?.let(DateTimes::parseInstant)
        if (asked != null) {
            prefs.setDeviceSyncHorizon(asked.toEpochMilli())
            if (again && asked > ranges.last().second) return upload(force = false, again = false)
        }
        return DeviceSyncResult.Synced(connection!!, busyCount)
    }

    /** How far ahead to read: what the server last asked for, within sensible bounds. */
    private suspend fun horizon(start: Instant): Instant {
        val asked = prefs.deviceSyncHorizon.first()?.let(Instant::ofEpochMilli)
        val fallback = start.plus(Duration.ofDays(DEFAULT_HORIZON_DAYS))
        val atMost = start.plus(Duration.ofDays(MAX_HORIZON_DAYS))
        return minOf(asked?.let { maxOf(it, start.plus(Duration.ofDays(1))) } ?: fallback, atMost)
    }

    private val uploadedSerializer = MapSerializer(String.serializer(), UploadedRange.serializer())

    private suspend fun loadUploaded(): Map<String, UploadedRange> =
        prefs.deviceSyncRanges.first()?.let { runCatching { json.decodeFromString(uploadedSerializer, it) }.getOrNull() }.orEmpty()

    private suspend fun saveUploaded(ranges: Map<String, UploadedRange>) =
        prefs.setDeviceSyncRanges(json.encodeToString(uploadedSerializer, ranges))

    private fun fingerprint(payload: DeviceSync): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(json.encodeToString(DeviceSync.serializer(), payload).toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        /** How far ahead to read until the server says. */
        const val DEFAULT_HORIZON_DAYS = 3 * 365L
        /** Never read further than this, whatever the server asks. */
        const val MAX_HORIZON_DAYS = 11 * 365L
        /** Uploads are split at fixed boundaries this many days apart. */
        const val RANGE_DAYS = 180L
        /** The near range is re-sent at least this often even when nothing changed… */
        const val MAX_UNCHANGED_AGE_MS = 24L * 60 * 60 * 1000
        /** …and later ranges at least this often. */
        const val MAX_FAR_UNCHANGED_AGE_MS = 7 * 24L * 60 * 60 * 1000
        /** Server limit on `busy` entries per upload. */
        private const val MAX_RANGES = 20_000
    }
}
