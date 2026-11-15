package dev.joeherbert.availabliss.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.joeherbert.availabliss.core.common.DateTimes
import dev.joeherbert.availabliss.core.common.UserMessage
import dev.joeherbert.availabliss.core.data.GroupsRepository
import dev.joeherbert.availabliss.core.data.UserRepository
import dev.joeherbert.availabliss.core.deeplink.DeepLink
import dev.joeherbert.availabliss.core.deeplink.DeepLinkBus
import dev.joeherbert.availabliss.core.network.ApiResult
import dev.joeherbert.availabliss.core.network.model.TileColor
import dev.joeherbert.availabliss.feature.timing.RequirementPresets
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
    val preset: TimingPreset = TimingPreset.Weekend,
    val color: TileColor = TileColor.entries.random(),
    val creating: Boolean = false,
    val createdGroupId: String? = null,
    // Invite people
    val inviteLink: String? = null,
    val creatingLink: Boolean = false,
    val inviteEmails: String = "",
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
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState(steps = if (deepLinks.pending.value is DeepLink.Invite) 2 else 4))
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    private var markBusyByHand = false

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

    fun onPresetChange(preset: TimingPreset) = _state.update { it.copy(preset = preset) }

    fun createGroup() {
        val s = _state.value
        if (s.groupName.isBlank() || s.creating) return
        _state.update { it.copy(creating = true) }
        viewModelScope.launch {
            val timezone = users.user.value?.timezone ?: ZoneId.systemDefault().id
            val draft = RequirementPresets.applyPreset(RequirementPresets.defaultDraft(DateTimes.today(), timezone), s.preset)
            when (val result = groups.create(s.groupName.trim(), s.color, draft.toRequirement())) {
                is ApiResult.Success -> _state.update { it.copy(creating = false, createdGroupId = result.value.id, step = OnboardingStep.Invite) }
                is ApiResult.Failure -> _state.update { it.copy(creating = false, message = UserMessage(result.error.message)) }
            }
        }
    }
    // endregion

    // region Invite people
    fun shareLink() {
        val id = _state.value.createdGroupId ?: return
        _state.value.inviteLink?.let { link -> return _state.update { it.copy(pendingShareUrl = link) } }
        if (_state.value.creatingLink) return
        _state.update { it.copy(creatingLink = true) }
        viewModelScope.launch {
            when (val result = groups.createInvite(id)) {
                is ApiResult.Success -> _state.update { it.copy(creatingLink = false, inviteLink = result.value.url, pendingShareUrl = result.value.url) }
                is ApiResult.Failure -> _state.update { it.copy(creatingLink = false, message = UserMessage(result.error.message)) }
            }
        }
    }

    fun onShareHandled() = _state.update { it.copy(pendingShareUrl = null) }

    fun onInviteEmailsChange(value: String) = _state.update { it.copy(inviteEmails = value) }

    fun sendInvites() {
        val id = _state.value.createdGroupId ?: return
        if (_state.value.sendingInvites) return
        val emails = _state.value.inviteEmails.split(',', ';', ' ', '\n').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct()
        emails.firstOrNull { !EMAIL.matches(it) }?.let { bad -> return _state.update { it.copy(message = UserMessage("“$bad” isn’t a valid email address")) } }
        if (emails.isEmpty()) return
        _state.update { it.copy(sendingInvites = true) }
        viewModelScope.launch {
            when (val result = groups.createInvite(id, emails)) {
                is ApiResult.Success -> _state.update {
                    it.copy(sendingInvites = false, inviteEmails = "", invited = (it.invited + emails).distinct(), message = UserMessage(if (emails.size == 1) "Invite sent to ${emails.first()}" else "Invites sent to ${emails.size} people"))
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

    private companion object {
        val EMAIL = Regex("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")
    }
}
