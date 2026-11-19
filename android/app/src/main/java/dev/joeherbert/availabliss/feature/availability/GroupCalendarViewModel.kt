package dev.joeherbert.availabliss.feature.availability

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.joeherbert.availabliss.core.billing.PlanRepository
import dev.joeherbert.availabliss.core.common.DateTimes
import dev.joeherbert.availabliss.core.common.UserMessage
import dev.joeherbert.availabliss.core.data.ChatRepository
import dev.joeherbert.availabliss.core.data.GroupsRepository
import dev.joeherbert.availabliss.core.data.UserRepository
import dev.joeherbert.availabliss.core.live.LiveEvents
import dev.joeherbert.availabliss.core.live.LivePayload
import dev.joeherbert.availabliss.core.network.ApiError
import dev.joeherbert.availabliss.core.network.ApiResult
import dev.joeherbert.availabliss.core.network.getOrNull
import dev.joeherbert.availabliss.core.network.model.AiPlannerAccess
import dev.joeherbert.availabliss.core.network.model.Availability
import dev.joeherbert.availabliss.core.network.model.AvailabilityDay
import dev.joeherbert.availabliss.core.network.model.DayAvailability
import dev.joeherbert.availabliss.core.network.model.DayStatus
import dev.joeherbert.availabliss.core.network.model.Group
import dev.joeherbert.availabliss.core.network.model.NextSuggestion
import dev.joeherbert.availabliss.core.network.model.PollSlot
import dev.joeherbert.availabliss.core.network.model.Rhythm
import dev.joeherbert.availabliss.core.network.model.Role
import dev.joeherbert.availabliss.core.network.model.Slot
import dev.joeherbert.availabliss.core.network.model.SlotPosition
import dev.joeherbert.availabliss.designsystem.component.availability.EveryoneBandUi
import dev.joeherbert.availabliss.designsystem.component.availability.MonthCellUi
import dev.joeherbert.availabliss.designsystem.component.availability.TimelineRowUi
import dev.joeherbert.availabliss.designsystem.component.availability.WeekColumnUi
import dev.joeherbert.availabliss.navigation.GroupRoute
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/** Month or week; the selected day's detail (who's free when) shows under either. */
enum class CalendarView(val label: String) { Month("Month"), Week("Week") }

/** The "Ask the group" sheet: the date poll's question, before it's posted. */
data class AskGroupUi(val question: String = Shortlist.DEFAULT_QUESTION, val sending: Boolean = false) {
    val canSend: Boolean get() = question.isNotBlank() && !sending
}

/**
 * The "Next one" card, for a group with a rhythm: when the rhythm says it falls ([targetLabel]) and
 * the nearest free time ([slot], with [label] and who'd miss it), or null [slot] when nothing works near it.
 */
data class NextOneUi(
    val target: LocalDate,
    /** "Fri 4 Dec". */
    val targetLabel: String,
    /** "Every week", "Every two weeks", "Every month". */
    val rhythmLabel: String,
    val slot: Slot? = null,
    /** "Fri 4 Dec · 18:00 – 22:00"; null without a [slot]. */
    val label: String? = null,
    /** "Everyone's free", "Ana can't make it"; null without a [slot]. */
    val note: String? = null,
)

/** Everything the group calendar renders. Built from raw state by [AvailabilityUiMapper]. */
data class GroupCalendarUiState(
    val loading: Boolean = true,
    val groupName: String = "",
    val membersLine: String = "",
    val requirementSummary: String = "",
    val isAdmin: Boolean = false,
    val view: CalendarView = CalendarView.Month,
    val heading: String = "",
    val columnLabels: List<String> = emptyList(),
    val monthRows: List<List<MonthCellUi?>> = emptyList(),
    /** A day on screen works only with someone missing: the legend adds "Most of us free". */
    val showPartialLegend: Boolean = false,
    /** The month's top upcoming times, ranked (fewer missing, then the Pro score). */
    val bestOptions: List<BestOptionUi> = emptyList(),
    val detail: DayDetailUi? = null,
    val syncBanner: SyncBannerUi? = null,
    val coverageNote: String? = null,
    val nudging: Boolean = false,
    val weekColumns: List<WeekColumnUi> = emptyList(),
    /** The date of each week column, for taps. */
    val weekDates: List<LocalDate> = emptyList(),
    val weekLoading: Boolean = false,
    /** The first time everyone's free on the selected day (ISO start, end), for "Propose 18:00 – 00:00". */
    val dayFree: Pair<String, String>? = null,
    val dayTitle: String = "",
    val dayNote: String? = null,
    val everyone: List<EveryoneBandUi> = emptyList(),
    val timelineRows: List<TimelineRowUi> = emptyList(),
    val dayLoading: Boolean = false,
    val error: String? = null,
    val message: UserMessage? = null,
    /** The optional AI planner: set up on the server / included in your plan. */
    val aiPlanner: AiPlannerAccess = AiPlannerAccess(),
    /** Some time works for everyone (the planner only picks from those). */
    val hasSlots: Boolean = false,
    /** The month or week on screen doesn't include today, so "Today" is offered. */
    val awayFromToday: Boolean = false,
    /** Today is later than what's on screen (which way "Today" slides). */
    val todayIsLater: Boolean = true,
    /** The nearest dates that work for everyone before / after the month or week on screen; null when there's none. */
    val previousFree: LocalDate? = null,
    val nextFree: LocalDate? = null,
    /** Still looking for them: the buttons wait rather than say there's none. */
    val findingFree: Boolean = true,
    /** The selected day (which way a jump to a free date slides). */
    val selectedDate: LocalDate = DateTimes.today(),
    /** Times picked from Best options for a date poll, soonest first. */
    val shortlist: List<Slot> = emptyList(),
    /** Non-null while the "Ask the group" sheet is open. */
    val asking: AskGroupUi? = null,
    /** The date poll was posted: the screen opens the chat, then calls [GroupCalendarViewModel.chatOpened]. */
    val openChat: Boolean = false,
    /** The group has a rhythm: the next time to meet. */
    val nextOne: NextOneUi? = null,
)

private data class RawState(
    val loading: Boolean = true,
    val view: CalendarView = CalendarView.Month,
    val month: YearMonth = YearMonth.from(DateTimes.today()),
    val availability: Availability? = null,
    /** Which week the Week view shows (and where month paging starts): not a selection. */
    val selected: LocalDate = DateTimes.today(),
    /** The day tapped, whose detail shows underneath; null until you tap one (and after tapping it again). */
    val selection: LocalDate? = null,
    /** The day picked on each month or week page (by [pageKey]), restored when you come back to it. */
    val remembered: Map<LocalDate, LocalDate> = emptyMap(),
    val week: List<DayAvailability> = emptyList(),
    val weekLoading: Boolean = false,
    val day: DayAvailability? = null,
    val dayLoading: Boolean = false,
    val nudging: Boolean = false,
    val error: String? = null,
    val message: UserMessage? = null,
    val previousFree: LocalDate? = null,
    val nextFree: LocalDate? = null,
    val findingFree: Boolean = true,
    val shortlist: List<Slot> = emptyList(),
    val asking: AskGroupUi? = null,
    val openChat: Boolean = false,
    val next: NextSuggestion? = null,
)

@HiltViewModel
class GroupCalendarViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val groups: GroupsRepository,
    private val chat: ChatRepository,
    plans: PlanRepository,
    users: UserRepository,
    liveEvents: LiveEvents,
) : ViewModel() {

    private val groupId = savedStateHandle.toRoute<GroupRoute>().groupId
    private val raw = MutableStateFlow(RawState())
    private var monthJob: Job? = null
    private var detailJob: Job? = null
    private var dayJob: Job? = null
    private var liveRefreshJob: Job? = null
    private var freeJob: Job? = null
    private var nextJob: Job? = null
    /** The rhythm the "Next one" card was last loaded for. */
    private var rhythm: Rhythm? = null
    /** The group's search range: nothing outside it can work, so the free-date search stops at its ends. */
    private var searchRange: Pair<LocalDate, LocalDate>? = null

    // Months and days already fetched, and ones on their way (a page change or a prefetch), so
    // paging to a neighbouring month or week shows it straight away instead of waiting on the network.
    private val months = mutableMapOf<YearMonth, Availability>()
    private val monthRequests = mutableMapOf<YearMonth, Deferred<ApiResult<Availability>>>()
    private val days = mutableMapOf<LocalDate, DayAvailability>()
    private val dayRequests = mutableMapOf<LocalDate, Deferred<ApiResult<DayAvailability>>>()
    /** Bumped by [refresh], so answers to requests made before it are dropped. */
    private var generation = 0
    /** The way the user last paged (1 = later), to prefetch the next week that way. */
    private var lastDelta = 1L

    val uiState: StateFlow<GroupCalendarUiState> = combine(raw, groups.group(groupId), users.user, plans.plan) { r, group, user, plan ->
        toUi(r, group, user?.id).copy(
            aiPlanner = plan?.aiPlanner ?: AiPlannerAccess(),
            hasSlots = r.availability?.slots.orEmpty().isNotEmpty(),
        )
        // Months, best options, week columns and the day panel are rebuilt on every change: off the main thread.
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GroupCalendarUiState())

    init {
        // The group itself is fetched (and kept current) by GroupViewModel, the hosting screen.
        // Only to know whether to offer "Plan with AI".
        viewModelScope.launch { plans.refresh() }
        loadMonth()
        viewModelScope.launch {
            liveEvents.payloadsFor(groupId).collect { payload ->
                when (payload) {
                    // GroupUpdated: GroupViewModel caches the new group from the payload; the
                    // timing may have changed, so availability is refetched.
                    is LivePayload.AvailabilityChanged, is LivePayload.MemberChanged, is LivePayload.MemberRemoved,
                    is LivePayload.ProposalConfirmed, is LivePayload.EventChanged, is LivePayload.EventDeleted,
                    is LivePayload.GroupUpdated -> refreshSoon()
                    else -> Unit
                }
            }
        }
        viewModelScope.launch {
            groups.group(groupId).collect { group ->
                val range = group?.requirement?.range?.let { r ->
                    val from = DateTimes.parseDate(r.from)
                    val to = DateTimes.parseDate(r.to)
                    if (from != null && to != null) from to to else null
                }
                if (range != searchRange) {
                    searchRange = range
                    findFreeNeighbours()
                }
                if (group != null && group.rhythm != rhythm) {
                    rhythm = group.rhythm
                    loadNext()
                }
            }
        }
        // GroupViewModel (the hosting screen) refetches the group itself on reconnect.
        viewModelScope.launch { liveEvents.reconnected.collect { refresh() } }
    }

    // region Intents
    fun selectView(view: CalendarView) {
        raw.update { s ->
            val switched = s.copy(view = view)
            // Same day, other view: kept if it's on the new page, else what was picked there before.
            val sel = s.selection
            if (sel == null || pageOf(switched, sel) == pageKey(switched)) switched
            else switched.copy(remembered = s.remembered + (pageKey(s) to sel)).let { it.copy(selection = it.remembered[pageKey(it)]) }
        }
        loadForView()
        loadDay()
    }

    /** A day tapped in the month or week grid: its detail loads underneath. Tapping it again clears it. */
    fun selectDate(date: LocalDate) {
        raw.update { s ->
            if (s.selection == date) s.copy(selection = null, remembered = s.remembered - pageKey(s))
            else s.copy(selected = date, selection = date).let { it.copy(remembered = it.remembered + (pageKey(it) to date)) }
        }
        loadDay()
    }

    /** The page on screen, keyed for [RawState.remembered]: its month's 1st, or its week's first day. */
    private fun pageKey(s: RawState): LocalDate = if (s.view == CalendarView.Month) s.month.atDay(1) else DateTimes.startOfWeek(s.selected)

    /** The page [date] is on in [s]'s view. */
    private fun pageOf(s: RawState, date: LocalDate): LocalDate =
        if (s.view == CalendarView.Month) YearMonth.from(date).atDay(1) else DateTimes.startOfWeek(date)

    /** Remembers this page's pick before paging away; the new page shows its own. */
    private fun RawState.leavingPage(): RawState = selection?.let { copy(remembered = remembered + (pageKey(this) to it)) } ?: this

    private fun RawState.arrived(): RawState = copy(selection = remembered[pageKey(this)])

    fun previous() = step(-1)

    /** Back to today's month or week, with today selected. */
    fun jumpToToday() {
        val today = DateTimes.today()
        val r = raw.value
        lastDelta = if (today.isAfter(r.selected)) 1 else -1
        val month = YearMonth.from(today)
        raw.update { s ->
            val moved = s.leavingPage().copy(month = month, selected = today).arrived()
            months[month]?.let { moved.copy(availability = it) } ?: moved
        }
        if (r.month != month) loadMonth() else loadDay()
        loadForView()
    }

    fun next() = step(1)

    /** To the month or week holding [date] (a free date either side), with it selected. */
    fun jumpTo(date: LocalDate) {
        val r = raw.value
        lastDelta = if (date.isAfter(r.selected)) 1 else -1
        val month = YearMonth.from(date)
        raw.update { s ->
            val moved = s.leavingPage().copy(month = month, selected = date, selection = date).let { it.copy(remembered = it.remembered + (pageKey(it) to date)) }
            months[month]?.let { moved.copy(availability = it) } ?: moved
        }
        if (r.month != month) loadMonth() else loadDay()
        loadForView()
    }

    fun refresh() {
        liveRefreshJob?.cancel()
        generation++
        monthRequests.clear()
        dayRequests.clear()
        months.clear()
        days.clear()
        loadMonth(silent = true)
        loadForView()
        loadNext()
    }

    /**
     * Live events come in bursts (a sync touches every group a member is in; week view
     * costs up to 8 requests), so they're coalesced into one refresh.
     */
    private fun refreshSoon() {
        liveRefreshJob?.cancel()
        liveRefreshJob = viewModelScope.launch {
            delay(LIVE_REFRESH_DEBOUNCE_MS)
            refresh()
        }
    }

    fun nudge() {
        if (raw.value.nudging) return
        raw.update { it.copy(nudging = true) }
        viewModelScope.launch {
            val message = when (val result = groups.nudge(groupId)) {
                is ApiResult.Success -> if (result.value == 0) "Everyone's already been nudged" else "Nudged ${result.value} ${if (result.value == 1) "person" else "people"}"
                is ApiResult.Failure -> if (result.error is ApiError.RateLimited) "You can nudge again later. Nudges are limited to once every 12 hours." else result.error.message
            }
            raw.update { it.copy(nudging = false, message = UserMessage(message)) }
        }
    }

    fun messageShown(message: UserMessage) = raw.update { if (it.message?.id == message.id) it.copy(message = null) else it }

    // region Shortlist (a date poll from Best options)
    /** "Shortlist" on a Best options row: picks it to ask the group about, or unpicks it. */
    fun toggleShortlist(slot: Slot) = raw.update { it.copy(shortlist = Shortlist.toggle(it.shortlist, slot)) }

    fun openAsk() {
        if (Shortlist.canAsk(raw.value.shortlist)) raw.update { it.copy(asking = AskGroupUi()) }
    }

    fun onAskQuestion(value: String) = raw.update { s -> s.copy(asking = s.asking?.copy(question = value.take(200))) }

    fun closeAsk() = raw.update { if (it.asking?.sending == true) it else it.copy(asking = null) }

    /** Posts the shortlisted times as a date poll, then opens the chat to it. */
    fun askGroup() {
        val s = raw.value
        val ask = s.asking ?: return
        if (!ask.canSend || !Shortlist.canAsk(s.shortlist)) return
        raw.update { it.copy(asking = ask.copy(sending = true)) }
        viewModelScope.launch {
            val slots = s.shortlist.map { PollSlot(it.start, it.end, AvailabilityUiMapper.isAllDay(it)) }
            when (val result = chat.sendDatePoll(groupId, ask.question, slots)) {
                is ApiResult.Success -> raw.update { it.copy(asking = null, shortlist = emptyList(), openChat = true) }
                is ApiResult.Failure -> raw.update {
                    it.copy(asking = it.asking?.copy(sending = false), message = UserMessage(result.error.message))
                }
            }
        }
    }

    fun chatOpened() = raw.update { it.copy(openChat = false) }
    // endregion

    /** The "Next one" card: only for a group with a rhythm. */
    private fun loadNext() {
        nextJob?.cancel()
        if (rhythm == null) {
            raw.update { it.copy(next = null) }
            return
        }
        nextJob = viewModelScope.launch {
            // A failure keeps the last suggestion: it's a hint, not the calendar itself.
            val result = groups.nextSuggestion(groupId)
            if (result is ApiResult.Success) raw.update { it.copy(next = result.value) }
        }
    }

    /** The slot the detail card proposes, with the all-day guess. */
    fun proposalFor(slot: Slot): Triple<String, String, Boolean> = Triple(slot.start, slot.end, AvailabilityUiMapper.isAllDay(slot))

    /** "Propose anyway" on a day that doesn't work: an evening, 18:00–21:00 (web's blank default). */
    fun eveningOn(date: LocalDate): Pair<String, String> =
        DateTimes.toIso(date.atTime(18, 0)) to DateTimes.toIso(date.atTime(21, 0))
    // endregion

    private fun step(delta: Long) {
        lastDelta = if (delta < 0) -1 else 1
        val r = raw.value
        when (r.view) {
            CalendarView.Month -> {
                val month = r.month.plusMonths(delta)
                val selected = if (YearMonth.from(DateTimes.today()) == month) DateTimes.today() else month.atDay(1)
                raw.update { s ->
                    val moved = s.leavingPage().copy(month = month, selected = selected).arrived()
                    // Already fetched (usually: neighbours are prefetched): show it as the page slides in.
                    months[month]?.let { moved.copy(availability = it) } ?: moved
                }
                loadMonth()
            }
            CalendarView.Week -> {
                raw.update { s -> s.leavingPage().copy(selected = s.selected.plusWeeks(delta)).arrived() }
                syncMonthToSelection()
                loadForView()
            }
        }
    }

    private fun syncMonthToSelection() {
        val month = YearMonth.from(raw.value.selected)
        if (month != raw.value.month) {
            raw.update { s -> s.copy(month = month, availability = months[month] ?: s.availability) }
            loadMonth()
        }
    }

    private fun loadMonth(silent: Boolean = false) {
        monthJob?.cancel()
        monthJob = viewModelScope.launch {
            val month = raw.value.month
            val cached = months[month]
            if (!silent && cached == null) raw.update { it.copy(loading = it.availability == null, error = null) }
            when (val result = cached?.let { ApiResult.Success(it) } ?: fetchMonth(month)) {
                is ApiResult.Success -> {
                    if (raw.value.month != month) return@launch
                    raw.update { s ->
                        s.copy(loading = false, availability = result.value, error = null)
                    }
                    if (raw.value.selection?.let { raw.value.day?.date != it.toString() } == true || silent) loadDay()
                    findFreeNeighbours()
                    prefetchMonth(month.plusMonths(1))
                    prefetchMonth(month.minusMonths(1))
                }
                is ApiResult.Failure -> raw.update { it.copy(loading = false, error = result.error.message) }
            }
        }
    }

    /** One month's availability: from the cache, the request already on its way, or a new one. */
    private suspend fun fetchMonth(month: YearMonth): ApiResult<Availability> {
        months[month]?.let { return ApiResult.Success(it) }
        val gen = generation
        val request = monthRequests.getOrPut(month) {
            viewModelScope.async { groups.availability(groupId, month.atDay(1).toString(), month.atEndOfMonth().toString()) }
        }
        val result = request.await()
        if (gen == generation) {
            monthRequests.remove(month)
            result.getOrNull()?.let { months[month] = it }
        }
        return result
    }

    private fun prefetchMonth(month: YearMonth) {
        if (month in months || month in monthRequests) return
        viewModelScope.launch { fetchMonth(month) }
    }

    /** Who's free on one day: from the cache, the request already on its way, or a new one. */
    private suspend fun fetchDay(date: LocalDate): ApiResult<DayAvailability> {
        days[date]?.let { return ApiResult.Success(it) }
        val gen = generation
        val request = dayRequests.getOrPut(date) {
            viewModelScope.async { groups.dayAvailability(groupId, date.toString()) }
        }
        val result = request.await()
        if (gen == generation) {
            dayRequests.remove(date)
            result.getOrNull()?.let { days[date] = it }
        }
        return result
    }

    /** Who's free on the selected day, shown under the grid. */
    private fun loadDay() {
        dayJob?.cancel()
        val date = raw.value.selection ?: run {
            raw.update { it.copy(dayLoading = false) }
            return
        }
        days[date]?.let { cached ->
            raw.update { it.copy(dayLoading = false, day = cached) }
            return
        }
        dayJob = viewModelScope.launch {
            raw.update { it.copy(dayLoading = true) }
            val day = fetchDay(date)
            raw.update { it.copy(dayLoading = false, day = day.getOrNull() ?: it.day.takeIf { d -> d?.date == date.toString() }, error = (day as? ApiResult.Failure)?.error?.message) }
        }
    }

    private fun loadForView() {
        detailJob?.cancel()
        findFreeNeighbours()
        val r = raw.value
        if (r.view == CalendarView.Week) loadDay()
        detailJob = when (r.view) {
            CalendarView.Month -> null
            CalendarView.Week -> viewModelScope.launch {
                val columns = AvailabilityUiMapper.columns(r.availability)
                val weekStart = DateTimes.startOfWeek(r.selected)
                val dates = columns.map { weekStart.plusDays(it.toLong()) }
                // Cached (usually: the next week is prefetched): show it at once, not the last week's.
                val cached = dates.mapNotNull { days[it] }
                raw.update { it.copy(weekLoading = cached.size < dates.size, week = if (cached.size == dates.size) cached else emptyList()) }
                val results = dates.map { date -> async { fetchDay(date) } }.awaitAll()
                // A week running into the next (or previous) month needs that month's days too.
                dates.map { YearMonth.from(it) }.distinct().filter { it != r.month && it !in months }.forEach { fetchMonth(it) }
                val failure = results.firstOrNull { it is ApiResult.Failure } as? ApiResult.Failure
                raw.update { it.copy(weekLoading = false, week = results.mapNotNull { res -> res.getOrNull() }, error = failure?.error?.message) }
                // The next week the user is likely to swipe to.
                dates.map { it.plusWeeks(lastDelta) }.filter { it !in days && it !in dayRequests }.forEach { date ->
                    launch { fetchDay(date) }
                }
            }
        }
    }

    private fun toUi(r: RawState, group: Group?, meId: String?): GroupCalendarUiState {
        val today = DateTimes.today()
        val isAdmin = group?.role?.isAdmin == true
        val columns = AvailabilityUiMapper.columns(r.availability)
        val weekStart = DateTimes.startOfWeek(r.selected)
        val weekDates = columns.map { weekStart.plusDays(it.toLong()) }
        val heading = when (r.view) {
            CalendarView.Month -> DateTimes.shortMonthYear(r.month.atDay(1))
            CalendarView.Week -> if (weekDates.isEmpty()) "" else DateTimes.weekRange(weekDates.first(), weekDates.last()).let { shortenMonth(it, weekDates) }
        }
        val day = r.selection?.let { sel -> r.day?.takeIf { it.date == sel.toString() } }
        val awayFromToday = when (r.view) {
            CalendarView.Month -> r.month != YearMonth.from(today)
            CalendarView.Week -> weekStart != DateTimes.startOfWeek(today)
        }
        val monthRows = AvailabilityUiMapper.monthRows(r.month, r.availability, group, meId, r.selection, today)
        val nameOf = { id: String -> AvailabilityUiMapper.displayName(group, id, meId) }
        return GroupCalendarUiState(
            shortlist = r.shortlist,
            asking = r.asking,
            openChat = r.openChat,
            nextOne = group?.rhythm?.let { rhythm -> r.next?.let { NextOne.ui(it, rhythm, nameOf) } },
            showPartialLegend = AvailabilityUiMapper.hasPartial(monthRows),
            bestOptions = BestOptions.options(r.availability?.slots.orEmpty(), java.time.Instant.now(), nameOf, shortlist = r.shortlist),
            awayFromToday = awayFromToday,
            previousFree = r.previousFree,
            nextFree = r.nextFree,
            findingFree = r.findingFree,
            selectedDate = r.selected,
            todayIsLater = if (r.view == CalendarView.Month) YearMonth.from(today).isAfter(r.month) else today.isAfter(r.selected),
            loading = r.loading,
            groupName = group?.name.orEmpty(),
            membersLine = group?.let {
                val count = "${it.memberCount} ${if (it.memberCount == 1) "member" else "members"}"
                when (it.role) {
                    Role.Owner -> "$count · you're the owner"
                    Role.Admin -> "$count · you're an admin"
                    else -> count
                }
            }.orEmpty(),
            requirementSummary = group?.requirementSummary.orEmpty(),
            isAdmin = isAdmin,
            view = r.view,
            heading = heading,
            columnLabels = AvailabilityUiMapper.columnLabels(columns),
            monthRows = monthRows,
            // Only once a day's been tapped: nothing is selected to begin with.
            detail = r.selection?.takeIf { r.view == CalendarView.Week || YearMonth.from(it) == r.month }
                ?.let { AvailabilityUiMapper.detail(it, r.availability, group, meId, today) },
            syncBanner = AvailabilityUiMapper.syncBanner(r.availability, group, isAdmin),
            coverageNote = AvailabilityUiMapper.coverageNote(r.availability, group, meId),
            nudging = r.nudging,
            weekColumns = AvailabilityUiMapper.weekColumns(
                r.week,
                group,
                meId,
                r.selection,
                status = { date -> dayStatus(r, date) },
                bankHoliday = { date -> monthDay(r, date)?.takeIf { it.bankHoliday }?.let { it.holiday ?: "Bank holiday" } },
                partial = { date -> monthDay(r, date)?.partial == true },
            ),
            weekDates = r.week.mapNotNull { DateTimes.parseDate(it.date) },
            dayFree = day?.everyoneFree?.firstOrNull()?.let { it.start to it.end },
            weekLoading = r.weekLoading,
            dayTitle = DateTimes.shortDay(r.selection ?: r.selected),
            dayNote = day?.let(AvailabilityUiMapper::dayNote),
            everyone = day?.let(AvailabilityUiMapper::everyoneBands).orEmpty(),
            timelineRows = day?.let { AvailabilityUiMapper.timelineRows(it, group, meId) }.orEmpty(),
            dayLoading = r.dayLoading,
            error = r.error,
            message = r.message,
        )
    }

    /**
     * Looks either side of the month or week on screen for the nearest free dates (the first day of
     * a run that works, from today on), fetching further months within the search range.
     */
    private fun findFreeNeighbours() {
        val range = searchRange ?: return
        freeJob?.cancel()
        freeJob = viewModelScope.launch {
            val r = raw.value
            val (start, end) = when (r.view) {
                CalendarView.Month -> r.month.atDay(1) to r.month.atEndOfMonth()
                CalendarView.Week -> DateTimes.startOfWeek(r.selected).let { it to it.plusDays(6) }
            }
            val earliest = maxOf(range.first, DateTimes.today())
            val next = freeDay(maxOf(end.plusDays(1), DateTimes.today()), range.second, forward = true)
            val previous = freeDay(earliest, start.minusDays(1), forward = false)
            val now = raw.value
            if (now.view != r.view || now.month != r.month || (DateTimes.startOfWeek(now.selected) != DateTimes.startOfWeek(r.selected) && r.view == CalendarView.Week)) return@launch
            raw.update { it.copy(previousFree = previous, nextFree = next, findingFree = false) }
        }
    }

    /** The first (or, going back, the last) day in [from]..[to] where a run that works starts. */
    private suspend fun freeDay(from: LocalDate, to: LocalDate, forward: Boolean): LocalDate? {
        if (to.isBefore(from)) return null
        var month = YearMonth.from(if (forward) from else to)
        // A search range is a few months; the cap just keeps a very long one from fetching for ever.
        repeat(24) {
            if (if (forward) month.isAfter(YearMonth.from(to)) else month.isBefore(YearMonth.from(from))) return null
            val loaded = fetchMonth(month).getOrNull() ?: return null
            val starts = loaded.days.filter { day ->
                val date = DateTimes.parseDate(day.date)
                date != null && !date.isBefore(from) && !date.isAfter(to) && day.status == DayStatus.Match &&
                    day.slotPosition != SlotPosition.Middle && day.slotPosition != SlotPosition.End
            }
            (if (forward) starts.firstOrNull() else starts.lastOrNull())?.let { return DateTimes.parseDate(it.date) }
            month = if (forward) month.plusMonths(1) else month.minusMonths(1)
        }
        return null
    }

    /** A day's status: from the month on screen, or a neighbouring month already fetched. */
    private fun dayStatus(r: RawState, date: LocalDate): DayStatus? = monthDay(r, date)?.status

    /** A date's day from the loaded month availability (or the cached month it's in). */
    private fun monthDay(r: RawState, date: LocalDate): AvailabilityDay? {
        val key = date.toString()
        return r.availability?.days?.firstOrNull { it.date == key } ?: months[YearMonth.from(date)]?.days?.firstOrNull { it.date == key }
    }

    private companion object {
        const val LIVE_REFRESH_DEBOUNCE_MS = 500L
    }

    /** "13 – 15 November" → "13 – 15 Nov" to fit beside the segmented control. */
    private fun shortenMonth(label: String, dates: List<LocalDate>): String =
        if (dates.first().month == dates.last().month) "${dates.first().dayOfMonth} – ${DateTimes.dayMonth(dates.last())}" else label
}
