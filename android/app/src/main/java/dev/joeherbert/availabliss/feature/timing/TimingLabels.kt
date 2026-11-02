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

    /** The one-liner (web `summarize`): "A weekend · Fri from 18:00 → Sun · Nov – Jan". */
    fun summary(draft: TimingDraft): String = "${lengthLabel(draft)} · ${detail(draft)}"

    /** "A weekend", "2 hours", "An evening from 18:00". */
    private fun lengthLabel(draft: TimingDraft): String = when {
        draft.preset == TimingPreset.Evening -> "An evening from ${draft.window?.from ?: "18:00"}"
        draft.preset != TimingPreset.Custom -> draft.preset.label
        else -> when (val l = draft.length) {
            is RequirementLength.Duration -> if (l.minutes % 60 == 0) plural(l.minutes / 60, "hour") else plural(l.minutes, "minute")
            is RequirementLength.Days -> when (l.count) {
                7 -> "A week"
                1 -> "One day"
                else -> "${l.count} days"
            }
            is RequirementLength.Span -> "A span"
        }
    }

    /** Everything after the shape: "Fri from 18:00 → Sun · Nov – Jan". */
    private fun detail(draft: TimingDraft): String {
        val bits = mutableListOf<String>()
        val window = draft.window
        when (val l = draft.length) {
            is RequirementLength.Span -> bits += spanLabel(l)
            else -> {
                if (window != null && draft.preset != TimingPreset.Evening) bits += "${window.from}–${window.to}"
                bits += daysLabel(draft.days)
            }
        }
        if (draft.schoolHolidaysOnly) bits += "school holidays only"
        bits += if (draft.dates.isNotEmpty()) plural(draft.dates.size, "date range") else monthSpan(draft.range.from, draft.range.to)
        return bits.joinToString(" · ")
    }

    /** "Fri from 18:00 → Sun", "Tue → following Thu". */
    private fun spanLabel(l: RequirementLength.Span): String {
        val start = DateTimes.shortWeekday(l.startDay) + if (l.startTime == "00:00") "" else " from ${l.startTime}"
        val endDay = (if (l.weeksLater == 1) "following " else "") + DateTimes.shortWeekday(l.endDay)
        return "$start → " + if (l.endTime == END_OF_DAY) endDay else "$endDay ${l.endTime}"
    }

    /** "any day", "weekdays", "weekends", "Fri–Sun", "Mon, Wed, Fri". */
    fun daysLabel(days: Set<Int>): String {
        val sorted = days.sorted()
        return when {
            sorted.isEmpty() || sorted.size == 7 -> "any day"
            sorted == listOf(0, 1, 2, 3, 4) -> "weekdays"
            sorted == listOf(5, 6) -> "weekends"
            sorted.size > 2 && sorted.zipWithNext().all { (a, b) -> b == a + 1 } ->
                "${DateTimes.shortWeekday(sorted.first())}–${DateTimes.shortWeekday(sorted.last())}"
            else -> sorted.joinToString(", ") { DateTimes.shortWeekday(it) }
        }
    }

    /** "Nov – Feb", or "Nov" when it's all one month. */
    private fun monthSpan(from: String, to: String): String {
        val a = DateTimes.parseDate(from)?.let(::monthShort) ?: return ""
        val b = DateTimes.parseDate(to)?.let(::monthShort) ?: return a
        return if (a == b) a else "$a – $b"
    }

    private fun monthShort(date: java.time.LocalDate) = date.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.UK).take(3)

    private fun plural(n: Int, one: String) = "$n $one${if (n == 1) "" else "s"}"

    fun dateRange(from: String, to: String): String {
        val f = DateTimes.parseDate(from) ?: return "$from – $to"
        val t = DateTimes.parseDate(to) ?: return "$from – $to"
        return if (f == t) DateTimes.dayMonth(f) else "${DateTimes.dayMonth(f)} – ${DateTimes.dayMonth(t)}"
    }
}
