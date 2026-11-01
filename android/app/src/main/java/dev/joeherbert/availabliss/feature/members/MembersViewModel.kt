package dev.joeherbert.availabliss.feature.members

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.joeherbert.availabliss.core.common.UserMessage
import dev.joeherbert.availabliss.core.data.GroupsRepository
import dev.joeherbert.availabliss.core.data.UserRepository
import dev.joeherbert.availabliss.core.live.LiveEvents
import dev.joeherbert.availabliss.core.live.LivePayload
import dev.joeherbert.availabliss.core.network.ApiResult
import dev.joeherbert.availabliss.core.network.model.Group
import dev.joeherbert.availabliss.core.network.model.Role
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MemberUi(
    val userId: String,
    val name: String,
    val image: String?,
    val role: Role,
    val synced: Boolean,
    val isMe: Boolean,
    /** Actions the current user may take on this member. */
    val canPromote: Boolean,
    val canDemote: Boolean,
    val canRemove: Boolean,
) {
    val hasActions: Boolean get() = canPromote || canDemote || canRemove
}

/** Destructive actions waiting for confirmation. */
sealed interface ConfirmAction {
    data class Remove(val member: MemberUi) : ConfirmAction
    data object Leave : ConfirmAction
    data object DeleteGroup : ConfirmAction
}

data class MembersUiState(
    val groupName: String = "",
    val members: List<MemberUi> = emptyList(),
    val myRole: Role? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val inviteEmail: String = "",
    val creatingLink: Boolean = false,
    val sendingEmail: Boolean = false,
    /** Invite URL to hand to the share sheet; cleared via [MembersViewModel.onShareHandled]. */
    val pendingShareUrl: String? = null,
    val confirm: ConfirmAction? = null,
    val working: Boolean = false,
    /** True once the user has left or deleted the group (navigate away). */
    val left: Boolean = false,
    val message: UserMessage? = null,
) {
    val isAdmin: Boolean get() = myRole?.isAdmin == true
    val isOwner: Boolean get() = myRole == Role.Owner
}

@HiltViewModel
class MembersViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val groups: GroupsRepository,
    private val users: UserRepository,
    liveEvents: LiveEvents,
) : ViewModel() {

    private val groupId: String = savedStateHandle.get<String>("groupId").orEmpty()

    private val _state = MutableStateFlow(MembersUiState())
    val state: StateFlow<MembersUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(groups.group(groupId), users.user) { group, user -> group to user?.id }.collect { (group, meId) ->
                if (group != null) _state.update { it.withGroup(group, meId) }
            }
        }
        refresh()
        viewModelScope.launch {
            liveEvents.payloadsFor(groupId).collect { payload ->
                when (payload) {
                    is LivePayload.MemberRemoved ->
                        if (payload.userId == users.user.value?.id) _state.update { it.copy(left = true) } else refresh()
                    is LivePayload.MemberChanged, is LivePayload.GroupUpdated -> refresh()
                    else -> Unit
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            when (val result = groups.refreshGroup(groupId)) {
                is ApiResult.Success -> _state.update { it.copy(loading = false, error = null) }
                is ApiResult.Failure -> _state.update { it.copy(loading = false, error = result.error.message) }
            }
        }
    }

    // region Invites
    fun shareInviteLink() {
        if (_state.value.creatingLink) return
        _state.update { it.copy(creatingLink = true) }
        viewModelScope.launch {
            when (val result = groups.createInvite(groupId)) {
                is ApiResult.Success -> _state.update { it.copy(creatingLink = false, pendingShareUrl = result.value.url) }
                is ApiResult.Failure -> _state.update { it.copy(creatingLink = false, message = UserMessage(result.error.message)) }
            }
        }
    }

    fun onShareHandled() = _state.update { it.copy(pendingShareUrl = null) }

    fun onInviteEmailChange(email: String) = _state.update { it.copy(inviteEmail = email.trim()) }

    fun sendEmailInvite() {
        val email = _state.value.inviteEmail
        if (!email.contains('@') || _state.value.sendingEmail) return
        _state.update { it.copy(sendingEmail = true) }
        viewModelScope.launch {
            when (val result = groups.createInvite(groupId, email)) {
                is ApiResult.Success -> _state.update { it.copy(sendingEmail = false, inviteEmail = "", message = UserMessage("Invite sent to $email")) }
                is ApiResult.Failure -> _state.update { it.copy(sendingEmail = false, message = UserMessage(result.error.message)) }
            }
        }
    }
    // endregion

    // region Roles & membership
    fun setRole(member: MemberUi, role: Role) {
        viewModelScope.launch {
            val result = groups.setRole(groupId, member.userId, role)
            if (result is ApiResult.Failure) showMessage(result.error.message)
            else showMessage(if (role == Role.Admin) "${member.name} is now an admin" else "${member.name} is now a member")
        }
    }

    fun requestRemove(member: MemberUi) = _state.update { it.copy(confirm = ConfirmAction.Remove(member)) }

    fun requestLeave() = _state.update { it.copy(confirm = ConfirmAction.Leave) }

    fun requestDelete() = _state.update { it.copy(confirm = ConfirmAction.DeleteGroup) }

    fun dismissConfirm() = _state.update { it.copy(confirm = null) }

    fun confirm() {
        val action = _state.value.confirm ?: return
        val meId = users.user.value?.id
        _state.update { it.copy(working = true) }
        viewModelScope.launch {
            val result: ApiResult<Unit> = when (action) {
                is ConfirmAction.Remove -> groups.removeMember(groupId, action.member.userId, isSelf = false)
                ConfirmAction.Leave -> meId?.let { groups.removeMember(groupId, it, isSelf = true) }
                    ?: ApiResult.Success(Unit)
                ConfirmAction.DeleteGroup -> groups.delete(groupId)
            }
            when (result) {
                is ApiResult.Success -> _state.update {
                    it.copy(working = false, confirm = null, left = action !is ConfirmAction.Remove)
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(working = false, confirm = null, message = UserMessage(result.error.message))
                }
            }
            groups.refreshGroups()
        }
    }
    // endregion

    fun messageShown(message: UserMessage) = _state.update { if (it.message?.id == message.id) it.copy(message = null) else it }

    private fun showMessage(text: String) = _state.update { it.copy(message = UserMessage(text)) }

    private fun MembersUiState.withGroup(group: Group, meId: String?): MembersUiState {
        val myRole = group.members.firstOrNull { it.userId == meId }?.role ?: group.role
        val members = group.members
            .sortedWith(compareBy({ it.userId != meId }, { it.role.ordinal }, { it.name.lowercase() }))
            .map { m ->
                val isMe = m.userId == meId
                val manageable = !isMe && m.role != Role.Owner && myRole.isAdmin
                MemberUi(
                    userId = m.userId,
                    name = m.name,
                    image = m.image,
                    role = m.role,
                    synced = m.synced,
                    isMe = isMe,
                    canPromote = manageable && m.role == Role.Member,
                    canDemote = manageable && m.role == Role.Admin,
                    canRemove = manageable,
                )
            }
        return copy(groupName = group.name, members = members, myRole = myRole, loading = false)
    }
}
