package dev.joeherbert.availabliss.designsystem.component

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.unit.dp
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import dev.joeherbert.availabliss.designsystem.theme.Shapes
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

private fun LocalDate.utcMillis() = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
private fun Long.utcDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

@Composable
private fun pickerColors() = AvailablissTheme.colors.let { c ->
    DatePickerDefaults.colors(
        containerColor = c.bg,
        titleContentColor = c.muted,
        headlineContentColor = c.ink,
        weekdayContentColor = c.muted,
        dayContentColor = c.ink,
        selectedDayContainerColor = c.accent,
        selectedDayContentColor = c.onAccent,
        todayContentColor = c.accentInk,
        todayDateBorderColor = c.accent,
        dayInSelectionRangeContainerColor = c.accentSoft,
        dayInSelectionRangeContentColor = c.ink,
        navigationContentColor = c.ink,
        yearContentColor = c.ink,
        selectedYearContainerColor = c.accent,
        selectedYearContentColor = c.onAccent,
        dividerColor = c.line,
    )
}

/** Material 3 date picker restyled with Availabliss tokens. Dates are local calendar days. */
@Composable
fun AvDatePickerDialog(
    initial: LocalDate,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    minDate: LocalDate? = null,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.utcMillis(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = minDate == null || !utcTimeMillis.utcDate().isBefore(minDate)
        },
    )
    val colors = pickerColors()
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextAction("Done", { state.selectedDateMillis?.let { onPick(it.utcDate()) }; onDismiss() }) },
        dismissButton = { TextAction("Cancel", onDismiss, color = AvailablissTheme.colors.muted) },
        shape = Shapes.cardLarge,
        colors = colors,
        tonalElevation = 0.dp,
    ) {
        DatePicker(state = state, colors = colors, showModeToggle = false)
    }
}

/** Inclusive date range picker ("Or only these dates", search range). */
@Composable
fun AvDateRangePickerDialog(
    initialStart: LocalDate?,
    initialEnd: LocalDate?,
    onPick: (LocalDate, LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialStart?.utcMillis(),
        initialSelectedEndDateMillis = initialEnd?.utcMillis(),
    )
    val colors = pickerColors()
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextAction("Done", {
                val s = state.selectedStartDateMillis
                val e = state.selectedEndDateMillis ?: s
                if (s != null && e != null) onPick(s.utcDate(), e.utcDate())
                onDismiss()
            })
        },
        dismissButton = { TextAction("Cancel", onDismiss, color = AvailablissTheme.colors.muted) },
        shape = Shapes.cardLarge,
        colors = colors,
        tonalElevation = 0.dp,
    ) {
        DateRangePicker(state = state, colors = colors, showModeToggle = false, modifier = Modifier.heightIn(max = 520.dp))
    }
}

/** 24-hour time picker in a dialog. */
@Composable
fun AvTimePickerDialog(
    title: String,
    initial: LocalTime,
    onPick: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = AvailablissTheme.colors
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.bg,
        shape = Shapes.cardLarge,
        tonalElevation = 0.dp,
        title = { Text(title, style = AvailablissTheme.type.headlineSmall, color = c.ink) },
        text = {
            TimePicker(
                state = state,
                colors = TimePickerDefaults.colors(
                    clockDialColor = c.surface,
                    selectorColor = c.accent,
                    containerColor = c.bg,
                    clockDialSelectedContentColor = c.onAccent,
                    clockDialUnselectedContentColor = c.ink,
                    timeSelectorSelectedContainerColor = c.accentSoft,
                    timeSelectorUnselectedContainerColor = c.surface,
                    timeSelectorSelectedContentColor = c.accentInk,
                    timeSelectorUnselectedContentColor = c.ink,
                ),
            )
        },
        confirmButton = { TextAction("Done", { onPick(LocalTime.of(state.hour, state.minute)); onDismiss() }) },
        dismissButton = { TextAction("Cancel", onDismiss, color = c.muted) },
    )
}
