package dev.joeherbert.availabliss.feature.common

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.joeherbert.availabliss.core.calendar.CalendarEventDraft
import dev.joeherbert.availabliss.core.calendar.CalendarInserter
import dev.joeherbert.availabliss.core.calendar.DeviceCalendarInfo
import dev.joeherbert.availabliss.core.calendar.InsertResult
import dev.joeherbert.availabliss.core.data.AppPreferences
import dev.joeherbert.availabliss.designsystem.component.AvBottomSheet
import dev.joeherbert.availabliss.designsystem.component.ButtonStyle
import dev.joeherbert.availabliss.designsystem.component.ColorSwatch
import dev.joeherbert.availabliss.designsystem.component.PrimaryButton
import dev.joeherbert.availabliss.designsystem.component.RowDivider
import dev.joeherbert.availabliss.designsystem.component.SurfacePanel
import dev.joeherbert.availabliss.designsystem.icon.AvIcons
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import dev.joeherbert.availabliss.designsystem.theme.Shapes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/** A plan waiting for the user to choose which calendar it goes in. */
data class PendingCalendarAdd(val key: String, val draft: CalendarEventDraft)

/** State of "Add to calendar" for a screen. */
data class AddToCalendarState(
    /** Writable device calendars (empty without calendar permission → intent fallback). */
    val calendars: List<DeviceCalendarInfo> = emptyList(),
    /** The calendar offered first: the one last added to, else the primary one. */
    val selectedCalendarId: Long? = null,
    /** Keys (event / proposal ids) already added during this session. */
    val added: Set<String> = emptySet(),
    /** Set while the calendar choice sheet is open. */
    val choosing: PendingCalendarAdd? = null,
    /** Set when the calendar app must handle the insert; the UI launches it then calls [AddToCalendarDelegate.onIntentHandled]. */
    val pendingIntent: Intent? = null,
) {
    val selectedCalendar: DeviceCalendarInfo? get() = calendars.firstOrNull { it.id == selectedCalendarId }
}

/**
 * Reusable "Add to calendar" logic for ViewModels: with more than one writable calendar it asks
 * which one first ([AddToCalendarState.choosing], shown by [AddToCalendarEffect]), then inserts
 * directly when WRITE_CALENDAR is granted, otherwise hands off to the calendar app with an
 * `ACTION_INSERT` intent. The calendar chosen is remembered for next time. Not a singleton: one per ViewModel.
 */
class AddToCalendarDelegate @Inject constructor(
    private val inserter: CalendarInserter,
    private val preferences: AppPreferences,
) {
    private val _state = MutableStateFlow(AddToCalendarState())
    val state: StateFlow<AddToCalendarState> = _state.asStateFlow()

    suspend fun loadCalendars() {
        val calendars = runCatching { inserter.writableCalendars() }.getOrDefault(emptyList())
        val last = preferences.addToCalendarId.first()
        _state.update { s ->
            s.copy(
                calendars = calendars,
                selectedCalendarId = s.selectedCalendarId?.takeIf { id -> calendars.any { it.id == id } }
                    ?: last?.takeIf { id -> calendars.any { it.id == id } }
                    ?: calendars.firstOrNull { it.isPrimary }?.id ?: calendars.firstOrNull()?.id,
            )
        }
    }

    /** Adds the plan, first asking which calendar when there's a choice. */
    suspend fun add(key: String, draft: CalendarEventDraft) {
        if (_state.value.calendars.size > 1) {
            _state.update { it.copy(choosing = PendingCalendarAdd(key, draft)) }
        } else {
            insert(key, draft, _state.value.selectedCalendarId)
        }
    }

    /** The calendar was chosen in the sheet: remember it and add the waiting plan there. */
    suspend fun choose(calendarId: Long) {
        val pending = _state.value.choosing ?: return
        _state.update { it.copy(choosing = null, selectedCalendarId = calendarId) }
        preferences.setAddToCalendarId(calendarId)
        insert(pending.key, pending.draft, calendarId)
    }

    fun cancelChoice() = _state.update { it.copy(choosing = null) }

    private suspend fun insert(key: String, draft: CalendarEventDraft, calendarId: Long?) {
        when (val result = inserter.insert(draft, calendarId)) {
            InsertResult.Inserted -> _state.update { it.copy(added = it.added + key) }
            is InsertResult.NeedsIntent -> _state.update { it.copy(pendingIntent = result.intent, added = it.added + key) }
        }
    }

    fun onIntentHandled() = _state.update { it.copy(pendingIntent = null) }
}

/**
 * Launches [AddToCalendarState.pendingIntent] when set, and shows the calendar choice sheet while
 * [AddToCalendarState.choosing] is set. Place once per screen.
 */
@Composable
fun AddToCalendarEffect(
    state: AddToCalendarState,
    onHandled: () -> Unit,
    onChoose: (Long) -> Unit,
    onCancelChoice: () -> Unit,
) {
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
    state.choosing?.let { pending ->
        CalendarChoiceSheet(
            title = "Add to calendar",
            message = "Which calendar should ${pending.draft.title} go in?",
            calendars = state.calendars,
            selectedId = state.selectedCalendarId,
            onChoose = onChoose,
            onDismiss = onCancelChoice,
        )
    }
}

/**
 * Chooses which of the phone's calendars a plan goes in: writable calendars grouped by account,
 * each with its colour, the current choice ticked. Picking one calls [onChoose].
 */
@Composable
fun CalendarChoiceSheet(
    title: String,
    message: String?,
    calendars: List<DeviceCalendarInfo>,
    selectedId: Long?,
    onChoose: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = AvailablissTheme.colors
    AvBottomSheet(title = title, onDismissRequest = onDismiss) {
        if (!message.isNullOrBlank()) {
            Text(message, style = AvailablissTheme.type.body, color = colors.muted)
        }
        calendars.groupBy { it.accountName }.toSortedMap(String.CASE_INSENSITIVE_ORDER).forEach { (account, list) ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(account, style = AvailablissTheme.type.captionStrong, color = colors.muted, modifier = Modifier.padding(start = 4.dp))
                SurfacePanel(Modifier.fillMaxWidth()) {
                    list.forEachIndexed { index, cal ->
                        if (index > 0) RowDivider()
                        val selected = cal.id == selectedId
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 52.dp)
                                .selectable(selected = selected, role = Role.RadioButton) { onChoose(cal.id) },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            ColorSwatch(cal.color?.let(::parseHex) ?: colors.warn, size = 12.dp, radius = 6.dp)
                            Text(
                                cal.name,
                                style = if (selected) AvailablissTheme.type.bodySmallStrong else AvailablissTheme.type.body,
                                color = colors.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (selected) Icon(AvIcons.Check, null, tint = colors.accentInk, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

/**
 * The "Add to calendar" button, which says "Added to your calendar" just after you add it. An event
 * already added before this screen opened isn't offered again: nothing shows.
 */
@Composable
fun AddToCalendarButton(added: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = 54.dp) {
    var tapped by remember { mutableStateOf(false) }
    if (added && !tapped) return
    PrimaryButton(
        text = if (added) "Added to your calendar" else "Add to calendar",
        onClick = {
            tapped = true
            onClick()
        },
        icon = if (added) AvIcons.Check else AvIcons.CalendarAdd,
        style = if (added) ButtonStyle.Success else ButtonStyle.Solid,
        height = height,
        shape = if (height >= 52.dp) Shapes.controlLarge else Shapes.control,
        modifier = modifier.fillMaxWidth(),
    )
}

/** Parses `#RRGGBB` / `#AARRGGBB`. */
fun parseHex(hex: String): Color? = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull()
