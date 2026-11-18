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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.joeherbert.availabliss.core.calendar.AddedCalendarEvent
import dev.joeherbert.availabliss.core.calendar.AddedCalendarEvents
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
import dev.joeherbert.availabliss.designsystem.component.TextAction
import dev.joeherbert.availabliss.designsystem.icon.AvIcons
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import dev.joeherbert.availabliss.designsystem.theme.Shapes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/** What the calendar choice sheet is open for. */
sealed interface CalendarChoice {
    val key: String

    /** A plan waiting for the user to choose which calendar it goes in. */
    data class Add(override val key: String, val draft: CalendarEventDraft) : CalendarChoice

    /** A plan already in [currentCalendarId], to move to another calendar. */
    data class Move(override val key: String, val currentCalendarId: Long) : CalendarChoice
}

/** Where one plan stands in the phone's calendars, for [AddToCalendarButton]. */
data class CalendarStatus(
    /** In a calendar (added from this phone and still there). */
    val inCalendar: Boolean = false,
    /** Added just now, on this screen. */
    val justAdded: Boolean = false,
    /** The calendar it's in, when known. */
    val calendar: DeviceCalendarInfo? = null,
    /** Whether it can be moved to another calendar. */
    val canChange: Boolean = false,
)

/** State of "Add to calendar" for a screen. */
data class AddToCalendarState(
    /** Writable device calendars (empty without calendar permission → intent fallback). */
    val calendars: List<DeviceCalendarInfo> = emptyList(),
    /** The calendar offered first: the one last added to, else the primary one. */
    val selectedCalendarId: Long? = null,
    /** Keys (event / proposal ids) added on this screen. */
    val added: Set<String> = emptySet(),
    /** Keys in a calendar → the calendar they're in (null when not known, e.g. added by the calendar app). */
    val inCalendar: Map<String, Long?> = emptyMap(),
    /** Set while the calendar choice sheet is open. */
    val choosing: CalendarChoice? = null,
    /** Set when the calendar app must handle the insert; the UI launches it then calls [AddToCalendarDelegate.onIntentHandled]. */
    val pendingIntent: Intent? = null,
) {
    val selectedCalendar: DeviceCalendarInfo? get() = calendars.firstOrNull { it.id == selectedCalendarId }

    fun status(key: String): CalendarStatus {
        if (key !in inCalendar) return CalendarStatus()
        val calendar = inCalendar[key]?.let { id -> calendars.firstOrNull { it.id == id } }
        return CalendarStatus(
            inCalendar = true,
            justAdded = key in added,
            calendar = calendar,
            canChange = calendar != null && calendars.size > 1,
        )
    }
}

/**
 * Reusable "Add to calendar" logic for ViewModels: with more than one writable calendar it asks
 * which one first ([AddToCalendarState.choosing], shown by [AddToCalendarEffect]), then inserts
 * directly when WRITE_CALENDAR is granted, otherwise hands off to the calendar app with an
 * `ACTION_INSERT` intent. The calendar chosen is remembered for next time, and plans added are
 * remembered across launches ([AddedCalendarEvents]): asked again for one already in a calendar,
 * it offers to move it to another. Not a singleton: one per ViewModel.
 */
class AddToCalendarDelegate @Inject constructor(
    private val inserter: CalendarInserter,
    private val preferences: AppPreferences,
    private val addedEvents: AddedCalendarEvents,
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
        refreshAdded()
    }

    /**
     * Re-checks which remembered plans are still in a calendar, and which one: a plan deleted in
     * the calendar app since is offered again. Without permission to read calendars they can't be
     * checked, so they count as still there.
     */
    private suspend fun refreshAdded() {
        val canRead = inserter.canReadCalendars()
        val found = addedEvents.all().mapNotNull { (key, added) ->
            val calendarId = inserter.calendarOf(added.eventId)
            when {
                calendarId != null -> key to calendarId
                !canRead -> key to null
                else -> null
            }
        }.toMap()
        // Keys added by the calendar app this session aren't remembered, so keep them.
        _state.update { s -> s.copy(inCalendar = s.inCalendar.filterKeys { it in s.added && it !in found } + found) }
    }

    /**
     * Adds the plan, first asking which calendar when there's a choice. For a plan already in a
     * calendar, asks which calendar to move it to instead.
     */
    suspend fun add(key: String, draft: CalendarEventDraft) {
        val s = _state.value
        val current = s.inCalendar[key]
        when {
            current != null -> if (s.calendars.size > 1) _state.update { it.copy(choosing = CalendarChoice.Move(key, current)) }
            key in s.inCalendar -> Unit
            s.calendars.size > 1 -> _state.update { it.copy(choosing = CalendarChoice.Add(key, draft)) }
            else -> insert(key, draft, s.selectedCalendarId)
        }
    }

    /** A calendar was chosen in the sheet: remember it, and add the waiting plan there or move it there. */
    suspend fun choose(calendarId: Long) {
        val choice = _state.value.choosing ?: return
        _state.update { it.copy(choosing = null, selectedCalendarId = calendarId) }
        preferences.setAddToCalendarId(calendarId)
        when (choice) {
            is CalendarChoice.Add -> insert(choice.key, choice.draft, calendarId)
            is CalendarChoice.Move -> move(choice.key, calendarId)
        }
    }

    fun cancelChoice() = _state.update { it.copy(choosing = null) }

    private suspend fun insert(key: String, draft: CalendarEventDraft, calendarId: Long?) {
        when (val result = inserter.insert(draft, calendarId)) {
            is InsertResult.Inserted -> {
                addedEvents.set(key, AddedCalendarEvent(result.eventId))
                _state.update { it.copy(added = it.added + key, inCalendar = it.inCalendar + (key to calendarId)) }
            }
            is InsertResult.NeedsIntent -> _state.update {
                it.copy(pendingIntent = result.intent, added = it.added + key, inCalendar = it.inCalendar + (key to null))
            }
        }
    }

    private suspend fun move(key: String, calendarId: Long) {
        val added = addedEvents.all()[key] ?: return
        if (_state.value.inCalendar[key] == calendarId) return
        val newId = inserter.move(added.eventId, calendarId) ?: return
        addedEvents.set(key, AddedCalendarEvent(newId))
        _state.update { it.copy(inCalendar = it.inCalendar + (key to calendarId)) }
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
    when (val choice = state.choosing) {
        is CalendarChoice.Add -> CalendarChoiceSheet(
            title = "Add to calendar",
            message = "Which calendar should ${choice.draft.title} go in?",
            calendars = state.calendars,
            selectedId = state.selectedCalendarId,
            onChoose = onChoose,
            onDismiss = onCancelChoice,
        )
        is CalendarChoice.Move -> CalendarChoiceSheet(
            title = "Move to calendar",
            message = "Choose another calendar and the plan moves there.",
            calendars = state.calendars,
            selectedId = choice.currentCalendarId,
            onChoose = onChoose,
            onDismiss = onCancelChoice,
        )
        null -> Unit
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
 * The "Add to calendar" button, which says "Added to your calendar" just after you add it. Once a
 * plan is in a calendar it says which one, with Change to move it (both call [onClick]; the
 * delegate decides). A plan deleted from the calendar since is offered again.
 */
@Composable
fun AddToCalendarButton(status: CalendarStatus, onClick: () -> Unit, modifier: Modifier = Modifier, height: Dp = 54.dp) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!status.inCalendar || status.justAdded) {
            PrimaryButton(
                text = if (status.justAdded) "Added to your calendar" else "Add to calendar",
                onClick = { if (!status.inCalendar) onClick() },
                icon = if (status.justAdded) AvIcons.Check else AvIcons.CalendarAdd,
                style = if (status.justAdded) ButtonStyle.Success else ButtonStyle.Solid,
                height = height,
                shape = if (height >= 52.dp) Shapes.controlLarge else Shapes.control,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (status.inCalendar) AddedToCalendarRow(status, onChange = onClick)
    }
}

/** "● In Work · Change": the quiet line once a plan is in the user's calendar. */
@Composable
private fun AddedToCalendarRow(status: CalendarStatus, onChange: () -> Unit) {
    val colors = AvailablissTheme.colors
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val calendar = status.calendar
        if (calendar != null) {
            ColorSwatch(calendar.color?.let(::parseHex) ?: colors.warn, size = 10.dp, radius = 5.dp)
        } else {
            Icon(AvIcons.Check, null, tint = colors.okInk, modifier = Modifier.size(14.dp))
        }
        Text(
            calendar?.let { "In ${it.name}" } ?: "In your calendar",
            style = AvailablissTheme.type.caption,
            color = colors.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (status.canChange) {
            TextAction("Change", onChange, Modifier.semantics { contentDescription = "Change calendar" })
        }
    }
}

/** Parses `#RRGGBB` / `#AARRGGBB`. */
fun parseHex(hex: String): Color? = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull()
