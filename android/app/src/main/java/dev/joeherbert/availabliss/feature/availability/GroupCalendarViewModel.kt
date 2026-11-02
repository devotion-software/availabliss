package dev.joeherbert.availabliss.feature.availability

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.joeherbert.availabliss.core.billing.PlanRepository
import dev.joeherbert.availabliss.core.common.DateTimes
import dev.joeherbert.availabliss.core.common.UserMessage
import dev.joeherbert.availabliss.core.data.GroupsRepository
import dev.joeherbert.availabliss.core.data.UserRepository
import dev.joeherbert.availabliss.core.live.LiveEvents
import dev.joeherbert.availabliss.core.live.LivePayload
import dev.joeherbert.availabliss.core.network.ApiError
import dev.joeherbert.availabliss.core.network.ApiResult
import dev.joeherbert.availabliss.core.network.getOrNull
import dev.joeherbert.availabliss.core.network.model.Availability
import dev.joeherbert.availabliss.core.network.model.DayAvailability
import dev.joeherbert.availabliss.core.network.model.AiPlannerAccess
import dev.joeherbert.availabliss.core.network.model.DayStatus
import dev.joeherbert.availabliss.core.network.model.Group
import dev.joeherbert.availabliss.core.network.model.Role
import dev.joeherbert.availabliss.core.network.model.Slot
import dev.joeherbert.availabliss.designsystem.component.availability.EveryoneBandUi
import dev.joeherbert.availabliss.designsystem.component.availability.MonthCellUi
import dev.joeherbert.availabliss.designsystem.component.availability.TimelineRowUi
import dev.joeherbert.availabliss.designsystem.component.availability.WeekColumnUi
import dev.joeherbert.availabliss.navigation.GroupRoute
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

enum class CalendarView(val label: String) { Month("Month"), Week("Week"), Day("Day") }

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
    val daysNote: String? = null,
    val detail: DayDetailUi? = null,
    val syncBanner: SyncBannerUi? = null,
    val nudging: Boolean = false,
    val weekColumns: List<WeekColumnUi> = emptyList(),
    val weekLoading: Boolean = false,
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
)

private data class RawState(
    val loading: Boolean = true,
    val view: CalendarView = CalendarView.Month,
    val month: YearMonth = YearMonth.from(DateTimes.today()),
    val availability: Availability? = null,
    val selected: LocalDate = DateTimes.today(),
    /** True once the user taps a day; reloads then keep their choice. */
    val userPicked: Boolean = false,
    val week: List<DayAvailability> = emptyList(),
    val weekLoading: Boolean = false,
    val day: DayAvailability? = null,
    val dayLoading: Boolean = false,
    val nudging: Boolean = false,
    val error: String? = null,
    val message: UserMessage? = null,
)

@HiltViewModel
class GroupCalendarViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val groups: GroupsRepository,
    plans: PlanRepository,
    users: UserRepository,
    liveEvents: LiveEvents,
) : ViewModel() {

    private val groupId = savedStateHandle.toRoute<GroupRoute>().groupId
    private val raw = MutableStateFlow(RawState())
    private var monthJob: Job? = null
    private var detailJob: Job? = null

    val uiState: StateFlow<GroupCalendarUiState> = combine(raw, groups.group(groupId), users.user, plans.plan) { r, group, user, plan ->
        toUi(r, group, user?.id).copy(
            aiPlanner = plan?.aiPlanner ?: AiPlannerAccess(),
            hasSlots = r.availability?.slots.orEmpty().isNotEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GroupCalendarUiState())

    init {
        viewModelScope.launch { groups.refreshGroup(groupId) }
        // Only to know whether to offer "Plan with AI".
        viewModelScope.launch { plans.refresh() }
        loadMonth()
        viewModelScope.launch {
            liveEvents.payloadsFor(groupId).collect { payload ->
                when (payload) {
                    is LivePayload.AvailabilityChanged, is LivePayload.MemberChanged, is LivePayload.MemberRemoved,
                    is LivePayload.ProposalConfirmed, is LivePayload.EventChanged, is LivePayload.EventDeleted -> refresh()
                    is LivePayload.GroupUpdated -> {
                        groups.refreshGroup(groupId)
                        refresh()
                    }
                    else -> Unit
                }
            }
        }
    }

    // region Intents
    fun selectView(view: CalendarView) {
        raw.update { it.copy(view = view) }
        loadForView()
    }

    fun selectDate(date: LocalDate) {
        raw.update { it.copy(selected = date, userPicked = true) }
        if (raw.value.view != CalendarView.Month) loadForView()
    }

    /** "See who's free": jump to the day view for the selected date. */
    fun showDay() = selectView(CalendarView.Day)

    fun previous() = step(-1)

    fun next() = step(1)

    fun refresh() {
        loadMonth(silent = true)
        if (raw.value.view != CalendarView.Month) loadForView()
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

    /** The slot the detail card proposes, with the all-day guess. */
    fun proposalFor(slot: Slot): Triple<String, String, Boolean> = Triple(slot.start, slot.end, AvailabilityUiMapper.isAllDay(slot))

    /** "Propose anyway" on a day that doesn't work: an evening, 18:00–21:00 (web's blank default). */
    fun eveningOn(date: LocalDate): Pair<String, String> =
        DateTimes.toIso(date.atTime(18, 0)) to DateTimes.toIso(date.atTime(21, 0))
    // endregion

    private fun step(delta: Long) {
        val r = raw.value
        when (r.view) {
            CalendarView.Month -> {
                val month = r.month.plusMonths(delta)
                val selected = if (YearMonth.from(DateTimes.today()) == month) DateTimes.today() else month.atDay(1)
                raw.update { it.copy(month = month, selected = selected, userPicked = false) }
                loadMonth()
            }
            CalendarView.Week -> {
                raw.update { it.copy(selected = it.selected.plusWeeks(delta)) }
                syncMonthToSelection()
                loadForView()
            }
            CalendarView.Day -> {
                raw.update { it.copy(selected = nextVisibleDay(it.selected, delta.toInt())) }
                syncMonthToSelection()
                loadForView()
            }
        }
    }

    /** Skips weekdays that can never match. */
    private fun nextVisibleDay(from: LocalDate, direction: Int): LocalDate {
        val columns = AvailabilityUiMapper.columns(raw.value.availability).toSet()
        var d = from.plusDays(direction.toLong())
        repeat(7) {
            if (DateTimes.apiWeekday(d) in columns) return d
            d = d.plusDays(direction.toLong())
        }
        return d
    }

    private fun syncMonthToSelection() {
        val month = YearMonth.from(raw.value.selected)
        if (month != raw.value.month) {
            raw.update { it.copy(month = month) }
            loadMonth()
        }
    }

    private fun loadMonth(silent: Boolean = false) {
        monthJob?.cancel()
        monthJob = viewModelScope.launch {
            val month = raw.value.month
            if (!silent) raw.update { it.copy(loading = it.availability == null, error = null) }
            when (val result = groups.availability(groupId, month.atDay(1).toString(), month.atEndOfMonth().toString())) {
                is ApiResult.Success -> raw.update { s ->
                    s.copy(loading = false, availability = result.value, error = null, selected = defaultSelection(s, result.value))
                }
                is ApiResult.Failure -> raw.update { it.copy(loading = false, error = result.error.message) }
            }
        }
    }

    /** Keeps a day the user picked; otherwise the first day that works, then today, then the 1st. */
    private fun defaultSelection(s: RawState, availability: Availability): LocalDate {
        if (s.userPicked && YearMonth.from(s.selected) == s.month) return s.selected
        availability.days.firstOrNull { it.status == DayStatus.Match }?.date?.let(DateTimes::parseDate)?.let { return it }
        val today = DateTimes.today()
        return if (YearMonth.from(today) == s.month) today else s.month.atDay(1)
    }

    private fun loadForView() {
        detailJob?.cancel()
        val r = raw.value
        detailJob = when (r.view) {
            CalendarView.Month -> null
            CalendarView.Day -> viewModelScope.launch {
                raw.update { it.copy(dayLoading = true) }
                val day = groups.dayAvailability(groupId, r.selected.toString())
                raw.update { it.copy(dayLoading = false, day = day.getOrNull() ?: it.day.takeIf { d -> d?.date == r.selected.toString() }, error = (day as? ApiResult.Failure)?.error?.message) }
            }
            CalendarView.Week -> viewModelScope.launch {
                raw.update { it.copy(weekLoading = true) }
                val columns = AvailabilityUiMapper.columns(r.availability)
                val weekStart = DateTimes.startOfWeek(r.selected)
                val dates = columns.map { weekStart.plusDays(it.toLong()) }
                val results = dates.map { date -> async { groups.dayAvailability(groupId, date.toString()) } }.awaitAll()
                val failure = results.firstOrNull { it is ApiResult.Failure } as? ApiResult.Failure
                raw.update { it.copy(weekLoading = false, week = results.mapNotNull { res -> res.getOrNull() }, error = failure?.error?.message) }
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
            CalendarView.Day -> DateTimes.shortDay(r.selected)
        }
        val day = r.day?.takeIf { it.date == r.selected.toString() }
        return GroupCalendarUiState(
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
            monthRows = AvailabilityUiMapper.monthRows(r.month, r.availability, group, meId, r.selected, today),
            daysNote = AvailabilityUiMapper.daysNote(columns),
            detail = if (YearMonth.from(r.selected) == r.month) AvailabilityUiMapper.detail(r.selected, r.availability, group, meId, today) else null,
            syncBanner = AvailabilityUiMapper.syncBanner(r.availability, group, isAdmin),
            nudging = r.nudging,
            weekColumns = AvailabilityUiMapper.weekColumns(r.week, group, meId),
            weekLoading = r.weekLoading,
            dayTitle = DateTimes.shortDay(r.selected),
            dayNote = day?.let(AvailabilityUiMapper::dayNote),
            everyone = day?.let(AvailabilityUiMapper::everyoneBands).orEmpty(),
            timelineRows = day?.let { AvailabilityUiMapper.timelineRows(it, group, meId) }.orEmpty(),
            dayLoading = r.dayLoading,
            error = r.error,
            message = r.message,
        )
    }

    /** "13 – 15 November" → "13 – 15 Nov" to fit beside the segmented control. */
    private fun shortenMonth(label: String, dates: List<LocalDate>): String =
        if (dates.first().month == dates.last().month) "${dates.first().dayOfMonth} – ${DateTimes.dayMonth(dates.last())}" else label
}
