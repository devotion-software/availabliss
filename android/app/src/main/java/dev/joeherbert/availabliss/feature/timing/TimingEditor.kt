package dev.joeherbert.availabliss.feature.timing

import dev.joeherbert.availabliss.core.common.DateTimes
import dev.joeherbert.availabliss.core.data.GroupsRepository
import dev.joeherbert.availabliss.core.data.RegionsRepository
import dev.joeherbert.availabliss.core.data.UserRepository
import dev.joeherbert.availabliss.core.network.getOrNull
import dev.joeherbert.availabliss.core.network.model.DateRange
import dev.joeherbert.availabliss.core.network.model.RequirementLength
import dev.joeherbert.availabliss.core.network.model.TimeWindow
import kotlinx.coroutines.CoroutineScope
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

/** One end of a span ("Fri from 18:00"). */
data class SpanEnd(val day: Int, val time: LocalTime)

/** Everything the timing editor sections show for one [draft]. */
data class TimingEditorUiState(
    val draft: TimingDraft,
    val startsLabel: String = "",
    val endsLabel: String = "",
    /** Sticky bar: "13 weekends" (live count), or the local title until it arrives. */
    val summaryTitle: String = "",
    /** "fit this timing between 2 Nov and 1 Feb", or the search range. */
    val summarySubtitle: String = "",
    /** A live count is being fetched. */
    val counting: Boolean = false,
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
)

/**
 * The timing editor's state and intents, shared by the group Timing tab and the
 * New group flow. Owned by a ViewModel (pass its scope). Keeps the labels and the
 * debounced live count (`POST /requirements/preview`) in step with the draft.
 */
class TimingEditor(
    private val scope: CoroutineScope,
    private val groups: GroupsRepository,
    private val users: UserRepository,
    private val regions: RegionsRepository,
) {
    private val _state = MutableStateFlow<TimingEditorUiState?>(null)
    val state: StateFlow<TimingEditorUiState?> = _state.asStateFlow()

    /** False makes every intent a no-op (non-admins on the Timing tab). */
    var canEdit: Boolean = true

    val draft: TimingDraft? get() = _state.value?.draft

    private var authorityName: String? = null
    private var previewJob: Job? = null

    /** Starts editing [draft] (e.g. a loaded group's timing, or a preset for a new group). */
    fun reset(draft: TimingDraft, schoolAuthority: String? = draft.schoolAuthority) {
        publish(draft)
        scope.launch { loadAuthorityName(schoolAuthority) }
    }

    private suspend fun loadAuthorityName(groupAuthority: String?) {
        val user = users.user.value ?: users.user.first { it != null } ?: return
        val country = user.country ?: return
        val id = groupAuthority ?: user.schoolAuthority ?: return
        authorityName = regions.schoolAuthorities(country).getOrNull()?.firstOrNull { it.id == id }?.name
        _state.value?.draft?.let(::publish)
    }

    // region Intents
    fun selectPreset(preset: TimingPreset) = edit { RequirementPresets.applyPreset(it, preset) }

    fun selectCustomKind(kind: CustomKind) = edit { RequirementPresets.applyCustomKind(it, kind) }

    fun changeDuration(deltaMinutes: Int) = edit { RequirementPresets.changeDuration(it, deltaMinutes) }

    fun changeDayCount(delta: Int) = edit { RequirementPresets.changeDayCount(it, delta) }

    fun setWeeksLater(following: Boolean) = edit { RequirementPresets.setWeeksLater(it, following) }

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

    fun setRange(from: LocalDate, to: LocalDate) = edit { d ->
        d.copy(range = DateRange(minOf(from, to).toString(), maxOf(from, to).toString()))
    }

    /** Adds a range, joining it with any it overlaps or runs straight on from. One already covered adds nothing. */
    fun addRange(list: RangeList, from: LocalDate, to: LocalDate) = edit { d ->
        val range = DateRange(minOf(from, to).toString(), maxOf(from, to).toString())
        val current = list.of(d)
        if (DateRanges.covering(range, current) != null) d else list.with(d, DateRanges.merging(current + range))
    }

    fun removeRange(list: RangeList, range: DateRange) = edit { d -> list.with(d, list.of(d) - range) }

    /** The editor's intents as [TimingActions] (screen-level callbacks are added by the caller). */
    fun actions(onBack: () -> Unit = {}, onRetry: () -> Unit = {}, onSave: () -> Unit = {}) = TimingActions(
        onBack = onBack,
        onRetry = onRetry,
        onPreset = ::selectPreset,
        onCustomKind = ::selectCustomKind,
        onDuration = ::changeDuration,
        onDayCount = ::changeDayCount,
        onWeeksLater = ::setWeeksLater,
        onToggleDay = ::toggleDay,
        onBankHolidays = ::setBankHolidays,
        onSchoolHolidays = ::setSchoolHolidaysOnly,
        onStartTime = ::setStartTime,
        onEndTime = ::setEndTime,
        onSpanStart = ::setSpanStart,
        onSpanEnd = ::setSpanEnd,
        onRange = ::setRange,
        onAddRange = ::addRange,
        onRemoveRange = ::removeRange,
        onSave = onSave,
    )
    // endregion

    private inline fun edit(transform: (TimingDraft) -> TimingDraft) {
        if (!canEdit) return
        val draft = _state.value?.draft ?: return
        publish(transform(draft))
    }

    private fun publish(draft: TimingDraft) {
        val window = draft.window
        val span = draft.length as? RequirementLength.Span
        _state.update { old ->
            val base = old ?: TimingEditorUiState(draft)
            base.copy(
                draft = draft,
                startsLabel = TimingLabels.starts(draft),
                endsLabel = TimingLabels.ends(draft),
                summaryTitle = TimingLabels.title(draft),
                summarySubtitle = TimingLabels.searchRange(draft),
                counting = validate(draft) == null,
                startTime = window?.from?.let(DateTimes::parseTime) ?: defaultStart(draft),
                endTime = window?.to?.let(DateTimes::parseTime) ?: LocalTime.of(23, 0),
                spanStart = span?.let { s -> SpanEnd(s.startDay, DateTimes.parseTime(s.startTime) ?: LocalTime.MIDNIGHT) } ?: base.spanStart,
                spanEnd = span?.let { s -> SpanEnd(s.endDay, DateTimes.parseTime(s.endTime) ?: LocalTime.of(23, 59)) } ?: base.spanEnd,
                highlightedDays = span?.let(RequirementPresets::spanDays) ?: draft.days,
                rangeFrom = DateTimes.parseDate(draft.range.from) ?: DateTimes.today(),
                rangeTo = DateTimes.parseDate(draft.range.to) ?: DateTimes.today().plusMonths(3),
                schoolSubtitle = authorityName?.let { name -> "$name term dates" } ?: "Your school authority's term dates",
            )
        }
        schedulePreview(draft)
    }

    /**
     * Debounced `POST /requirements/preview`: "13 weekends / fit this timing between
     * 1 Nov and 31 Jan". Keeps the local summary on failure.
     */
    private fun schedulePreview(draft: TimingDraft) {
        previewJob?.cancel()
        if (validate(draft) != null) return
        previewJob = scope.launch {
            delay(PREVIEW_DEBOUNCE_MS)
            val requirement = draft.toRequirement()
            val preview = groups.previewRequirement(requirement).getOrNull()
            _state.update { s ->
                when {
                    s == null || s.draft != draft -> s
                    preview == null -> s.copy(counting = false)
                    else -> {
                        val from = DateTimes.parseDate(preview.from)
                        val to = DateTimes.parseDate(preview.to)
                        s.copy(
                            counting = false,
                            summaryTitle = "${preview.count} ${requirement.slotNoun(plural = preview.count != 1)}",
                            summarySubtitle = if (from != null && to != null) {
                                "fit this timing between ${DateTimes.dayMonth(from)} and ${DateTimes.dayMonth(to)}"
                            } else {
                                preview.summary
                            },
                        )
                    }
                }
            }
        }
    }

    private fun defaultStart(draft: TimingDraft): LocalTime =
        if (draft.length is RequirementLength.Duration) LocalTime.of(8, 0) else LocalTime.of(9, 0)

    companion object {
        private const val END_OF_DAY = "23:59"
        private const val PREVIEW_DEBOUNCE_MS = 350L

        /** A problem that stops the timing being saved, or null. */
        fun validate(draft: TimingDraft): String? {
            val w = draft.window
            if (!draft.isSpan && w != null && w.from >= w.to) return "The end time needs to be after the start time."
            if (draft.range.from > draft.range.to) return "The search range ends before it starts."
            return null
        }
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

/** Keeps a list of date ranges free of repeats: no range inside another, and no two that overlap or touch. */
object DateRanges {
    /** The range in [ranges] that already holds every day of [range], if any (an identical one included). */
    fun covering(range: DateRange, ranges: List<DateRange>): DateRange? =
        ranges.firstOrNull { it.from <= range.from && range.to <= it.to }

    /** The ranges in [ranges] that [range] overlaps or runs straight on from, so would be joined with it. */
    fun joining(range: DateRange, ranges: List<DateRange>): List<DateRange> =
        ranges.filter { dayAfter(it.to) >= range.from && dayAfter(range.to) >= it.from }

    /** Sorted, with ranges that overlap or touch (one ends the day before the next starts) joined into one. */
    fun merging(ranges: List<DateRange>): List<DateRange> {
        val merged = mutableListOf<DateRange>()
        for (range in ranges.sortedBy { it.from }) {
            val last = merged.lastOrNull()
            if (last != null && range.from <= dayAfter(last.to)) {
                merged[merged.lastIndex] = last.copy(to = maxOf(last.to, range.to))
            } else {
                merged += range
            }
        }
        return merged
    }

    /** What to tell someone picking [range]: why it can't be added, or what it'll be joined with. */
    fun note(range: DateRange, ranges: List<DateRange>): String? {
        covering(range, ranges)?.let { return if (it == range) "You\u2019ve already added these dates." else "Already included in ${label(it)}." }
        val joining = joining(range, ranges)
        return when (joining.size) {
            0 -> null
            1 -> "Joins up with ${label(joining[0])}, so they\u2019ll become one range."
            else -> "Joins up with ${joining.size} of your ranges, so they\u2019ll become one."
        }
    }

    private fun label(range: DateRange) = TimingLabels.dateRange(range.from, range.to)

    /** ISO dates compare as strings, so only the day after needs real date maths. */
    private fun dayAfter(date: String) = DateTimes.parseDate(date)?.plusDays(1)?.toString() ?: date
}
