package dev.joeherbert.availabliss.core.calendar

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.joeherbert.availabliss.core.common.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

/** A calendar on the device (CalendarContract.Calendars). */
data class DeviceCalendarInfo(
    val id: Long,
    val name: String,
    val accountName: String,
    val accountType: String,
    /** `#RRGGBB` */
    val color: String?,
    val writable: Boolean,
    val isPrimary: Boolean,
) {
    val externalId: String get() = id.toString()

    /** Calendars that rarely mean "busy" (birthdays, public holidays) start switched off. */
    val busyByDefault: Boolean
        get() {
            val lower = name.lowercase()
            return !(lower.contains("birthday") || lower.contains("holiday") || accountType == "com.android.contacts")
        }
}

/** A busy time range from a device calendar. Titles are never read. */
data class DeviceBusyRange(val calendarId: Long, val start: Instant, val end: Instant)

/** Reads calendars and event instances from [CalendarContract]. Requires READ_CALENDAR. */
@Singleton
class DeviceCalendarReader @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    fun hasReadPermission(): Boolean = granted(Manifest.permission.READ_CALENDAR)

    fun hasWritePermission(): Boolean = granted(Manifest.permission.WRITE_CALENDAR)

    suspend fun calendars(): List<DeviceCalendarInfo> = withContext(io) {
        if (!hasReadPermission()) return@withContext emptyList()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.CALENDAR_COLOR,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
            CalendarContract.Calendars.IS_PRIMARY,
        )
        val result = mutableListOf<DeviceCalendarInfo>()
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            "${CalendarContract.Calendars.VISIBLE} = 1",
            null,
            "${CalendarContract.Calendars.ACCOUNT_NAME} ASC, ${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                val access = c.getInt(5)
                result += DeviceCalendarInfo(
                    id = c.getLong(0),
                    name = c.getString(1) ?: "Calendar",
                    accountName = c.getString(2).orEmpty(),
                    accountType = c.getString(3).orEmpty(),
                    color = if (c.isNull(4)) null else "#%06X".format(c.getInt(4) and 0xFFFFFF),
                    writable = access >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR,
                    isPrimary = !c.isNull(6) && c.getInt(6) == 1,
                )
            }
        }
        result
    }

    /**
     * Busy instances in [from, to) for [calendarIds]. Skips events marked "free",
     * cancelled events and invitations the user declined. All-day events cover the
     * whole local day(s).
     */
    suspend fun busyRanges(calendarIds: Collection<Long>, from: Instant, to: Instant, zone: ZoneId = ZoneId.systemDefault()): List<DeviceBusyRange> =
        withContext(io) {
            if (!hasReadPermission() || calendarIds.isEmpty()) return@withContext emptyList()
            val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
                ContentUris.appendId(it, from.toEpochMilli())
                ContentUris.appendId(it, to.toEpochMilli())
            }.build()
            val projection = arrayOf(
                CalendarContract.Instances.CALENDAR_ID,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.AVAILABILITY,
                CalendarContract.Instances.STATUS,
                CalendarContract.Instances.SELF_ATTENDEE_STATUS,
            )
            val placeholders = calendarIds.joinToString(",") { "?" }
            val selection = "${CalendarContract.Instances.CALENDAR_ID} IN ($placeholders)"
            val args = calendarIds.map { it.toString() }.toTypedArray()
            val ranges = mutableListOf<DeviceBusyRange>()
            context.contentResolver.query(uri, projection, selection, args, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
                while (c.moveToNext()) {
                    val availability = if (c.isNull(4)) CalendarContract.Events.AVAILABILITY_BUSY else c.getInt(4)
                    val status = if (c.isNull(5)) CalendarContract.Events.STATUS_CONFIRMED else c.getInt(5)
                    val self = if (c.isNull(6)) CalendarContract.Attendees.ATTENDEE_STATUS_NONE else c.getInt(6)
                    if (availability == CalendarContract.Events.AVAILABILITY_FREE) continue
                    if (status == CalendarContract.Events.STATUS_CANCELED) continue
                    if (self == CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED) continue

                    val allDay = c.getInt(3) == 1
                    var start = Instant.ofEpochMilli(c.getLong(1))
                    var end = Instant.ofEpochMilli(c.getLong(2))
                    if (allDay) {
                        // All-day instances are stored at UTC midnight; re-anchor to local midnight.
                        start = start.atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(zone).toInstant()
                        end = end.atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(zone).toInstant()
                    }
                    if (!end.isAfter(start)) continue
                    ranges += DeviceBusyRange(c.getLong(0), start, end)
                }
            }
            ranges
        }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
