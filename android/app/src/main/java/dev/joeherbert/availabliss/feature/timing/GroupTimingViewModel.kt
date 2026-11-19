package dev.joeherbert.availabliss.feature.timing

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.joeherbert.availabliss.core.common.UserMessage
import dev.joeherbert.availabliss.core.data.GroupsRepository
import dev.joeherbert.availabliss.core.data.RegionsRepository
import dev.joeherbert.availabliss.core.data.TemplatesRepository
import dev.joeherbert.availabliss.core.data.UserRepository
import dev.joeherbert.availabliss.core.network.ApiError
import dev.joeherbert.availabliss.core.network.ApiResult
import dev.joeherbert.availabliss.core.network.getOrNull
import dev.joeherbert.availabliss.core.network.model.Requirement
import dev.joeherbert.availabliss.core.network.model.RequirementTemplate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
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
    /** Someone else saved a different timing while you had unsaved edits (saving replaces theirs). */
    val changedElsewhere: Boolean = false,
    /** "Save this timing as a template" (anyone can: it's a personal copy). */
    val templateSave: TemplateSaveState = TemplateSaveState(),
    /** Name of your template with this exact timing, if there is one (then it isn't offered again). */
    val savedAsTemplate: String? = null,
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
    private val templates: TemplatesRepository,
    users: UserRepository,
    regions: RegionsRepository,
) : ViewModel() {

    private val groupId = savedStateHandle.get<String>("groupId").orEmpty()
    private val _uiState = MutableStateFlow(GroupTimingUiState())
    val uiState: StateFlow<GroupTimingUiState> = _uiState.asStateFlow()

    val editor = TimingEditor(viewModelScope, groups, users, regions).apply { canEdit = false }
    private var original: TimingDraft? = null
    private var originalAuthority: String? = null

    /** Your templates, to tell whether the timing shown is already one. */
    private val savedTemplates = MutableStateFlow<List<RequirementTemplate>>(emptyList())

    private val templateSaver = TemplateSaver(
        viewModelScope,
        templates,
        onMessage = { text -> _uiState.update { it.copy(message = UserMessage(text)) } },
        onSaved = { saved -> savedTemplates.update { it + saved } },
    )

    init {
        viewModelScope.launch {
            combine(editor.state, savedTemplates) { e, list -> e to list }.collect { (e, list) ->
                val match = e?.let { Templates.matching(list, it.draft.toRequirement()) }
                _uiState.update { it.copy(editor = e, dirty = e != null && e.draft != original, savedAsTemplate = match?.name) }
            }
        }
        viewModelScope.launch { templates.list().getOrNull()?.let { savedTemplates.value = it } }
        // Someone else saving the timing reaches the cached group live.
        viewModelScope.launch {
            groups.group(groupId).filterNotNull().map { it.requirement }.distinctUntilChanged().collect(::savedTimingChanged)
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
            originalAuthority = group.requirement.schoolAuthority
            editor.canEdit = group.role.isAdmin
            editor.members = group.members
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
                    originalAuthority = result.value.requirement.schoolAuthority
                    _uiState.update { it.copy(saving = false, dirty = false, saved = true, changedElsewhere = false) }
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
        // Who needs to be free is about this group's members, so it isn't kept in the template.
        templateSaver.save(name, draft.toRequirement().copy(quorum = null, optionalMemberIds = emptyList()))
    }

    /**
     * The group's saved timing changed. With no unsaved edits it's taken as it is; over unsaved edits
     * it says so instead (as web does), and saving replaces theirs.
     */
    private fun savedTimingChanged(saved: Requirement) {
        val before = original ?: return
        if (_uiState.value.saving) return
        val savedDraft = RequirementPresets.fromRequirement(saved)
        if (savedDraft == before) return
        val current = editor.draft
        original = savedDraft
        originalAuthority = saved.schoolAuthority
        if (current == null || current == before || current == savedDraft) {
            editor.reset(savedDraft, saved.schoolAuthority)
            _uiState.update { it.copy(changedElsewhere = false, dirty = false) }
        } else {
            _uiState.update { it.copy(changedElsewhere = true, dirty = true) }
        }
    }

    /** "Load theirs": drops your edits for the timing someone else saved. */
    fun loadTheirs() {
        val saved = original ?: return
        editor.reset(saved, originalAuthority)
        _uiState.update { it.copy(changedElsewhere = false) }
    }

    fun savedHandled() = _uiState.update { it.copy(saved = false) }

    fun messageShown(message: UserMessage) = _uiState.update { if (it.message?.id == message.id) it.copy(message = null) else it }
}
