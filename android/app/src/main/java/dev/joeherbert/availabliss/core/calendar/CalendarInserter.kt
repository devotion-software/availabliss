package dev.joeherbert.availabliss.core.calendar

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.joeherbert.availabliss.core.common.DateTimes
import dev.joeherbert.availabliss.core.common.IoDispatcher
import dev.joeherbert.availabliss.core.network.model.GroupEvent
import dev.joeherbert.availabliss.core.network.model.Proposal
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/** Everything needed to put a plan in the user's own calendar. */
data class CalendarEventDraft(
    val title: String,
    val location: String?,
    val note: String?,
    val startMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
) {
    companion object {
        fun from(event: GroupEvent): CalendarEventDraft? =
            build(event.title, event.location, event.note, event.start, event.end, event.allDay)

        fun from(proposal: Proposal, fallbackTitle: String): CalendarEventDraft? =
            build(proposal.title ?: fallbackTitle, proposal.location, proposal.note, proposal.start, proposal.end, proposal.allDay)

        private fun build(title: String, location: String?, note: String?, start: String, end: String, allDay: Boolean): CalendarEventDraft? {
            val s = DateTimes.parseInstant(start) ?: return null
            val e = DateTimes.parseInstant(end) ?: return null
            return CalendarEventDraft(title, location, note, s.toEpochMilli(), e.toEpochMilli(), allDay)
        }
    }
}

/** Result of [CalendarInserter.insert]. */
sealed interface InsertResult {
    data object Inserted : InsertResult
    /** No write access or no target calendar: hand off to the calendar app with this intent. */
    data class NeedsIntent(val intent: Intent) : InsertResult
}

/**
 * One-tap "Add to calendar": inserts directly when WRITE_CALENDAR is granted and a
 * calendar is chosen, otherwise builds an `ACTION_INSERT` intent for the calendar app.
 */
@Singleton
class CalendarInserter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val reader: DeviceCalendarReader,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    /** Calendars the user can add events to (requires READ_CALENDAR). */
    suspend fun writableCalendars(): List<DeviceCalendarInfo> = reader.calendars().filter { it.writable }

    suspend fun insert(draft: CalendarEventDraft, calendarId: Long?): InsertResult {
        if (calendarId == null || !reader.hasWritePermission()) return InsertResult.NeedsIntent(insertIntent(draft))
        return withContext(io) {
            val (start, end, tz) = draft.normalised()
            val values = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.TITLE, draft.title)
                draft.location?.let { put(CalendarContract.Events.EVENT_LOCATION, it) }
                draft.note?.let { put(CalendarContract.Events.DESCRIPTION, it) }
                put(CalendarContract.Events.DTSTART, start)
                put(CalendarContract.Events.DTEND, end)
                put(CalendarContract.Events.ALL_DAY, if (draft.allDay) 1 else 0)
                put(CalendarContract.Events.EVENT_TIMEZONE, tz)
                put(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_BUSY)
            }
            val uri = runCatching { context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values) }.getOrNull()
            if (uri != null) InsertResult.Inserted else InsertResult.NeedsIntent(insertIntent(draft))
        }
    }

    fun insertIntent(draft: CalendarEventDraft): Intent =
        Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI).apply {
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, draft.startMillis)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, draft.endMillis)
            putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, draft.allDay)
            putExtra(CalendarContract.Events.TITLE, draft.title)
            draft.location?.let { putExtra(CalendarContract.Events.EVENT_LOCATION, it) }
            draft.note?.let { putExtra(CalendarContract.Events.DESCRIPTION, it) }
            putExtra(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_BUSY)
        }

    /** All-day events must be stored at UTC midnight with an end-exclusive date. */
    private fun CalendarEventDraft.normalised(): Triple<Long, Long, String> {
        if (!allDay) return Triple(startMillis, endMillis, TimeZone.getDefault().id)
        val zone = DateTimes.zone
        val startDate = java.time.Instant.ofEpochMilli(startMillis).atZone(zone).toLocalDate()
        val endZoned = java.time.Instant.ofEpochMilli(endMillis).atZone(zone)
        var endDate = endZoned.toLocalDate()
        if (endZoned.toLocalTime() != LocalTime.MIDNIGHT) endDate = endDate.plusDays(1)
        if (!endDate.isAfter(startDate)) endDate = startDate.plusDays(1)
        return Triple(
            startDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            endDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            "UTC",
        )
    }
}
