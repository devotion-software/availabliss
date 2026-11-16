package dev.joeherbert.availabliss.core.common

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Editable start/end of a date range, shared by the block sheet, the event editor and
 * "Propose a time" (see `DateRangeInput`). For all-day ranges [endDate] is the last day
 * (inclusive); the wire format is end-exclusive at local midnight.
 *
 * Rules: moving the start keeps the length (the end moves by the same amount); the end
 * can't be put before the start (a timed end that would be keeps the old length instead).
 */
data class TimeRangeDraft(
    val startDate: LocalDate,
    val startTime: LocalTime,
    val endDate: LocalDate,
    val endTime: LocalTime,
    val allDay: Boolean,
) {
    /** Local start: midnight on [startDate] when all day. */
    val start: LocalDateTime get() = if (allDay) startDate.atStartOfDay() else startDate.atTime(startTime)

    /** Local end: the midnight after [endDate] when all day. */
    val end: LocalDateTime get() = if (allDay) endDate.plusDays(1).atStartOfDay() else endDate.atTime(endTime)

    val isValid: Boolean get() = end.isAfter(start)

    /** All day over more than one day. */
    val isMultiDay: Boolean get() = allDay && endDate.isAfter(startDate)

    fun startIso(zone: ZoneId = DateTimes.zone): String = DateTimes.toIso(start, zone)
    fun endIso(zone: ZoneId = DateTimes.zone): String = DateTimes.toIso(end, zone)

    /** "Fri 13 – Sun 15 Nov", "Wed 11 Nov · 18:30 – 21:00" */
    val label: String get() = DateTimes.rangeLabel(startIso(), endIso(), allDay)

    /** The timed length, at least [MIN_LENGTH] (2 hours if the range is empty). */
    private val timedLength: Duration
        get() {
            val length = Duration.between(startDate.atTime(startTime), endDate.atTime(endTime))
            return when {
                length <= Duration.ZERO -> Duration.ofHours(2)
                length < MIN_LENGTH -> MIN_LENGTH
                else -> length
            }
        }

    /** Moves the start (date and/or time), keeping the length: the end moves by the same amount. */
    fun withStart(date: LocalDate, time: LocalTime = startTime): TimeRangeDraft {
        val days = ChronoUnit.DAYS.between(startDate, endDate).coerceAtLeast(0)
        val newEnd = date.atTime(time).plus(timedLength)
        return if (allDay) {
            // Keep the day count; the hidden times follow so switching all day off stays sensible.
            copy(startDate = date, startTime = time, endDate = date.plusDays(days), endTime = newEnd.toLocalTime())
        } else {
            copy(startDate = date, startTime = time, endDate = newEnd.toLocalDate(), endTime = newEnd.toLocalTime())
        }
    }

    /**
     * Moves the end. All day: never before the start date. Timed: an end at or before the
     * start is replaced by start + the previous length.
     */
    fun withEnd(date: LocalDate, time: LocalTime = endTime): TimeRangeDraft {
        if (allDay) return copy(endDate = if (date.isBefore(startDate)) startDate else date, endTime = time)
        val moved = copy(endDate = date, endTime = time)
        if (moved.isValid) return moved
        val newEnd = start.plus(timedLength)
        return copy(endDate = newEnd.toLocalDate(), endTime = newEnd.toLocalTime())
    }

    /**
     * Switches all day on or off. Turning it off on a range with no real times (midnight to
     * midnight, e.g. one that was always all day) gives 09:00 – 10:00 on the first day.
     */
    fun withAllDay(value: Boolean): TimeRangeDraft {
        if (value == allDay) return this
        if (value) return copy(allDay = true, endDate = if (endDate.isBefore(startDate)) startDate else endDate)
        val timed = copy(allDay = false)
        val noTimes = startTime == LocalTime.MIDNIGHT && endTime == LocalTime.MIDNIGHT
        return if (noTimes || !timed.isValid) {
            copy(allDay = false, startTime = DEFAULT_START, endDate = startDate, endTime = DEFAULT_START.plusHours(1))
        } else {
            timed
        }
    }

    companion object {
        /** The shortest timed range an end change can leave. */
        val MIN_LENGTH: Duration = Duration.ofMinutes(15)

        private val DEFAULT_START: LocalTime = LocalTime.of(9, 0)

        /** A timed range from [start] to [end]. */
        fun timed(start: LocalDateTime, end: LocalDateTime): TimeRangeDraft =
            TimeRangeDraft(start.toLocalDate(), start.toLocalTime(), end.toLocalDate(), end.toLocalTime(), allDay = false)

        /** All day [first]..[last] inclusive (hidden times 09:00 – 10:00 for switching all day off). */
        fun allDay(first: LocalDate, last: LocalDate = first): TimeRangeDraft =
            TimeRangeDraft(first, DEFAULT_START, if (last.isBefore(first)) first else last, DEFAULT_START.plusHours(1), allDay = true)

        /** From ISO timestamps (e.g. a slot); all-day ends at midnight become the previous day. */
        fun fromIso(startIso: String?, endIso: String?, allDay: Boolean, zone: ZoneId = DateTimes.zone): TimeRangeDraft? {
            val s = startIso?.let { DateTimes.localDateTime(it, zone) } ?: return null
            val e = endIso?.let { DateTimes.localDateTime(it, zone) } ?: s.plusHours(2)
            val endDate = if (allDay && e.toLocalTime() == LocalTime.MIDNIGHT && e.toLocalDate().isAfter(s.toLocalDate())) {
                e.toLocalDate().minusDays(1)
            } else {
                e.toLocalDate()
            }
            return TimeRangeDraft(s.toLocalDate(), s.toLocalTime(), endDate, e.toLocalTime(), allDay)
        }

        /** Tomorrow 18:00 – 20:00. */
        fun default(): TimeRangeDraft {
            val day = DateTimes.today().plusDays(1)
            return TimeRangeDraft(day, LocalTime.of(18, 0), day, LocalTime.of(20, 0), allDay = false)
        }
    }
}
