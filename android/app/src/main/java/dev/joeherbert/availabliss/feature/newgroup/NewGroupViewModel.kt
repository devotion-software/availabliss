package dev.joeherbert.availabliss.feature.newgroup

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.joeherbert.availabliss.core.billing.PlanLabels
import dev.joeherbert.availabliss.core.billing.PlanRepository
import dev.joeherbert.availabliss.core.common.DateTimes
import dev.joeherbert.availabliss.core.common.UserMessage
import dev.joeherbert.availabliss.core.data.GroupsRepository
import dev.joeherbert.availabliss.core.data.RegionsRepository
import dev.joeherbert.availabliss.core.data.TemplatesRepository
import dev.joeherbert.availabliss.core.data.UserRepository
import dev.joeherbert.availabliss.core.network.ApiError
import dev.joeherbert.availabliss.core.network.ApiResult
import dev.joeherbert.availabliss.core.network.getOrNull
import dev.joeherbert.availabliss.core.network.model.Group
import dev.joeherbert.availabliss.core.network.model.Member
import dev.joeherbert.availabliss.core.network.model.RequirementTemplate
import dev.joeherbert.availabliss.core.network.model.TileColor
import dev.joeherbert.availabliss.feature.timing.RequirementPresets
import dev.joeherbert.availabliss.feature.timing.TemplateSaveState
import dev.joeherbert.availabliss.feature.timing.TemplateSaver
import dev.joeherbert.availabliss.feature.timing.Templates
import dev.joeherbert.availabliss.feature.timing.TimingEditor
import dev.joeherbert.availabliss.feature.timing.TimingEditorUiState
import dev.joeherbert.availabliss.feature.timing.TimingPreset
import dev.joeherbert.availabliss.navigation.NewGroupRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class NewGroupStep { Details, People, Timing }

data class NewGroupUiState(
    val step: NewGroupStep = NewGroupStep.Details,
    val name: String = "",
    val color: TileColor = TileColor.T1,
    /** The timing being built (step 2 edits it in full). */
    val editor: TimingEditorUiState? = null,
    /** Your saved timings, offered on step 1. */
    val templates: List<RequirementTemplate> = emptyList(),
    /** The template the timing came from; null for the default timing. */
    val templateId: String? = null,
    val templateSave: TemplateSaveState = TemplateSaveState(),
    val creating: Boolean = false,
    val error: String? = null,
    val message: UserMessage? = null,
    /** Free plan, already at its group limit: show the limit and a way to Pro instead of the form. */
    val atLimit: Boolean = false,
    val groupLimit: Int = PlanLabels.FREE_GROUP_LIMIT,
    /** Set once created; the screen navigates and calls [NewGroupViewModel.createdHandled]. */
    val createdGroupId: String? = null,
    /** "New group with these people": the group it starts from (its name), else null. */
    val basedOnName: String? = null,
    /** That group's other members, in its order, and which of them get an invite (all to start with). */
    val people: List<Member> = emptyList(),
    val invitees: Set<String> = emptySet(),
    /** The timing is the other group's (picked to start with; a template replaces it). */
    val copiesTiming: Boolean = false,
) {
    /** People is its own step only when starting from another group. */
    val steps: List<NewGroupStep>
        get() = if (people.isEmpty()) listOf(NewGroupStep.Details, NewGroupStep.Timing) else NewGroupStep.entries
    val stepNumber: Int get() = steps.indexOf(step) + 1
    val canContinue: Boolean get() = name.isNotBlank()
    /** Name of your template with the timing shown, if there is one (then it isn't offered again). */
    val savedAsTemplate: String? get() = editor?.let { Templates.matching(templates, it.draft.toRequirement()) }?.name
    val canCreate: Boolean get() = name.isNotBlank() && editor != null && !creating

    /** Preview monogram, e.g. "Lake District crew" → "LD" (the server assigns the real one). */
    val monogramPreview: String
        get() = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2)
            .joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
}

/**
 * New group in two steps: name, colour and optionally a saved template; then the full timing editor and Create.
 * From a group ("New group with these people", [NewGroupRoute.fromGroupId]) it starts from that group's
 * name (with a 2), colour and timing, with a step between listing its other members, all ticked; each one
 * still ticked gets an invite to accept once the group is made.
 */
@HiltViewModel
class NewGroupViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val groups: GroupsRepository,
    private val plans: PlanRepository,
    private val templates: TemplatesRepository,
    private val users: UserRepository,
    regions: RegionsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(NewGroupUiState(color = TileColor.entries.random()))
    val uiState: StateFlow<NewGroupUiState> = _uiState.asStateFlow()

    val editor = TimingEditor(viewModelScope, groups, users, regions)

    private val templateSaver = TemplateSaver(
        viewModelScope,
        templates,
        onMessage = { text -> _uiState.update { it.copy(message = UserMessage(text)) } },
        onSaved = { saved -> _uiState.update { it.copy(templates = it.templates + saved) } },
    )

    private val fromGroupId: String? = runCatching { savedStateHandle.toRoute<NewGroupRoute>().fromGroupId }.getOrNull()
    private var basedOn: Group? = null

    init {
        viewModelScope.launch { editor.state.collect { e -> _uiState.update { it.copy(editor = e) } } }
        viewModelScope.launch { templateSaver.state.collect { t -> _uiState.update { it.copy(templateSave = t) } } }
        resetTiming()
        fromGroupId?.let { id ->
            groups.cachedGroup(id)?.let(::startFrom) ?: viewModelScope.launch { groups.refreshGroup(id).getOrNull()?.let(::startFrom) }
        }
        viewModelScope.launch {
            templates.list().getOrNull()?.let { list -> _uiState.update { it.copy(templates = list) } }
        }
        viewModelScope.launch {
            plans.refresh().getOrNull()?.let { plan ->
                _uiState.update { it.copy(atLimit = !plan.canCreateGroup, groupLimit = plan.groupLimit ?: it.groupLimit) }
            }
        }
    }

    /** Prefills from the group this one starts from: its name with a 2, colour, timing and other members. */
    private fun startFrom(group: Group) {
        basedOn = group
        val me = users.user.value?.id
        val others = group.members.filter { it.userId != me }
        _uiState.update {
            it.copy(
                name = nextName(group.name),
                color = group.color,
                basedOnName = group.name,
                people = others,
                invitees = others.map { m -> m.userId }.toSet(),
            )
        }
        copyTiming(group)
    }

    private fun copyTiming(group: Group) {
        _uiState.update { it.copy(templateId = null, copiesTiming = true) }
        val requirement = Templates.requirementFromTemplate(group.requirement, DateTimes.today(), timezone)
        editor.reset(RequirementPresets.fromRequirement(requirement))
    }

    /** The other group's timing, or (tapped again) the default. */
    fun toggleCopiedTiming() {
        val group = basedOn ?: return
        if (_uiState.value.copiesTiming) resetTiming() else copyTiming(group)
    }

    fun toggleInvitee(userId: String) = _uiState.update {
        it.copy(invitees = if (userId in it.invitees) it.invitees - userId else it.invitees + userId)
    }

    /** After the upgrade sheet switched Pro on: back to the form. */
    fun upgraded(message: String) = _uiState.update { it.copy(atLimit = false, message = UserMessage(message)) }

    private val timezone: String get() = users.user.value?.timezone ?: DateTimes.zone.id

    fun setName(name: String) = _uiState.update { it.copy(name = name.take(MAX_NAME), error = null) }

    fun setColor(color: TileColor) = _uiState.update { it.copy(color = color) }

    /** The default timing (a weekend); step 2 fine-tunes it. */
    private fun resetTiming() {
        _uiState.update { it.copy(templateId = null, copiesTiming = false) }
        val base = RequirementPresets.defaultDraft(DateTimes.today(), timezone)
        editor.reset(RequirementPresets.applyPreset(base, TimingPreset.Weekend))
    }

    /**
     * Starts from a saved timing: same rules, but searching from today in your time zone.
     * Tapping the selected template again goes back to the default timing.
     */
    fun selectTemplate(template: RequirementTemplate) {
        if (_uiState.value.templateId == template.id) return resetTiming()
        _uiState.update { it.copy(templateId = template.id, copiesTiming = false) }
        val requirement = Templates.requirementFromTemplate(template.requirement, DateTimes.today(), timezone)
        editor.reset(RequirementPresets.fromRequirement(requirement))
    }

    fun saveTemplate(name: String) {
        val draft = editor.draft ?: return
        templateSaver.save(name, draft.toRequirement())
    }

    fun next() {
        if (!_uiState.value.canContinue) {
            _uiState.update { it.copy(error = "Give your group a name") }
            return
        }
        _uiState.update { it.copy(step = it.steps[minOf(it.stepNumber, it.steps.size - 1)], error = null) }
    }

    fun back() = _uiState.update { it.copy(step = it.steps[maxOf(it.stepNumber - 2, 0)]) }

    fun create() {
        val state = _uiState.value
        val draft = editor.draft ?: return
        if (!state.canCreate) return
        TimingEditor.validate(draft)?.let { problem ->
            _uiState.update { it.copy(message = UserMessage(problem)) }
            return
        }
        _uiState.update { it.copy(creating = true) }
        viewModelScope.launch {
            when (val result = groups.create(state.name, state.color, draft.toRequirement())) {
                is ApiResult.Success -> {
                    val group = result.value
                    // Keep the people's order from the other group.
                    val userIds = state.people.map { it.userId }.filter { it in state.invitees }
                    val invited = if (userIds.isEmpty()) null else groups.createInvite(group.id, userIds = userIds)
                    groups.refreshGroups()
                    if (invited is ApiResult.Failure) {
                        // The group was made but its invites weren't sent: say so, then open it.
                        pendingGroupId = group.id
                        _uiState.update {
                            it.copy(
                                creating = false,
                                message = UserMessage("${group.name} was made, but the invites didn't send (${invited.error.message}). You can invite people from Members."),
                            )
                        }
                    } else {
                        _uiState.update { it.copy(creating = false, createdGroupId = group.id) }
                    }
                }
                is ApiResult.Failure if result.error is ApiError.UpgradeRequired -> {
                    _uiState.update { it.copy(creating = false, atLimit = true) }
                }
                is ApiResult.Failure -> {
                    val nameIssue = (result.error as? ApiError.Validation)?.issueFor("name")
                    _uiState.update {
                        if (nameIssue != null) {
                            it.copy(creating = false, step = NewGroupStep.Details, error = nameIssue)
                        } else {
                            val text = (result.error as? ApiError.Validation)?.issues?.firstOrNull()?.message ?: result.error.message
                            it.copy(creating = false, message = UserMessage(text))
                        }
                    }
                }
            }
        }
    }

    fun createdHandled() = _uiState.update { it.copy(createdGroupId = null) }

    /** Made, but inviting its people failed: opened once that message has shown. */
    private var pendingGroupId: String? = null

    fun messageShown(message: UserMessage) = _uiState.update {
        val opens = pendingGroupId?.also { pendingGroupId = null }
        if (it.message?.id == message.id) it.copy(message = null, createdGroupId = opens ?: it.createdGroupId) else it
    }

    companion object {
        private const val MAX_NAME = 80

        /** "Book club" → "Book club 2", "Book club 2" → "Book club 3". */
        fun nextName(name: String): String {
            val trimmed = name.trim()
            val space = trimmed.lastIndexOf(' ')
            val n = if (space > 0) trimmed.substring(space + 1).toIntOrNull() else null
            if (n != null && n in 0..999) return "${trimmed.substring(0, space)} ${n + 1}"
            return "$trimmed 2".take(MAX_NAME)
        }
    }
}
