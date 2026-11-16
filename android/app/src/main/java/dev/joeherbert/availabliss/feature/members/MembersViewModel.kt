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
import dev.joeherbert.availabliss.core.network.model.TileColor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import dev.joeherbert.availabliss.core.network.model.GroupInvite
import dev.joeherbert.availabliss.core.common.DateTimes
import dev.joeherbert.availabliss.core.network.getOrNull
import dev.joeherbert.availabliss.feature.common.InviteRecipient
import dev.joeherbert.availabliss.feature.common.InviteRecipients
import dev.joeherbert.availabliss.feature.common.InviteRecipientsState
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

/** "sam@example.com" / "@sam" + "Invited · invite expires Saturday 28 November", or the link. */
data class InviteUi(
    val id: String,
    val title: String,
    val status: String,
    val isLink: Boolean,
    val url: String,
    /** Sent to someone's username (to their Activity), not emailed. */
    val byUsername: Boolean = false,
)

data class MembersUiState(
    val groupName: String = "",
    val monogram: String = "",
    val tileColor: TileColor = TileColor.T1,
    /** The group's photo, instead of the monogram. */
    val groupImage: String? = null,
    val uploadingPhoto: Boolean = false,
    val members: List<MemberUi> = emptyList(),
    val myRole: Role? = null,
    val loading: Boolean = true,
    val error: String? = null,
    /** Who to invite, by email or username. */
    val recipients: InviteRecipientsState = InviteRecipientsState(),
    /** Live invites, newest first (admins only). */
    val invites: List<InviteUi> = emptyList(),
    val creatingLink: Boolean = false,
    val sendingInvites: Boolean = false,
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

    private val recipients = InviteRecipients(viewModelScope, users::checkUsername, selfId = { users.user.value?.id })

    init {
        viewModelScope.launch { recipients.state.collect { r -> _state.update { it.copy(recipients = r) } } }
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
        viewModelScope.launch { liveEvents.reconnected.collect { refresh() } }
    }

    fun refresh() {
        viewModelScope.launch {
            when (val result = groups.refreshGroup(groupId)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(loading = false, error = null) }
                    if (_state.value.isAdmin) loadInvites()
                }
                is ApiResult.Failure -> _state.update { it.copy(loading = false, error = result.error.message) }
            }
        }
    }

    // region Invites
    private suspend fun loadInvites() {
        groups.invites(groupId).getOrNull()?.let { list -> _state.update { it.copy(invites = list.map(::inviteUi)) } }
    }

    /** Shares the newest link anyone can use, making one first if there isn't one. */
    fun shareInviteLink() {
        _state.value.invites.firstOrNull { it.isLink }?.let { link ->
            _state.update { it.copy(pendingShareUrl = link.url) }
            return
        }
        if (_state.value.creatingLink) return
        _state.update { it.copy(creatingLink = true) }
        viewModelScope.launch {
            when (val result = groups.createInvite(groupId)) {
                is ApiResult.Success -> _state.update {
                    it.copy(creatingLink = false, pendingShareUrl = result.value.url, invites = result.value.invites.map(::inviteUi) + it.invites)
                }
                is ApiResult.Failure -> _state.update { it.copy(creatingLink = false, message = UserMessage(result.error.message)) }
            }
        }
    }

    fun onShareHandled() = _state.update { it.copy(pendingShareUrl = null) }

    fun onInviteDraftChange(text: String) = recipients.onDraftChange(text)

    fun addInviteRecipient() = recipients.add()

    fun removeInviteRecipient(recipient: InviteRecipient) = recipients.remove(recipient)

    /**
     * Invites everyone added (and anything still typed): each email gets its own emailed
     * invite, each username an invite in their Activity. Only that person can join.
     */
    fun sendInvites() {
        if (_state.value.sendingInvites) return
        _state.update { it.copy(sendingInvites = true) }
        viewModelScope.launch {
            val r = if (recipients.addDraft()) recipients.state.value else null
            if (r == null || r.recipients.isEmpty()) return@launch _state.update { it.copy(sendingInvites = false) }
            val labels = r.labels
            when (val result = groups.createInvite(groupId, r.emails, r.usernames)) {
                is ApiResult.Success -> {
                    recipients.clear()
                    _state.update { s ->
                        s.copy(
                            sendingInvites = false,
                            invites = result.value.invites.map(::inviteUi) + s.invites.filterNot { it.title in labels },
                            message = UserMessage(if (labels.size == 1) "Invite sent to ${labels.first()}" else "Invites sent to ${labels.size} people"),
                        )
                    }
                }
                is ApiResult.Failure -> _state.update { it.copy(sendingInvites = false, message = UserMessage(result.error.message)) }
            }
        }
    }

    fun revokeInvite(invite: InviteUi) {
        viewModelScope.launch {
            when (val result = groups.revokeInvite(groupId, invite.id)) {
                is ApiResult.Success -> _state.update { s ->
                    s.copy(
                        invites = s.invites.filterNot { it.id == invite.id },
                        message = UserMessage(if (invite.isLink) "Link revoked: it no longer works" else "Invite for ${invite.title} revoked"),
                    )
                }
                is ApiResult.Failure -> showMessage(result.error.message)
            }
        }
    }

    private fun inviteUi(invite: GroupInvite): InviteUi {
        val expires = DateTimes.localDateTime(invite.expiresAt)?.let { DateTimes.longDay(it.toLocalDate()) }.orEmpty()
        return InviteUi(
            id = invite.id,
            title = invite.email ?: invite.username?.let { "@$it" } ?: invite.url,
            status = if (invite.isLink) "Anyone with the link · expires $expires" else "Invited · invite expires $expires",
            isLink = invite.isLink,
            url = invite.url,
            byUsername = invite.email == null && invite.username != null,
        )
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

    /** Uploads a picked group photo (already a JPEG), or removes it with null. Admins only. */
    fun setPhoto(jpeg: ByteArray?) {
        if (!_state.value.isAdmin || _state.value.uploadingPhoto) return
        viewModelScope.launch {
            _state.update { it.copy(uploadingPhoto = true) }
            // Success caches the group in GroupsRepository, which flows back here through groups.group().
            val result = groups.setImage(groupId, jpeg)
            _state.update {
                it.copy(
                    uploadingPhoto = false,
                    message = UserMessage(
                        when (result) {
                            is ApiResult.Success -> if (jpeg == null) "Group photo removed" else "Group photo updated"
                            is ApiResult.Failure -> result.error.message
                        },
                    ),
                )
            }
        }
    }

    fun photoUnreadable() = showMessage("Couldn't read that photo.")

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
        return copy(
            groupName = group.name,
            monogram = group.monogram,
            tileColor = group.color,
            groupImage = group.image,
            members = members,
            myRole = myRole,
            loading = false,
        )
    }
}
