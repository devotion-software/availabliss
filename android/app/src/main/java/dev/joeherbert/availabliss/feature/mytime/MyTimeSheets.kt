package dev.joeherbert.availabliss.feature.mytime

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.joeherbert.availabliss.core.common.DateTimes
import dev.joeherbert.availabliss.core.network.model.Repeat
import dev.joeherbert.availabliss.designsystem.component.AvBottomSheet
import dev.joeherbert.availabliss.designsystem.component.AvDatePickerDialog
import dev.joeherbert.availabliss.designsystem.component.AvTextField
import dev.joeherbert.availabliss.designsystem.component.AvTimePickerDialog
import dev.joeherbert.availabliss.designsystem.component.ButtonStyle
import dev.joeherbert.availabliss.designsystem.component.Chip
import dev.joeherbert.availabliss.designsystem.component.FieldTile
import dev.joeherbert.availabliss.designsystem.component.PrimaryButton
import dev.joeherbert.availabliss.designsystem.component.SecondaryButton
import dev.joeherbert.availabliss.designsystem.component.Segment
import dev.joeherbert.availabliss.designsystem.component.SegmentedControl
import dev.joeherbert.availabliss.designsystem.component.SegmentedStyle
import dev.joeherbert.availabliss.designsystem.component.TextAction
import dev.joeherbert.availabliss.designsystem.icon.AvIcons
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import dev.joeherbert.availabliss.designsystem.theme.Shapes
import java.time.LocalDate
import java.time.LocalTime

private enum class PickerTarget { Date, Start, End, Until }

private val repeatSegments = listOf(
    Segment(Repeat.None, "Once"),
    Segment(Repeat.Weekly, "Weekly"),
    Segment(Repeat.Fortnightly, "Fortnightly"),
)

/** "Block a time" sheet: date, time option, repeat, private note. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BlockTimeSheet(
    draft: BlockDraft,
    saving: Boolean,
    onDismiss: () -> Unit,
    onDate: (LocalDate) -> Unit,
    onWhen: (WhenOption) -> Unit,
    onCustomStart: (LocalTime) -> Unit,
    onCustomEnd: (LocalTime) -> Unit,
    onRepeat: (Repeat) -> Unit,
    onUntil: (LocalDate?) -> Unit,
    onNote: (String) -> Unit,
    onSave: () -> Unit,
) {
    val colors = AvailablissTheme.colors
    val type = AvailablissTheme.type
    var picker by rememberSaveable { mutableStateOf<PickerTarget?>(null) }

    AvBottomSheet(title = "Block a time", onDismissRequest = onDismiss) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldTile("Date", DateTimes.shortDay(draft.date), onClick = { picker = PickerTarget.Date }, modifier = Modifier.weight(1f))
            FieldTile("Time", draft.timeLabel, onClick = { picker = PickerTarget.Start }, modifier = Modifier.weight(1f))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            WhenOption.entries.forEach { option ->
                Chip(
                    label = option.label,
                    selected = draft.whenOption == option,
                    onClick = { if (option == WhenOption.Custom) picker = PickerTarget.Start else onWhen(option) },
                )
            }
        }
        if (draft.whenOption == WhenOption.Custom) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldTile("From", DateTimes.time(draft.customStart), onClick = { picker = PickerTarget.Start }, modifier = Modifier.weight(1f))
                FieldTile("Until", DateTimes.time(draft.customEnd), onClick = { picker = PickerTarget.End }, modifier = Modifier.weight(1f))
            }
            if (!draft.customEnd.isAfter(draft.customStart)) {
                Text("The end time needs to be after the start.", style = type.caption, color = colors.warnInk)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Repeat", style = type.label, color = colors.muted)
            SegmentedControl(repeatSegments, draft.repeat, onRepeat, style = SegmentedStyle.Inset, modifier = Modifier.fillMaxWidth())
        }
        if (draft.repeat != Repeat.None) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldTile(
                    label = "Ends",
                    value = draft.until?.let { DateTimes.dayMonthYear(it) } ?: "Never",
                    onClick = { picker = PickerTarget.Until },
                    modifier = Modifier.weight(1f),
                )
                if (draft.until != null) TextAction("No end date", onClick = { onUntil(null) })
            }
        }
        AvTextField(
            value = draft.note,
            onValueChange = onNote,
            label = "Note (only you see this)",
            placeholder = "e.g. Wedding",
            height = 48.dp,
            shape = Shapes.controlSmall,
            textStyle = type.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Normal),
        )
        PrimaryButton(
            text = "Block this time",
            onClick = onSave,
            style = ButtonStyle.Accent,
            height = 52.dp,
            shape = Shapes.control,
            enabled = draft.isValid,
            loading = saving,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    when (picker) {
        PickerTarget.Date -> AvDatePickerDialog(
            initial = draft.date,
            onPick = onDate,
            onDismiss = { picker = null },
            minDate = DateTimes.today(),
        )
        PickerTarget.Until -> AvDatePickerDialog(
            initial = draft.until ?: draft.date.plusMonths(3),
            onPick = { onUntil(it) },
            onDismiss = { picker = null },
            minDate = draft.date,
        )
        PickerTarget.Start -> AvTimePickerDialog(
            title = "Starts",
            initial = draft.customStart,
            onPick = { time ->
                onCustomStart(time)
                // Chain straight into the end time, like a range picker.
                picker = PickerTarget.End
            },
            onDismiss = { if (picker == PickerTarget.Start) picker = null },
        )
        PickerTarget.End -> AvTimePickerDialog(
            title = "Ends",
            initial = draft.customEnd,
            onPick = onCustomEnd,
            onDismiss = { if (picker == PickerTarget.End) picker = null },
        )
        null -> Unit
    }
}

/** Details of an existing block with note editing and delete. */
@Composable
fun EditBlockSheet(
    state: BlockEditState,
    onDismiss: () -> Unit,
    onNote: (String) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = AvailablissTheme.colors
    val type = AvailablissTheme.type
    AvBottomSheet(title = state.title, onDismissRequest = onDismiss) {
        Text(state.schedule, style = type.body, color = colors.muted)
        if (state.block.repeat != Repeat.None) {
            Text("Deleting removes every occurrence.", style = type.caption.copy(fontWeight = FontWeight.Normal), color = colors.muted)
        }
        AvTextField(
            value = state.note,
            onValueChange = onNote,
            label = "Note (only you see this)",
            placeholder = "e.g. Wedding",
            height = 48.dp,
            shape = Shapes.controlSmall,
            textStyle = type.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Normal),
        )
        PrimaryButton(
            text = "Save",
            onClick = onSave,
            height = 52.dp,
            shape = Shapes.control,
            loading = state.saving,
            enabled = state.note.trim() != state.block.note.orEmpty(),
            modifier = Modifier.fillMaxWidth(),
        )
        SecondaryButton(
            text = "Delete block",
            onClick = onDelete,
            icon = AvIcons.Trash,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
