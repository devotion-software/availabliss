package dev.joeherbert.availabliss.feature.mytime

import dev.joeherbert.availabliss.core.common.DateTimes
import dev.joeherbert.availabliss.core.common.UserMessage
import dev.joeherbert.availabliss.core.network.model.CreateBlockRequest
import dev.joeherbert.availabliss.core.network.model.ManualBlock
import dev.joeherbert.availabliss.core.network.model.Repeat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** The three parts of a day shown in the "My time" grid. The single source of slot times. */
enum class DayPart(val label: String, val start: LocalTime, val end: LocalTime) {
    Morning("Morning", LocalTime.of(6, 0), LocalTime.of(12, 0)),
    Afternoon("Afternoon", LocalTime.of(12, 0), LocalTime.of(17, 0)),
    Evening("Evening", LocalTime.of(17, 0), LocalTime.of(23, 59));

    fun startOn(date: LocalDate): LocalDateTime = date.atTime(start)
    fun endOn(date: LocalDate): LocalDateTime = date.atTime(end)
}

/** What one grid cell shows. */
sealed interface SlotState {
    data object Free : SlotState

    /** Busy from a connected calendar (read-only here). [dotColor] is `#RRGGBB` when known. */
    data class Calendar(val label: String, val dotColor: String?) : SlotState

    /** Blocked by hand. [pending] while the create request is in flight. */
    data class Manual(val blockId: String?, val label: String, val pending: Boolean = false) : SlotState
}

data class SlotUi(val date: LocalDate, val part: DayPart, val state: SlotState) {
    val accessibilityLabel: String
        get() {
            val day = "${DateTimes.shortWeekday(DateTimes.apiWeekday(date))} ${date.dayOfMonth}"
            val status = when (state) {
                SlotState.Free -> "free"
                is SlotState.Calendar -> "busy from calendar: ${state.label}"
                is SlotState.Manual -> "blocked by you"
            }
            return "$day ${part.label.lowercase()}, $status"
        }
}

data class DayRowUi(
    val date: LocalDate,
    val dayLabel: String,
    val dateLabel: String,
    val slots: List<SlotUi>,
)

/** A repeating manual block listed under the grid ("Choir · Every Tuesday · 19:00 – 21:30"). */
data class RecurringBlockUi(val id: String, val title: String, val schedule: String)

enum class WhenOption(val label: String) { AllDay("All day"), Morning("Morning"), Evening("Evening"), Custom("Custom") }

/** The "Block a time" sheet form. */
data class BlockDraft(
    val date: LocalDate,
    /** Set when editing an existing block rather than adding one. */
    val editingId: String? = null,
    val whenOption: WhenOption = WhenOption.AllDay,
    val customStart: LocalTime = LocalTime.of(14, 0),
    val customEnd: LocalTime = LocalTime.of(18, 0),
    val repeat: Repeat = Repeat.None,
    val until: LocalDate? = null,
    val note: String = "",
) {
    val timeLabel: String
        get() = when (whenOption) {
            WhenOption.Custom -> "${DateTimes.time(customStart)} – ${DateTimes.time(customEnd)}"
            else -> whenOption.label
        }

    val isValid: Boolean
        get() = (whenOption != WhenOption.Custom || customEnd.isAfter(customStart)) &&
            (until == null || !until.isBefore(date))

    /** Local start/end for the chosen option. All day = local midnight → next midnight. */
    fun range(): Pair<LocalDateTime, LocalDateTime> = when (whenOption) {
        WhenOption.AllDay -> date.atStartOfDay() to date.plusDays(1).atStartOfDay()
        WhenOption.Morning -> DayPart.Morning.startOn(date) to DayPart.Morning.endOn(date)
        WhenOption.Evening -> DayPart.Evening.startOn(date) to DayPart.Evening.endOn(date)
        WhenOption.Custom -> date.atTime(customStart) to date.atTime(customEnd)
    }

    val isEditing: Boolean get() = editingId != null

    fun toRequest(): CreateBlockRequest {
        val (start, end) = range()
        return CreateBlockRequest(
            start = DateTimes.toIso(start),
            end = DateTimes.toIso(end),
            allDay = whenOption == WhenOption.AllDay,
            repeat = repeat,
            until = if (repeat == Repeat.None) null else until?.let(DateTimes::toWireDate),
            note = note.trim().ifBlank { null },
        )
    }

    companion object {
        /** Prefills the sheet from an existing block so every part of it can be edited. */
        fun fromBlock(block: ManualBlock): BlockDraft? {
            val start = DateTimes.localDateTime(block.start) ?: return null
            val end = DateTimes.localDateTime(block.end) ?: return null
            val date = start.toLocalDate()
            // The sheet edits single-day times; a block that runs to midnight ends at 23:59 here.
            val endTime = if (end.toLocalDate().isAfter(date)) LocalTime.of(23, 59) else end.toLocalTime()
            val option = when {
                block.allDay -> WhenOption.AllDay
                start.toLocalTime() == DayPart.Morning.start && endTime == DayPart.Morning.end -> WhenOption.Morning
                start.toLocalTime() == DayPart.Evening.start && endTime == DayPart.Evening.end -> WhenOption.Evening
                else -> WhenOption.Custom
            }
            return BlockDraft(
                date = date,
                editingId = block.id,
                whenOption = option,
                customStart = start.toLocalTime(),
                customEnd = endTime,
                repeat = block.repeat,
                until = block.until?.let(DateTimes::parseDate),
                note = block.note.orEmpty(),
            )
        }
    }
}

data class MyTimeUiState(
    val weekStart: LocalDate,
    val weekLabel: String,
    val days: List<DayRowUi> = emptyList(),
    val recurring: List<RecurringBlockUi> = emptyList(),
    val blockedCount: Int = 0,
    val loading: Boolean = true,
    val error: String? = null,
    val draft: BlockDraft? = null,
    val savingDraft: Boolean = false,
    val message: UserMessage? = null,
)
