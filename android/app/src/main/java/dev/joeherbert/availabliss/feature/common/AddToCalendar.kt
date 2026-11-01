package dev.joeherbert.availabliss.feature.common

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.joeherbert.availabliss.core.calendar.CalendarEventDraft
import dev.joeherbert.availabliss.core.calendar.CalendarInserter
import dev.joeherbert.availabliss.core.calendar.DeviceCalendarInfo
import dev.joeherbert.availabliss.core.calendar.InsertResult
import dev.joeherbert.availabliss.designsystem.component.ButtonStyle
import dev.joeherbert.availabliss.designsystem.component.ColorSwatch
import dev.joeherbert.availabliss.designsystem.component.PrimaryButton
import dev.joeherbert.availabliss.designsystem.icon.AvIcons
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import dev.joeherbert.availabliss.designsystem.theme.Shapes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/** State of one-tap "Add to calendar" for a screen. */
data class AddToCalendarState(
    /** Writable device calendars (empty without calendar permission → intent fallback). */
    val calendars: List<DeviceCalendarInfo> = emptyList(),
    val selectedCalendarId: Long? = null,
    /** Keys (event / proposal ids) already added during this session. */
    val added: Set<String> = emptySet(),
    /** Set when the calendar app must handle the insert; the UI launches it then calls [AddToCalendarDelegate.onIntentHandled]. */
    val pendingIntent: Intent? = null,
) {
    val selectedCalendar: DeviceCalendarInfo? get() = calendars.firstOrNull { it.id == selectedCalendarId }
}

/**
 * Reusable "Add to calendar" logic for ViewModels: inserts directly into the chosen
 * device calendar when WRITE_CALENDAR is granted, otherwise hands off to the
 * calendar app with an `ACTION_INSERT` intent. Not a singleton: one per ViewModel.
 */
class AddToCalendarDelegate @Inject constructor(
    private val inserter: CalendarInserter,
) {
    private val _state = MutableStateFlow(AddToCalendarState())
    val state: StateFlow<AddToCalendarState> = _state.asStateFlow()

    suspend fun loadCalendars() {
        val calendars = runCatching { inserter.writableCalendars() }.getOrDefault(emptyList())
        _state.update { s ->
            s.copy(
                calendars = calendars,
                selectedCalendarId = s.selectedCalendarId?.takeIf { id -> calendars.any { it.id == id } }
                    ?: calendars.firstOrNull { it.isPrimary }?.id ?: calendars.firstOrNull()?.id,
            )
        }
    }

    fun select(calendarId: Long) = _state.update { it.copy(selectedCalendarId = calendarId) }

    suspend fun add(key: String, draft: CalendarEventDraft) {
        when (val result = inserter.insert(draft, _state.value.selectedCalendarId)) {
            InsertResult.Inserted -> _state.update { it.copy(added = it.added + key) }
            is InsertResult.NeedsIntent -> _state.update { it.copy(pendingIntent = result.intent, added = it.added + key) }
        }
    }

    fun onIntentHandled() = _state.update { it.copy(pendingIntent = null) }
}

/** Launches [AddToCalendarState.pendingIntent] when set. Place once per screen. */
@Composable
fun AddToCalendarEffect(state: AddToCalendarState, onHandled: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(state.pendingIntent) {
        val intent = state.pendingIntent ?: return@LaunchedEffect
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // No calendar app; nothing more we can do.
        }
        onHandled()
    }
}

/** Calendar chooser row ("● Personal · iCloud ⌄") shown when direct insert is possible. */
@Composable
fun CalendarPickerRow(state: AddToCalendarState, onSelect: (Long) -> Unit, modifier: Modifier = Modifier) {
    val selected = state.selectedCalendar ?: return
    val colors = AvailablissTheme.colors
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 52.dp)
                .clip(Shapes.control)
                .background(colors.surface)
                .border(1.dp, colors.glassBorder, Shapes.control)
                .clickable(role = Role.DropdownList, onClickLabel = "Choose calendar") { open = true }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ColorSwatch(selected.color?.let(::parseHex) ?: colors.warn)
            Text(
                buildAnnotatedString {
                    append(selected.name)
                    withStyle(SpanStyle(fontWeight = FontWeight.Medium, color = colors.muted)) { append(" · ${selected.accountName}") }
                },
                style = AvailablissTheme.type.bodySmallStrong,
                color = colors.ink,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Icon(AvIcons.ChevronDown, null, tint = colors.muted, modifier = Modifier.size(16.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = colors.bg) {
            state.calendars.forEach { cal ->
                DropdownMenuItem(
                    text = { Text("${cal.name} · ${cal.accountName}", style = AvailablissTheme.type.bodySmallStrong) },
                    leadingIcon = { ColorSwatch(cal.color?.let(::parseHex) ?: colors.warn) },
                    onClick = {
                        onSelect(cal.id)
                        open = false
                    },
                )
            }
        }
    }
}

/** The "Add to calendar" / "Added to your calendar" button. */
@Composable
fun AddToCalendarButton(added: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = 54.dp) {
    PrimaryButton(
        text = if (added) "Added to your calendar" else "Add to calendar",
        onClick = onClick,
        icon = if (added) AvIcons.Check else AvIcons.CalendarAdd,
        style = if (added) ButtonStyle.Success else ButtonStyle.Solid,
        height = height,
        shape = if (height >= 52.dp) Shapes.controlLarge else Shapes.control,
        modifier = modifier.fillMaxWidth(),
    )
}

/** Parses `#RRGGBB` / `#AARRGGBB`. */
fun parseHex(hex: String): Color? = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull()
