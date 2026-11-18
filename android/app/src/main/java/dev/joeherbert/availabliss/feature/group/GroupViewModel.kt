package dev.joeherbert.availabliss.feature.group

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.joeherbert.availabliss.core.common.UserMessage
import dev.joeherbert.availabliss.core.data.GroupsRepository
import dev.joeherbert.availabliss.core.data.MuteDuration
import dev.joeherbert.availabliss.core.data.NotificationSettingsRepository
import dev.joeherbert.availabliss.core.data.UserRepository
import dev.joeherbert.availabliss.core.live.LiveEvents
import dev.joeherbert.availabliss.core.live.LivePayload
import dev.joeherbert.availabliss.core.network.ApiResult
import dev.joeherbert.availabliss.core.network.model.GroupSummary
import dev.joeherbert.availabliss.core.network.model.ReceiptState
import dev.joeherbert.availabliss.navigation.GroupRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GroupShellUiState(
    val groupName: String = "",
    val unreadCount: Int = 0,
    val isAdmin: Boolean = false,
    /** "5 members · you're the owner" */
    val membersLine: String = "",
    /** Someone else is in it: offers "New group with these people". */
    val hasOthers: Boolean = false,
    /** You've muted its pushes: the More menu offers "Unmute" instead of "Mute notifications". */
    val muted: Boolean = false,
)

/**
 * A group's header: loads the [dev.joeherbert.availabliss.core.network.model.Group] (cached in
 * [GroupsRepository] for the sections pushed on top) and tracks the chat button's unread badge
 * while the chat isn't open, and mutes or unmutes its pushes from the More menu. Scoped to the [GroupRoute] entry; the chat screen reports its
 * visibility through [onChatVisible].
 */
@HiltViewModel
class GroupViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val groups: GroupsRepository,
    private val users: UserRepository,
    private val notificationSettings: NotificationSettingsRepository,
    liveEvents: LiveEvents,
) : ViewModel() {

    private val groupId = savedStateHandle.toRoute<GroupRoute>().groupId
    private val unread = MutableStateFlow(0)
    private var chatVisible = false
    private val _message = MutableStateFlow<UserMessage?>(null)

    /** Snackbars for muting and unmuting. */
    val message: StateFlow<UserMessage?> = _message.asStateFlow()

    val uiState: StateFlow<GroupShellUiState> = combine(groups.group(groupId), unread, notificationSettings.settings) { group, count, settings ->
        GroupShellUiState(
            groupName = group?.name.orEmpty(),
            unreadCount = count,
            isAdmin = group?.role?.isAdmin == true,
            hasOthers = (group?.members?.size ?: 0) > 1,
            muted = settings?.isMuted(groupId) == true,
            membersLine = group?.let {
                val count = "${it.memberCount} ${if (it.memberCount == 1) "member" else "members"}"
                val members = if (it.archived) "$count · archived" else count
                when (it.role) {
                    dev.joeherbert.availabliss.core.network.model.Role.Owner -> "$members · you're the owner"
                    dev.joeherbert.availabliss.core.network.model.Role.Admin -> "$members · you're an admin"
                    else -> members
                }
            }.orEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GroupShellUiState())

    init {
        viewModelScope.launch { groups.refreshGroup(groupId) }
        viewModelScope.launch { seedUnread() }
        // Only to know whether the menu says "Mute notifications" or "Unmute".
        viewModelScope.launch { notificationSettings.refresh() }
        viewModelScope.launch {
            liveEvents.payloadsFor(groupId).collect { payload ->
                when (payload) {
                    is LivePayload.MessageCreated -> {
                        val me = users.user.value?.id ?: users.user.filterNotNull().first().id
                        if (!chatVisible && payload.message.authorId != me) unread.update { it + 1 }
                    }
                    // Our own "seen" receipt (here or on another device) means the chat has been read.
                    is LivePayload.MessageReceipts -> {
                        val me = users.user.value?.id ?: users.user.filterNotNull().first().id
                        if (payload.payload.userId == me && payload.payload.state == ReceiptState.Seen) unread.value = 0
                    }
                    // The payload is the whole group: no need to refetch it.
                    is LivePayload.GroupUpdated -> groups.applyLiveUpdate(payload.group, users.user.value?.id)
                    else -> Unit
                }
            }
        }
        viewModelScope.launch {
            liveEvents.reconnected.collect {
                groups.refreshGroup(groupId)
                seedUnread()
            }
        }
    }

    /**
     * Seeds the badge with the server's count, which only the groups list carries. Shows the cached
     * list's count straight away, then refetches it: the cache may predate reading the chat.
     */
    private suspend fun seedUnread() {
        fun countIn(list: List<GroupSummary>?) = list?.firstOrNull { it.id == groupId }?.unreadCount
        countIn(groups.groups.value)?.let { if (!chatVisible) unread.value = it }
        if (groups.refreshGroups() is ApiResult.Success && !chatVisible) unread.value = countIn(groups.groups.value) ?: 0
    }

    fun mute(duration: MuteDuration) {
        viewModelScope.launch {
            _message.value = when (val result = notificationSettings.mute(groupId, duration)) {
                is ApiResult.Success -> UserMessage(duration.confirmation)
                is ApiResult.Failure -> UserMessage("Couldn't mute this group: ${result.error.message}")
            }
        }
    }

    fun unmute() {
        viewModelScope.launch {
            _message.value = when (val result = notificationSettings.unmute(groupId)) {
                is ApiResult.Success -> UserMessage("Notifications back on")
                is ApiResult.Failure -> UserMessage("Couldn't unmute this group: ${result.error.message}")
            }
        }
    }

    fun onMessageShown(shown: UserMessage) {
        if (_message.value?.id == shown.id) _message.value = null
    }

    fun onChatVisible(visible: Boolean) {
        chatVisible = visible
        // Opening the chat sends "seen" receipts (GroupChatTab), so clear the badge.
        if (visible) unread.value = 0
    }
}
