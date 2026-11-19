package dev.joeherbert.availabliss.feature.timing

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.joeherbert.availabliss.core.common.DateTimes
import dev.joeherbert.availabliss.core.network.model.DateRange
import dev.joeherbert.availabliss.core.network.model.RequirementLength
import dev.joeherbert.availabliss.core.network.model.TimeWindow
import dev.joeherbert.availabliss.designsystem.component.AvBottomSheet
import dev.joeherbert.availabliss.designsystem.component.AvDateRangePickerDialog
import dev.joeherbert.availabliss.designsystem.component.AvTimePickerDialog
import dev.joeherbert.availabliss.designsystem.component.Chip
import dev.joeherbert.availabliss.designsystem.component.FieldTile
import dev.joeherbert.availabliss.designsystem.component.PrimaryButton
import dev.joeherbert.availabliss.designsystem.component.SectionLabel
import dev.joeherbert.availabliss.designsystem.component.ToggleRow
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import dev.joeherbert.availabliss.designsystem.theme.Shapes
import java.time.LocalDate
import java.time.LocalTime

/**
 * The timing sentence's wording: "We're looking for a weekend, from Friday 18:00 to Sunday night,
 * any time between 20 Nov and 28 Feb." Same as web's and iOS's.
 */
object TimingSentence {
    /** "a weekend", "2 hours", "2½ hours", "45 minutes", "3 days in a row". */
    fun kind(draft: TimingDraft): String = when (RequirementPresets.detect(draft.length, draft.window)) {
        TimingPreset.TwoHours -> "2 hours"
        TimingPreset.Evening -> "an evening"
        TimingPreset.OneDay -> "a whole day"
        TimingPreset.Weekend -> "a weekend"
        TimingPreset.Week -> "a week"
        TimingPreset.Custom -> when (val l = draft.length) {
            is RequirementLength.Duration -> when {
                l.minutes % 60 == 0 -> plural(l.minutes / 60, "hour")
                l.minutes % 30 == 0 -> if (l.minutes < 60) "½ hour" else "${l.minutes / 60}½ hours"
                else -> plural(l.minutes, "minute")
            }
            is RequirementLength.Days -> if (l.count == 1) "a day" else "${l.count} days in a row"
            is RequirementLength.Span -> "a stretch of days"
        }
    }

    /** "Friday 18:00", "Tuesday" (from the start of the day). */
    fun start(span: RequirementLength.Span): String =
        DAY_NAMES[span.startDay] + if (span.startTime == "00:00") "" else " ${span.startTime}"

    /** "Sunday night" (the end of the day), "Sunday 17:00", "the following Thursday night". */
    fun end(span: RequirementLength.Span): String {
        val day = (if (span.weeksLater == 1) "the following " else "") + DAY_NAMES[span.endDay]
        return if (span.endTime == "23:59") "$day night" else "$day ${span.endTime}"
    }

    /** "any time of day", "between 18:00 and midnight". */
    fun window(window: TimeWindow?): String =
        if (window == null) "any time of day" else "between ${window.from} and ${if (window.to == "23:59") "midnight" else window.to}"

    /** "any day", "weekdays", "weekends", "Fridays to Sundays", "Mondays, Wednesdays and Fridays". */
    fun days(days: Set<Int>): String {
        val sorted = days.sorted()
        if (sorted.isEmpty() || sorted.size == 7) return "any day"
        if (sorted == listOf(0, 1, 2, 3, 4)) return "weekdays"
        if (sorted == listOf(5, 6)) return "weekends"
        val names = sorted.map { DAY_NAMES[it] + "s" }
        val contiguous = sorted.zipWithNext().all { (a, b) -> b == a + 1 }
        if (contiguous && sorted.size > 2) return "${names.first()} to ${names.last()}"
        return if (names.size == 1) names[0] else names.dropLast(1).joinToString(", ") + " and " + names.last()
    }

    private val DAY_NAMES = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    private fun plural(n: Int, word: String) = if (n == 1) "1 $word" else "$n ${word}s"
}

/** Which part of the sentence is being changed. */
private enum class SentencePart { None, Kind, Start, End, Window, Days, Range }

private sealed interface Piece {
    data class Words(val text: String) : Piece
    data class Part(val text: String, val part: SentencePart, val after: String = "") : Piece
}

/**
 * The group's timing as one sentence; each underlined part opens a sheet with a few quick picks and
 * full control (any length, day, time or dates). The holiday options sit under it as chips.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TimingSentenceSection(state: TimingEditorUiState, canEdit: Boolean, actions: TimingActions) {
    val colors = AvailablissTheme.colors
    val draft = state.draft
    var open by rememberSaveable { mutableStateOf(SentencePart.None) }
    val pieces = buildList {
        add(Piece.Words("We’re looking for"))
        val span = draft.length as? RequirementLength.Span
        add(Piece.Part(TimingSentence.kind(draft), SentencePart.Kind, ","))
        if (span != null) {
            add(Piece.Words("from"))
            add(Piece.Part(TimingSentence.start(span), SentencePart.Start))
            add(Piece.Words("to"))
            add(Piece.Part(TimingSentence.end(span), SentencePart.End, ","))
        } else {
            if (draft.length is RequirementLength.Duration || draft.window != null) {
                add(Piece.Part(TimingSentence.window(draft.window), SentencePart.Window, ","))
            }
            add(Piece.Words("on"))
            add(Piece.Part(TimingSentence.days(draft.days), SentencePart.Days, ","))
        }
        if (draft.dates.isEmpty()) {
            add(Piece.Words("any time between"))
            add(Piece.Part(DateTimes.dayMonth(state.rangeFrom), SentencePart.Range))
            add(Piece.Words("and"))
            add(Piece.Part(DateTimes.dayMonth(state.rangeTo), SentencePart.Range, "."))
        } else {
            add(Piece.Words("on"))
            val n = draft.dates.size
            add(Piece.Part(if (n == 1) "1 chosen date range" else "$n chosen date ranges", SentencePart.Range, "."))
        }
    }
    val style = AvailablissTheme.type.headlineMedium.copy(fontSize = 24.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold)
    Column {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            pieces.forEach { piece ->
                when (piece) {
                    is Piece.Words -> piece.text.split(" ").forEach { Text(it, style = style, color = colors.ink) }
                    is Piece.Part -> Row {
                        val underline = colors.accentInk.copy(alpha = 0.6f)
                        Text(
                            piece.text,
                            style = style,
                            color = colors.accentInk,
                            modifier = Modifier
                                .clip(Shapes.controlSmall)
                                .then(if (piece.part == SentencePart.Kind) Modifier.background(colors.accentSoft) else Modifier)
                                .clickable(enabled = canEdit, role = Role.Button, onClickLabel = "Change") { open = piece.part }
                                .drawBehind {
                                    if (canEdit) drawLine(underline, Offset(0f, size.height - 2.dp.toPx()), Offset(size.width, size.height - 2.dp.toPx()), 2.dp.toPx())
                                }
                                .padding(horizontal = if (piece.part == SentencePart.Kind) 6.dp else 1.dp),
                        )
                        if (piece.after.isNotEmpty()) Text(piece.after, style = style, color = colors.ink)
                    }
                }
            }
        }
        if (canEdit) {
            Text(
                "Tap any underlined part to change it.",
                style = AvailablissTheme.type.caption.copy(fontWeight = FontWeight.Normal),
                color = colors.muted,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        FlowRow(
            Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // With every day allowed (none picked means any), bank holidays are already included.
            if (draft.isSpan || (draft.days.isNotEmpty() && draft.days.size < 7)) {
                Chip(
                    (if (draft.bankHolidays) "✓ " else "+ ") + "Bank holidays count as weekends",
                    selected = draft.bankHolidays,
                    onClick = { if (canEdit) actions.onBankHolidays(!draft.bankHolidays) },
                )
            }
            Chip(
                (if (draft.schoolHolidaysOnly) "✓ " else "+ ") + "Only school holidays",
                selected = draft.schoolHolidaysOnly,
                onClick = { if (canEdit) actions.onSchoolHolidays(!draft.schoolHolidaysOnly) },
            )
        }
    }
    val close = { open = SentencePart.None }
    when (open) {
        SentencePart.None -> Unit
        SentencePart.Kind -> KindSheet(draft, actions, close)
        SentencePart.Start -> SpanEndSheet("Starting", state.spanStart, following = null, onDone = {
            actions.onSpanStart(it)
            close()
        }, onDismiss = close)
        SentencePart.End -> SpanEndSheet(
            "Ending",
            state.spanEnd,
            following = (draft.length as? RequirementLength.Span)?.weeksLater == 1,
            onFollowing = actions.onWeeksLater,
            onDone = {
                actions.onSpanEnd(it)
                close()
            },
            onDismiss = close,
        )
        SentencePart.Window -> WindowSheet(draft, actions, close)
        SentencePart.Days -> DaysSheet(draft, actions, close)
        SentencePart.Range -> RangeSheet(state, actions, close)
    }
}

/** "We're looking for…": quick picks, or any length (hours in 15-minute steps, days in a row, or start to end). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KindSheet(draft: TimingDraft, actions: TimingActions, close: () -> Unit) {
    AvBottomSheet(title = "We’re looking for…", onDismissRequest = close) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TimingPreset.entries.forEach { preset ->
                val label = if (preset == TimingPreset.Custom) "Any length" else preset.label
                Chip(label, selected = draft.preset == preset, onClick = { actions.onPreset(preset) }, height = 40.dp)
            }
        }
        if (draft.preset == TimingPreset.Custom) {
            CustomLengthEditor(draft, true, actions.onCustomKind, actions.onDuration, actions.onDayCount, actions.onWeeksLater)
        }
        PrimaryButton("Done", onClick = close, height = 52.dp, shape = Shapes.control, modifier = Modifier.fillMaxWidth())
    }
}

/** Picks the weekday and time for one end of a span; the end also says whether it's the following week. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SpanEndSheet(
    title: String,
    initial: SpanEnd,
    following: Boolean?,
    onFollowing: (Boolean) -> Unit = {},
    onDone: (SpanEnd) -> Unit,
    onDismiss: () -> Unit,
) {
    var day by remember { mutableStateOf(initial.day) }
    var time by remember { mutableStateOf(initial.time) }
    var pickingTime by remember { mutableStateOf(false) }
    AvBottomSheet(title = title, onDismissRequest = onDismiss) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            (0..6).forEach { d -> Chip(DateTimes.shortWeekday(d), selected = d == day, onClick = { day = d }) }
        }
        FieldTile("Time", DateTimes.time(time), onClick = { pickingTime = true }, modifier = Modifier.fillMaxWidth())
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (following == null) {
                Chip("Start of the day", selected = time == LocalTime.MIDNIGHT, onClick = { time = LocalTime.MIDNIGHT })
                Chip("Morning", selected = time == LocalTime.of(9, 0), onClick = { time = LocalTime.of(9, 0) })
                Chip("Evening", selected = time == LocalTime.of(18, 0), onClick = { time = LocalTime.of(18, 0) })
            } else {
                Chip("End of the day", selected = time == LocalTime.of(23, 59), onClick = { time = LocalTime.of(23, 59) })
                Chip("Teatime", selected = time == LocalTime.of(17, 0), onClick = { time = LocalTime.of(17, 0) })
            }
        }
        if (following != null) {
            ToggleRow(title = "The following week", checked = following, onCheckedChange = onFollowing)
        }
        PrimaryButton("Done", onClick = { onDone(SpanEnd(day, time)) }, height = 52.dp, shape = Shapes.control, modifier = Modifier.fillMaxWidth())
    }
    if (pickingTime) AvTimePickerDialog(title, time, { time = it }, { pickingTime = false })
}

/** The time of day: any time, or from–until at any times. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WindowSheet(draft: TimingDraft, actions: TimingActions, close: () -> Unit) {
    var picking by remember { mutableStateOf<String?>(null) }
    val duration = draft.length is RequirementLength.Duration
    val window = draft.window ?: TimeWindow("08:00", "23:00")
    AvBottomSheet(title = "Time of day", onDismissRequest = close) {
        if (!duration) {
            ToggleRow(
                title = "Any time of day",
                checked = draft.window == null,
                onCheckedChange = { any -> actions.onWindow(if (any) null else TimeWindow("18:00", "23:59")) },
            )
        }
        if (duration || draft.window != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldTile("From", window.from, onClick = { picking = "from" }, modifier = Modifier.weight(1f))
                FieldTile("Until", window.to, onClick = { picking = "to" }, modifier = Modifier.weight(1f))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Daytime and evening", selected = window == TimeWindow("08:00", "23:00"), onClick = { actions.onWindow(TimeWindow("08:00", "23:00")) })
                Chip("Working hours", selected = window == TimeWindow("09:00", "17:00"), onClick = { actions.onWindow(TimeWindow("09:00", "17:00")) })
                Chip("Evenings", selected = window == TimeWindow("18:00", "23:59"), onClick = { actions.onWindow(TimeWindow("18:00", "23:59")) })
            }
        }
        PrimaryButton("Done", onClick = close, height = 52.dp, shape = Shapes.control, modifier = Modifier.fillMaxWidth())
    }
    picking?.let { end ->
        val initial = DateTimes.parseTime(if (end == "from") window.from else window.to) ?: LocalTime.of(8, 0)
        AvTimePickerDialog(if (end == "from") "From" else "Until", initial, { t ->
            val wire = DateTimes.toWireTime(t)
            actions.onWindow(if (end == "from") window.copy(from = wire) else window.copy(to = wire))
        }, { picking = null })
    }
}

/** Which days it can fall on: any combination, or a quick pick. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DaysSheet(draft: TimingDraft, actions: TimingActions, close: () -> Unit) {
    AvBottomSheet(title = "On which days?", onDismissRequest = close) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            (0..6).forEach { d -> Chip(DateTimes.shortWeekday(d), selected = d in draft.days, onClick = { actions.onToggleDay(d) }) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Any day", selected = draft.days.isEmpty(), onClick = { actions.onDays(emptySet()) })
            Chip("Weekdays", selected = draft.days == setOf(0, 1, 2, 3, 4), onClick = { actions.onDays(setOf(0, 1, 2, 3, 4)) })
            Chip("Weekends", selected = draft.days == setOf(5, 6), onClick = { actions.onDays(setOf(5, 6)) })
            Chip("Fri to Sun", selected = draft.days == setOf(4, 5, 6), onClick = { actions.onDays(setOf(4, 5, 6)) })
        }
        Text(
            "No days picked means any day of the week.",
            style = AvailablissTheme.type.caption.copy(fontWeight = FontWeight.Normal),
            color = AvailablissTheme.colors.muted,
        )
        PrimaryButton("Done", onClick = close, height = 52.dp, shape = Shapes.control, modifier = Modifier.fillMaxWidth())
    }
}

/** Any time between two dates (any, on a calendar), or only on certain date ranges. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RangeSheet(state: TimingEditorUiState, actions: TimingActions, close: () -> Unit) {
    var picking by remember { mutableStateOf<RangeList?>(null) }
    var pickingSearch by remember { mutableStateOf(false) }
    val draft = state.draft
    AvBottomSheet(title = "When could it be?", onDismissRequest = close) {
        SectionLabel("Any time between")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldTile("From", DateTimes.dayMonthYear(state.rangeFrom), onClick = { pickingSearch = true }, modifier = Modifier.weight(1f))
            FieldTile("Until", DateTimes.dayMonthYear(state.rangeTo), onClick = { pickingSearch = true }, modifier = Modifier.weight(1f))
        }
        SectionLabel("Or only these dates")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            draft.dates.forEach { range ->
                val label = TimingLabels.dateRange(range.from, range.to)
                dev.joeherbert.availabliss.designsystem.component.RemovableChip(label, "Remove $label", onRemove = { actions.onRemoveRange(RangeList.Dates, range) })
            }
            dev.joeherbert.availabliss.designsystem.component.DashedChip("+ Add date range", onClick = { picking = RangeList.Dates })
        }
        PrimaryButton("Done", onClick = close, height = 52.dp, shape = Shapes.control, modifier = Modifier.fillMaxWidth())
    }
    if (pickingSearch) AvDateRangePickerDialog(state.rangeFrom, state.rangeTo, actions.onRange, { pickingSearch = false })
    picking?.let { list ->
        val existing = list.of(draft)
        val range = { from: LocalDate, to: LocalDate -> DateRange(minOf(from, to).toString(), maxOf(from, to).toString()) }
        AvDateRangePickerDialog(
            null,
            null,
            { from, to -> actions.onAddRange(list, from, to) },
            { picking = null },
            note = { from, to -> DateRanges.note(range(from, to), existing) },
            canPick = { from, to -> DateRanges.covering(range(from, to), existing) == null },
        )
    }
}
