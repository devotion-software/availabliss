package dev.joeherbert.availabliss.feature.mytime

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import dev.joeherbert.availabliss.designsystem.component.FieldTile
import dev.joeherbert.availabliss.designsystem.component.PrimaryButton
import dev.joeherbert.availabliss.designsystem.component.Segment
import dev.joeherbert.availabliss.designsystem.component.SegmentedControl
import dev.joeherbert.availabliss.designsystem.component.SegmentedStyle
import dev.joeherbert.availabliss.designsystem.component.TextAction
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import dev.joeherbert.availabliss.designsystem.theme.Shapes
import java.time.LocalDate
import java.time.LocalTime

private enum class PickerTarget { Date, Start, End, Until }

private val whenSegments = BlockWhen.entries.map { Segment(it, it.label) }

private val repeatSegments = listOf(
    Segment(Repeat.None, "Once"),
    Segment(Repeat.Weekly, "Weekly"),
    Segment(Repeat.Fortnightly, "Fortnightly"),
)

/** "Block a time" sheet: date, all day or exact times, repeat, private note. */
@Composable
fun BlockTimeSheet(
    draft: BlockDraft,
    saving: Boolean,
    onDismiss: () -> Unit,
    onDate: (LocalDate) -> Unit,
    onWhen: (BlockWhen) -> Unit,
    onStart: (LocalTime) -> Unit,
    onEnd: (LocalTime) -> Unit,
    onRepeat: (Repeat) -> Unit,
    onUntil: (LocalDate?) -> Unit,
    onNote: (String) -> Unit,
    onSave: () -> Unit,
    /** Shown when editing an existing block; deletes every occurrence. */
    onDelete: () -> Unit = {},
) {
    val colors = AvailablissTheme.colors
    val type = AvailablissTheme.type
    var picker by rememberSaveable { mutableStateOf<PickerTarget?>(null) }

    AvBottomSheet(title = if (draft.isEditing) "Edit busy time" else "Block a time", onDismissRequest = onDismiss) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldTile("Date", DateTimes.shortDay(draft.date), onClick = { picker = PickerTarget.Date }, modifier = Modifier.weight(1f))
            FieldTile(
                "Time",
                draft.timeLabel,
                onClick = { if (draft.whenOption == BlockWhen.Times) picker = PickerTarget.Start },
                modifier = Modifier.weight(1f),
            )
        }
        SegmentedControl(whenSegments, draft.whenOption, onWhen, style = SegmentedStyle.Inset, modifier = Modifier.fillMaxWidth())
        if (draft.whenOption == BlockWhen.Times) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldTile("From", DateTimes.time(draft.start), onClick = { picker = PickerTarget.Start }, modifier = Modifier.weight(1f))
                FieldTile("To", DateTimes.time(draft.end), onClick = { picker = PickerTarget.End }, modifier = Modifier.weight(1f))
            }
            if (draft.endsNextDay) {
                Text("Runs past midnight, ending the next day.", style = type.caption, color = colors.muted)
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
            text = if (draft.isEditing) "Save changes" else "Block this time",
            onClick = onSave,
            style = ButtonStyle.Accent,
            height = 52.dp,
            shape = Shapes.control,
            enabled = draft.isValid,
            loading = saving,
            modifier = Modifier.fillMaxWidth(),
        )
        if (draft.isEditing) {
            TextAction(
                text = if (draft.repeat == Repeat.None) "Delete" else "Delete every occurrence",
                onClick = onDelete,
                color = colors.warnInk,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
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
            initial = draft.start,
            onPick = { time ->
                onStart(time)
                // Chain straight into the end time, like a range picker.
                picker = PickerTarget.End
            },
            onDismiss = { if (picker == PickerTarget.Start) picker = null },
        )
        PickerTarget.End -> AvTimePickerDialog(
            title = "Ends",
            initial = draft.end,
            onPick = onEnd,
            onDismiss = { if (picker == PickerTarget.End) picker = null },
        )
        null -> Unit
    }
}
