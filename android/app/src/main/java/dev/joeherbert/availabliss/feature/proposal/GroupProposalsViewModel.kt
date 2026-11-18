package dev.joeherbert.availabliss.feature.proposal

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import dev.joeherbert.availabliss.core.network.model.ResponseKind
import dev.joeherbert.availabliss.designsystem.component.chat.ProposalCardUi
import dev.joeherbert.availabliss.feature.common.MemberDirectory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

/**
 * A proposal row. [headline] / [detail] are the compact one-line forms the calendar's Plans card
 * shows: "Pub quiz" / "Fri 13 Nov · 3 of 6 in · 1 maybe · you're in".
 */
data class ProposalListItem(
    val id: String,
    val card: ProposalCardUi,
    val headline: String = card.title,
    val detail: String = card.status,
)

data class GroupProposalsUiState(
    val groupName: String = "",
    val loading: Boolean = true,
    val error: String? = null,
    val open: List<ProposalListItem> = emptyList(),
    val confirmed: List<ProposalListItem> = emptyList(),
) {
    /** Up to two for the calendar's Plans card: open proposals first, then the next confirmed plan. */
    val highlights: List<ProposalListItem> get() = (open + confirmed.take(1)).take(2)
}

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
    private val users: UserRepository,
    liveEvents: LiveEvents,
) : ViewModel() {

    // Shared by the group's calendar (its Plans card) and the Plans screen, so read the id by name.
    private val groupId: String = savedStateHandle.get<String>("groupId").orEmpty()
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
                    is LivePayload.GroupUpdated -> {
                        val group = groups.applyLiveUpdate(payload.group, users.user.value?.id)
                        data.update { it.copy(group = group) }
                    }
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
        fun item(p: Proposal): ProposalListItem {
            val card = ProposalFormatting.card(p, directory, memberCount)
            val title = p.title?.takeIf { it.isNotBlank() }
            val status = if (p.status == ProposalStatus.Open) {
                ProposalFormatting.withMaybes(
                    "${ProposalFormatting.acceptedCount(p)} of ${ProposalFormatting.totalCount(p, memberCount)} in",
                    ProposalFormatting.maybeCount(p),
                )
            } else {
                "Confirmed"
            }
            val yours = when (p.responses.firstOrNull { it.userId == meId }?.response.takeIf { p.status == ProposalStatus.Open }) {
                ResponseKind.Accepted -> "you're in"
                ResponseKind.Maybe -> "you said maybe"
                ResponseKind.Declined -> "you can't make it"
                ResponseKind.Pending -> "waiting on you"
                else -> null
            }
            val detail = listOfNotNull(card.title.takeIf { title != null }, status, yours).joinToString(" · ")
            return ProposalListItem(p.id, card, headline = title ?: card.title, detail = detail)
        }
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
