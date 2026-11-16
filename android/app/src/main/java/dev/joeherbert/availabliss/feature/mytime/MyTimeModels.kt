package dev.joeherbert.availabliss.feature.mytime

import dev.joeherbert.availabliss.core.common.DateTimes
import dev.joeherbert.availabliss.core.common.UserMessage
import dev.joeherbert.availabliss.core.network.model.BusyInterval
import dev.joeherbert.availabliss.core.network.model.BusySource
import dev.joeherbert.availabliss.core.network.model.CreateBlockRequest
import dev.joeherbert.availabliss.core.network.model.ManualBlock
import dev.joeherbert.availabliss.core.network.model.Repeat
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

/** A repeating manual block listed under the grid ("Choir · Every Tuesday · 19:00 – 21:30"). */
data class RecurringBlockUi(val id: String, val title: String, val schedule: String)

/** What a tap on busy time in the grid opens. */
sealed interface IntervalTap {
    /** Your own block: edit it (a repeating block edits the whole series). */
    data class EditBlock(val block: ManualBlock) : IntervalTap

    /** Synced calendar time: explain it can only be changed in the calendar. */
    data class CalendarBusy(val info: CalendarBusyUi) : IntervalTap

    /** The block isn't loaded (e.g. it was just removed). */
    data object MissingBlock : IntervalTap
}

/** The "Busy from your calendar" sheet. */
data class CalendarBusyUi(
    val calendarName: String,
    val timeLabel: String,
    /** The tapped interval, for "not busy" / "busy again". */
    val interval: BusyInterval? = null,
    val working: Boolean = false,
) {
    val ignored: Boolean get() = interval?.isIgnored == true
    val recurring: Boolean get() = interval?.recurring == true
}

/** Resolves a tapped busy [interval] against the loaded [blocks]. */
fun resolveIntervalTap(interval: BusyInterval, blocks: List<ManualBlock>, zone: ZoneId = DateTimes.zone): IntervalTap =
    when (interval.source) {
        BusySource.Calendar -> IntervalTap.CalendarBusy(
            CalendarBusyUi(
                calendarName = interval.label?.takeIf { it.isNotBlank() } ?: "A connected calendar",
                timeLabel = calendarTimeLabel(interval, zone),
                interval = interval,
            ),
        )
        else -> blocks.firstOrNull { it.id == interval.blockId }?.let(IntervalTap::EditBlock) ?: IntervalTap.MissingBlock
    }

/** "Tue 10 Nov · 09:00 – 10:00"; midnight-to-midnight spans read as whole days. */
private fun calendarTimeLabel(interval: BusyInterval, zone: ZoneId): String {
    val start = DateTimes.localDateTime(interval.start, zone)
    val end = DateTimes.localDateTime(interval.end, zone)
    val allDay = start != null && end != null && start != end &&
        start.toLocalTime() == LocalTime.MIDNIGHT && end.toLocalTime() == LocalTime.MIDNIGHT
    return DateTimes.rangeLabel(interval.start, interval.end, allDay, zone)
}

/** The sheet's "Set times" / "All day" choice. */
enum class BlockWhen(val label: String) { Times("Set times"), AllDay("All day") }

/** The "Block a time" sheet form. */
data class BlockDraft(
    val date: LocalDate,
    /** Set when editing an existing block rather than adding one. */
    val editingId: String? = null,
    val whenOption: BlockWhen = BlockWhen.Times,
    val start: LocalTime = LocalTime.of(9, 0),
    /** At or before [start] means the block runs past midnight into the next day. */
    val end: LocalTime = LocalTime.of(10, 0),
    val repeat: Repeat = Repeat.None,
    val until: LocalDate? = null,
    val note: String = "",
    /** All day only: the last day of a block over several days (inclusive), or null for one day. */
    val lastDay: LocalDate? = null,
) {
    /** All day over more than one day. */
    val isMultiDay: Boolean get() = whenOption == BlockWhen.AllDay && lastDay != null && lastDay.isAfter(date)

    /** "Mon 17 Nov", or "17 – 21 Nov" for an all-day block over several days. */
    val dateLabel: String
        get() = if (isMultiDay) DateTimes.weekRange(date, lastDay!!) else DateTimes.shortDay(date)

    val timeLabel: String
        get() = when (whenOption) {
            BlockWhen.Times -> "${DateTimes.time(start)} – ${DateTimes.time(end)}"
            BlockWhen.AllDay -> whenOption.label
        }

    /** The set times run into the next day. */
    val endsNextDay: Boolean get() = whenOption == BlockWhen.Times && !end.isAfter(start)

    val isValid: Boolean get() = (until == null || !until.isBefore(date)) && (lastDay == null || !lastDay.isBefore(date))

    /** Local start/end. All day = local midnight → the midnight after the last day; an end at or before the start is the next day. */
    fun range(): Pair<LocalDateTime, LocalDateTime> = when (whenOption) {
        BlockWhen.AllDay -> date.atStartOfDay() to (if (isMultiDay) lastDay!! else date).plusDays(1).atStartOfDay()
        BlockWhen.Times -> date.atTime(start) to (if (endsNextDay) date.plusDays(1) else date).atTime(end)
    }

    val isEditing: Boolean get() = editingId != null

    /** Moves the start, keeping the block's length. */
    fun withStart(time: LocalTime): BlockDraft {
        val length = Duration.between(start, end).toMinutes().let { if (it <= 0) it + DAY_MINUTES else it }
        return copy(whenOption = BlockWhen.Times, start = time, end = time.plusMinutes(length))
    }

    fun toRequest(): CreateBlockRequest {
        val (start, end) = range()
        return CreateBlockRequest(
            start = DateTimes.toIso(start),
            end = DateTimes.toIso(end),
            allDay = whenOption == BlockWhen.AllDay,
            repeat = repeat,
            until = if (repeat == Repeat.None) null else until?.let(DateTimes::toWireDate),
            note = note.trim().ifBlank { null },
        )
    }

    companion object {
        /** A new block on [date] pre-filled with [range] (e.g. the hour tapped on the grid). 24:00 becomes 00:00. */
        fun forRange(date: LocalDate, range: MinuteRange): BlockDraft = BlockDraft(
            date = date,
            whenOption = BlockWhen.Times,
            start = timeOf(range.from),
            end = timeOf(range.to),
        )

        /** "This evening": 18:00 to midnight today, or tomorrow once it's past 18:00. */
        fun evening(now: LocalDateTime): BlockDraft {
            val date = if (now.toLocalTime() >= EVENING) now.toLocalDate().plusDays(1) else now.toLocalDate()
            return BlockDraft(date = date, whenOption = BlockWhen.Times, start = EVENING, end = LocalTime.MIDNIGHT)
        }

        /** "Tomorrow evening" once this evening has started (see [evening]). */
        fun eveningLabel(now: LocalDateTime): String = if (now.toLocalTime() >= EVENING) "Tomorrow evening" else "This evening"

        /** The whole of [date]. */
        fun allDay(date: LocalDate): BlockDraft = BlockDraft(date = date, whenOption = BlockWhen.AllDay)

        /** Every day [from]..[to] inclusive ("Away for a few days"). */
        fun away(from: LocalDate, to: LocalDate): BlockDraft {
            val (first, last) = if (to.isBefore(from)) to to from else from to to
            return BlockDraft(date = first, whenOption = BlockWhen.AllDay, lastDay = last.takeIf { it.isAfter(first) })
        }

        private val EVENING = LocalTime.of(18, 0)

        private fun timeOf(minutes: Int): LocalTime = LocalTime.MIDNIGHT.plusMinutes(Math.floorMod(minutes, DAY_MINUTES).toLong())

        /** Prefills the sheet from an existing block so every part of it can be edited. */
        fun fromBlock(block: ManualBlock, zone: ZoneId = DateTimes.zone): BlockDraft? {
            val start = DateTimes.localDateTime(block.start, zone) ?: return null
            val end = DateTimes.localDateTime(block.end, zone) ?: return null
            val draft = BlockDraft(
                date = start.toLocalDate(),
                editingId = block.id,
                repeat = block.repeat,
                until = block.until?.let(DateTimes::parseDate),
                note = block.note.orEmpty(),
            )
            // An all-day block keeps the default times in case it is switched to "Set times".
            return if (block.allDay) {
                // Ends at the midnight after its last day.
                val last = if (end.toLocalTime() == LocalTime.MIDNIGHT) end.toLocalDate().minusDays(1) else end.toLocalDate()
                draft.copy(whenOption = BlockWhen.AllDay, lastDay = last.takeIf { it.isAfter(draft.date) })
            } else {
                draft.copy(start = start.toLocalTime(), end = end.toLocalTime())
            }
        }
    }
}

data class MyTimeUiState(
    val today: LocalDate = DateTimes.today(),
    /** Month (default) or agenda; remembered per device. */
    val mode: MyTimeMode = MyTimeMode.Month,
    /** The month on screen in the month view. */
    val month: YearMonth = YearMonth.from(DateTimes.today()),
    /** Busy time laid out for every loaded day, from today. */
    val layout: DaysLayout = DaysLayout.Empty,
    /** One summary per loaded day, in order. */
    val days: List<DayRowUi> = emptyList(),
    /** [days] by date. */
    val rows: Map<LocalDate, DayRowUi> = emptyMap(),
    /** Each of your blocks' repeat, by block id (the agenda's "Weekly" tags). */
    val repeats: Map<String, Repeat> = emptyMap(),
    val recurring: List<RecurringBlockUi> = emptyList(),
    /** The first load (nothing to show yet). */
    val loading: Boolean = true,
    /** Loading further weeks at the bottom of the agenda. */
    val loadingMore: Boolean = false,
    /** Pull to refresh. */
    val refreshing: Boolean = false,
    val error: String? = null,
    /** No calendar is connected: show the "Connect a calendar" hint. */
    val showCalendarHint: Boolean = false,
    /** The day selected in the week view; null when the month or agenda is showing. */
    val openDay: LocalDate? = null,
    val draft: BlockDraft? = null,
    /** Set while the "Busy from your calendar" sheet is open. */
    val calendarBusy: CalendarBusyUi? = null,
    val savingDraft: Boolean = false,
    val message: UserMessage? = null,
) {
    /** The last day My time goes to. */
    val lastDay: LocalDate get() = today.plusDays(MY_TIME_DAYS_AHEAD)

    val months: ClosedRange<YearMonth> get() = monthRange(today)
}

/** "Every Tuesday · 19:00 – 21:30", "Every other Friday · All day", "Sat 21 Nov · All day". */
internal fun ManualBlock.scheduleLabel(zone: ZoneId = DateTimes.zone): String {
    val s = DateTimes.parseInstant(start)?.atZone(zone)?.toLocalDateTime() ?: return ""
    val e = DateTimes.parseInstant(end)?.atZone(zone)?.toLocalDateTime() ?: return ""
    val weekday = DateTimes.longWeekday(DateTimes.apiWeekday(s.toLocalDate()))
    val day = when (repeat) {
        Repeat.Weekly -> "Every $weekday"
        Repeat.Fortnightly -> "Every other $weekday"
        Repeat.None -> DateTimes.shortDay(s.toLocalDate())
    }
    val time = if (allDay || (s.toLocalTime() == LocalTime.MIDNIGHT && e.toLocalTime() == LocalTime.MIDNIGHT)) {
        "All day"
    } else {
        "${DateTimes.time(s)} – ${DateTimes.time(e)}"
    }
    val untilLabel = until?.let(DateTimes::parseDate)?.takeIf { repeat != Repeat.None }?.let { " · until ${DateTimes.dayMonth(it)}" }.orEmpty()
    return "$day · $time$untilLabel"
}

internal fun ManualBlock.title(): String = note?.takeIf { it.isNotBlank() } ?: "Busy"
