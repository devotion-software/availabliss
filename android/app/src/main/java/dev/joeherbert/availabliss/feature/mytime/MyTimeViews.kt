package dev.joeherbert.availabliss.feature.mytime

import dev.joeherbert.availabliss.core.common.DateTimes
import dev.joeherbert.availabliss.core.network.model.BusyInterval
import dev.joeherbert.availabliss.core.network.model.BusySource
import dev.joeherbert.availabliss.core.network.model.Repeat
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToInt

/*
 * The "My time" views' pure parts: the month heat map's cells, the agenda's rows and the
 * week view's stepping. Unit-testable.
 */

/** How far ahead My time goes: today + 365 days. */
const val MY_TIME_DAYS_AHEAD = 365L

/** The month (default) or the agenda; the week view opens from either. */
enum class MyTimeMode { Month, Agenda }

// region Month
/** One day in the month heat map. */
data class MonthDayUi(
    val date: LocalDate,
    /** 0 (busy all day, not loaded yet, or past) to 3 (12 hours or more free). */
    val level: Int,
    val past: Boolean,
    val today: Boolean,
    /** One of your own blocks is on this day (a dot under the number). */
    val hasBlock: Boolean,
    val accessibility: String,
)

/**
 * The weeks (Monday first) of [month]; null cells are outside it. Days not in [rows] (not loaded
 * yet) read as level 0; days before [today] are past.
 */
fun monthWeeks(month: YearMonth, rows: Map<LocalDate, DayRowUi>, today: LocalDate): List<List<MonthDayUi?>> {
    val first = month.atDay(1)
    val lead = DateTimes.apiWeekday(first)
    val cells = ArrayList<MonthDayUi?>()
    repeat(lead) { cells += null }
    for (d in 1..month.lengthOfMonth()) {
        val date = month.atDay(d)
        val past = date.isBefore(today)
        val row = rows[date].takeUnless { past }
        cells += MonthDayUi(
            date = date,
            level = row?.level ?: 0,
            past = past,
            today = date == today,
            hasBlock = row?.hasBlock == true,
            accessibility = monthDayLabel(date, row, past, date == today),
        )
    }
    while (cells.size % 7 != 0) cells += null
    return cells.chunked(7)
}

/** "Monday 23 November, free from 17:30"; "Today, …"; "…, past". */
internal fun monthDayLabel(date: LocalDate, row: DayRowUi?, past: Boolean, today: Boolean): String {
    val parts = mutableListOf((if (today) "Today, " else "") + DateTimes.longDay(date))
    when {
        past -> parts += "past"
        row != null -> {
            parts += row.summary.label.replaceFirstChar { it.lowercase() }
            row.names?.let { parts += it }
        }
    }
    return parts.joinToString(", ")
}

/** Months the month view can show: today's month to the month of today + 365 days. */
fun monthRange(today: LocalDate): ClosedRange<YearMonth> = YearMonth.from(today)..YearMonth.from(today.plusDays(MY_TIME_DAYS_AHEAD))
// endregion

// region Agenda
/** Where a busy item comes from, as the agenda's small tag says. */
enum class AgendaTag(val label: String) {
    Weekly("Weekly"),
    Fortnightly("Every other week"),
    Block("Your block"),
    Calendar("Calendar"),
}

/** One busy item under a day in the agenda. [from] / [to] are minutes on the day (to may be 1440). */
data class AgendaItem(
    val interval: BusyInterval,
    val allDay: Boolean,
    val from: Int,
    val to: Int,
    val title: String,
    val tag: AgendaTag,
) {
    val manual: Boolean get() = interval.source == BusySource.Manual
}

/**
 * A day's busy items for the agenda: all-day first, then in time order. Time marked not busy is
 * left out. [repeats] gives each of your blocks' repeat by block id.
 */
fun agendaItems(allDay: List<BusyInterval>, timed: List<PlacedInterval>, repeats: Map<String, Repeat>): List<AgendaItem> {
    fun tag(i: BusyInterval) = when {
        i.source == BusySource.Calendar -> AgendaTag.Calendar
        repeats[i.blockId] == Repeat.Weekly -> AgendaTag.Weekly
        repeats[i.blockId] == Repeat.Fortnightly -> AgendaTag.Fortnightly
        else -> AgendaTag.Block
    }
    fun title(i: BusyInterval) = if (i.source == BusySource.Manual) i.label?.trim()?.takeIf { it.isNotEmpty() } ?: "Busy" else "Busy"
    val whole = allDay.filterNot { it.isIgnored }.map { AgendaItem(it, true, 0, DAY_MINUTES, title(it), tag(it)) }
    val parts = timed.filterNot { it.interval.isIgnored }
        .sortedWith(compareBy<PlacedInterval> { it.from }.thenBy { it.to })
        .map { AgendaItem(it.interval, false, it.from.roundToInt(), it.to.roundToInt(), title(it.interval), tag(it.interval)) }
    return whole + parts
}

/** The block sheet for a day's longest free stretch ([summary]); null when busy all day. */
fun blockDraftForFree(date: LocalDate, summary: DaySummary): BlockDraft? = when (summary) {
    DaySummary.BusyAllDay -> null
    DaySummary.FreeAllDay -> BlockDraft.allDay(date)
    is DaySummary.FreeFrom -> BlockDraft.forRange(date, MinuteRange(summary.from, DAY_MINUTES))
    is DaySummary.FreeUntil -> BlockDraft.forRange(date, MinuteRange(STRIP_FROM, summary.to))
    is DaySummary.FreeBetween -> BlockDraft.forRange(date, MinuteRange(summary.from, summary.to))
}
// endregion

// region Week view
/** [day] moved by [days], or null when that's before [today] or after [last]. */
fun steppedDay(day: LocalDate, days: Long, today: LocalDate, last: LocalDate): LocalDate? =
    day.plusDays(days).takeIf { !it.isBefore(today) && !it.isAfter(last) }

/**
 * The same weekday [weeks] weeks on, clamped to [today]..[last]; null when there's no such week
 * (before today's week or past [last]'s).
 */
fun steppedWeek(day: LocalDate, weeks: Long, today: LocalDate, last: LocalDate): LocalDate? {
    val target = day.plusWeeks(weeks)
    val week = DateTimes.startOfWeek(target)
    if (week.isBefore(DateTimes.startOfWeek(today)) || week.isAfter(DateTimes.startOfWeek(last))) return null
    return target.coerceIn(today, last).takeIf { it != day }
}

/** "Today · Free from 17:30 · Choir". */
fun weekSummaryLine(row: DayRowUi?, isToday: Boolean): String {
    if (row == null) return "Loading…"
    return listOfNotNull(if (isToday) "Today" else null, row.summary.label, row.names).joinToString(" · ")
}
// endregion
