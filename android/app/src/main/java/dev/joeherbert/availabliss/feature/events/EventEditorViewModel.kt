package dev.joeherbert.availabliss.feature.events

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.joeherbert.availabliss.core.calendar.CalendarEventDraft
import dev.joeherbert.availabliss.core.common.UserMessage
import dev.joeherbert.availabliss.core.data.EventsRepository
import dev.joeherbert.availabliss.core.data.GroupsRepository
import dev.joeherbert.availabliss.core.data.UserRepository
import dev.joeherbert.availabliss.core.network.ApiError
import dev.joeherbert.availabliss.core.network.ApiResult
import dev.joeherbert.availabliss.core.network.model.CreateEventRequest
import dev.joeherbert.availabliss.core.network.model.GroupEvent
import dev.joeherbert.availabliss.feature.common.AddToCalendarDelegate
import dev.joeherbert.availabliss.feature.common.AddToCalendarState
import dev.joeherbert.availabliss.navigation.EventEditorRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EventEditorUiState(
    val groupName: String = "",
    /** Set when viewing / editing an existing event rather than adding one. */
    val eventId: String? = null,
    val loading: Boolean = false,
    /** New events are always editable; existing ones only by their creator or a group admin. */
    val canEdit: Boolean = true,
    val title: String = "",
    val location: String = "",
    val note: String = "",
    val range: TimeRangeDraft = TimeRangeDraft.default(),
    val titleError: String? = null,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val deleted: Boolean = false,
    val calendar: AddToCalendarState = AddToCalendarState(),
    val message: UserMessage? = null,
) {
    val isExisting: Boolean get() = eventId != null
    val canSave: Boolean get() = canEdit && title.isNotBlank() && range.isValid && !saving
    val addedToCalendar: Boolean get() = eventId != null && eventId in calendar.added
}

@HiltViewModel
class EventEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val events: EventsRepository,
    private val groups: GroupsRepository,
    private val users: UserRepository,
    private val addToCalendar: AddToCalendarDelegate,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<EventEditorRoute>()
    private val _state = MutableStateFlow(
        EventEditorUiState(
            eventId = route.eventId,
            loading = route.eventId != null,
            range = TimeRangeDraft.fromIso(route.start, route.end, allDay = false) ?: TimeRangeDraft.default(),
        ),
    )
    val state: StateFlow<EventEditorUiState> = _state.asStateFlow()

    /** The event as last loaded or saved, for "Add to calendar". */
    private var event: GroupEvent? = null

    init {
        viewModelScope.launch { addToCalendar.state.collect { cal -> _state.update { it.copy(calendar = cal) } } }
        viewModelScope.launch {
            val group = groups.cachedGroup(route.groupId) ?: (groups.refreshGroup(route.groupId) as? ApiResult.Success)?.value
            _state.update { it.copy(groupName = group?.name.orEmpty()) }
            route.eventId?.let { load(it, isAdmin = group?.role?.isAdmin == true) }
        }
    }

    private suspend fun load(eventId: String, isAdmin: Boolean) {
        when (val result = events.get(eventId)) {
            is ApiResult.Success -> {
                val e = result.value
                event = e
                addToCalendar.loadCalendars()
                _state.update {
                    it.copy(
                        loading = false,
                        canEdit = isAdmin || e.createdBy == users.user.value?.id,
                        title = e.title,
                        location = e.location.orEmpty(),
                        note = e.note.orEmpty(),
                        range = TimeRangeDraft.fromIso(e.start, e.end, e.allDay) ?: it.range,
                    )
                }
            }
            is ApiResult.Failure -> _state.update { it.copy(loading = false, message = UserMessage(result.error.message)) }
        }
    }

    fun onTitleChange(value: String) = _state.update { it.copy(title = value.take(120), titleError = null) }
    fun onLocationChange(value: String) = _state.update { it.copy(location = value.take(160)) }
    fun onNoteChange(value: String) = _state.update { it.copy(note = value.take(1000)) }
    fun onRangeChange(range: TimeRangeDraft) = _state.update { it.copy(range = range) }

    fun save() {
        val s = _state.value
        if (s.title.isBlank()) {
            _state.update { it.copy(titleError = "Give the event a name") }
            return
        }
        if (!s.canSave) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            val title = s.title.trim()
            val location = s.location.trim().ifBlank { null }
            val note = s.note.trim().ifBlank { null }
            val result = if (s.eventId == null) {
                events.create(
                    route.groupId,
                    CreateEventRequest(
                        title = title,
                        start = s.range.startIso(),
                        end = s.range.endIso(),
                        allDay = s.range.allDay,
                        location = location,
                        note = note,
                    ),
                )
            } else {
                events.update(s.eventId) {
                    set("title", title)
                    set("start", s.range.startIso())
                    set("end", s.range.endIso())
                    set("allDay", s.range.allDay)
                    set("location", location)
                    set("note", note)
                }
            }
            when (result) {
                is ApiResult.Success -> {
                    event = result.value
                    _state.update { it.copy(saving = false, saved = true) }
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(
                        saving = false,
                        titleError = (result.error as? ApiError.Validation)?.issueFor("title"),
                        message = UserMessage(result.error.message),
                    )
                }
            }
        }
    }

    fun delete() {
        val id = _state.value.eventId ?: return
        viewModelScope.launch {
            when (val result = events.delete(id)) {
                is ApiResult.Success -> _state.update { it.copy(deleted = true) }
                is ApiResult.Failure -> _state.update { it.copy(message = UserMessage(result.error.message)) }
            }
        }
    }

    fun onAddToCalendar() {
        val e = event ?: return
        val draft = CalendarEventDraft.from(e) ?: return
        viewModelScope.launch { addToCalendar.add(e.id, draft) }
    }

    fun onCalendarIntentHandled() = addToCalendar.onIntentHandled()

    fun onMessageShown(message: UserMessage) = _state.update { if (it.message?.id == message.id) it.copy(message = null) else it }
}
