package dev.joeherbert.availabliss.feature.timing

import dev.joeherbert.availabliss.core.network.model.DateRange
import dev.joeherbert.availabliss.core.network.model.Requirement
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

    /** "Saved “X”. Pick it next time you create a group." */
    fun savedMessage(name: String) = "Saved “$name”. Pick it next time you create a group."
}
