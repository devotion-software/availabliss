package dev.joeherbert.availabliss.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.joeherbert.availabliss.core.common.DateTimes
import dev.joeherbert.availabliss.core.common.UserMessage
import dev.joeherbert.availabliss.core.data.GroupsRepository
import dev.joeherbert.availabliss.core.data.RegionsRepository
import dev.joeherbert.availabliss.core.data.UserRepository
import dev.joeherbert.availabliss.core.deeplink.DeepLink
import dev.joeherbert.availabliss.core.deeplink.DeepLinkBus
import dev.joeherbert.availabliss.core.network.ApiResult
import dev.joeherbert.availabliss.core.network.model.TileColor
import dev.joeherbert.availabliss.feature.common.InviteRecipient
import dev.joeherbert.availabliss.feature.common.InviteRecipients
import dev.joeherbert.availabliss.feature.common.InviteRecipientsState
import dev.joeherbert.availabliss.feature.timing.RequirementPresets
import dev.joeherbert.availabliss.feature.timing.TimingEditor
import dev.joeherbert.availabliss.feature.timing.TimingEditorUiState
import dev.joeherbert.availabliss.feature.timing.TimingPreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId
import javax.inject.Inject

enum class OnboardingStep { Region, Calendars, Group, Invite }

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.Region,
    /** 4 steps, or 2 for someone joining a group from an invite (no first group / invites). */
    val steps: Int = 4,
    val finishing: Boolean = false,
    val message: UserMessage? = null,
    // First group
    val groupName: String = "",
    /** The full timing sentence, as in New group, with its live fit count. */
    val editor: TimingEditorUiState? = null,
    val color: TileColor = TileColor.entries.random(),
    val creating: Boolean = false,
    val createdGroupId: String? = null,
    // Invite people
    val inviteLink: String? = null,
    /** "5 people can join with this link." */
    val inviteLinkSummary: String? = null,
    val creatingLink: Boolean = false,
    val sendingInvites: Boolean = false,
    val invited: List<String> = emptyList(),
    /** Invite link to hand to the share sheet; cleared via [OnboardingViewModel.onShareHandled]. */
    val pendingShareUrl: String? = null,
)

/**
 * The sign-up wizard: about you, calendars, your first group, invite people. Someone who
 * arrived from an invite stops after calendars. Finishing sets `onboarded` via `PATCH /me`,
 * which switches the app root to the main app.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val users: UserRepository,
    private val groups: GroupsRepository,
    private val deepLinks: DeepLinkBus,
    regions: RegionsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState(steps = if (deepLinks.pending.value is DeepLink.Invite) 2 else 4))
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    /** Who to invite, by email or username. */
    private val recipients = InviteRecipients(viewModelScope, users::checkUsername, selfId = { users.user.value?.id })
    val inviteRecipients: StateFlow<InviteRecipientsState> = recipients.state

    private var markBusyByHand = false

    /** The first group's timing, edited with the same sentence and sheets as New group. */
    val editor = TimingEditor(viewModelScope, groups, users, regions)

    init {
        viewModelScope.launch { editor.state.collect { e -> _state.update { it.copy(editor = e) } } }
        val timezone = users.user.value?.timezone ?: ZoneId.systemDefault().id
        editor.reset(RequirementPresets.applyPreset(RequirementPresets.defaultDraft(DateTimes.today(), timezone), TimingPreset.Weekend))
    }

    fun goToCalendars() = _state.update { it.copy(step = OnboardingStep.Calendars) }

    fun goToRegion() = _state.update { it.copy(step = OnboardingStep.Region) }

    fun backToCalendars() = _state.update { it.copy(step = OnboardingStep.Calendars) }

    /** [byHand] = "No calendar? Mark busy times yourself": land on My time at the end. */
    fun calendarsDone(byHand: Boolean) {
        markBusyByHand = byHand
        if (_state.value.steps > 2) _state.update { it.copy(step = OnboardingStep.Group) } else finish()
    }

    // region First group
    fun onGroupNameChange(value: String) = _state.update { it.copy(groupName = value.take(80)) }

    fun createGroup() {
        val s = _state.value
        val editing = editor.draft ?: return
        if (s.groupName.isBlank() || s.creating) return
        TimingEditor.validate(editing)?.let { problem -> return _state.update { it.copy(message = UserMessage(problem)) } }
        _state.update { it.copy(creating = true) }
        viewModelScope.launch {
            val timezone = users.user.value?.timezone ?: ZoneId.systemDefault().id
            val draft = editing.copy(timezone = timezone)
            when (val result = groups.create(s.groupName.trim(), s.color, draft.toRequirement())) {
                is ApiResult.Success -> _state.update { it.copy(creating = false, createdGroupId = result.value.id, step = OnboardingStep.Invite) }
                is ApiResult.Failure -> _state.update { it.copy(creating = false, message = UserMessage(result.error.message)) }
            }
        }
    }
    // endregion

    // region Invite people
    /** Shares the link made here, making one (for one person) first if there isn't one. */
    fun shareLink() {
        _state.value.inviteLink?.let { link -> return _state.update { it.copy(pendingShareUrl = link) } }
        createLink(1)
    }

    /** Makes a link [maxUses] people can join with (null: no limit), then shares it. */
    fun createLink(maxUses: Int?) {
        val id = _state.value.createdGroupId ?: return
        if (_state.value.creatingLink) return
        _state.update { it.copy(creatingLink = true) }
        viewModelScope.launch {
            when (val result = groups.createInvite(id, maxUses = maxUses)) {
                is ApiResult.Success -> _state.update {
                    it.copy(
                        creatingLink = false,
                        inviteLink = result.value.url,
                        inviteLinkSummary = result.value.invites.firstOrNull()?.linkSummary,
                        pendingShareUrl = result.value.url,
                    )
                }
                is ApiResult.Failure -> _state.update { it.copy(creatingLink = false, message = UserMessage(result.error.message)) }
            }
        }
    }

    fun onShareHandled() = _state.update { it.copy(pendingShareUrl = null) }

    fun onInviteDraftChange(value: String) = recipients.onDraftChange(value)

    fun addInviteRecipient() = recipients.add()

    fun removeInviteRecipient(recipient: InviteRecipient) = recipients.remove(recipient)

    /** Invites everyone added (and anything still typed): emails get an email, usernames an invite in Activity. */
    fun sendInvites() {
        val id = _state.value.createdGroupId ?: return
        if (_state.value.sendingInvites) return
        _state.update { it.copy(sendingInvites = true) }
        viewModelScope.launch {
            val r = if (recipients.addDraft()) recipients.state.value else null
            if (r == null || r.recipients.isEmpty()) return@launch _state.update { it.copy(sendingInvites = false) }
            val labels = r.labels
            when (val result = groups.createInvite(id, r.emails, r.usernames)) {
                is ApiResult.Success -> {
                    recipients.clear()
                    _state.update {
                        it.copy(sendingInvites = false, invited = (it.invited + labels).distinct(), message = UserMessage(if (labels.size == 1) "Invite sent to ${labels.first()}" else "Invites sent to ${labels.size} people"))
                    }
                }
                is ApiResult.Failure -> _state.update { it.copy(sendingInvites = false, message = UserMessage(result.error.message)) }
            }
        }
    }
    // endregion

    fun finish() {
        if (_state.value.finishing) return
        viewModelScope.launch {
            _state.update { it.copy(finishing = true) }
            // Post first so the main app opens there as soon as it appears.
            val landing = _state.value.createdGroupId?.let { DeepLink.GroupCalendar(it) } ?: DeepLink.MyTime.takeIf { markBusyByHand }
            landing?.let(deepLinks::post)
            when (val result = users.update { set("onboarded", true) }) {
                is ApiResult.Success -> _state.update { it.copy(finishing = false) }
                is ApiResult.Failure -> {
                    landing?.let(deepLinks::consume)
                    _state.update { it.copy(finishing = false, message = UserMessage(result.error.message)) }
                }
            }
        }
    }

    fun onMessageShown(message: UserMessage) =
        _state.update { if (it.message?.id == message.id) it.copy(message = null) else it }
}
