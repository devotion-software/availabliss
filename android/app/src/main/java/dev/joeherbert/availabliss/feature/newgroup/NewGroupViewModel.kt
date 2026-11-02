package dev.joeherbert.availabliss.feature.newgroup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import dev.joeherbert.availabliss.core.network.model.RequirementTemplate
import dev.joeherbert.availabliss.core.network.model.TileColor
import dev.joeherbert.availabliss.feature.timing.RequirementPresets
import dev.joeherbert.availabliss.feature.timing.TemplateSaveState
import dev.joeherbert.availabliss.feature.timing.TemplateSaver
import dev.joeherbert.availabliss.feature.timing.Templates
import dev.joeherbert.availabliss.feature.timing.TimingEditor
import dev.joeherbert.availabliss.feature.timing.TimingEditorUiState
import dev.joeherbert.availabliss.feature.timing.TimingLabels
import dev.joeherbert.availabliss.feature.timing.TimingPreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class NewGroupStep { Details, Timing }

data class NewGroupUiState(
    val step: NewGroupStep = NewGroupStep.Details,
    val name: String = "",
    val color: TileColor = TileColor.T1,
    /** The timing being built (step 2 edits it in full). */
    val editor: TimingEditorUiState? = null,
    /** Your saved timings, offered above the presets. */
    val templates: List<RequirementTemplate> = emptyList(),
    /** The template the timing came from; null when it came from a preset. */
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
) {
    val canContinue: Boolean get() = name.isNotBlank()
    val canCreate: Boolean get() = name.isNotBlank() && editor != null && !creating

    /** "A weekend · Fri from 18:00 → Sun · Nov – Feb" */
    val summary: String get() = editor?.draft?.let(TimingLabels::summary).orEmpty()

    /** The highlighted "What are you planning?" chip (none for a template or a customised timing). */
    val preset: TimingPreset? get() = if (templateId != null) null else editor?.draft?.preset

    /** Preview monogram, e.g. "Lake District crew" → "LD" (the server assigns the real one). */
    val monogramPreview: String
        get() = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2)
            .joinToString("") { it.first().uppercase() }.ifEmpty { "?" }

    companion object {
        /** The "What are you planning?" chips. */
        val QUICK_PRESETS = listOf(TimingPreset.TwoHours, TimingPreset.Evening, TimingPreset.OneDay, TimingPreset.Weekend, TimingPreset.Week)
    }
}

/** New group in two steps: name, colour and a rough preset; then the full timing editor and Create. */
@HiltViewModel
class NewGroupViewModel @Inject constructor(
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

    init {
        viewModelScope.launch { editor.state.collect { e -> _uiState.update { it.copy(editor = e) } } }
        viewModelScope.launch { templateSaver.state.collect { t -> _uiState.update { it.copy(templateSave = t) } } }
        selectPreset(TimingPreset.Weekend)
        viewModelScope.launch {
            templates.list().getOrNull()?.let { list -> _uiState.update { it.copy(templates = list) } }
        }
        viewModelScope.launch {
            plans.refresh().getOrNull()?.let { plan ->
                _uiState.update { it.copy(atLimit = !plan.canCreateGroup, groupLimit = plan.groupLimit ?: it.groupLimit) }
            }
        }
    }

    /** After the upgrade sheet switched Pro on: back to the form. */
    fun upgraded(message: String) = _uiState.update { it.copy(atLimit = false, message = UserMessage(message)) }

    private val timezone: String get() = users.user.value?.timezone ?: DateTimes.zone.id

    fun setName(name: String) = _uiState.update { it.copy(name = name.take(MAX_NAME), error = null) }

    fun setColor(color: TileColor) = _uiState.update { it.copy(color = color) }

    /** A fresh timing per preset: "A week" needs every day, "A weekend" Fri–Sun, and so on. */
    fun selectPreset(preset: TimingPreset) {
        _uiState.update { it.copy(templateId = null) }
        val base = RequirementPresets.defaultDraft(DateTimes.today(), timezone)
        editor.reset(RequirementPresets.applyPreset(base, preset))
    }

    /** Starts from a saved timing: same rules, but searching from today in your time zone. */
    fun selectTemplate(template: RequirementTemplate) {
        _uiState.update { it.copy(templateId = template.id) }
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
        _uiState.update { it.copy(step = NewGroupStep.Timing, error = null) }
    }

    fun back() = _uiState.update { it.copy(step = NewGroupStep.Details) }

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
                    groups.refreshGroups()
                    _uiState.update { it.copy(creating = false, createdGroupId = result.value.id) }
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

    fun messageShown(message: UserMessage) = _uiState.update { if (it.message?.id == message.id) it.copy(message = null) else it }

    private companion object {
        const val MAX_NAME = 80
    }
}
