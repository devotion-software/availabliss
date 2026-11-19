package dev.joeherbert.availabliss.feature.proposal

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.joeherbert.availabliss.core.calendar.CalendarEventDraft
import dev.joeherbert.availabliss.core.common.DateTimes
import dev.joeherbert.availabliss.core.common.UserMessage
import dev.joeherbert.availabliss.core.common.firstName
import dev.joeherbert.availabliss.core.common.joinNamesSummary
import dev.joeherbert.availabliss.core.data.EventsRepository
import dev.joeherbert.availabliss.core.data.GroupsRepository
import dev.joeherbert.availabliss.core.data.ProposalsRepository
import dev.joeherbert.availabliss.core.data.UserRepository
import dev.joeherbert.availabliss.core.live.LiveEvents
import dev.joeherbert.availabliss.core.live.LivePayload
import dev.joeherbert.availabliss.core.network.ApiResult
import dev.joeherbert.availabliss.core.network.model.Group
import dev.joeherbert.availabliss.core.network.model.LiveEvent
import dev.joeherbert.availabliss.core.network.model.Proposal
import dev.joeherbert.availabliss.core.network.model.ProposalStatus
import dev.joeherbert.availabliss.core.network.model.ResponseKind
import dev.joeherbert.availabliss.designsystem.component.AvatarPerson
import dev.joeherbert.availabliss.feature.common.AddToCalendarDelegate
import dev.joeherbert.availabliss.feature.common.AddToCalendarState
import dev.joeherbert.availabliss.feature.common.CalendarStatus
import dev.joeherbert.availabliss.feature.common.MemberDirectory
import dev.joeherbert.availabliss.navigation.ProposalRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
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
    val groupId: String = "",
    val groupName: String = "",
    /** "Ana proposed · 2h ago" */
    val proposedBy: String = "",
    val title: String? = null,
    /** "Fri 13 – Sun 15 Nov" */
    val range: String = "",
    /** [range] split for the confirmed screen: the date ("Wed 11 Nov") and, within a day, the time ("18:30 – 21:00"). */
    val rangeDate: String = "",
    val rangeTime: String? = null,
    /** "From Fri 18:00 · Keswick" */
    val detail: String = "",
    /** Firm yeses. */
    val accepted: Int = 0,
    /** Maybes (soft yeses), counted apart from [accepted]. */
    val maybe: Int = 0,
    val total: Int = 0,
    val members: List<ProposalMemberUi> = emptyList(),
    /** Confirmed: the members who accepted, proposer first. */
    val coming: List<ProposalMemberUi> = emptyList(),
    /** Confirmed: members who said maybe (a soft yes), between who's coming and who can't. */
    val maybes: List<ProposalMemberUi> = emptyList(),
    /** Confirmed: members who said they can't make it (confirmed anyway without them). */
    val cantMakeIt: List<ProposalMemberUi> = emptyList(),
    /** Confirmed: members who never replied (confirmed anyway before they did). */
    val notReplied: List<ProposalMemberUi> = emptyList(),
    /**
     * Confirmed: members with no reply at all, i.e. people who joined the group after it was
     * confirmed (replies are only asked of the members at the time).
     */
    val joinedLater: List<ProposalMemberUi> = emptyList(),
    /** Everyone it was put to can make it ("Everyone's in."), rather than confirmed anyway ("It's on."). */
    val everyoneIn: Boolean = true,
    /** How many it was put to: who's coming, who said maybe, who can't and who never replied. */
    val invited: Int = 0,
    /** The proposer or an admin, on an open proposal someone else has accepted: "Confirm anyway". */
    val canConfirmAnyway: Boolean = false,
    /** For the Confirm anyway sheet: "Priya can't make it and Tom hasn't replied. …" */
    val confirmAnywayMessage: String = "",
    /** Confirmed, and you're the proposer or an admin while someone who was coming has dropped out. */
    val needsReview: Boolean = false,
    /** "Priya can't make it any more" */
    val reviewHeadline: String = "",
    /** You were asked (members who joined since weren't), so you can still change your reply once confirmed. */
    val canChangeReply: Boolean = false,
    /** Cancelled a confirmed plan: go to the group's calendar to find another date. */
    val findAnotherDate: Boolean = false,
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
    /** Confirmed, and you're the proposer or an admin: edit the plan's details or cancel it. */
    val canManagePlan: Boolean = false,
    /** The confirmed plan's event, to edit. */
    val eventId: String? = null,
    val location: String? = null,
    val calendar: AddToCalendarState = AddToCalendarState(),
    val calendarStatus: CalendarStatus = CalendarStatus(),
    /** The confirmed plan's cover photo, if someone added one. */
    val coverImage: String? = null,
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
    val findAnotherDate: Boolean = false,
    val message: UserMessage? = null,
    /** The confirmed plan's cover photo (fetched with its event). */
    val coverImage: String? = null,
    /** Whose event [coverImage] came from, so it's fetched once per event. */
    val coverFor: String? = null,
)

@HiltViewModel
class ProposalViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val proposals: ProposalsRepository,
    private val events: EventsRepository,
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
        // A plan already in a calendar (e.g. from before a reinstall) counts as added.
        viewModelScope.launch {
            data.map { d ->
                d.proposal?.takeIf { it.eventId != null }?.let { p ->
                    CalendarEventDraft.from(p, fallbackTitle = d.group?.name ?: "Availabliss plan")?.let { p.eventId!! to it }
                }
            }.distinctUntilChanged().collectLatest { plan -> if (plan != null) addToCalendar.findExisting(listOf(plan)) }
        }
        viewModelScope.launch {
            liveEvents.payloads.collect { payload ->
                when (payload) {
                    is LivePayload.ProposalChanged -> if (payload.proposal.id == proposalId) setProposal(payload.proposal)
                    is LivePayload.ProposalConfirmed -> if (payload.proposal.id == proposalId) setProposal(payload.proposal)
                    else -> Unit
                }
            }
        }
        // Member changes in this proposal's group only (payloads don't carry the group id).
        viewModelScope.launch {
            liveEvents.events.filter { it.type == LiveEvent.MEMBER_CHANGED }.collect { event ->
                val groupId = data.value.proposal?.groupId ?: return@collect
                if (event.groupId == groupId) loadGroup(groupId, force = true)
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

    fun confirmAnyway() {
        if (data.value.responding) return
        viewModelScope.launch {
            data.update { it.copy(responding = true) }
            when (val result = proposals.confirm(proposalId)) {
                is ApiResult.Success -> {
                    setProposal(result.value)
                    data.update { it.copy(responding = false) }
                }
                is ApiResult.Failure -> data.update { it.copy(responding = false, message = UserMessage(result.error.message)) }
            }
        }
    }

    /** "Keep the plan": clears the review; whoever dropped out stays as can't make it. */
    fun keepPlan() {
        if (data.value.responding) return
        viewModelScope.launch {
            data.update { it.copy(responding = true) }
            when (val result = proposals.keep(proposalId)) {
                is ApiResult.Success -> {
                    setProposal(result.value)
                    data.update { it.copy(responding = false) }
                }
                is ApiResult.Failure -> data.update { it.copy(responding = false, message = UserMessage(result.error.message)) }
            }
        }
    }

    /** "Cancel and find another date": cancels the plan (its event goes too), then opens the group's calendar. */
    fun cancelPlan() {
        if (data.value.responding) return
        viewModelScope.launch {
            data.update { it.copy(responding = true) }
            when (val result = proposals.cancel(proposalId)) {
                is ApiResult.Success -> data.update { it.copy(responding = false, findAnotherDate = true) }
                is ApiResult.Failure -> data.update { it.copy(responding = false, message = UserMessage(result.error.message)) }
            }
        }
    }

    fun onFoundAnotherDate() = data.update { it.copy(findAnotherDate = false) }

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

    fun chooseCalendar(id: Long) {
        viewModelScope.launch { addToCalendar.choose(id) }
    }

    fun cancelCalendarChoice() = addToCalendar.cancelChoice()
    fun onIntentHandled() = addToCalendar.onIntentHandled()
    fun onMessageShown(message: UserMessage) = data.update { if (it.message?.id == message.id) it.copy(message = null) else it }

    private fun setProposal(proposal: Proposal) {
        data.update { it.copy(proposal = proposal) }
        val eventId = proposal.eventId ?: return
        if (data.value.coverFor == eventId) return
        data.update { it.copy(coverFor = eventId) }
        viewModelScope.launch {
            val image = (events.get(eventId) as? ApiResult.Success)?.value?.image
            data.update { if (it.coverFor == eventId) it.copy(coverImage = image) else it }
        }
    }

    private suspend fun loadGroup(groupId: String, force: Boolean) {
        val cached = if (force) null else groups.cachedGroup(groupId)
        val group = cached ?: (groups.refreshGroup(groupId) as? ApiResult.Success)?.value
        if (group != null) data.update { it.copy(group = group) }
    }

    /** "Priya can't make it, Ana said maybe and Tom hasn't replied. Everyone will be told it's on, with 1 of 4 coming and 1 maybe." */
    private fun confirmAnywayMessage(p: Proposal, members: List<ProposalMemberUi>, meId: String?, directory: MemberDirectory): String {
        val replied = p.responses.map { it.userId }.toSet()
        fun names(kind: ResponseKind) = members.filter { it.response == kind && it.userId in replied }
            .map { if (it.userId == meId) "You" else directory[it.userId]?.name?.firstName() ?: "Someone" }
        val declined = names(ResponseKind.Declined)
        val maybe = names(ResponseKind.Maybe)
        val pending = names(ResponseKind.Pending)
        val parts = buildList {
            if (declined.isNotEmpty()) add("${declined.joinNamesSummary(4)} can't make it")
            if (maybe.isNotEmpty()) add("${maybe.joinNamesSummary(4)} said maybe")
            if (pending.isNotEmpty()) add("${pending.joinNamesSummary(4)} ${if (pending.size == 1) "hasn't" else "haven't"} replied")
        }
        val joined = if (parts.size <= 1) parts.firstOrNull().orEmpty() else parts.dropLast(1).joinToString(", ") + " and " + parts.last()
        val who = if (joined.isEmpty()) "" else "$joined. "
        val maybeCount = ProposalFormatting.maybeCount(p)
        val maybes = if (maybeCount > 0) " and $maybeCount maybe" else ""
        return "${who}Everyone will be told it's on, with ${ProposalFormatting.acceptedCount(p)} of ${p.responses.size} coming$maybes."
    }

    /** "Priya can't make it any more" / "Priya and Tom can't make it any more". */
    private fun reviewHeadline(p: Proposal, members: List<ProposalMemberUi>, meId: String?, directory: MemberDirectory): String {
        val dropped = p.responses.filter { it.droppedOut }.map { it.userId }.toSet()
        val names = members.filter { it.userId in dropped }
            .map { if (it.userId == meId) "You" else directory[it.userId]?.name?.firstName() ?: "Someone" }
        return if (names.isEmpty()) "Someone can't make it any more" else "${names.joinNamesSummary(4)} can't make it any more"
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
                id in busyIds && response == ResponseKind.Maybe -> "Busy then · said maybe"
                response == ResponseKind.Pending -> "Hasn't replied"
                response == ResponseKind.Maybe -> "Said maybe"
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
        val inGroup = { id: String -> g == null || g.members.any { it.userId == id } }
        val confirmed = p.status == ProposalStatus.Confirmed
        val proposerName = directory.displayName(p.createdBy, youSuffix = false).let { if (p.createdBy == meId) "You" else it }
        val key = p.eventId ?: p.id
        val rangeParts = DateTimes.rangeParts(p.start, p.end, p.allDay)
        return ProposalUiState(
            loading = loading,
            error = error,
            groupId = p.groupId,
            groupName = g?.name.orEmpty(),
            proposedBy = "$proposerName proposed · ${DateTimes.relative(p.createdAt)}",
            title = p.title?.takeIf { it.isNotBlank() },
            range = ProposalFormatting.rangeLabel(p),
            rangeDate = rangeParts.first,
            rangeTime = rangeParts.second,
            detail = ProposalFormatting.detailLine(p),
            accepted = ProposalFormatting.acceptedCount(p),
            maybe = ProposalFormatting.maybeCount(p),
            total = ProposalFormatting.totalCount(p, g?.memberCount ?: 0),
            members = members,
            coming = if (!confirmed) emptyList() else members
                .filter { it.response == ResponseKind.Accepted && inGroup(it.userId) }
                .map { it.copy(note = if (it.userId == p.createdBy) "Proposed this" else "") },
            maybes = if (!confirmed) emptyList() else members
                .filter { it.userId in responses && it.response == ResponseKind.Maybe && inGroup(it.userId) }
                .map { it.copy(note = if (it.userId == p.createdBy) "Proposed this" else "") },
            cantMakeIt = if (!confirmed) emptyList() else members
                .filter { it.userId in responses && it.response == ResponseKind.Declined && inGroup(it.userId) }
                .map { it.copy(note = if (responses[it.userId]?.droppedOut == true) "Can't make it any more" else "") },
            notReplied = if (!confirmed) emptyList() else members
                .filter { it.userId in responses && it.response == ResponseKind.Pending && inGroup(it.userId) }
                .map { it.copy(note = "") },
            joinedLater = if (!confirmed || g == null) emptyList() else members
                .filter { it.userId !in responses && inGroup(it.userId) }
                .map { it.copy(note = "Joined after this was confirmed") },
            everyoneIn = p.everyoneIn,
            invited = p.responses.size,
            canConfirmAnyway = p.status == ProposalStatus.Open && meId != null && (p.createdBy == meId || isAdmin) &&
                p.responses.any { it.response == ResponseKind.Accepted && it.userId != meId },
            confirmAnywayMessage = confirmAnywayMessage(p, members, meId, directory),
            needsReview = confirmed && p.needsReview && meId != null && (p.createdBy == meId || isAdmin),
            reviewHeadline = reviewHeadline(p, members, meId, directory),
            canChangeReply = confirmed && meId != null && meId in responses,
            findAnotherDate = findAnotherDate,
            clash = Clash.text(busyIds, { directory[it]?.name }, meId),
            clashDetail = Clash.detail(busyIds, meId),
            iAmBusy = meId != null && meId in busyIds,
            isAdmin = isAdmin,
            myResponse = meId?.let { responses[it]?.response },
            responding = responding,
            status = p.status,
            canCancel = p.status == ProposalStatus.Open && (p.createdBy == meId || isAdmin),
            canManagePlan = confirmed && p.eventId != null && (p.createdBy == meId || isAdmin),
            eventId = p.eventId,
            location = p.location?.takeIf { it.isNotBlank() },
            calendar = cal,
            calendarStatus = cal.status(key),
            coverImage = coverImage,
            closed = closed,
            message = message,
        )
    }
}
