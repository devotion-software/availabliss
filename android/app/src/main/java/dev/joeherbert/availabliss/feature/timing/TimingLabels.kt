package dev.joeherbert.availabliss.feature.timing

import dev.joeherbert.availabliss.core.common.DateTimes
import dev.joeherbert.availabliss.core.network.model.RequirementLength

/** Human copy for the timing editor ("Fri from 18:00", "Sun, any time"). */
object TimingLabels {
    private const val END_OF_DAY = "23:59"

    fun duration(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0 -> "$m minutes"
            m == 0 -> if (h == 1) "1 hour" else "$h hours"
            m == 30 -> "$h½ hours"
            else -> "$h h $m min"
        }
    }

    fun days(count: Int): String = if (count == 1) "1 day" else "$count days"

    fun starts(draft: TimingDraft): String = when (val l = draft.length) {
        is RequirementLength.Span -> {
            val day = DateTimes.shortWeekday(l.startDay)
            if (l.startTime == "00:00") "$day, any time" else "$day from ${l.startTime}"
        }
        is RequirementLength.Duration -> "After ${(draft.window ?: RequirementPresets.DEFAULT_DURATION_WINDOW).from}"
        is RequirementLength.Days -> draft.window?.let { "From ${it.from}" } ?: "Start of the day"
    }

    fun ends(draft: TimingDraft): String = when (val l = draft.length) {
        is RequirementLength.Span -> {
            val day = (if (l.weeksLater == 1) "following " else "") + DateTimes.shortWeekday(l.endDay)
            if (l.endTime == END_OF_DAY) "$day, any time" else "$day by ${l.endTime}"
        }
        is RequirementLength.Duration -> "Done by ${(draft.window ?: RequirementPresets.DEFAULT_DURATION_WINDOW).to}"
        is RequirementLength.Days -> {
            val w = draft.window
            when {
                w != null && w.to == END_OF_DAY -> "Late"
                w != null -> "Until ${w.to}"
                l.count == 1 -> "End of the day"
                else -> "After ${days(l.count)}"
            }
        }
    }

    fun title(draft: TimingDraft): String = when {
        draft.preset != TimingPreset.Custom -> draft.preset.label
        else -> when (val l = draft.length) {
            is RequirementLength.Duration -> duration(l.minutes)
            is RequirementLength.Days -> days(l.count)
            is RequirementLength.Span ->
                "${DateTimes.shortWeekday(l.startDay)} → ${if (l.weeksLater == 1) "following " else ""}${DateTimes.shortWeekday(l.endDay)}"
        }
    }

    fun searchRange(draft: TimingDraft): String {
        if (draft.dates.isNotEmpty()) {
            return "Only ${draft.dates.size} chosen ${if (draft.dates.size == 1) "date range" else "date ranges"}"
        }
        val from = DateTimes.parseDate(draft.range.from)
        val to = DateTimes.parseDate(draft.range.to)
        return if (from != null && to != null) "Searching ${DateTimes.dayMonth(from)} – ${DateTimes.dayMonth(to)}" else ""
    }

    fun dateRange(from: String, to: String): String {
        val f = DateTimes.parseDate(from) ?: return "$from – $to"
        val t = DateTimes.parseDate(to) ?: return "$from – $to"
        return if (f == t) DateTimes.dayMonth(f) else "${DateTimes.dayMonth(f)} – ${DateTimes.dayMonth(t)}"
    }
}
