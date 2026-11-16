package dev.joeherbert.availabliss.feature.timing

import dev.joeherbert.availabliss.core.network.model.DateRange
import dev.joeherbert.availabliss.core.network.model.Requirement
import dev.joeherbert.availabliss.core.network.model.RequirementTemplate
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Timing templates: saved timings reused for new groups (mirrors web `client/templates.ts`). */
object Templates {
    /** Longest template name the API accepts. */
    const val MAX_NAME = 60

    /**
     * A template's timing, ready for a new group: same rules, but the search range
     * starts [today] (keeping its length), date ranges already over are dropped, and
     * times are read in [timezone] (yours).
     */
    fun requirementFromTemplate(requirement: Requirement, today: LocalDate, timezone: String): Requirement {
        val from = LocalDate.parse(requirement.range.from)
        val to = LocalDate.parse(requirement.range.to)
        val length = ChronoUnit.DAYS.between(from, to).coerceAtLeast(0)
        val stillAhead = { r: DateRange -> r.to >= today.toString() }
        return requirement.copy(
            range = DateRange(today.toString(), today.plusDays(length).toString()),
            dates = requirement.dates.filter(stillAhead),
            customSchoolHolidays = requirement.customSchoolHolidays.filter(stillAhead),
            timezone = timezone,
        )
    }

    /**
     * True when [a] and [b] are the same timing, ignoring when the search range starts
     * (its length still counts) and the time zone: a template's range moves to "today"
     * when it's used. Mirrors web `sameTiming`.
     */
    fun sameTiming(a: Requirement, b: Requirement): Boolean {
        fun normalized(r: Requirement): Requirement {
            val length = ChronoUnit.DAYS.between(LocalDate.parse(r.range.from), LocalDate.parse(r.range.to))
            return r.copy(range = DateRange("", length.toString()), days = r.days.toSortedSet().toList(), timezone = "")
        }
        return runCatching { normalized(a) == normalized(b) }.getOrDefault(false)
    }

    /** The template with the same timing as [requirement], if one was saved. */
    fun matching(templates: List<RequirementTemplate>, requirement: Requirement): RequirementTemplate? =
        templates.firstOrNull { sameTiming(it.requirement, requirement) }

    /** "Saved “X”. Pick it next time you create a group." */
    fun savedMessage(name: String) = "Saved “$name”. Pick it next time you create a group."
}
