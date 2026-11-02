package dev.joeherbert.availabliss.core.common

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.TextStyle
import java.util.Locale

/**
 * Conversions between the API's wire formats and java.time, plus the display formats
 * used across the app ("Wed 11 Nov · 18:30", "2 – 8 November").
 *
 * API conventions: timestamps ISO 8601 UTC, dates `YYYY-MM-DD`, times `HH:MM`,
 * weekdays `0` = Monday … `6` = Sunday.
 */
object DateTimes {
    private val locale: Locale get() = Locale.getDefault()

    val zone: ZoneId get() = ZoneId.systemDefault()

    // region Parsing / formatting wire values
    fun parseInstant(value: String): Instant? = try {
        Instant.parse(value)
    } catch (e: DateTimeParseException) {
        runCatching { java.time.OffsetDateTime.parse(value).toInstant() }.getOrNull()
    }

    fun parseDate(value: String): LocalDate? = runCatching { LocalDate.parse(value) }.getOrNull()

    fun parseTime(value: String): LocalTime? = runCatching { LocalTime.parse(value) }.getOrNull()

    /** `2026-11-13T18:00:00.000Z` */
    fun toIso(instant: Instant): String = DateTimeFormatter.ISO_INSTANT.format(instant)

    fun toIso(dateTime: LocalDateTime, zone: ZoneId = this.zone): String = toIso(dateTime.atZone(zone).toInstant())

    /** `YYYY-MM-DD` */
    fun toWireDate(date: LocalDate): String = date.toString()

    /** `HH:MM` */
    fun toWireTime(time: LocalTime): String = "%02d:%02d".format(time.hour, time.minute)

    fun localDateTime(iso: String, zone: ZoneId = this.zone): LocalDateTime? =
        parseInstant(iso)?.atZone(zone)?.toLocalDateTime()

    fun localDate(iso: String, zone: ZoneId = this.zone): LocalDate? = localDateTime(iso, zone)?.toLocalDate()
    // endregion

    // region Weekdays (API: 0 = Monday)
    fun apiWeekday(day: DayOfWeek): Int = day.value - 1

    fun dayOfWeek(apiWeekday: Int): DayOfWeek = DayOfWeek.of(apiWeekday.coerceIn(0, 6) + 1)

    fun apiWeekday(date: LocalDate): Int = apiWeekday(date.dayOfWeek)

    fun shortWeekday(apiWeekday: Int): String = dayOfWeek(apiWeekday).getDisplayName(TextStyle.SHORT, locale)

    fun longWeekday(apiWeekday: Int): String = dayOfWeek(apiWeekday).getDisplayName(TextStyle.FULL, locale)

    /** Single-letter weekday, e.g. "M". */
    fun narrowWeekday(apiWeekday: Int): String = dayOfWeek(apiWeekday).getDisplayName(TextStyle.NARROW, locale)

    fun startOfWeek(date: LocalDate): LocalDate = date.minusDays(apiWeekday(date).toLong())
    // endregion

    // region Display formats
    private fun pattern(p: String) = DateTimeFormatter.ofPattern(p, locale)

    /** "Sunday 1 November" */
    fun longDay(date: LocalDate): String = pattern("EEEE d MMMM").format(date)

    /** "Wed 11 Nov" */
    fun shortDay(date: LocalDate): String = pattern("EEE d MMM").format(date)

    /** "Fri 13" */
    fun weekdayAndDay(date: LocalDate): String = pattern("EEE d").format(date)

    /** "13 Nov" */
    fun dayMonth(date: LocalDate): String = pattern("d MMM").format(date)

    /** "1 Nov 2026" */
    fun dayMonthYear(date: LocalDate): String = pattern("d MMM yyyy").format(date)

    /** "November 2026" */
    fun monthYear(date: LocalDate): String = pattern("MMMM yyyy").format(date)

    /** "Nov 2026", for tight headers. */
    fun shortMonthYear(date: LocalDate): String = pattern("MMM yyyy").format(date)

    /** "NOV" */
    fun monthAbbrev(date: LocalDate): String = pattern("MMM").format(date).uppercase(locale)

    /** "18:30" */
    fun time(dateTime: LocalDateTime): String = pattern("HH:mm").format(dateTime)

    fun time(time: LocalTime): String = pattern("HH:mm").format(time)

    /** "Wed 11 Nov · 18:30" (or just the day for all-day items). */
    fun dayAndTime(iso: String, allDay: Boolean = false): String {
        val dt = localDateTime(iso) ?: return ""
        return if (allDay) shortDay(dt.toLocalDate()) else "${shortDay(dt.toLocalDate())} · ${time(dt)}"
    }

    /** "2 – 8 Nov", "28 Oct – 3 Nov". */
    fun weekRange(start: LocalDate, end: LocalDate): String = when {
        start.month == end.month -> "${start.dayOfMonth} – ${pattern("d MMM").format(end)}"
        else -> "${dayMonth(start)} – ${dayMonth(end)}"
    }

    /**
     * "Fri 13 – Sun 15 Nov", "Wed 11 Nov · 18:30 – 21:00", "Mon 2 Nov · from 18:00", for
     * proposals and events. The end is exclusive, so a range ending at midnight belongs to
     * the day before (matching the server's labels).
     */
    fun rangeLabel(startIso: String, endIso: String, allDay: Boolean, zone: ZoneId = this.zone): String {
        val start = localDateTime(startIso, zone) ?: return ""
        val end = localDateTime(endIso, zone) ?: return shortDay(start.toLocalDate())
        val endsAtMidnight = end.toLocalTime() == LocalTime.MIDNIGHT
        val lastDay = if (endsAtMidnight) end.toLocalDate().minusDays(1) else end.toLocalDate()
        val firstDay = start.toLocalDate()
        return when {
            firstDay == lastDay && allDay -> shortDay(firstDay)
            firstDay == lastDay && endsAtMidnight -> "${shortDay(firstDay)} · from ${time(start)}"
            firstDay == lastDay -> "${shortDay(firstDay)} · ${time(start)} – ${time(end)}"
            firstDay.month == lastDay.month -> "${weekdayAndDay(firstDay)} – ${shortDay(lastDay)}"
            else -> "${shortDay(firstDay)} – ${shortDay(lastDay)}"
        }
    }

    /** "2h ago", "just now", "3d ago" */
    fun relative(iso: String, now: Instant = Instant.now()): String {
        val then = parseInstant(iso) ?: return ""
        val minutes = java.time.Duration.between(then, now).toMinutes()
        return when {
            minutes < 1 -> "just now"
            minutes < 60 -> "${minutes}m ago"
            minutes < 60 * 24 -> "${minutes / 60}h ago"
            minutes < 60 * 24 * 7 -> "${minutes / (60 * 24)}d ago"
            else -> shortDay(then.atZone(zone).toLocalDate())
        }
    }

    fun today(): LocalDate = LocalDate.now(zone)

    fun now(): ZonedDateTime = ZonedDateTime.now(zone)
    // endregion
}
