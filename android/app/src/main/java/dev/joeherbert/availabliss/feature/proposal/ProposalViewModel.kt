package dev.joeherbert.availabliss.feature.proposal

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.joeherbert.availabliss.core.calendar.CalendarEventDraft
import dev.joeherbert.availabliss.core.common.DateTimes
import dev.joeherbert.availabliss.core.common.UserMessage
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
import dev.joeherbert.availabliss.designsystem.component.AvatarPerson
import dev.joeherbert.availabliss.feature.common.AddToCalendarDelegate
import dev.joeherbert.availabliss.feature.common.AddToCalendarState
import dev.joeherbert.availabliss.feature.common.MemberDirectory
import dev.joeherbert.availabliss.navigation.ProposalRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProposalMemberUi(
    val userId: String,
    val person: AvatarPerson,
    val name: String,
    val note: String,
    val response: ResponseKind,
    val canOverride: Boolean,
)

data class ProposalUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val groupName: String = "",
    /** "Ana proposed · 2h ago" */
    val proposedBy: String = "",
    val title: String? = null,
    /** "Fri 13 – Sun 15 Nov" */
    val range: String = "",
    /** "From Fri 18:00 · Keswick" */
    val detail: String = "",
    val accepted: Int = 0,
    val total: Int = 0,
    val members: List<ProposalMemberUi> = emptyList(),
    /** Open proposals that clash with someone's busy time: "Priya is busy then". */
    val clash: String? = null,
    /** "Their calendars or blocked time overlap. …" */
    val clashDetail: String = "",
    /** You're among the busy members. */
    val iAmBusy: Boolean = false,
    val isAdmin: Boolean = false,
    val myResponse: ResponseKind? = null,
    val responding: Boolean = false,
    val status: ProposalStatus = ProposalStatus.Open,
    val canCancel: Boolean = false,
    val location: String? = null,
    val calendar: AddToCalendarState = AddToCalendarState(),
    val addedToCalendar: Boolean = false,
    /** Cancelled by this user: leave the screen. */
    val closed: Boolean = false,
    val message: UserMessage? = null,
)

private data class ProposalData(
    val proposal: Proposal? = null,
    val group: Group? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val responding: Boolean = false,
    val closed: Boolean = false,
    val message: UserMessage? = null,
)

@HiltViewModel
class ProposalViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val proposals: ProposalsRepository,
    private val groups: GroupsRepository,
    private val users: UserRepository,
    private val addToCalendar: AddToCalendarDelegate,
    liveEvents: LiveEvents,
) : ViewModel() {

    private val proposalId = savedStateHandle.toRoute<ProposalRoute>().proposalId
    private val data = MutableStateFlow(ProposalData())

    val state: StateFlow<ProposalUiState> = combine(data, addToCalendar.state, users.user) { d, cal, me -> d.toUi(cal, me?.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProposalUiState())

    init {
        refresh()
        viewModelScope.launch { addToCalendar.loadCalendars() }
        viewModelScope.launch {
            liveEvents.payloads.collect { payload ->
                when (payload) {
                    is LivePayload.ProposalChanged -> if (payload.proposal.id == proposalId) setProposal(payload.proposal)
                    is LivePayload.ProposalConfirmed -> if (payload.proposal.id == proposalId) setProposal(payload.proposal)
                    is LivePayload.MemberChanged, is LivePayload.MemberRemoved -> data.value.proposal?.let { loadGroup(it.groupId, force = true) }
                    else -> Unit
                }
            }
        }
        viewModelScope.launch { liveEvents.reconnected.collect { refresh() } }
    }

    fun refresh() {
        viewModelScope.launch {
            data.update { it.copy(loading = true, error = null) }
            when (val result = proposals.get(proposalId)) {
                is ApiResult.Success -> {
                    setProposal(result.value)
                    data.update { it.copy(loading = false) }
                    loadGroup(result.value.groupId, force = false)
                }
                is ApiResult.Failure -> data.update { it.copy(loading = false, error = result.error.message) }
            }
        }
    }

    fun respond(response: ResponseKind) {
        if (data.value.responding) return
        viewModelScope.launch {
            data.update { it.copy(responding = true) }
            when (val result = proposals.respond(proposalId, response)) {
                is ApiResult.Success -> {
                    setProposal(result.value)
                    data.update { it.copy(responding = false) }
                }
                is ApiResult.Failure -> data.update { it.copy(responding = false, message = UserMessage(result.error.message)) }
            }
        }
    }

    fun override(userId: String, response: ResponseKind) {
        viewModelScope.launch {
            when (val result = proposals.override(proposalId, userId, response)) {
                is ApiResult.Success -> setProposal(result.value)
                is ApiResult.Failure -> data.update { it.copy(message = UserMessage(result.error.message)) }
            }
        }
    }

    fun cancel() {
        viewModelScope.launch {
            when (val result = proposals.cancel(proposalId)) {
                is ApiResult.Success -> data.update { it.copy(closed = true) }
                is ApiResult.Failure -> data.update { it.copy(message = UserMessage(result.error.message)) }
            }
        }
    }

    fun addToCalendar() {
        val d = data.value
        val proposal = d.proposal ?: return
        val draft = CalendarEventDraft.from(proposal, fallbackTitle = d.group?.name ?: "Availabliss plan") ?: return
        viewModelScope.launch { addToCalendar.add(proposal.eventId ?: proposal.id, draft) }
    }

    fun onSelectCalendar(id: Long) = addToCalendar.select(id)
    fun onIntentHandled() = addToCalendar.onIntentHandled()
    fun onMessageShown(message: UserMessage) = data.update { if (it.message?.id == message.id) it.copy(message = null) else it }

    private fun setProposal(proposal: Proposal) = data.update { it.copy(proposal = proposal) }

    private suspend fun loadGroup(groupId: String, force: Boolean) {
        val cached = if (force) null else groups.cachedGroup(groupId)
        val group = cached ?: (groups.refreshGroup(groupId) as? ApiResult.Success)?.value
        if (group != null) data.update { it.copy(group = group) }
    }

    private fun ProposalData.toUi(cal: AddToCalendarState, meId: String?): ProposalUiState {
        val p = proposal ?: return ProposalUiState(loading = loading, error = error, calendar = cal, message = message)
        val g = group
        val directory = MemberDirectory(g?.members.orEmpty(), meId)
        val isAdmin = g?.role?.isAdmin == true
        val responses = p.responses.associateBy { it.userId }
        val busyIds = ProposalFormatting.busyIds(p)
        // Proposer first, then the group's member order.
        val memberIds = (listOf(p.createdBy) + (g?.members?.map { it.userId } ?: p.responses.map { it.userId })).distinct()
            .filter { it in responses || g?.members?.any { m -> m.userId == it } == true }
        val members = memberIds.map { id ->
            val member = directory[id]
            val r = responses[id]
            val response = r?.response ?: ResponseKind.Pending
            val note = when {
                id == p.createdBy -> "Proposed this"
                r?.setBy != null && r.setBy != id -> if (r.setBy == meId) "Set by you (admin)" else "Set by ${directory.displayName(r.setBy, youSuffix = false)}"
                member != null && !member.synced -> "Not synced yet"
                id in busyIds && response == ResponseKind.Pending -> "Busy then · hasn't replied"
                id in busyIds && response == ResponseKind.Accepted -> "Busy then, but in"
                response == ResponseKind.Pending -> "Hasn't replied"
                response == ResponseKind.Declined -> "Can't make it"
                member?.role?.isAdmin == true -> "Admin"
                else -> "Synced"
            }
            ProposalMemberUi(
                userId = id,
                person = directory.avatar(id),
                name = directory.displayName(id),
                note = note,
                response = response,
                canOverride = isAdmin && id != meId && p.status == ProposalStatus.Open && response != ResponseKind.Accepted,
            )
        }
        val proposerName = directory.displayName(p.createdBy, youSuffix = false).let { if (p.createdBy == meId) "You" else it }
        val key = p.eventId ?: p.id
        return ProposalUiState(
            loading = loading,
            error = error,
            groupName = g?.name.orEmpty(),
            proposedBy = "$proposerName proposed · ${DateTimes.relative(p.createdAt)}",
            title = p.title?.takeIf { it.isNotBlank() },
            range = ProposalFormatting.rangeLabel(p),
            detail = ProposalFormatting.detailLine(p),
            accepted = ProposalFormatting.acceptedCount(p),
            total = ProposalFormatting.totalCount(p, g?.memberCount ?: 0),
            members = members,
            clash = Clash.text(busyIds, { directory[it]?.name }, meId),
            clashDetail = Clash.detail(busyIds, meId),
            iAmBusy = meId != null && meId in busyIds,
            isAdmin = isAdmin,
            myResponse = meId?.let { responses[it]?.response },
            responding = responding,
            status = p.status,
            canCancel = p.status == ProposalStatus.Open && (p.createdBy == meId || isAdmin),
            location = p.location?.takeIf { it.isNotBlank() },
            calendar = cal,
            addedToCalendar = key in cal.added,
            closed = closed,
            message = message,
        )
    }
}
