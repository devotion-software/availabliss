package dev.joeherbert.availabliss.feature.timing

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.joeherbert.availabliss.core.common.UserMessage
import dev.joeherbert.availabliss.core.data.GroupsRepository
import dev.joeherbert.availabliss.core.data.RegionsRepository
import dev.joeherbert.availabliss.core.data.TemplatesRepository
import dev.joeherbert.availabliss.core.data.UserRepository
import dev.joeherbert.availabliss.core.network.ApiError
import dev.joeherbert.availabliss.core.network.ApiResult
import dev.joeherbert.availabliss.navigation.GroupRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GroupTimingUiState(
    val loading: Boolean = true,
    val groupName: String = "",
    val canEdit: Boolean = false,
    /** The editor sections; null until the group loads. */
    val editor: TimingEditorUiState? = null,
    val dirty: Boolean = false,
    /** "Save this timing as a template" (anyone can: it's a personal copy). */
    val templateSave: TemplateSaveState = TemplateSaveState(),
    val saving: Boolean = false,
    /** Set after a successful save; the screen navigates and calls [GroupTimingViewModel.savedHandled]. */
    val saved: Boolean = false,
    val error: String? = null,
    val message: UserMessage? = null,
)

/** Admin editor for the group's [dev.joeherbert.availabliss.core.network.model.Requirement]. */
@HiltViewModel
class GroupTimingViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val groups: GroupsRepository,
    templates: TemplatesRepository,
    users: UserRepository,
    regions: RegionsRepository,
) : ViewModel() {

    private val groupId = savedStateHandle.toRoute<GroupRoute>().groupId
    private val _uiState = MutableStateFlow(GroupTimingUiState())
    val uiState: StateFlow<GroupTimingUiState> = _uiState.asStateFlow()

    val editor = TimingEditor(viewModelScope, groups, users, regions).apply { canEdit = false }
    private var original: TimingDraft? = null

    private val templateSaver = TemplateSaver(viewModelScope, templates, onMessage = { text -> _uiState.update { it.copy(message = UserMessage(text)) } })

    init {
        viewModelScope.launch {
            editor.state.collect { e -> _uiState.update { it.copy(editor = e, dirty = e != null && e.draft != original) } }
        }
        viewModelScope.launch { templateSaver.state.collect { t -> _uiState.update { it.copy(templateSave = t) } } }
        load()
    }

    fun load() {
        viewModelScope.launch {
            val group = groups.cachedGroup(groupId) ?: when (val result = groups.refreshGroup(groupId)) {
                is ApiResult.Success -> result.value
                is ApiResult.Failure -> {
                    _uiState.update { it.copy(loading = false, error = result.error.message) }
                    return@launch
                }
            }
            val draft = RequirementPresets.fromRequirement(group.requirement)
            original = draft
            editor.canEdit = group.role.isAdmin
            _uiState.update { it.copy(loading = false, groupName = group.name, canEdit = group.role.isAdmin, error = null) }
            editor.reset(draft, group.requirement.schoolAuthority)
        }
    }

    fun save() {
        val state = _uiState.value
        val draft = editor.draft ?: return
        if (!state.canEdit || state.saving) return
        TimingEditor.validate(draft)?.let { problem ->
            _uiState.update { it.copy(message = UserMessage(problem)) }
            return
        }
        _uiState.update { it.copy(saving = true) }
        viewModelScope.launch {
            when (val result = groups.update(groupId, requirement = draft.toRequirement())) {
                is ApiResult.Success -> {
                    original = RequirementPresets.fromRequirement(result.value.requirement)
                    _uiState.update { it.copy(saving = false, dirty = false, saved = true) }
                }
                is ApiResult.Failure -> {
                    val text = (result.error as? ApiError.Validation)?.issues?.firstOrNull()?.message ?: result.error.message
                    _uiState.update { it.copy(saving = false, message = UserMessage(text)) }
                }
            }
        }
    }

    /** Saves the timing as shown (including unsaved edits) as a template for new groups. */
    fun saveTemplate(name: String) {
        val draft = editor.draft ?: return
        templateSaver.save(name, draft.toRequirement())
    }

    fun savedHandled() = _uiState.update { it.copy(saved = false) }

    fun messageShown(message: UserMessage) = _uiState.update { if (it.message?.id == message.id) it.copy(message = null) else it }
}
