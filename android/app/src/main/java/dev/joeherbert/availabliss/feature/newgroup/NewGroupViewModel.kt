package dev.joeherbert.availabliss.feature.newgroup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.joeherbert.availabliss.core.data.GroupsRepository
import dev.joeherbert.availabliss.core.network.ApiError
import dev.joeherbert.availabliss.core.network.ApiResult
import dev.joeherbert.availabliss.core.network.model.TileColor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NewGroupUiState(
    val name: String = "",
    val color: TileColor = TileColor.T1,
    val creating: Boolean = false,
    val error: String? = null,
    /** Set once created; the screen navigates and calls [NewGroupViewModel.createdHandled]. */
    val createdGroupId: String? = null,
) {
    val canCreate: Boolean get() = name.isNotBlank() && !creating

    /** Preview monogram, e.g. "Lake District crew" → "LD" (the server assigns the real one). */
    val monogramPreview: String
        get() = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2)
            .joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
}

@HiltViewModel
class NewGroupViewModel @Inject constructor(
    private val groups: GroupsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(NewGroupUiState())
    val uiState: StateFlow<NewGroupUiState> = _uiState.asStateFlow()

    fun setName(name: String) = _uiState.update { it.copy(name = name.take(MAX_NAME), error = null) }

    fun setColor(color: TileColor) = _uiState.update { it.copy(color = color) }

    fun create() {
        val state = _uiState.value
        if (!state.canCreate) return
        _uiState.update { it.copy(creating = true, error = null) }
        viewModelScope.launch {
            // No requirement: the server applies its default and the admin lands on Timing next.
            when (val result = groups.create(state.name, state.color, requirement = null)) {
                is ApiResult.Success -> {
                    groups.refreshGroups()
                    _uiState.update { it.copy(creating = false, createdGroupId = result.value.id) }
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(creating = false, error = (result.error as? ApiError.Validation)?.issueFor("name") ?: result.error.message)
                }
            }
        }
    }

    fun createdHandled() = _uiState.update { it.copy(createdGroupId = null) }

    private companion object {
        const val MAX_NAME = 80
    }
}
