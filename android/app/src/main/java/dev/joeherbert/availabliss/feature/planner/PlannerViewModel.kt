package dev.joeherbert.availabliss.feature.planner

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.joeherbert.availabliss.core.data.PlannerRepository
import dev.joeherbert.availabliss.core.network.ApiError
import dev.joeherbert.availabliss.core.network.ApiResult
import dev.joeherbert.availabliss.core.network.model.PlannerResult
import dev.joeherbert.availabliss.navigation.GroupRoute
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlannerUiState(
    val notes: String = "",
    /** Waiting for Claude (often 20-60 seconds). */
    val thinking: Boolean = false,
    val result: PlannerResult? = null,
    val error: String? = null,
    /** The server said this needs Pro (402): the host shows the upgrade sheet. */
    val needsPro: Boolean = false,
)

/** The "Plan with AI" sheet for the group on screen. */
@HiltViewModel
class PlannerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val planner: PlannerRepository,
) : ViewModel() {
    private val groupId = savedStateHandle.toRoute<GroupRoute>().groupId
    private val _state = MutableStateFlow(PlannerUiState())
    val state: StateFlow<PlannerUiState> = _state.asStateFlow()
    private var job: Job? = null

    fun onNotesChange(value: String) = _state.update { it.copy(notes = value.take(PlannerRepository.MAX_NOTES), error = null) }

    fun suggest() {
        if (_state.value.thinking) return
        _state.update { it.copy(thinking = true, error = null) }
        job = viewModelScope.launch {
            when (val result = planner.plan(groupId, _state.value.notes)) {
                is ApiResult.Success -> _state.update { it.copy(thinking = false, result = result.value) }
                is ApiResult.Failure -> _state.update {
                    if (result.error is ApiError.UpgradeRequired) it.copy(thinking = false, needsPro = true)
                    else it.copy(thinking = false, error = result.error.message)
                }
            }
        }
    }

    /** "Try again with different notes": back to the notes, keeping them. */
    fun startOver() = _state.update { it.copy(result = null, error = null) }

    fun needsProHandled() = _state.update { it.copy(needsPro = false) }

    /** Closing the sheet abandons a request still running and clears the last answer. */
    fun reset() {
        job?.cancel()
        _state.update { PlannerUiState(notes = it.notes) }
    }
}
