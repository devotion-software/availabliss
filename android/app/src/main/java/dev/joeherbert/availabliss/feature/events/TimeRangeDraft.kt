package dev.joeherbert.availabliss.feature.events

import dev.joeherbert.availabliss.core.common.DateTimes
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * Editable start/end of a plan, shared by the event editor and "Propose a time".
 * For all-day plans [endDate] is the last day (inclusive); the wire format is
 * end-exclusive at local midnight.
 */
data class TimeRangeDraft(
    val startDate: LocalDate,
    val startTime: LocalTime,
    val endDate: LocalDate,
    val endTime: LocalTime,
    val allDay: Boolean,
) {
    private val start: LocalDateTime get() = if (allDay) startDate.atStartOfDay() else startDate.atTime(startTime)
    private val end: LocalDateTime get() = if (allDay) endDate.plusDays(1).atStartOfDay() else endDate.atTime(endTime)

    val isValid: Boolean get() = end.isAfter(start)

    fun startIso(): String = DateTimes.toIso(start)
    fun endIso(): String = DateTimes.toIso(end)

    /** "Fri 13 – Sun 15 Nov", "Wed 11 Nov · 18:30 – 21:00" */
    val label: String get() = DateTimes.rangeLabel(startIso(), endIso(), allDay)

    val startLabel: String get() = if (allDay) DateTimes.shortDay(startDate) else "${DateTimes.shortDay(startDate)} · ${DateTimes.time(startTime)}"
    val endLabel: String get() = if (allDay) DateTimes.shortDay(endDate) else "${DateTimes.shortDay(endDate)} · ${DateTimes.time(endTime)}"

    /** Moves the start, keeping the end after it (shifting the end by the same amount if needed). */
    fun withStart(date: LocalDate, time: LocalTime = startTime): TimeRangeDraft {
        val moved = copy(startDate = date, startTime = time)
        if (moved.isValid) return moved
        return if (allDay) {
            val days = ChronoUnit.DAYS.between(startDate, endDate).coerceAtLeast(0)
            moved.copy(endDate = date.plusDays(days))
        } else {
            val length = Duration.between(start, end).takeIf { it > Duration.ZERO } ?: Duration.ofHours(2)
            val newEnd = date.atTime(time).plus(length)
            moved.copy(endDate = newEnd.toLocalDate(), endTime = newEnd.toLocalTime())
        }
    }

    fun withEnd(date: LocalDate, time: LocalTime = endTime): TimeRangeDraft = copy(endDate = date, endTime = time)

    fun withAllDay(value: Boolean): TimeRangeDraft = copy(allDay = value)

    companion object {
        /** From ISO timestamps (e.g. a slot); all-day ends at midnight become the previous day. */
        fun fromIso(startIso: String?, endIso: String?, allDay: Boolean): TimeRangeDraft? {
            val s = startIso?.let { DateTimes.localDateTime(it) } ?: return null
            val e = endIso?.let { DateTimes.localDateTime(it) } ?: s.plusHours(2)
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
