package dev.joeherbert.availabliss.core.calendar

import java.security.MessageDigest
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
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Outcome of a device calendar upload. */
sealed interface DeviceSyncResult {
    data class Synced(val connection: CalendarConnection, val busyCount: Int) : DeviceSyncResult
    /** Same calendars, busy flags, window and busy time as the last upload: nothing was sent. */
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
 * Without READ_CALENDAR there is nothing new to read, but the last upload (up to
 * [DeviceCalendarSync.WINDOW_DAYS] of busy time) would otherwise stay on the server and
 * keep the user busy to their groups, so a revoked permission clears it.
 */
internal fun deviceSyncAction(signedIn: Boolean, hasPermission: Boolean, lastSyncAt: Long?): DeviceSyncAction = when {
    !signedIn -> DeviceSyncAction.Skip
    hasPermission -> DeviceSyncAction.Upload
    lastSyncAt != null -> DeviceSyncAction.Clear
    else -> DeviceSyncAction.Skip
}

/**
 * Whether an upload with fingerprint [hash] can be skipped: the last one sent exactly
 * this, less than [DeviceCalendarSync.MAX_UNCHANGED_AGE_MS] ago. The window's dates are
 * part of the payload, so it changes at least daily anyway; the age cap is a backstop.
 */
internal fun isUnchanged(hash: String, lastHash: String?, lastSyncAt: Long?, now: Long): Boolean =
    lastHash == hash && lastSyncAt != null && now - lastSyncAt in 0 until DeviceCalendarSync.MAX_UNCHANGED_AGE_MS

/**
 * Uploads busy time ranges (never titles) from the device calendars the user marked
 * as busy, for the next [WINDOW_DAYS] days, via `PUT /calendars/device`.
 *
 * Every upload makes the server tell each of the user's groups that availability
 * changed, so an upload identical to the last one is skipped (see [isUnchanged]).
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
            rangeEnd = DateTimes.toIso(today.plus(Duration.ofDays(WINDOW_DAYS))),
            calendars = emptyList(),
            busy = emptyList(),
        )
        return when (val result = calendars.uploadDeviceBusy(payload).onSuccess { prefs.clearLastDeviceSyncAt() }) {
            is ApiResult.Success -> DeviceSyncResult.Cleared
            is ApiResult.Failure -> DeviceSyncResult.Failed(result.error)
        }
    }

    private suspend fun upload(force: Boolean): DeviceSyncResult {
        val available = reader.calendars()
        val busyIds = busyCalendarIds(available)
        val now = Instant.now()
        // Start of today so events earlier today still count; end 120 days out.
        val rangeStart = DateTimes.today().atStartOfDay(DateTimes.zone).toInstant()
        val rangeEnd = now.plus(Duration.ofDays(WINDOW_DAYS)).truncatedTo(ChronoUnit.DAYS)

        val busy = reader.busyRanges(
            calendarIds = available.filter { it.externalId in busyIds }.map { it.id },
            from = rangeStart,
            to = rangeEnd,
        )
        val payload = DeviceSync(
            deviceId = prefs.deviceId(),
            deviceName = deviceName(context),
            rangeStart = DateTimes.toIso(rangeStart),
            rangeEnd = DateTimes.toIso(rangeEnd),
            calendars = available.map {
                DeviceCalendar(
                    externalId = it.externalId,
                    name = it.name.take(120),
                    color = it.color,
                    busy = it.externalId in busyIds,
                )
            },
            busy = busy.take(MAX_RANGES).map {
                DeviceBusy(
                    calendarExternalId = it.calendarId.toString(),
                    start = DateTimes.toIso(it.start),
                    end = DateTimes.toIso(it.end),
                )
            },
        )
        val hash = fingerprint(payload)
        val nowMs = System.currentTimeMillis()
        if (!force && isUnchanged(hash, prefs.lastDeviceSyncHash.first(), prefs.lastDeviceSyncAt.first(), nowMs)) {
            return DeviceSyncResult.Unchanged
        }
        return when (val result = calendars.uploadDeviceBusy(payload).onSuccess { prefs.setLastDeviceSyncAt(nowMs, hash) }) {
            is ApiResult.Success -> DeviceSyncResult.Synced(result.value, payload.busy.size)
            is ApiResult.Failure -> DeviceSyncResult.Failed(result.error)
        }
    }

    private fun fingerprint(payload: DeviceSync): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(json.encodeToString(DeviceSync.serializer(), payload).toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val WINDOW_DAYS = 120L
        /** Upload at least this often even when nothing changed. */
        const val MAX_UNCHANGED_AGE_MS = 24L * 60 * 60 * 1000
        /** Server limit on `busy` entries per upload. */
        private const val MAX_RANGES = 20_000
    }
}
