package dev.joeherbert.availabliss.feature.proposal

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.joeherbert.availabliss.core.data.GroupsRepository
import dev.joeherbert.availabliss.core.data.ProposalsRepository
import dev.joeherbert.availabliss.core.data.UserRepository
import dev.joeherbert.availabliss.core.live.LiveEvents
import dev.joeherbert.availabliss.core.live.LivePayload
import dev.joeherbert.availabliss.core.network.ApiResult
import dev.joeherbert.availabliss.core.network.model.Group
import dev.joeherbert.availabliss.core.network.model.Proposal
import dev.joeherbert.availabliss.core.network.model.ProposalStatus
import dev.joeherbert.availabliss.designsystem.component.chat.ProposalCardUi
import dev.joeherbert.availabliss.feature.common.MemberDirectory
import dev.joeherbert.availabliss.navigation.GroupRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class ProposalListItem(val id: String, val card: ProposalCardUi)

data class GroupProposalsUiState(
    val groupName: String = "",
    val loading: Boolean = true,
    val error: String? = null,
    val open: List<ProposalListItem> = emptyList(),
    val confirmed: List<ProposalListItem> = emptyList(),
)

private data class ProposalsData(
    val proposals: List<Proposal> = emptyList(),
    val group: Group? = null,
    val loading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class GroupProposalsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val proposals: ProposalsRepository,
    private val groups: GroupsRepository,
    users: UserRepository,
    liveEvents: LiveEvents,
) : ViewModel() {

    private val groupId = savedStateHandle.toRoute<GroupRoute>().groupId
    private val data = MutableStateFlow(ProposalsData(group = groups.cachedGroup(groupId)))

    val state: StateFlow<GroupProposalsUiState> = combine(data, users.user) { d, me -> d.toUi(me?.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GroupProposalsUiState())

    init {
        refresh()
        viewModelScope.launch {
            liveEvents.payloadsFor(groupId).collect { payload ->
                when (payload) {
                    is LivePayload.ProposalChanged -> upsert(payload.proposal)
                    is LivePayload.ProposalConfirmed -> upsert(payload.proposal)
                    is LivePayload.GroupUpdated -> data.update { it.copy(group = payload.group) }
                    else -> Unit
                }
            }
        }
        viewModelScope.launch { liveEvents.reconnected.collect { refresh() } }
    }

    fun refresh() {
        viewModelScope.launch {
            data.update { it.copy(loading = true, error = null) }
            if (data.value.group == null) {
                (groups.refreshGroup(groupId) as? ApiResult.Success)?.value?.let { g -> data.update { it.copy(group = g) } }
            }
            when (val result = proposals.list(groupId)) {
                is ApiResult.Success -> data.update { it.copy(proposals = result.value, loading = false) }
                is ApiResult.Failure -> data.update { it.copy(loading = false, error = result.error.message) }
            }
        }
    }

    private fun upsert(proposal: Proposal) = data.update { d ->
        d.copy(proposals = d.proposals.filterNot { it.id == proposal.id } + proposal)
    }

    private fun ProposalsData.toUi(meId: String?): GroupProposalsUiState {
        val directory = MemberDirectory(group?.members.orEmpty(), meId)
        val memberCount = group?.memberCount ?: 0
        val now = Instant.now()
        fun item(p: Proposal) = ProposalListItem(p.id, ProposalFormatting.card(p, directory, memberCount))
        val upcoming = proposals.filter { p -> endsInFuture(p.end, now) }
        return GroupProposalsUiState(
            groupName = group?.name.orEmpty(),
            loading = loading,
            error = error,
            open = upcoming.filter { it.status == ProposalStatus.Open }.sortedBy { it.start }.map(::item),
            confirmed = upcoming.filter { it.status == ProposalStatus.Confirmed }.sortedBy { it.start }.map(::item),
        )
    }
}

private fun endsInFuture(iso: String, now: Instant): Boolean =
    dev.joeherbert.availabliss.core.common.DateTimes.parseInstant(iso)?.isAfter(now) ?: true
