package dev.joeherbert.availabliss.feature.group

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.joeherbert.availabliss.core.data.GroupsRepository
import dev.joeherbert.availabliss.core.data.UserRepository
import dev.joeherbert.availabliss.core.live.LiveEvents
import dev.joeherbert.availabliss.core.live.LivePayload
import dev.joeherbert.availabliss.navigation.GroupRoute
import dev.joeherbert.availabliss.navigation.GroupTab
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GroupShellUiState(
    val groupName: String = "",
    val unreadCount: Int = 0,
    val isAdmin: Boolean = false,
)

/**
 * Shell of a group: loads the [dev.joeherbert.availabliss.core.network.model.Group] the tabs
 * share and tracks the chat unread badge while the chat isn't open.
 */
@HiltViewModel
class GroupViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val groups: GroupsRepository,
    private val users: UserRepository,
    liveEvents: LiveEvents,
) : ViewModel() {

    private val groupId = savedStateHandle.toRoute<GroupRoute>().groupId
    private val unread = MutableStateFlow(0)
    private var currentTab: GroupTab = GroupTab.Calendar

    val uiState: StateFlow<GroupShellUiState> = combine(groups.group(groupId), unread) { group, count ->
        GroupShellUiState(groupName = group?.name.orEmpty(), unreadCount = count, isAdmin = group?.role?.isAdmin == true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GroupShellUiState())

    init {
        viewModelScope.launch { groups.refreshGroup(groupId) }
        viewModelScope.launch {
            // Seed the badge from the groups list (refreshing it if it hasn't loaded yet).
            val summary = groups.groups.value ?: run { groups.refreshGroups(); groups.groups.value }
            val count = summary?.firstOrNull { it.id == groupId }?.unreadCount ?: 0
            if (currentTab != GroupTab.Chat) unread.value = count
        }
        viewModelScope.launch {
            liveEvents.payloadsFor(groupId).collect { payload ->
                when (payload) {
                    is LivePayload.MessageCreated -> {
                        val me = users.user.value?.id ?: users.user.filterNotNull().first().id
                        if (currentTab != GroupTab.Chat && payload.message.authorId != me) unread.update { it + 1 }
                    }
                    is LivePayload.GroupUpdated -> groups.refreshGroup(groupId)
                    else -> Unit
                }
            }
        }
    }

    fun onTabChanged(tab: GroupTab) {
        currentTab = tab
        // Opening the chat sends "seen" receipts (GroupChatTab), so clear the badge.
        if (tab == GroupTab.Chat) unread.value = 0
    }
}
