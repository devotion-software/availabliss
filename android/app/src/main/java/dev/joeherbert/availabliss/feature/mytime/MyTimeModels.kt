package dev.joeherbert.availabliss.feature.mytime

import dev.joeherbert.availabliss.core.common.DateTimes
import dev.joeherbert.availabliss.core.common.UserMessage
import dev.joeherbert.availabliss.core.network.model.CreateBlockRequest
import dev.joeherbert.availabliss.core.network.model.ManualBlock
import dev.joeherbert.availabliss.core.network.model.Repeat
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** A repeating manual block listed under the grid ("Choir · Every Tuesday · 19:00 – 21:30"). */
data class RecurringBlockUi(val id: String, val title: String, val schedule: String)

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
) {
    val timeLabel: String
        get() = when (whenOption) {
            BlockWhen.Times -> "${DateTimes.time(start)} – ${DateTimes.time(end)}"
            BlockWhen.AllDay -> whenOption.label
        }

    /** The set times run into the next day. */
    val endsNextDay: Boolean get() = whenOption == BlockWhen.Times && !end.isAfter(start)

    val isValid: Boolean get() = until == null || !until.isBefore(date)

    /** Local start/end. All day = local midnight → next midnight; an end at or before the start is the next day. */
    fun range(): Pair<LocalDateTime, LocalDateTime> = when (whenOption) {
        BlockWhen.AllDay -> date.atStartOfDay() to date.plusDays(1).atStartOfDay()
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
                draft.copy(whenOption = BlockWhen.AllDay)
            } else {
                draft.copy(start = start.toLocalTime(), end = end.toLocalTime())
            }
        }
    }
}

data class MyTimeUiState(
    val weekStart: LocalDate,
    val weekLabel: String,
    val today: LocalDate = DateTimes.today(),
    val layout: WeekLayout = WeekLayout.empty(weekStart),
    val recurring: List<RecurringBlockUi> = emptyList(),
    val blockedCount: Int = 0,
    val loading: Boolean = true,
    val error: String? = null,
    val draft: BlockDraft? = null,
    val savingDraft: Boolean = false,
    val message: UserMessage? = null,
)

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
