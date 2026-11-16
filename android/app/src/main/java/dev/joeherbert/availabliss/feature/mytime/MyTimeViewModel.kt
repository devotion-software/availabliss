package dev.joeherbert.availabliss.feature.mytime

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.joeherbert.availabliss.core.common.DateTimes
import dev.joeherbert.availabliss.core.common.TimeRangeDraft
import dev.joeherbert.availabliss.core.common.UserMessage
import dev.joeherbert.availabliss.core.data.AppPreferences
import dev.joeherbert.availabliss.core.data.BlocksRepository
import dev.joeherbert.availabliss.core.data.CalendarsRepository
import dev.joeherbert.availabliss.core.data.UserRepository
import dev.joeherbert.availabliss.core.live.LiveEvents
import dev.joeherbert.availabliss.core.live.LivePayload
import dev.joeherbert.availabliss.core.network.ApiResult
import dev.joeherbert.availabliss.core.network.model.BusyInterval
import dev.joeherbert.availabliss.core.network.model.ManualBlock
import dev.joeherbert.availabliss.core.network.model.Repeat
import dev.joeherbert.availabliss.core.network.model.wireName
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.YearMonth
import javax.inject.Inject

/** Days fetched per page as the agenda scrolls (each request stays well under the 120-day limit). */
private const val PAGE_DAYS = 28

/** The busy endpoint takes at most 120 days per call. */
private const val MAX_REQUEST_DAYS = 112

@HiltViewModel
class MyTimeViewModel @Inject constructor(
    private val blocksRepository: BlocksRepository,
    private val users: UserRepository,
    private val calendars: CalendarsRepository,
    private val prefs: AppPreferences,
    liveEvents: LiveEvents,
) : ViewModel() {

    /** Raw data the list is derived from. */
    private var intervals: List<BusyInterval> = emptyList()
    private var blocks: List<ManualBlock> = emptyList()

    /** Loaded days run from [start] to [loadedThrough] (inclusive); [wantedThrough] is how far they should reach. */
    private var start: LocalDate = DateTimes.today()
    private var loadedThrough: LocalDate? = null

    /** A few weeks, and at least the whole of this month and the next (the month view swipes ahead). */
    private var wantedThrough: LocalDate = maxOf(
        endOfWeek(start).plusDays(PAGE_DAYS - 7L),
        endOfWeek(YearMonth.from(start).plusMonths(1).atEndOfMonth()),
    )

    private val _state = MutableStateFlow(MyTimeUiState())
    val state: StateFlow<MyTimeUiState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var moreJob: Job? = null

    init {
        load()
        viewModelScope.launch {
            val saved = runCatching { prefs.myTimeView.first() }.getOrNull()
            MyTimeMode.entries.firstOrNull { it.name == saved }?.let { mode -> _state.update { it.copy(mode = mode) } }
        }
        viewModelScope.launch {
            liveEvents.payloads.filterIsInstance<LivePayload.AvailabilityChanged>().collect { event ->
                if (event.userId == users.user.value?.id) load(silent = true)
            }
        }
        viewModelScope.launch { liveEvents.reconnected.collect { load(silent = true) } }
        // "Connect a calendar" hint: only once we know for sure there are none.
        viewModelScope.launch {
            if (calendars.refresh() is ApiResult.Success) {
                calendars.connections.collect { list -> _state.update { it.copy(showCalendarHint = list.isEmpty()) } }
            }
        }
    }

    fun refresh() = load(silent = true)

    /** Pull to refresh. */
    fun pullRefresh() = load(silent = true, pulled = true)

    /** Reloads every loaded day (from today, which may have moved on since). */
    private fun load(silent: Boolean = false, pulled: Boolean = false) {
        loadJob?.cancel()
        moreJob?.cancel()
        val today = DateTimes.today()
        if (today.isAfter(start)) start = today
        val through = maxOf(loadedThrough ?: wantedThrough, start)
        loadJob = viewModelScope.launch {
            if (!silent) _state.update { it.copy(loading = true, error = null) }
            if (pulled) _state.update { it.copy(refreshing = true) }
            val blocksCall = async { blocksRepository.blocks() }
            val busy = fetch(start, through)
            val list = blocksCall.await()
            if (list is ApiResult.Success) blocks = list.value
            when (busy) {
                is ApiResult.Success -> {
                    intervals = busy.value
                    loadedThrough = through
                    _state.update { it.copy(loading = false, refreshing = false, loadingMore = false, error = null) }
                    publish()
                    loadMore()
                }
                is ApiResult.Failure -> {
                    _state.update { it.copy(loading = false, refreshing = false, loadingMore = false, error = busy.error.message) }
                    publish()
                }
            }
        }
    }

    /** Busy time for [from]..[to], in parallel requests under the endpoint's limit. */
    private suspend fun fetch(from: LocalDate, to: LocalDate): ApiResult<List<BusyInterval>> = coroutineScope {
        val results = dateChunks(from, to, MAX_REQUEST_DAYS)
            .map { range -> async { blocksRepository.busy(range.start, range.endInclusive) } }
            .awaitAll()
        results.firstOrNull { it is ApiResult.Failure }
            ?: ApiResult.Success(results.flatMap { (it as ApiResult.Success).value }.distinct())
    }

    // region Loading further days
    /** The list has scrolled near its end: load the next few weeks. */
    fun onNearEnd() {
        val last = loadedThrough ?: return
        if (moreJob?.isActive == true || loadJob?.isActive == true) return
        wantedThrough = maxOf(wantedThrough, last.plusDays(PAGE_DAYS.toLong()))
        loadMore()
    }

    /** Makes sure [date] (and the rest of its week) is loaded. */
    private fun ensureLoaded(date: LocalDate) {
        val last = endOfWeek(DateTimes.today().plusDays(MY_TIME_DAYS_AHEAD))
        wantedThrough = maxOf(wantedThrough, minOf(endOfWeek(date), last))
        if (loadJob?.isActive != true) loadMore()
    }

    /** Loads from just after [loadedThrough] up to [wantedThrough], a page at a time. */
    private fun loadMore() {
        if (moreJob?.isActive == true) return
        val from = loadedThrough?.plusDays(1) ?: return
        if (from.isAfter(wantedThrough)) return
        moreJob = viewModelScope.launch {
            _state.update { it.copy(loadingMore = true) }
            var next = from
            while (!next.isAfter(wantedThrough)) {
                val to = minOf(maxOf(wantedThrough, next.plusDays(PAGE_DAYS - 1L)), next.plusDays(MAX_REQUEST_DAYS - 1L))
                when (val busy = fetch(next, to)) {
                    is ApiResult.Success -> {
                        intervals = (intervals + busy.value).distinct()
                        loadedThrough = to
                        publish()
                    }
                    is ApiResult.Failure -> {
                        // Scrolling to the end again retries.
                        wantedThrough = loadedThrough ?: wantedThrough
                        showMessage(busy.error.message)
                        break
                    }
                }
                next = to.plusDays(1)
            }
            _state.update { it.copy(loadingMore = false) }
        }
    }
    // endregion

    // region Views: month / agenda, the month on screen, the week view
    /** Month or agenda; remembered on this device. */
    fun setMode(mode: MyTimeMode) {
        _state.update { it.copy(mode = mode) }
        viewModelScope.launch { runCatching { prefs.setMyTimeView(mode.name) } }
    }

    fun previousMonth() = showMonth(_state.value.month.minusMonths(1))

    fun nextMonth() = showMonth(_state.value.month.plusMonths(1))

    fun thisMonth() = showMonth(YearMonth.from(DateTimes.today()))

    /** Shows [month] (within today's month .. a year ahead), loading it and the month after. */
    private fun showMonth(month: YearMonth) {
        val range = monthRange(DateTimes.today())
        if (month < range.start || month > range.endInclusive) return
        _state.update { it.copy(month = month) }
        ensureLoaded(month.plusMonths(1).atEndOfMonth())
    }

    /** Opens the week view on [date] (today or later). */
    fun openDay(date: LocalDate) {
        val today = DateTimes.today()
        val day = date.coerceIn(today, today.plusDays(MY_TIME_DAYS_AHEAD))
        _state.update { it.copy(openDay = day) }
        ensureLoaded(day)
    }

    /** Back to the month or agenda, as they were. */
    fun closeDay() = _state.update { it.copy(openDay = null) }

    /** The timeline's swipe: the previous / next day, never before today or past a year ahead. */
    fun stepDay(days: Long) {
        val current = _state.value.openDay ?: return
        val today = DateTimes.today()
        steppedDay(current, days, today, today.plusDays(MY_TIME_DAYS_AHEAD))?.let(::openDay)
    }

    /** The week strip's swipe: the same weekday a week earlier / later (clamped to today). */
    fun stepWeek(weeks: Long) {
        val current = _state.value.openDay ?: return
        val today = DateTimes.today()
        steppedWeek(current, weeks, today, today.plusDays(MY_TIME_DAYS_AHEAD))?.let(::openDay)
    }
    // endregion

    // region Taps
    /** A tap on empty time in the day sheet opens the block sheet on that day and range. */
    fun onEmptyTapped(date: LocalDate, range: MinuteRange) {
        _state.update { it.copy(draft = BlockDraft.forRange(date, range), savingDraft = false) }
    }

    /** A tap on busy time: edit your own block, or explain synced calendar time. */
    fun onIntervalTapped(interval: BusyInterval) {
        when (val tap = resolveIntervalTap(interval, blocks)) {
            is IntervalTap.EditBlock -> openEditor(tap.block)
            is IntervalTap.CalendarBusy -> _state.update { it.copy(calendarBusy = tap.info) }
            IntervalTap.MissingBlock -> showMessage("That block was just removed.")
        }
    }

    fun closeCalendarBusy() = _state.update { it.copy(calendarBusy = null) }

    /** Marks the open calendar event not busy: just it, or ([series]) every occurrence. */
    fun ignoreCalendarBusy(series: Boolean) {
        val info = _state.value.calendarBusy ?: return
        val interval = info.interval ?: return
        if (info.working) return
        _state.update { it.copy(calendarBusy = info.copy(working = true)) }
        viewModelScope.launch {
            when (val result = blocksRepository.ignoreBusy(interval, series)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(calendarBusy = null) }
                    showMessage(if (series) "Not busy every time it repeats" else "Marked not busy")
                    refresh()
                }
                is ApiResult.Failure -> {
                    _state.update { it.copy(calendarBusy = info.copy(working = false)) }
                    showMessage(result.error.message)
                }
            }
        }
    }

    /** The open calendar event counts as busy again. */
    fun unignoreCalendarBusy() {
        val info = _state.value.calendarBusy ?: return
        val id = info.interval?.ignoreId ?: return
        if (info.working) return
        _state.update { it.copy(calendarBusy = info.copy(working = true)) }
        viewModelScope.launch {
            when (val result = blocksRepository.unignoreBusy(id)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(calendarBusy = null) }
                    showMessage("Counts as busy again")
                    refresh()
                }
                is ApiResult.Failure -> {
                    _state.update { it.copy(calendarBusy = info.copy(working = false)) }
                    showMessage(result.error.message)
                }
            }
        }
    }

    /** Optimistically removes [block] (every occurrence), restoring it on failure. */
    fun deleteBlock(block: ManualBlock) {
        val removedIntervals = intervals.filter { it.blockId == block.id }
        intervals = intervals - removedIntervals.toSet()
        blocks = blocks.filterNot { it.id == block.id }
        _state.update { if (it.draft?.editingId == block.id) it.copy(draft = null, savingDraft = false) else it }
        publish()
        viewModelScope.launch {
            when (val result = blocksRepository.delete(block.id)) {
                is ApiResult.Success -> if (block.repeat != Repeat.None) load(silent = true)
                is ApiResult.Failure -> {
                    intervals = intervals + removedIntervals
                    blocks = blocks + block
                    publish()
                    showMessage(result.error.message)
                }
            }
        }
    }

    fun deleteBlock(id: String) {
        blocks.firstOrNull { it.id == id }?.let(::deleteBlock)
    }
    // endregion

    // region Editing an existing block (same sheet as adding one)
    fun openEditor(id: String) {
        blocks.firstOrNull { it.id == id }?.let(::openEditor)
    }

    private fun openEditor(block: ManualBlock) {
        val draft = BlockDraft.fromBlock(block) ?: return showMessage("That block couldn't be opened.")
        _state.update { it.copy(draft = draft, savingDraft = false) }
    }

    /** Deletes the block open in the sheet (every occurrence of a repeat). */
    fun deleteEditingBlock() {
        _state.value.draft?.editingId?.let(::deleteBlock)
    }
    // endregion

    // region "Block a time" sheet
    /** "Something else…": the sheet as it comes, on today. */
    fun openBlockSheet() = openDraft(BlockDraft.on(DateTimes.today()))

    /** The week view's "+ Block": the sheet on the selected day. */
    fun openBlockSheetOn(date: LocalDate) = openDraft(BlockDraft.on(date))

    /** The agenda's free row: the sheet prefilled with the day's longest free stretch. */
    fun blockFree(date: LocalDate, summary: DaySummary) {
        blockDraftForFree(date, summary)?.let(::openDraft)
    }

    private fun openDraft(draft: BlockDraft) = _state.update { it.copy(draft = draft, savingDraft = false) }

    fun closeBlockSheet() = _state.update { it.copy(draft = null, savingDraft = false) }

    fun updateDraft(transform: (BlockDraft) -> BlockDraft) = _state.update { s -> s.copy(draft = s.draft?.let(transform)) }

    /** The date range input changed (start, end or all day). */
    fun onDraftRange(range: TimeRangeDraft) = updateDraft { it.withRange(range) }

    fun onDraftRepeat(repeat: Repeat) = updateDraft { it.withRepeat(repeat) }

    fun onDraftUntil(until: LocalDate?) = updateDraft { it.copy(until = until) }

    fun onDraftNote(note: String) = updateDraft { it.copy(note = note.take(120)) }

    fun saveDraft() {
        val draft = _state.value.draft ?: return
        if (!draft.isValid || _state.value.savingDraft) return
        _state.update { it.copy(savingDraft = true) }
        viewModelScope.launch {
            val request = draft.toRequest()
            val result = draft.editingId?.let { id ->
                blocksRepository.update(id) {
                    set("start", request.start)
                    set("end", request.end)
                    set("allDay", request.allDay)
                    // A repeat this version doesn't know is left as it is.
                    if (request.repeat != Repeat.Unknown) set("repeat", request.repeat.wireName)
                    set("until", request.until)
                    set("note", request.note)
                }
            } ?: blocksRepository.create(request)
            when (result) {
                is ApiResult.Success -> {
                    blocks = blocks.filterNot { it.id == result.value.id } + result.value
                    _state.update { it.copy(draft = null, savingDraft = false) }
                    val verb = if (draft.isEditing) "Updated" else "Blocked"
                    showMessage("$verb ${draft.label}")
                    load(silent = true)
                }
                is ApiResult.Failure -> {
                    _state.update { it.copy(savingDraft = false) }
                    showMessage(result.error.message)
                }
            }
        }
    }
    // endregion

    fun messageShown(message: UserMessage) = _state.update { if (it.message?.id == message.id) it.copy(message = null) else it }

    private fun showMessage(text: String) = _state.update { it.copy(message = UserMessage(text)) }

    /** Recomputes everything derived from the raw data. */
    private fun publish() {
        val today = DateTimes.today()
        val last = loadedThrough
        val days = if (last == null || last.isBefore(start)) emptyList()
        else (0..ChronoUnit.DAYS.between(start, last)).map(start::plusDays)
        val layout = layoutDays(days, intervals)
        val rows = days.map { day -> dayRow(day, layout.allDay[day].orEmpty(), layout.timed[day].orEmpty(), today) }
        _state.update { s ->
            s.copy(
                today = today,
                layout = layout,
                days = rows,
                rows = rows.associateBy { it.date },
                repeats = blocks.associate { it.id to it.repeat },
                recurring = blocks
                    .filter { it.repeat != Repeat.None }
                    .sortedBy { it.start }
                    .map { RecurringBlockUi(it.id, it.title(), it.scheduleLabel()) },
            )
        }
    }
}
