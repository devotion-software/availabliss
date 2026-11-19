package dev.joeherbert.availabliss.feature.mytime

import dev.joeherbert.availabliss.core.common.DateTimes
import dev.joeherbert.availabliss.core.network.model.BusyInterval
import dev.joeherbert.availabliss.core.network.model.BusySource
import java.time.LocalDate
import kotlin.math.roundToInt

/*
 * "My time" per-day summary: the busy time from 06:00 to 24:00, how much of it is
 * free and a one-line summary ("Free from 18:00"). Pure, so it is unit-testable.
 */

/** The part of the day the summary and the free-time level look at: 06:00 – 24:00. */
const val STRIP_FROM = 6 * 60

/** Free gaps shorter than this don't count as free in the summary (a minute between two things isn't free). */
const val MIN_FREE_MINUTES = 15

/** Busy minutes [from]..[to] on a day's 24-hour axis. [manual] = blocked by you. */
data class BusySpan(val from: Int, val to: Int, val manual: Boolean = false) {
    val minutes: Int get() = to - from
}

/** The one-line summary of a day. */
sealed interface DaySummary {
    val label: String

    data object FreeAllDay : DaySummary {
        override val label = "Free all day"
    }

    data object BusyAllDay : DaySummary {
        override val label = "Busy all day"
    }

    data class FreeFrom(val from: Int) : DaySummary {
        override val label get() = "Free from ${clock(from.toFloat())}"
    }

    data class FreeUntil(val to: Int) : DaySummary {
        override val label get() = "Free until ${clock(to.toFloat())}"
    }

    data class FreeBetween(val from: Int, val to: Int) : DaySummary {
        override val label get() = "Free ${clock(from.toFloat())} – ${clock(to.toFloat())}"
    }
}

/** One day, summarised (the month cell, the agenda's day header and the week strip). */
data class DayRowUi(
    val date: LocalDate,
    /** Calendar busy time, merged. */
    val calendarSpans: List<BusySpan>,
    /** Your own blocks, merged. */
    val manualSpans: List<BusySpan>,
    val summary: DaySummary,
    /** Names of your blocks that day ("Choir, Gym"), or null. */
    val names: String?,
    val accessibility: String,
    /** Free minutes between 06:00 and 24:00. */
    val freeMinutes: Int = WINDOW_MINUTES,
) {
    /** You've blocked some of this day yourself. */
    val hasBlock: Boolean get() = manualSpans.isNotEmpty()

    /** A connected calendar makes you busy some of this day. */
    val hasCalendarBusy: Boolean get() = calendarSpans.isNotEmpty()

    /** How free the day is, 0 (busy all day) to 4 (nothing on). */
    val level: Int get() = freeLevel(freeMinutes)
}

/** Length of the 06:00 – 24:00 window. */
const val WINDOW_MINUTES = DAY_MINUTES - STRIP_FROM

/** Free minutes between 06:00 and 24:00 given the day's [busy] spans (any order, may overlap). */
fun freeMinutes(busy: List<BusySpan>): Int {
    val inWindow = mergeSpans(
        busy.mapNotNull { s ->
            val from = maxOf(s.from, STRIP_FROM)
            val to = minOf(s.to, DAY_MINUTES)
            if (to > from) BusySpan(from, to) else null
        }
    )
    return WINDOW_MINUTES - inWindow.sumOf { it.minutes }
}

/**
 * The month's fill for a day, by time busy between 06:00 and 24:00: nothing → 4, under 3h → 3,
 * under 8h → 2, 30 min+ still free → 1, otherwise 0. Graded by busy time so an hour's plan already
 * shows against a free day.
 */
fun freeLevel(freeMinutes: Int): Int {
    val busy = WINDOW_MINUTES - freeMinutes
    return when {
        busy <= 0 -> 4
        busy < 3 * 60 -> 3
        busy < 8 * 60 -> 2
        freeMinutes >= 30 -> 1
        else -> 0
    }
}

/** Merges overlapping or touching spans; the result is sorted. */
fun mergeSpans(spans: List<BusySpan>): List<BusySpan> {
    val out = ArrayList<BusySpan>()
    for (span in spans.filter { it.to > it.from }.sortedBy { it.from }) {
        val last = out.lastOrNull()
        if (last != null && span.from <= last.to) {
            out[out.lastIndex] = last.copy(to = maxOf(last.to, span.to))
        } else {
            out += span
        }
    }
    return out
}

/**
 * The longest free stretch between 06:00 and 24:00 given the day's merged [busy] spans.
 * Ties go to the later stretch (evenings matter most when making plans).
 */
fun summarise(busy: List<BusySpan>): DaySummary {
    val inWindow = mergeSpans(
        busy.mapNotNull { s ->
            val from = maxOf(s.from, STRIP_FROM)
            val to = minOf(s.to, DAY_MINUTES)
            if (to > from) BusySpan(from, to) else null
        }
    )
    if (inWindow.isEmpty()) return DaySummary.FreeAllDay
    val free = ArrayList<BusySpan>()
    var cursor = STRIP_FROM
    inWindow.forEach { span ->
        if (span.from > cursor) free += BusySpan(cursor, span.from)
        cursor = maxOf(cursor, span.to)
    }
    if (cursor < DAY_MINUTES) free += BusySpan(cursor, DAY_MINUTES)
    val best = free.filter { it.minutes >= MIN_FREE_MINUTES }
        .maxWithOrNull(compareBy<BusySpan> { it.minutes }.thenBy { it.from })
        ?: return DaySummary.BusyAllDay
    return when {
        best.to == DAY_MINUTES -> DaySummary.FreeFrom(best.from)
        best.from == STRIP_FROM -> DaySummary.FreeUntil(best.to)
        else -> DaySummary.FreeBetween(best.from, best.to)
    }
}

/**
 * Builds the row for [date] from its all-day items and timed pieces (see [layoutDays]).
 * Time marked not busy is left out.
 */
fun dayRow(date: LocalDate, allDay: List<BusyInterval>, timed: List<PlacedInterval>, today: LocalDate? = null): DayRowUi {
    val counted = allDay.filterNot { it.isIgnored }.map { BusySpan(0, DAY_MINUTES, it.source == BusySource.Manual) } +
        timed.filterNot { it.interval.isIgnored }.map { BusySpan(it.from.roundToInt(), it.to.roundToInt(), it.interval.source == BusySource.Manual) }
    val calendar = mergeSpans(counted.filterNot { it.manual })
    val manual = mergeSpans(counted.filter { it.manual }).map { it.copy(manual = true) }
    val busy = mergeSpans(counted.map { it.copy(manual = false) })
    val summary = summarise(busy)
    val names = (
        allDay.filterNot { it.isIgnored }.map { it.name } +
            timed.filterNot { it.interval.isIgnored }.sortedBy { it.from }.map { it.interval.name }
        )
        .mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
        .distinct()
        .take(3)
        .joinToString(", ")
        .ifEmpty { null }
    return DayRowUi(
        date = date,
        calendarSpans = calendar,
        manualSpans = manual,
        summary = summary,
        names = names,
        accessibility = describeDay(date, busy, summary, names, isToday = date == today),
        freeMinutes = freeMinutes(busy),
    )
}

/** "Monday 17 November, busy 09:00 to 17:00 and 19:00 to 21:30, free from 21:30". */
internal fun describeDay(date: LocalDate, busy: List<BusySpan>, summary: DaySummary, names: String?, isToday: Boolean = false): String {
    fun spoken(minutes: Int) = if (minutes >= DAY_MINUTES) "midnight" else clock(minutes.toFloat())
    val parts = mutableListOf((if (isToday) "Today, " else "") + DateTimes.longDay(date))
    when {
        busy.isEmpty() -> parts += "free all day"
        busy.size == 1 && busy[0].from <= 0 && busy[0].to >= DAY_MINUTES -> parts += "busy all day"
        else -> {
            val ranges = busy.map { "${spoken(it.from)} to ${spoken(it.to)}" }
            parts += "busy " + if (ranges.size == 1) ranges[0] else ranges.dropLast(1).joinToString(", ") + " and " + ranges.last()
            when (summary) {
                DaySummary.FreeAllDay -> parts += "free from ${clock(STRIP_FROM.toFloat())}"
                DaySummary.BusyAllDay -> Unit
                is DaySummary.FreeFrom -> parts += "free from ${spoken(summary.from)}"
                is DaySummary.FreeUntil -> parts += "free until ${spoken(summary.to)}"
                is DaySummary.FreeBetween -> parts += "free ${spoken(summary.from)} to ${spoken(summary.to)}"
            }
        }
    }
    names?.let { parts += it }
    return parts.joinToString(", ")
}

/** "This week", "Next week", then "17 – 23 Nov". [weekStart] is a Monday. */
fun weekHeading(weekStart: LocalDate, today: LocalDate): String {
    val thisWeek = DateTimes.startOfWeek(today)
    return when (weekStart) {
        thisWeek -> "This week"
        thisWeek.plusWeeks(1) -> "Next week"
        else -> DateTimes.weekRange(weekStart, weekStart.plusDays(6))
    }
}

/** The last day (a Sunday) of the week [date] is in. */
fun endOfWeek(date: LocalDate): LocalDate = DateTimes.startOfWeek(date).plusDays(6)

/** Splits [from]..[to] (inclusive) into pieces of at most [maxDays] days, for the busy endpoint's limit. */
fun dateChunks(from: LocalDate, to: LocalDate, maxDays: Int): List<ClosedRange<LocalDate>> {
    val out = ArrayList<ClosedRange<LocalDate>>()
    var start = from
    while (!start.isAfter(to)) {
        val end = minOf(start.plusDays(maxDays - 1L), to)
        out += start..end
        start = end.plusDays(1)
    }
    return out
}
