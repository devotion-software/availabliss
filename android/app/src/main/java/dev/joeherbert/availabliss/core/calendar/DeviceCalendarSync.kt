package dev.joeherbert.availabliss.core.calendar

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
import kotlinx.coroutines.flow.Flow
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
    /** Not signed in, or READ_CALENDAR not granted: nothing to do. */
    data object Skipped : DeviceSyncResult
    data class Failed(val error: ApiError) : DeviceSyncResult
}

/**
 * Uploads busy time ranges (never titles) from the device calendars the user marked
 * as busy, for the next [WINDOW_DAYS] days, via `PUT /calendars/device`.
 */
@Singleton
class DeviceCalendarSync @Inject constructor(
    @ApplicationContext private val context: Context,
    private val reader: DeviceCalendarReader,
    private val prefs: AppPreferences,
    private val calendars: CalendarsRepository,
    private val tokenStore: TokenStore,
) {
    private val mutex = Mutex()

    /** Device calendar ids that count as busy: the user's choice, or sensible defaults. */
    suspend fun busyCalendarIds(available: List<DeviceCalendarInfo>): Set<String> =
        prefs.busyDeviceCalendarIds.first()
            ?: available.filter { it.busyByDefault }.map { it.externalId }.toSet()

    val selection: Flow<Set<String>?> get() = prefs.busyDeviceCalendarIds

    suspend fun setBusyCalendarIds(ids: Set<String>) = prefs.setBusyDeviceCalendarIds(ids)

    suspend fun sync(): DeviceSyncResult = mutex.withLock { syncLocked() }

    private suspend fun syncLocked(): DeviceSyncResult {
        if (tokenStore.token() == null || !reader.hasReadPermission()) return DeviceSyncResult.Skipped

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
        return when (val result = calendars.uploadDeviceBusy(payload).onSuccess { prefs.setLastDeviceSyncAt(System.currentTimeMillis()) }) {
            is ApiResult.Success -> DeviceSyncResult.Synced(result.value, payload.busy.size)
            is ApiResult.Failure -> DeviceSyncResult.Failed(result.error)
        }
    }

    companion object {
        const val WINDOW_DAYS = 120L
        /** Server limit on `busy` entries per upload. */
        private const val MAX_RANGES = 20_000
    }
}
