package dev.joeherbert.availabliss.feature.mytime

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.joeherbert.availabliss.core.common.DateTimes
import dev.joeherbert.availabliss.core.common.UserMessage
import dev.joeherbert.availabliss.core.data.BlocksRepository
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

@HiltViewModel
class MyTimeViewModel @Inject constructor(
    private val blocksRepository: BlocksRepository,
    private val users: UserRepository,
    liveEvents: LiveEvents,
) : ViewModel() {

    /** Raw data the grid is derived from. */
    private var intervals: List<BusyInterval> = emptyList()
    private var blocks: List<ManualBlock> = emptyList()

    private val _state = MutableStateFlow(initialState(DateTimes.startOfWeek(DateTimes.today())))
    val state: StateFlow<MyTimeUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        load()
        viewModelScope.launch {
            liveEvents.payloads.filterIsInstance<LivePayload.AvailabilityChanged>().collect { event ->
                if (event.userId == users.user.value?.id) load(silent = true)
            }
        }
        viewModelScope.launch { liveEvents.reconnected.collect { load(silent = true) } }
    }

    // region Week navigation
    fun previousWeek() = showWeek(_state.value.weekStart.minusWeeks(1))

    fun nextWeek() = showWeek(_state.value.weekStart.plusWeeks(1))

    private fun showWeek(start: LocalDate) {
        _state.update { initialState(start).copy(recurring = it.recurring) }
        intervals = emptyList()
        load()
    }
    // endregion

    fun refresh() = load(silent = true)

    private fun load(silent: Boolean = false) {
        loadJob?.cancel()
        val weekStart = _state.value.weekStart
        loadJob = viewModelScope.launch {
            if (!silent) _state.update { it.copy(loading = true, error = null) }
            val busyCall = async { blocksRepository.busy(weekStart, weekStart.plusDays(6)) }
            val blocksCall = async { blocksRepository.blocks() }
            val busy = busyCall.await()
            val list = blocksCall.await()
            if (list is ApiResult.Success) blocks = list.value
            when (busy) {
                is ApiResult.Success -> {
                    intervals = busy.value
                    _state.update { it.copy(loading = false, error = null) }
                }
                is ApiResult.Failure -> _state.update { it.copy(loading = false, error = busy.error.message) }
            }
            publish()
        }
    }

    // region Grid taps
    /** A tap on empty time opens the sheet on that day and range. */
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
    fun openBlockSheet() {
        val today = DateTimes.today()
        val weekStart = _state.value.weekStart
        val date = if (today in weekStart..weekStart.plusDays(6)) today else weekStart
        _state.update { it.copy(draft = BlockDraft(date = date)) }
    }

    fun closeBlockSheet() = _state.update { it.copy(draft = null, savingDraft = false) }

    fun updateDraft(transform: (BlockDraft) -> BlockDraft) = _state.update { s -> s.copy(draft = s.draft?.let(transform)) }

    fun onDraftDate(date: LocalDate) = updateDraft { it.copy(date = date, until = it.until?.takeIf { u -> !u.isBefore(date) }) }

    fun onDraftWhen(option: BlockWhen) = updateDraft { it.copy(whenOption = option) }

    fun onDraftStart(time: LocalTime) = updateDraft { it.withStart(time) }

    fun onDraftEnd(time: LocalTime) = updateDraft { it.copy(whenOption = BlockWhen.Times, end = time) }

    fun onDraftRepeat(repeat: Repeat) = updateDraft { it.copy(repeat = repeat, until = if (repeat == Repeat.None) null else it.until) }

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
                    set("repeat", request.repeat.wireName)
                    set("until", request.until)
                    set("note", request.note)
                }
            } ?: blocksRepository.create(request)
            when (result) {
                is ApiResult.Success -> {
                    blocks = blocks.filterNot { it.id == result.value.id } + result.value
                    _state.update { it.copy(draft = null, savingDraft = false) }
                    val verb = if (draft.isEditing) "Updated" else "Blocked"
                    showMessage("$verb ${DateTimes.shortDay(draft.date)} · ${draft.timeLabel}")
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
        _state.update { s ->
            s.copy(
                today = DateTimes.today(),
                layout = layoutWeek(s.weekStart, intervals),
                blockedCount = blockedOccurrences(intervals),
                recurring = blocks
                    .filter { it.repeat != Repeat.None }
                    .sortedBy { it.start }
                    .map { RecurringBlockUi(it.id, it.title(), it.scheduleLabel()) },
            )
        }
    }

    private fun initialState(weekStart: LocalDate) = MyTimeUiState(
        weekStart = weekStart,
        weekLabel = DateTimes.weekRange(weekStart, weekStart.plusDays(6)),
    )
}
