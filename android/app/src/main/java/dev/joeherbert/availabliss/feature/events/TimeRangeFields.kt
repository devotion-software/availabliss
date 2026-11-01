package dev.joeherbert.availabliss.feature.events

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.joeherbert.availabliss.designsystem.component.AvDatePickerDialog
import dev.joeherbert.availabliss.designsystem.component.AvTimePickerDialog
import dev.joeherbert.availabliss.designsystem.component.FieldTile
import dev.joeherbert.availabliss.designsystem.component.ToggleRow
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import java.time.LocalDate

/** Which picker is showing. Pure UI state. */
private enum class PickerStep { StartDate, StartTime, EndDate, EndTime }

/**
 * "All day" toggle plus Starts / Ends tiles. Tapping a tile opens a date picker,
 * then (unless all-day) a time picker. Every change is reported through [onChange].
 */
@Composable
fun TimeRangeFields(
    draft: TimeRangeDraft,
    onChange: (TimeRangeDraft) -> Unit,
    modifier: Modifier = Modifier,
) {
    var step by rememberSaveable { mutableStateOf<PickerStep?>(null) }
    var pendingDate by rememberSaveable { mutableStateOf<LocalDate?>(null) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ToggleRow(title = "All day", checked = draft.allDay, onCheckedChange = { onChange(draft.withAllDay(it)) })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldTile("Starts", draft.startLabel, onClick = { step = PickerStep.StartDate }, modifier = Modifier.weight(1f))
            FieldTile("Ends", draft.endLabel, onClick = { step = PickerStep.EndDate }, modifier = Modifier.weight(1f))
        }
        if (!draft.isValid) {
            Text("Ends before it starts", style = AvailablissTheme.type.caption, color = AvailablissTheme.colors.warnInk)
        }
    }

    when (step) {
        PickerStep.StartDate -> AvDatePickerDialog(
            initial = draft.startDate,
            minDate = null,
            onPick = { date ->
                if (draft.allDay) onChange(draft.withStart(date)) else {
                    pendingDate = date
                    step = PickerStep.StartTime
                }
            },
            onDismiss = { if (step == PickerStep.StartDate) step = null },
        )
        PickerStep.StartTime -> AvTimePickerDialog(
            title = "Starts",
            initial = draft.startTime,
            onPick = { time -> onChange(draft.withStart(pendingDate ?: draft.startDate, time)) },
            onDismiss = { step = null },
        )
        PickerStep.EndDate -> AvDatePickerDialog(
            initial = draft.endDate,
            minDate = draft.startDate,
            onPick = { date ->
                if (draft.allDay) onChange(draft.withEnd(date)) else {
                    pendingDate = date
                    step = PickerStep.EndTime
                }
            },
            onDismiss = { if (step == PickerStep.EndDate) step = null },
        )
        PickerStep.EndTime -> AvTimePickerDialog(
            title = "Ends",
            initial = draft.endTime,
            onPick = { time -> onChange(draft.withEnd(pendingDate ?: draft.endDate, time)) },
            onDismiss = { step = null },
        )
        null -> Unit
    }
}
