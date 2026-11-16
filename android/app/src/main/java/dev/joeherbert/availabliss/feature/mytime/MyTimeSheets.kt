package dev.joeherbert.availabliss.feature.mytime

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.joeherbert.availabliss.core.common.DateTimes
import dev.joeherbert.availabliss.core.common.TimeRangeDraft
import dev.joeherbert.availabliss.core.network.model.Repeat
import dev.joeherbert.availabliss.designsystem.component.AvBottomSheet
import dev.joeherbert.availabliss.designsystem.component.AvDatePickerDialog
import dev.joeherbert.availabliss.designsystem.component.AvTextField
import dev.joeherbert.availabliss.designsystem.component.ButtonStyle
import dev.joeherbert.availabliss.designsystem.component.DateRangeInput
import dev.joeherbert.availabliss.designsystem.component.PrimaryButton
import dev.joeherbert.availabliss.designsystem.component.RowDivider
import dev.joeherbert.availabliss.designsystem.component.SecondaryButton
import dev.joeherbert.availabliss.designsystem.component.SurfacePanel
import dev.joeherbert.availabliss.designsystem.component.TextAction
import dev.joeherbert.availabliss.designsystem.icon.AvIcons
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import dev.joeherbert.availabliss.designsystem.theme.Shapes
import java.time.LocalDate

/** "Block a time" sheet: the date range, then repeat (and when it ends), then a private note. */
@Composable
fun BlockTimeSheet(
    draft: BlockDraft,
    saving: Boolean,
    onDismiss: () -> Unit,
    onRange: (TimeRangeDraft) -> Unit,
    onRepeat: (Repeat) -> Unit,
    onUntil: (LocalDate?) -> Unit,
    onNote: (String) -> Unit,
    onSave: () -> Unit,
    /** Shown when editing an existing block; deletes every occurrence. */
    onDelete: () -> Unit = {},
) {
    val colors = AvailablissTheme.colors
    val type = AvailablissTheme.type
    var pickingUntil by rememberSaveable { mutableStateOf(false) }

    AvBottomSheet(title = if (draft.isEditing) "Edit busy time" else "Block a time", onDismissRequest = onDismiss) {
        SurfacePanel(Modifier.fillMaxWidth(), shape = Shapes.control, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
            // A block being edited may have started in the past; new ones start today or later.
            DateRangeInput(draft.range, onRange, minDate = minOf(DateTimes.today(), draft.date))
        }
        SurfacePanel(Modifier.fillMaxWidth(), shape = Shapes.control, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp)) {
            MenuRow(label = "Repeat", value = draft.repeatLabel) { close ->
                Repeat.choices.forEach { option ->
                    MenuOption(repeatOptionLabel(option, draft.date), selected = option == draft.repeat) {
                        close()
                        onRepeat(option)
                    }
                }
            }
            if (draft.repeat != Repeat.None) {
                RowDivider()
                MenuRow(label = "Ends", value = draft.until?.let { "On ${DateTimes.dayMonthYear(it)}" } ?: "Never") { close ->
                    MenuOption("Never", selected = draft.until == null) {
                        close()
                        onUntil(null)
                    }
                    MenuOption(if (draft.until == null) "On a date…" else "Change date…", selected = false) {
                        close()
                        pickingUntil = true
                    }
                }
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

    if (pickingUntil) {
        AvDatePickerDialog(
            initial = draft.until ?: draft.date.plusMonths(3),
            onPick = { onUntil(it) },
            onDismiss = { pickingUntil = false },
            minDate = draft.date,
        )
    }
}

/** "Repeat · Every week on Monday ▾": a row whose value opens a menu. */
@Composable
private fun MenuRow(label: String, value: String, menu: @Composable ColumnScope.(close: () -> Unit) -> Unit) {
    val colors = AvailablissTheme.colors
    val type = AvailablissTheme.type
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 50.dp)
                .clickable(role = Role.Button, onClickLabel = "Change ${label.lowercase()}") { open = true }
                .semantics(mergeDescendants = true) { contentDescription = "$label, $value" },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(label, style = type.bodyMediumRegular, color = colors.ink)
            Text(value, style = type.bodyMedium, color = colors.accentInk, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
            Icon(AvIcons.ChevronDown, null, tint = colors.muted, modifier = Modifier.size(16.dp))
        }
        // Anchored at the row's bottom end, so the menu opens under the value.
        Box(Modifier.align(Alignment.BottomEnd)) {
            DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = colors.bg) {
                menu { open = false }
            }
        }
    }
}

@Composable
private fun MenuOption(text: String, selected: Boolean, onClick: () -> Unit) {
    val colors = AvailablissTheme.colors
    DropdownMenuItem(
        text = { Text(text, style = AvailablissTheme.type.bodySmallStrong, color = if (selected) colors.accentInk else colors.ink) },
        trailingIcon = if (selected) ({ Icon(AvIcons.Check, null, tint = colors.accentInk, modifier = Modifier.size(16.dp)) }) else null,
        onClick = onClick,
        modifier = Modifier.semantics { if (selected) stateDescription = "Selected" },
    )
}

/**
 * "Busy from your calendar": synced busy time can't be edited here (only the busy range is
 * ever read). It can be marked not busy (this occurrence, or every time it repeats), or a
 * whole calendar can stop counting in the calendar settings.
 */
@Composable
fun CalendarBusySheet(
    info: CalendarBusyUi,
    onDismiss: () -> Unit,
    onOpenCalendars: () -> Unit,
    onIgnore: (series: Boolean) -> Unit = {},
    onUnignore: () -> Unit = {},
) {
    val colors = AvailablissTheme.colors
    val type = AvailablissTheme.type
    AvBottomSheet(title = if (info.ignored) "Marked not busy" else "Busy from your calendar", onDismissRequest = onDismiss) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(Shapes.control)
                .background(colors.surface)
                .border(1.dp, colors.glassBorder, Shapes.control)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.busyBg)
                    .border(1.dp, colors.busyLine, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AvIcons.CalendarSync, null, tint = colors.ink, modifier = Modifier.size(20.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(info.calendarName, style = type.bodyMedium, color = colors.ink)
                Text(info.timeLabel, style = type.label.copy(fontWeight = FontWeight.Normal), color = colors.muted)
            }
        }
        if (info.ignored) {
            Text("You said this doesn't make you busy, so your groups see you as free then.", style = type.label.copy(fontWeight = FontWeight.Normal), color = colors.muted)
            PrimaryButton("Count as busy again", onUnignore, loading = info.working, modifier = Modifier.fillMaxWidth())
        } else if (info.interval?.calendarId != null) {
            Text("Doesn't really make you busy? Your groups will see you as free then.", style = type.label.copy(fontWeight = FontWeight.Normal), color = colors.muted)
            PrimaryButton(if (info.recurring) "Not busy: just this one" else "Mark as not busy", { onIgnore(false) }, loading = info.working, modifier = Modifier.fillMaxWidth())
            if (info.recurring) {
                SecondaryButton(text = "Not busy: every time it repeats", onClick = { onIgnore(true) }, enabled = !info.working, modifier = Modifier.fillMaxWidth())
            }
        }
        Text(
            "Availabliss only reads when you're busy, never what the event is, so it can't be edited here. " +
                "Change it in your calendar and it updates here automatically, or choose which calendars count as busy.",
            style = type.label.copy(fontWeight = FontWeight.Normal, lineHeight = 19.sp),
            color = colors.muted,
        )
        SecondaryButton(
            text = "Calendar settings",
            onClick = {
                onDismiss()
                onOpenCalendars()
            },
            icon = AvIcons.CalendarSync,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
