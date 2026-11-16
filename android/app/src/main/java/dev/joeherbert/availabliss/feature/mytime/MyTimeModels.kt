package dev.joeherbert.availabliss.feature.mytime

import dev.joeherbert.availabliss.core.common.DateTimes
import dev.joeherbert.availabliss.core.common.TimeRangeDraft
import dev.joeherbert.availabliss.core.common.UserMessage
import dev.joeherbert.availabliss.core.network.model.BusyInterval
import dev.joeherbert.availabliss.core.network.model.BusySource
import dev.joeherbert.availabliss.core.network.model.CreateBlockRequest
import dev.joeherbert.availabliss.core.network.model.ManualBlock
import dev.joeherbert.availabliss.core.network.model.Repeat
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
                calendarName = interval.title ?: interval.label?.takeIf { it.isNotBlank() } ?: "A connected calendar",
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

/** The "Block a time" sheet form. */
data class BlockDraft(
    /** When: start → end, or all day over one or more days. */
    val range: TimeRangeDraft,
    /** Set when editing an existing block rather than adding one. */
    val editingId: String? = null,
    val repeat: Repeat = Repeat.None,
    /** The last date a repeat can occur, or null for no end. */
    val until: LocalDate? = null,
    val note: String = "",
) {
    /** The first day. */
    val date: LocalDate get() = range.startDate

    val isValid: Boolean get() = range.isValid && (until == null || !until.isBefore(date))

    val isEditing: Boolean get() = editingId != null

    /** "Mon 17 Nov · 09:00 – 10:00", "Fri 13 – Sun 15 Nov". */
    val label: String get() = range.label

    /** The Repeat row's value: "Every month on the 3rd Monday". */
    val repeatLabel: String get() = repeatOptionLabel(repeat, date)

    /** A new range; an end date before the new first day is dropped. */
    fun withRange(range: TimeRangeDraft): BlockDraft = copy(range = range, until = until?.takeUnless { it.isBefore(range.startDate) })

    /** A new repeat; "Never" clears the end date. */
    fun withRepeat(repeat: Repeat): BlockDraft = copy(repeat = repeat, until = if (repeat == Repeat.None) null else until)

    fun toRequest(zone: ZoneId = DateTimes.zone): CreateBlockRequest = CreateBlockRequest(
        start = range.startIso(zone),
        end = range.endIso(zone),
        allDay = range.allDay,
        repeat = repeat,
        until = if (repeat == Repeat.None) null else until?.let(DateTimes::toWireDate),
        note = note.trim().ifBlank { null },
    )

    companion object {
        /** Today (or [date]) 09:00 – 10:00. */
        fun on(date: LocalDate): BlockDraft = BlockDraft(TimeRangeDraft.timed(date.atTime(9, 0), date.atTime(10, 0)))

        /** A new block on [date] pre-filled with [range] (e.g. the hour tapped on the grid). 24:00 is the next midnight. */
        fun forRange(date: LocalDate, range: MinuteRange): BlockDraft {
            val start = date.atStartOfDay().plusMinutes(range.from.toLong())
            val end = date.atStartOfDay().plusMinutes(range.to.toLong()).let { if (it.isAfter(start)) it else start.plusHours(1) }
            return BlockDraft(TimeRangeDraft.timed(start, end))
        }

        /** "This evening": 18:00 to midnight today, or tomorrow once it's past 18:00. */
        fun evening(now: LocalDateTime): BlockDraft {
            val date = if (now.toLocalTime() >= EVENING) now.toLocalDate().plusDays(1) else now.toLocalDate()
            return BlockDraft(TimeRangeDraft.timed(date.atTime(EVENING), date.plusDays(1).atStartOfDay()))
        }

        /** "Tomorrow evening" once this evening has started (see [evening]). */
        fun eveningLabel(now: LocalDateTime): String = if (now.toLocalTime() >= EVENING) "Tomorrow evening" else "This evening"

        /** The whole of [date]. */
        fun allDay(date: LocalDate): BlockDraft = BlockDraft(TimeRangeDraft.allDay(date))

        /** Every day [from]..[to] inclusive ("Away for a few days"). */
        fun away(from: LocalDate, to: LocalDate): BlockDraft {
            val (first, last) = if (to.isBefore(from)) to to from else from to to
            return BlockDraft(TimeRangeDraft.allDay(first, last))
        }

        private val EVENING = LocalTime.of(18, 0)

        /** Prefills the sheet from an existing block so every part of it can be edited. */
        fun fromBlock(block: ManualBlock, zone: ZoneId = DateTimes.zone): BlockDraft? {
            val range = TimeRangeDraft.fromIso(block.start, block.end, block.allDay, zone) ?: return null
            return BlockDraft(
                range = range,
                editingId = block.id,
                repeat = block.repeat,
                until = block.until?.let(DateTimes::parseDate),
                note = block.note.orEmpty(),
            )
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
    /** Each of your blocks' repeat, by block id (the agenda's "Weekly", "Monthly"… tags). */
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

/** "Every Tuesday · 19:00 – 21:30", "Monthly on the 3rd Monday · All day · until 20 Dec", "Sat 21 Nov · All day". */
internal fun ManualBlock.scheduleLabel(zone: ZoneId = DateTimes.zone): String {
    val s = DateTimes.parseInstant(start)?.atZone(zone)?.toLocalDateTime() ?: return ""
    val e = DateTimes.parseInstant(end)?.atZone(zone)?.toLocalDateTime() ?: return ""
    val day = repeatScheduleLabel(repeat, s.toLocalDate())
    val time = if (allDay || (s.toLocalTime() == LocalTime.MIDNIGHT && e.toLocalTime() == LocalTime.MIDNIGHT)) {
        "All day"
    } else {
        "${DateTimes.time(s)} – ${DateTimes.time(e)}"
    }
    val untilLabel = until?.let(DateTimes::parseDate)?.takeIf { repeat != Repeat.None }?.let { " · until ${DateTimes.dayMonth(it)}" }.orEmpty()
    return "$day · $time$untilLabel"
}

internal fun ManualBlock.title(): String = note?.takeIf { it.isNotBlank() } ?: "Busy"
