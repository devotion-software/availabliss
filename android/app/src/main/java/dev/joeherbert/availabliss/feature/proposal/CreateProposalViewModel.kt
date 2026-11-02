package dev.joeherbert.availabliss.feature.proposal

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.joeherbert.availabliss.core.common.UserMessage
import dev.joeherbert.availabliss.core.data.GroupsRepository
import dev.joeherbert.availabliss.core.data.ProposalsRepository
import dev.joeherbert.availabliss.core.data.UserRepository
import dev.joeherbert.availabliss.core.network.ApiResult
import dev.joeherbert.availabliss.core.network.model.CreateProposalRequest
import dev.joeherbert.availabliss.feature.events.TimeRangeDraft
import dev.joeherbert.availabliss.navigation.CreateProposalRoute
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CreateProposalUiState(
    val groupName: String = "",
    val range: TimeRangeDraft = TimeRangeDraft.default(),
    val title: String = "",
    val location: String = "",
    val note: String = "",
    val saving: Boolean = false,
    val createdId: String? = null,
    val message: UserMessage? = null,
    /** "Priya is busy then" when the chosen time clashes with someone's busy time. */
    val clash: String? = null,
) {
    val canSubmit: Boolean get() = range.isValid && !saving
}

@HiltViewModel
class CreateProposalViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val proposals: ProposalsRepository,
    private val groups: GroupsRepository,
    private val users: UserRepository,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<CreateProposalRoute>()
    private val _state = MutableStateFlow(
        CreateProposalUiState(range = TimeRangeDraft.fromIso(route.start, route.end, route.allDay) ?: TimeRangeDraft.default()),
    )
    val state: StateFlow<CreateProposalUiState> = _state.asStateFlow()

    private var members: Map<String, String> = emptyMap()

    init {
        viewModelScope.launch {
            val group = groups.cachedGroup(route.groupId) ?: (groups.refreshGroup(route.groupId) as? ApiResult.Success)?.value
            members = group?.members.orEmpty().associate { it.userId to it.name }
            _state.update { it.copy(groupName = group?.name.orEmpty()) }
        }
        watchConflicts()
    }

    /** Asks who's busy whenever the times change (debounced; a newer change cancels an older check). */
    @OptIn(FlowPreview::class)
    private fun watchConflicts() {
        viewModelScope.launch {
            _state.map { it.range }.distinctUntilChanged().debounce(CONFLICT_DEBOUNCE_MS).collectLatest { range ->
                val busy = if (!range.isValid) {
                    emptyList()
                } else {
                    (proposals.conflicts(route.groupId, range.startIso(), range.endIso()) as? ApiResult.Success)?.value.orEmpty()
                }
                _state.update { it.copy(clash = Clash.text(busy, members::get, users.user.value?.id)) }
            }
        }
    }

    fun onRangeChange(range: TimeRangeDraft) = _state.update { it.copy(range = range) }
    fun onTitleChange(value: String) = _state.update { it.copy(title = value.take(120)) }
    fun onLocationChange(value: String) = _state.update { it.copy(location = value.take(160)) }
    fun onNoteChange(value: String) = _state.update { it.copy(note = value.take(1000)) }

    fun submit() {
        val s = _state.value
        if (!s.canSubmit) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            val request = CreateProposalRequest(
                start = s.range.startIso(),
                end = s.range.endIso(),
                allDay = s.range.allDay,
                title = s.title.trim().ifBlank { null },
                location = s.location.trim().ifBlank { null },
                note = s.note.trim().ifBlank { null },
            )
            when (val result = proposals.create(route.groupId, request)) {
                is ApiResult.Success -> _state.update { it.copy(saving = false, createdId = result.value.id) }
                is ApiResult.Failure -> _state.update { it.copy(saving = false, message = UserMessage(result.error.message)) }
            }
        }
    }

    private companion object {
        const val CONFLICT_DEBOUNCE_MS = 250L
    }

    fun onMessageShown(message: UserMessage) = _state.update { if (it.message?.id == message.id) it.copy(message = null) else it }
}
