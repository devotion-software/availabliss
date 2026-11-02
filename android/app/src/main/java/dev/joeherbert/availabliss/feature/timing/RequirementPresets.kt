package dev.joeherbert.availabliss.feature.timing

import dev.joeherbert.availabliss.core.network.model.DateRange
import dev.joeherbert.availabliss.core.network.model.Requirement
import dev.joeherbert.availabliss.core.network.model.RequirementLength
import dev.joeherbert.availabliss.core.network.model.TimeWindow
import java.time.LocalDate

/** The "How long?" chips on the timing editor. */
enum class TimingPreset(val label: String) {
    TwoHours("2 hours"),
    Evening("An evening"),
    OneDay("One day"),
    Weekend("A weekend"),
    Week("A week"),
    Custom("Custom"),
}

/** What a Custom length is made of. */
enum class CustomKind(val label: String) { Hours("Hours"), Days("Days"), Span("Span") }

/**
 * Editable form of a [Requirement]. Pure and side-effect free so the preset ⇄
 * Requirement mapping (API.md "Requirements" table) is unit tested.
 */
data class TimingDraft(
    val preset: TimingPreset,
    val customKind: CustomKind,
    val length: RequirementLength,
    val window: TimeWindow?,
    /** 0 = Monday. Empty = any day. */
    val days: Set<Int>,
    val bankHolidays: Boolean,
    val schoolHolidaysOnly: Boolean,
    val schoolAuthority: String?,
    val customSchoolHolidays: List<DateRange>,
    val range: DateRange,
    val dates: List<DateRange>,
    val timezone: String,
) {
    val isSpan: Boolean get() = length is RequirementLength.Span

    fun toRequirement(): Requirement = Requirement(
        length = length,
        // Spans carry their own times; the window is meaningless for them.
        window = if (isSpan) null else window,
        // `days` is ignored for spans; send the days the span covers for display parity.
        days = (if (length is RequirementLength.Span) RequirementPresets.spanDays(length) else days).sorted(),
        bankHolidays = bankHolidays,
        schoolHolidaysOnly = schoolHolidaysOnly,
        schoolAuthority = schoolAuthority,
        customSchoolHolidays = customSchoolHolidays,
        range = range,
        dates = dates,
        timezone = timezone,
    )
}

object RequirementPresets {
    const val TWO_HOURS = 120
    val DEFAULT_DURATION_WINDOW = TimeWindow("08:00", "23:00")
    val EVENING_WINDOW = TimeWindow("18:00", "23:59")
    val WEEKEND = RequirementLength.Span(startDay = 4, startTime = "18:00", endDay = 6, endTime = "23:59", weeksLater = 0)
    /** Default custom span: Tue → Thu. */
    val TUE_TO_THU = RequirementLength.Span(startDay = 1, startTime = "00:00", endDay = 3, endTime = "23:59", weeksLater = 0)

    /** Length + window for a preset, exactly per the API.md table. */
    fun lengthAndWindow(preset: TimingPreset, current: TimingDraft? = null): Pair<RequirementLength, TimeWindow?> = when (preset) {
        TimingPreset.TwoHours -> RequirementLength.Duration(TWO_HOURS) to
            (current?.window?.takeIf { current.length is RequirementLength.Duration } ?: DEFAULT_DURATION_WINDOW)
        TimingPreset.Evening -> RequirementLength.Days(1) to EVENING_WINDOW
        TimingPreset.OneDay -> RequirementLength.Days(1) to null
        TimingPreset.Weekend -> WEEKEND to null
        TimingPreset.Week -> RequirementLength.Days(7) to null
        TimingPreset.Custom -> (current?.length ?: RequirementLength.Duration(180)) to current?.window
    }

    /** Detects which chip an existing length + window corresponds to. */
    fun detect(length: RequirementLength, window: TimeWindow?): TimingPreset = when (length) {
        is RequirementLength.Duration -> if (length.minutes == TWO_HOURS) TimingPreset.TwoHours else TimingPreset.Custom
        is RequirementLength.Days -> when {
            length.count == 1 && window == EVENING_WINDOW -> TimingPreset.Evening
            length.count == 1 && window == null -> TimingPreset.OneDay
            length.count == 7 && window == null -> TimingPreset.Week
            else -> TimingPreset.Custom
        }
        is RequirementLength.Span -> if (length == WEEKEND) TimingPreset.Weekend else TimingPreset.Custom
    }

    fun customKindOf(length: RequirementLength): CustomKind = when (length) {
        is RequirementLength.Duration -> CustomKind.Hours
        is RequirementLength.Days -> CustomKind.Days
        is RequirementLength.Span -> CustomKind.Span
    }

    fun fromRequirement(requirement: Requirement): TimingDraft = TimingDraft(
        preset = detect(requirement.length, requirement.window),
        customKind = customKindOf(requirement.length),
        length = requirement.length,
        window = requirement.window,
        days = requirement.days.filter { it in 0..6 }.toSet(),
        bankHolidays = requirement.bankHolidays,
        schoolHolidaysOnly = requirement.schoolHolidaysOnly,
        schoolAuthority = requirement.schoolAuthority,
        customSchoolHolidays = requirement.customSchoolHolidays,
        range = requirement.range,
        dates = requirement.dates,
        timezone = requirement.timezone,
    )

    val ALL_DAYS: Set<Int> = (0..6).toSet()

    /** Applies a chip. Weekday choice and everything else is kept, unless the run is longer than the days allowed. */
    fun applyPreset(draft: TimingDraft, preset: TimingPreset): TimingDraft {
        if (preset == TimingPreset.Custom) return draft.copy(preset = TimingPreset.Custom, customKind = customKindOf(draft.length))
        val (length, window) = lengthAndWindow(preset, draft)
        // "A week" (7 days in a row) can never fit in Fri–Sun: open up every day when the run is longer than the days allowed.
        val days = if (length is RequirementLength.Days && draft.days.isNotEmpty() && length.count > draft.days.size) ALL_DAYS else draft.days
        return draft.copy(preset = preset, customKind = customKindOf(length), length = length, window = window, days = days)
    }

    /** Switches the Custom editor between hours / days / span, with sensible starting values. */
    fun applyCustomKind(draft: TimingDraft, kind: CustomKind): TimingDraft {
        val length = when (kind) {
            CustomKind.Hours -> draft.length as? RequirementLength.Duration ?: RequirementLength.Duration(180)
            CustomKind.Days -> draft.length as? RequirementLength.Days ?: RequirementLength.Days(2)
            CustomKind.Span -> draft.length as? RequirementLength.Span ?: TUE_TO_THU
        }
        val window = when (kind) {
            CustomKind.Hours -> draft.window ?: DEFAULT_DURATION_WINDOW
            CustomKind.Days -> if (draft.length is RequirementLength.Days) draft.window else null
            CustomKind.Span -> null
        }
        return draft.copy(preset = TimingPreset.Custom, customKind = kind, length = length, window = window)
    }

    /** Re-detects the chip after an edit (e.g. changing the evening's start makes it Custom). */
    fun withLength(draft: TimingDraft, length: RequirementLength, window: TimeWindow? = draft.window): TimingDraft {
        val detected = detect(length, window)
        return draft.copy(length = length, window = window, preset = detected, customKind = customKindOf(length))
    }

    /** Weekdays a span covers, wrapping past Sunday. */
    fun spanDays(span: RequirementLength.Span): Set<Int> {
        if (span.weeksLater == 1) return (0..6).toSet()
        val result = mutableSetOf<Int>()
        var d = span.startDay
        while (true) {
            result += d
            if (d == span.endDay) break
            d = (d + 1) % 7
        }
        return result
    }

    /** Default timing for a brand-new group: two hours, any day, next three months. */
    fun defaultDraft(today: LocalDate, timezone: String): TimingDraft = TimingDraft(
        preset = TimingPreset.TwoHours,
        customKind = CustomKind.Hours,
        length = RequirementLength.Duration(TWO_HOURS),
        window = DEFAULT_DURATION_WINDOW,
        days = emptySet(),
        bankHolidays = true,
        schoolHolidaysOnly = false,
        schoolAuthority = null,
        customSchoolHolidays = emptyList(),
        range = DateRange(today.toString(), today.plusMonths(3).toString()),
        dates = emptyList(),
        timezone = timezone,
    )
}

/** Singular / plural noun for a matching slot: "weekend", "evening", "day", "week", "time". */
fun Requirement.slotNoun(plural: Boolean = false): String {
    val singular = when (val l = length) {
        is RequirementLength.Span -> if (l == RequirementPresets.WEEKEND) "weekend" else "trip"
        is RequirementLength.Days -> when {
            l.count == 7 -> "week"
            l.count == 1 && window == RequirementPresets.EVENING_WINDOW -> "evening"
            l.count == 1 -> "day"
            else -> "stretch"
        }
        is RequirementLength.Duration -> "time"
    }
    return if (plural) "${singular}s" else singular
}
