package dev.joeherbert.availabliss.feature.events

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.joeherbert.availabliss.core.common.UserMessage
import dev.joeherbert.availabliss.core.data.EventsRepository
import dev.joeherbert.availabliss.core.data.GroupsRepository
import dev.joeherbert.availabliss.core.network.ApiError
import dev.joeherbert.availabliss.core.network.ApiResult
import dev.joeherbert.availabliss.core.network.model.CreateEventRequest
import dev.joeherbert.availabliss.navigation.EventEditorRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EventEditorUiState(
    val groupName: String = "",
    val title: String = "",
    val location: String = "",
    val note: String = "",
    val range: TimeRangeDraft = TimeRangeDraft.default(),
    val titleError: String? = null,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val message: UserMessage? = null,
) {
    val canSave: Boolean get() = title.isNotBlank() && range.isValid && !saving
}

@HiltViewModel
class EventEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val events: EventsRepository,
    private val groups: GroupsRepository,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<EventEditorRoute>()
    private val _state = MutableStateFlow(
        EventEditorUiState(range = TimeRangeDraft.fromIso(route.start, route.end, allDay = false) ?: TimeRangeDraft.default()),
    )
    val state: StateFlow<EventEditorUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val group = groups.cachedGroup(route.groupId) ?: (groups.refreshGroup(route.groupId) as? ApiResult.Success)?.value
            _state.update { it.copy(groupName = group?.name.orEmpty()) }
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
            val request = CreateEventRequest(
                title = s.title.trim(),
                start = s.range.startIso(),
                end = s.range.endIso(),
                allDay = s.range.allDay,
                location = s.location.trim().ifBlank { null },
                note = s.note.trim().ifBlank { null },
            )
            when (val result = events.create(route.groupId, request)) {
                is ApiResult.Success -> _state.update { it.copy(saving = false, saved = true) }
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

    fun onMessageShown(message: UserMessage) = _state.update { if (it.message?.id == message.id) it.copy(message = null) else it }
}
