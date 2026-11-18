package dev.joeherbert.availabliss.feature.common

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
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
import dev.joeherbert.availabliss.designsystem.icon.AvIcons
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import dev.joeherbert.availabliss.designsystem.theme.Shapes
import kotlinx.coroutines.delay
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

    /** Several plans ("Add all to calendar") waiting for one choice of calendar, by key. */
    data class AddAll(val drafts: List<Pair<String, CalendarEventDraft>>) : CalendarChoice {
        override val key: String get() = "all"
    }
}

/**
 * How "Add all to calendar" went: [added] plans went in. With [needsAccess] they couldn't be added
 * directly (no calendar access), so the calendar app was opened for the first one only.
 */
data class AddAllResult(val added: Int, val needsAccess: Boolean = false) {
    /** "Added 3 plans to your calendar". */
    val message: String
        get() = when {
            needsAccess -> "Opened your calendar app for the first one. Allow calendar access to add them all at once."
            added == 1 -> "Added 1 plan to your calendar"
            else -> "Added $added plans to your calendar"
        }
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

    /**
     * Adds every plan in [drafts] (by key) not already in a calendar, asking which calendar once when
     * there's a choice. Returns how it went, or null while the choice is showing ([choose] returns it then).
     */
    suspend fun addAll(drafts: List<Pair<String, CalendarEventDraft>>): AddAllResult? {
        val s = _state.value
        val todo = drafts.filter { (key, _) -> key !in s.inCalendar }.distinctBy { it.first }
        return when {
            todo.isEmpty() -> AddAllResult(0)
            s.calendars.size > 1 -> {
                _state.update { it.copy(choosing = CalendarChoice.AddAll(todo)) }
                null
            }
            else -> insertAll(todo, s.selectedCalendarId)
        }
    }

    /**
     * A calendar was chosen in the sheet: remember it, and add the waiting plan(s) there or move it
     * there. Returns how "Add all" went when that's what it was for.
     */
    suspend fun choose(calendarId: Long): AddAllResult? {
        val choice = _state.value.choosing ?: return null
        _state.update { it.copy(choosing = null, selectedCalendarId = calendarId) }
        preferences.setAddToCalendarId(calendarId)
        return when (choice) {
            is CalendarChoice.Add -> insert(choice.key, choice.draft, calendarId).let { null }
            is CalendarChoice.Move -> move(choice.key, calendarId).let { null }
            is CalendarChoice.AddAll -> insertAll(choice.drafts, calendarId)
        }
    }

    /** Inserts each in turn; without calendar access only the first can go (through the calendar app). */
    private suspend fun insertAll(drafts: List<Pair<String, CalendarEventDraft>>, calendarId: Long?): AddAllResult {
        var added = 0
        for ((key, draft) in drafts) {
            when (val result = inserter.insert(draft, calendarId)) {
                is InsertResult.Inserted -> {
                    addedEvents.set(key, AddedCalendarEvent(result.eventId))
                    _state.update { it.copy(added = it.added + key, inCalendar = it.inCalendar + (key to calendarId)) }
                    added++
                }
                is InsertResult.NeedsIntent -> {
                    if (added > 0) continue
                    _state.update { it.copy(pendingIntent = result.intent, added = it.added + key, inCalendar = it.inCalendar + (key to null)) }
                    return AddAllResult(1, needsAccess = drafts.size > 1)
                }
            }
        }
        return AddAllResult(added)
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
        is CalendarChoice.AddAll -> CalendarChoiceSheet(
            title = "Add to calendar",
            message = "Which calendar should your ${choice.drafts.size} plans go in?",
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
 * The "Add to calendar" button, which says "Added to your calendar" for a few seconds after you
 * add it. After that (and on later visits) the button goes: a quiet line says which calendar the
 * plan is in, with Change to move it (both call [onClick]; the delegate decides). A plan deleted
 * from the calendar since is offered again.
 */
@Composable
fun AddToCalendarButton(status: CalendarStatus, onClick: () -> Unit, modifier: Modifier = Modifier, height: Dp = 54.dp) {
    var confirmed by remember(status.justAdded) { mutableStateOf(false) }
    LaunchedEffect(status.justAdded) {
        if (!status.justAdded) return@LaunchedEffect
        delay(CONFIRMATION_MILLIS)
        confirmed = true
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AnimatedVisibility(visible = !status.inCalendar || (status.justAdded && !confirmed), enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            PrimaryButton(
                text = if (status.inCalendar) "Added to your calendar" else "Add to calendar",
                onClick = { if (!status.inCalendar) onClick() },
                icon = if (status.inCalendar) AvIcons.Check else AvIcons.CalendarAdd,
                style = if (status.inCalendar) ButtonStyle.Success else ButtonStyle.Solid,
                height = height,
                shape = if (height >= 52.dp) Shapes.controlLarge else Shapes.control,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (status.inCalendar) AddedToCalendarRow(status, onChange = onClick)
    }
}

/** How long "Added to your calendar" shows before the button goes. */
private const val CONFIRMATION_MILLIS = 3_000L

/**
 * "● In Work ⌄": a small chip once a plan is in the user's calendar. Tapping it (when the calendar
 * can be chosen) moves the plan to another calendar; it hugs its content instead of spanning the card.
 */
@Composable
private fun AddedToCalendarRow(status: CalendarStatus, onChange: () -> Unit) {
    val colors = AvailablissTheme.colors
    val calendar = status.calendar
    val label = calendar?.let { "In ${it.name}" } ?: "In your calendar"
    Row(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .defaultMinSize(minHeight = 28.dp)
                .clip(CircleShape)
                .background(colors.surface)
                .border(1.dp, colors.line, CircleShape)
                .then(
                    if (status.canChange) {
                        Modifier.clickable(role = Role.Button, onClickLabel = "Change calendar", onClick = onChange)
                    } else Modifier,
                )
                .semantics(mergeDescendants = true) { contentDescription = label }
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (calendar != null) {
                ColorSwatch(calendar.color?.let(::parseHex) ?: colors.warn, size = 8.dp, radius = 4.dp)
            } else {
                Icon(AvIcons.Check, null, tint = colors.okInk, modifier = Modifier.size(12.dp))
            }
            Text(
                label,
                style = AvailablissTheme.type.caption,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (status.canChange) {
                Icon(AvIcons.ChevronDown, null, tint = colors.muted, modifier = Modifier.size(12.dp))
            }
        }
    }
}

/** Parses `#RRGGBB` / `#AARRGGBB`. */
fun parseHex(hex: String): Color? = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull()
