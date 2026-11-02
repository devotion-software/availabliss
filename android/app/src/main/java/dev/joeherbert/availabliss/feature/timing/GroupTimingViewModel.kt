package dev.joeherbert.availabliss.feature.timing

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.joeherbert.availabliss.core.common.DateTimes
import dev.joeherbert.availabliss.core.common.UserMessage
import dev.joeherbert.availabliss.core.data.GroupsRepository
import dev.joeherbert.availabliss.core.data.RegionsRepository
import dev.joeherbert.availabliss.core.data.UserRepository
import dev.joeherbert.availabliss.core.network.ApiError
import dev.joeherbert.availabliss.core.network.ApiResult
import dev.joeherbert.availabliss.core.network.getOrNull
import dev.joeherbert.availabliss.core.network.model.DateRange
import dev.joeherbert.availabliss.core.network.model.RequirementLength
import dev.joeherbert.availabliss.core.network.model.TimeWindow
import dev.joeherbert.availabliss.navigation.GroupRoute
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

/** One end of a span ("Fri from 18:00"). */
data class SpanEnd(val day: Int, val time: LocalTime)

data class GroupTimingUiState(
    val loading: Boolean = true,
    val groupName: String = "",
    val canEdit: Boolean = false,
    val draft: TimingDraft? = null,
    val startsLabel: String = "",
    val endsLabel: String = "",
    val summaryTitle: String = "",
    val summarySubtitle: String = "",
    /** Times shown when opening the start / end pickers. */
    val startTime: LocalTime = LocalTime.of(8, 0),
    val endTime: LocalTime = LocalTime.of(23, 0),
    val spanStart: SpanEnd = SpanEnd(4, LocalTime.of(18, 0)),
    val spanEnd: SpanEnd = SpanEnd(6, LocalTime.of(23, 59)),
    /** Weekdays highlighted in the toggle row (span coverage for spans). */
    val highlightedDays: Set<Int> = emptySet(),
    val rangeFrom: LocalDate = DateTimes.today(),
    val rangeTo: LocalDate = DateTimes.today().plusMonths(3),
    val bankSubtitle: String = "Group owner's region · treat like a weekend day",
    val schoolSubtitle: String = "Your school authority's term dates",
    val dirty: Boolean = false,
    val saving: Boolean = false,
    /** Set after a successful save; the screen navigates and calls [GroupTimingViewModel.savedHandled]. */
    val saved: Boolean = false,
    val error: String? = null,
    val message: UserMessage? = null,
)

/** Admin editor for the group's [dev.joeherbert.availabliss.core.network.model.Requirement]. */
@HiltViewModel
class GroupTimingViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val groups: GroupsRepository,
    private val users: UserRepository,
    private val regions: RegionsRepository,
) : ViewModel() {

    private val groupId = savedStateHandle.toRoute<GroupRoute>().groupId
    private val _uiState = MutableStateFlow(GroupTimingUiState())
    val uiState: StateFlow<GroupTimingUiState> = _uiState.asStateFlow()

    private var original: TimingDraft? = null
    private var authorityName: String? = null
    private var previewJob: Job? = null

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            val group = groups.cachedGroup(groupId) ?: when (val result = groups.refreshGroup(groupId)) {
                is ApiResult.Success -> result.value
                is ApiResult.Failure -> {
                    _uiState.update { it.copy(loading = false, error = result.error.message) }
                    return@launch
                }
            }
            val draft = RequirementPresets.fromRequirement(group.requirement)
            original = draft
            _uiState.update { it.copy(loading = false, groupName = group.name, canEdit = group.role.isAdmin, error = null) }
            publish(draft)
            loadAuthorityName(group.requirement.schoolAuthority)
        }
    }

    private suspend fun loadAuthorityName(groupAuthority: String?) {
        val user = users.user.value ?: users.user.first { it != null } ?: return
        val country = user.country ?: return
        val id = groupAuthority ?: user.schoolAuthority ?: return
        authorityName = regions.schoolAuthorities(country).getOrNull()?.firstOrNull { it.id == id }?.name
        _uiState.value.draft?.let(::publish)
    }

    // region Intents
    fun selectPreset(preset: TimingPreset) = edit { RequirementPresets.applyPreset(it, preset) }

    fun selectCustomKind(kind: CustomKind) = edit { RequirementPresets.applyCustomKind(it, kind) }

    fun changeDuration(deltaMinutes: Int) = edit { d ->
        val l = d.length as? RequirementLength.Duration ?: return@edit d
        RequirementPresets.withLength(d, RequirementLength.Duration((l.minutes + deltaMinutes).coerceIn(30, 12 * 60)))
    }

    fun changeDayCount(delta: Int) = edit { d ->
        val l = d.length as? RequirementLength.Days ?: return@edit d
        RequirementPresets.withLength(d, RequirementLength.Days((l.count + delta).coerceIn(1, 31)))
    }

    fun setWeeksLater(following: Boolean) = edit { d ->
        val l = d.length as? RequirementLength.Span ?: return@edit d
        RequirementPresets.withLength(d, l.copy(weeksLater = if (following) 1 else 0), null)
    }

    fun toggleDay(day: Int) = edit { d ->
        if (d.isSpan) d else d.copy(days = if (day in d.days) d.days - day else d.days + day)
    }

    fun setBankHolidays(on: Boolean) = edit { it.copy(bankHolidays = on) }

    fun setSchoolHolidaysOnly(on: Boolean) = edit { d ->
        d.copy(schoolHolidaysOnly = on, schoolAuthority = d.schoolAuthority ?: users.user.value?.schoolAuthority)
    }

    /** Start of the daily window (duration / days lengths). */
    fun setStartTime(time: LocalTime) = edit { d ->
        val current = d.window ?: TimeWindow(DateTimes.toWireTime(time), END_OF_DAY)
        RequirementPresets.withLength(d, d.length, current.copy(from = DateTimes.toWireTime(time)))
    }

    /** End of the daily window (duration / days lengths). */
    fun setEndTime(time: LocalTime) = edit { d ->
        val current = d.window ?: TimeWindow("00:00", DateTimes.toWireTime(time))
        RequirementPresets.withLength(d, d.length, current.copy(to = DateTimes.toWireTime(time)))
    }

    fun setSpanStart(end: SpanEnd) = edit { d ->
        val l = d.length as? RequirementLength.Span ?: return@edit d
        RequirementPresets.withLength(d, l.copy(startDay = end.day, startTime = DateTimes.toWireTime(end.time)), null)
    }

    fun setSpanEnd(end: SpanEnd) = edit { d ->
        val l = d.length as? RequirementLength.Span ?: return@edit d
        RequirementPresets.withLength(d, l.copy(endDay = end.day, endTime = DateTimes.toWireTime(end.time)), null)
    }

    fun setRangeFrom(date: LocalDate) = edit { d ->
        val to = DateTimes.parseDate(d.range.to)?.takeIf { !it.isBefore(date) } ?: date.plusMonths(3)
        d.copy(range = DateRange(date.toString(), to.toString()))
    }

    fun setRangeTo(date: LocalDate) = edit { d ->
        val from = DateTimes.parseDate(d.range.from)?.takeIf { !it.isAfter(date) } ?: date
        d.copy(range = DateRange(from.toString(), date.toString()))
    }

    fun addRange(list: RangeList, from: LocalDate, to: LocalDate) = edit { d ->
        val range = DateRange(minOf(from, to).toString(), maxOf(from, to).toString())
        val current = list.of(d)
        if (range in current) d else list.with(d, (current + range).sortedBy { it.from })
    }

    fun removeRange(list: RangeList, range: DateRange) = edit { d -> list.with(d, list.of(d) - range) }

    fun save() {
        val state = _uiState.value
        val draft = state.draft ?: return
        if (!state.canEdit || state.saving) return
        validate(draft)?.let { problem ->
            _uiState.update { it.copy(message = UserMessage(problem)) }
            return
        }
        _uiState.update { it.copy(saving = true) }
        viewModelScope.launch {
            when (val result = groups.update(groupId, requirement = draft.toRequirement())) {
                is ApiResult.Success -> {
                    original = RequirementPresets.fromRequirement(result.value.requirement)
                    _uiState.update { it.copy(saving = false, dirty = false, saved = true) }
                }
                is ApiResult.Failure -> {
                    val text = (result.error as? ApiError.Validation)?.issues?.firstOrNull()?.message ?: result.error.message
                    _uiState.update { it.copy(saving = false, message = UserMessage(text)) }
                }
            }
        }
    }

    fun savedHandled() = _uiState.update { it.copy(saved = false) }

    fun messageShown(message: UserMessage) = _uiState.update { if (it.message?.id == message.id) it.copy(message = null) else it }
    // endregion

    private fun validate(draft: TimingDraft): String? {
        val w = draft.window
        if (!draft.isSpan && w != null && w.from >= w.to) return "The end time needs to be after the start time."
        if (draft.range.from > draft.range.to) return "The search range ends before it starts."
        return null
    }

    private inline fun edit(transform: (TimingDraft) -> TimingDraft) {
        val state = _uiState.value
        if (!state.canEdit) return
        val draft = state.draft ?: return
        publish(transform(draft))
    }

    private fun publish(draft: TimingDraft) {
        val window = draft.window
        val span = draft.length as? RequirementLength.Span
        _uiState.update {
            it.copy(
                draft = draft,
                startsLabel = TimingLabels.starts(draft),
                endsLabel = TimingLabels.ends(draft),
                summaryTitle = TimingLabels.title(draft),
                summarySubtitle = TimingLabels.searchRange(draft),
                startTime = window?.from?.let(DateTimes::parseTime) ?: defaultStart(draft),
                endTime = window?.to?.let(DateTimes::parseTime) ?: LocalTime.of(23, 0),
                spanStart = span?.let { s -> SpanEnd(s.startDay, DateTimes.parseTime(s.startTime) ?: LocalTime.MIDNIGHT) } ?: it.spanStart,
                spanEnd = span?.let { s -> SpanEnd(s.endDay, DateTimes.parseTime(s.endTime) ?: LocalTime.of(23, 59)) } ?: it.spanEnd,
                highlightedDays = span?.let(RequirementPresets::spanDays) ?: draft.days,
                rangeFrom = DateTimes.parseDate(draft.range.from) ?: DateTimes.today(),
                rangeTo = DateTimes.parseDate(draft.range.to) ?: DateTimes.today().plusMonths(3),
                schoolSubtitle = authorityName?.let { name -> "$name term dates" } ?: "Your school authority's term dates",
                dirty = draft != original,
            )
        }
        schedulePreview(draft)
    }

    /**
     * Debounced `POST /requirements/preview` for the sticky bar: "13 weekends / fit this
     * timing between 1 Nov and 31 Jan". Falls back to the local summary on failure.
     */
    private fun schedulePreview(draft: TimingDraft) {
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            delay(PREVIEW_DEBOUNCE_MS)
            if (validate(draft) != null) return@launch
            val requirement = draft.toRequirement()
            val preview = groups.previewRequirement(requirement).getOrNull() ?: return@launch
            val from = DateTimes.parseDate(preview.from)
            val to = DateTimes.parseDate(preview.to)
            val noun = requirement.slotNoun(plural = preview.count != 1)
            _uiState.update {
                if (it.draft != draft) it else it.copy(
                    summaryTitle = "${preview.count} $noun",
                    summarySubtitle = if (from != null && to != null) {
                        "fit this timing between ${DateTimes.dayMonth(from)} and ${DateTimes.dayMonth(to)}"
                    } else {
                        preview.summary
                    },
                )
            }
        }
    }

    private fun defaultStart(draft: TimingDraft): LocalTime =
        if (draft.length is RequirementLength.Duration) LocalTime.of(8, 0) else LocalTime.of(9, 0)

    private companion object {
        const val END_OF_DAY = "23:59"
        const val PREVIEW_DEBOUNCE_MS = 400L
    }
}

/** The editable lists of date ranges on a timing draft. */
enum class RangeList {
    /** "Or only these dates": the only dates the group considers. */
    Dates,

    /** The group's own school holiday dates, used with "School holidays only". */
    SchoolHolidays;

    fun of(draft: TimingDraft): List<DateRange> = when (this) {
        Dates -> draft.dates
        SchoolHolidays -> draft.customSchoolHolidays
    }

    fun with(draft: TimingDraft, ranges: List<DateRange>): TimingDraft = when (this) {
        Dates -> draft.copy(dates = ranges)
        SchoolHolidays -> draft.copy(customSchoolHolidays = ranges)
    }
}
