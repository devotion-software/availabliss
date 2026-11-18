package dev.joeherbert.availabliss.feature.members

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.joeherbert.availabliss.core.common.DateTimes
import dev.joeherbert.availabliss.core.data.GroupsRepository
import dev.joeherbert.availabliss.core.deeplink.DeepLinkBus
import dev.joeherbert.availabliss.core.deeplink.DeepLink
import dev.joeherbert.availabliss.core.data.UserRepository
import dev.joeherbert.availabliss.core.network.ApiError
import dev.joeherbert.availabliss.core.network.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class InvitePreviewUi(
    val groupName: String,
    val inviterName: String,
    val memberCount: Int,
    /** "Expires 14 Nov" */
    val expiry: String,
)

data class InviteUiState(
    val loading: Boolean = true,
    val preview: InvitePreviewUi? = null,
    /** Invite can't be used (expired, already used, not found). */
    val invalid: Boolean = false,
    val error: String? = null,
    val joining: Boolean = false,
    /** Sent to you in Activity (by username, or as one of a group's people): you can decline it. */
    val personal: Boolean = false,
    val declining: Boolean = false,
    /** Set once declined; the screen goes back. */
    val declined: Boolean = false,
    /** Set once joined (and the name sheet is done); the screen navigates to the group. */
    val joinedGroupId: String? = null,
    /** Just joined: the sheet asking what name to go by in the group is open for it. */
    val askingName: Boolean = false,
    val accountName: String = "",
    /** The name to go by in the group, as typed (starts as the account name). */
    val nameDraft: String = "",
    val savingName: Boolean = false,
    /** The id of the group just joined, while the name sheet is open. */
    val newGroupId: String? = null,
    /** The invite was emailed to this address, and you're signed in as [signedInEmail]: you can't use it. */
    val invitedOtherEmail: String? = null,
    val signedInEmail: String = "",
    /** The invite was sent to this username (no "@"), and it isn't yours: you can't use it. */
    val invitedOtherUsername: String? = null,
)

@HiltViewModel
class InviteViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val groups: GroupsRepository,
    private val users: UserRepository,
    private val deepLinks: DeepLinkBus,
) : ViewModel() {

    private val token: String = savedStateHandle.get<String>("token").orEmpty()

    private val _state = MutableStateFlow(InviteUiState())
    val state: StateFlow<InviteUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val result = groups.invite(token)) {
                is ApiResult.Success -> {
                    val p = result.value
                    val expires = DateTimes.parseInstant(p.expiresAt)
                    val expired = expires != null && expires.isBefore(Instant.now())
                    val mine = users.user.value?.email.orEmpty()
                    val myUsername = users.user.value?.username
                    _state.update {
                        it.copy(
                            loading = false,
                            invalid = expired,
                            invitedOtherEmail = p.email?.takeIf { invited -> !invited.equals(mine, ignoreCase = true) },
                            signedInEmail = mine,
                            personal = p.personal,
                            invitedOtherUsername = p.username?.takeIf { invited -> !invited.equals(myUsername, ignoreCase = true) },
                            preview = InvitePreviewUi(
                                groupName = p.groupName,
                                inviterName = p.inviterName,
                                memberCount = p.memberCount,
                                expiry = expires?.let { e -> "Expires ${DateTimes.dayMonth(e.atZone(DateTimes.zone).toLocalDate())}" }.orEmpty(),
                            ),
                        )
                    }
                }
                is ApiResult.Failure -> _state.update {
                    when (result.error) {
                        is ApiError.NotFound, is ApiError.Forbidden, is ApiError.Conflict -> it.copy(loading = false, invalid = true)
                        else -> it.copy(loading = false, error = result.error.message)
                    }
                }
            }
        }
    }

    fun onNameChange(name: String) = _state.update { it.copy(nameDraft = name) }

    /** "Use this name": sets the name to go by in the group just joined (unless it's the account name), then opens it. */
    fun useName() {
        val s = _state.value
        val groupId = s.newGroupId ?: return
        val name = s.nameDraft.trim()
        if (s.savingName || name.isEmpty()) return
        if (name == s.accountName) return keepAccountName()
        _state.update { it.copy(savingName = true, error = null) }
        viewModelScope.launch {
            when (val result = groups.setMyName(groupId, name)) {
                is ApiResult.Success -> _state.update { it.copy(savingName = false, askingName = false, joinedGroupId = groupId) }
                is ApiResult.Failure -> _state.update { it.copy(savingName = false, error = result.error.message) }
            }
        }
    }

    /** "Keep <account name>", or the sheet dismissed: opens the group just joined. */
    fun keepAccountName() {
        if (_state.value.savingName) return
        _state.update { it.copy(askingName = false, joinedGroupId = it.newGroupId) }
    }

    /** Keeps the invite for after sign-in (where its email is prefilled); the caller then signs out. */
    fun rememberInviteForSignIn() = deepLinks.post(DeepLink.Invite(token))

    /** Turns the invite down: it goes, with its Activity item, and nobody is told. */
    fun decline() {
        if (_state.value.declining || _state.value.joining) return
        _state.update { it.copy(declining = true, error = null) }
        viewModelScope.launch {
            when (val result = groups.declineInvite(token)) {
                is ApiResult.Success -> _state.update { it.copy(declining = false, declined = true) }
                is ApiResult.Failure -> _state.update { it.copy(declining = false, error = result.error.message) }
            }
        }
    }

    fun join() {
        if (_state.value.joining) return
        _state.update { it.copy(joining = true, error = null) }
        viewModelScope.launch {
            when (val result = groups.acceptInvite(token)) {
                is ApiResult.Success -> {
                    groups.refreshGroups()
                    val group = result.value
                    _state.update {
                        if (group.joined == false) {
                            // Already in it: nothing to ask, just open it.
                            it.copy(joining = false, joinedGroupId = group.id)
                        } else {
                            val account = users.user.value?.accountName.orEmpty()
                            it.copy(joining = false, askingName = true, newGroupId = group.id, accountName = account, nameDraft = account)
                        }
                    }
                }
                is ApiResult.Failure -> _state.update {
                    when (result.error) {
                        is ApiError.NotFound -> it.copy(joining = false, invalid = true)
                        else -> it.copy(joining = false, error = result.error.message)
                    }
                }
            }
        }
    }
}
