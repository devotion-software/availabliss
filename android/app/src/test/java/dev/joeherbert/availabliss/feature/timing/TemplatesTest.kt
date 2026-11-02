package dev.joeherbert.availabliss.feature.timing

import dev.joeherbert.availabliss.core.network.model.DateRange
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** Ported from web `client/templates.test.ts`. */
class TemplatesTest {
    @Test
    fun templatesRestartTheirSearchRangeToday_andDropPastDates() {
        val weekend = RequirementPresets.applyPreset(RequirementPresets.defaultDraft(LocalDate.of(2026, 1, 10), "Europe/London"), TimingPreset.Weekend)
        val saved = weekend.toRequirement().copy(
            range = DateRange("2026-01-10", "2026-03-10"),
            dates = listOf(DateRange("2026-02-01", "2026-02-05"), DateRange("2026-12-20", "2026-12-31")),
            customSchoolHolidays = listOf(DateRange("2026-10-26", "2026-10-30")),
        )
        val r = Templates.requirementFromTemplate(saved, LocalDate.of(2026, 11, 2), "America/New_York")
        assertEquals(DateRange("2026-11-02", "2026-12-31"), r.range)
        assertEquals(listOf(DateRange("2026-12-20", "2026-12-31")), r.dates)
        assertEquals(emptyList<DateRange>(), r.customSchoolHolidays)
        assertEquals("America/New_York", r.timezone)
        assertEquals(saved.length, r.length)
        assertEquals(2, saved.dates.size) // the template itself is untouched
    }
}
