package dev.joeherbert.availabliss.core.calendar

import android.content.ContentUris
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
    /** In the calendar as row [eventId] of `CalendarContract.Events`. */
    data class Inserted(val eventId: Long) : InsertResult
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
    fun canReadCalendars(): Boolean = reader.hasReadPermission()

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
            val id = uri?.let { runCatching { ContentUris.parseId(it) }.getOrNull() }
            if (id != null) InsertResult.Inserted(id) else InsertResult.NeedsIntent(insertIntent(draft))
        }
    }

    /**
     * The calendar event [eventId] is in, or null when it's gone (deleted in the calendar app)
     * or can't be read without permission.
     */
    suspend fun calendarOf(eventId: Long): Long? = withContext(io) {
        if (!reader.hasReadPermission()) return@withContext null
        runCatching {
            context.contentResolver.query(
                ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
                arrayOf(CalendarContract.Events.CALENDAR_ID, CalendarContract.Events.DELETED),
                null, null, null,
            )?.use { c -> if (c.moveToFirst() && c.getInt(1) == 0) c.getLong(0) else null }
        }.getOrNull()
    }

    /**
     * Moves event [eventId] to [calendarId]. Calendar providers don't reliably move an event
     * between accounts in place, so it's copied into the new calendar and the old one deleted.
     * Returns the new event's id, or null if it couldn't be moved.
     */
    suspend fun move(eventId: Long, calendarId: Long): Long? = withContext(io) {
        if (!reader.hasWritePermission()) return@withContext null
        val columns = arrayOf(
            CalendarContract.Events.TITLE,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.EVENT_TIMEZONE,
            CalendarContract.Events.AVAILABILITY,
        )
        val resolver = context.contentResolver
        val oldUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        runCatching {
            val values = resolver.query(oldUri, columns, null, null, null)?.use { c ->
                if (!c.moveToFirst()) return@use null
                ContentValues().apply {
                    put(CalendarContract.Events.CALENDAR_ID, calendarId)
                    put(CalendarContract.Events.TITLE, c.getString(0))
                    c.getString(1)?.let { put(CalendarContract.Events.EVENT_LOCATION, it) }
                    c.getString(2)?.let { put(CalendarContract.Events.DESCRIPTION, it) }
                    put(CalendarContract.Events.DTSTART, c.getLong(3))
                    put(CalendarContract.Events.DTEND, c.getLong(4))
                    put(CalendarContract.Events.ALL_DAY, c.getInt(5))
                    put(CalendarContract.Events.EVENT_TIMEZONE, c.getString(6) ?: TimeZone.getDefault().id)
                    put(CalendarContract.Events.AVAILABILITY, c.getInt(7))
                }
            } ?: return@runCatching null
            val newId = resolver.insert(CalendarContract.Events.CONTENT_URI, values)?.let(ContentUris::parseId) ?: return@runCatching null
            resolver.delete(oldUri, null, null)
            newId
        }.getOrNull()
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
