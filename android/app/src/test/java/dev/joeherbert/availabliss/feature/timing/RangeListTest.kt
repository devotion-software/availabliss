package dev.joeherbert.availabliss.feature.timing

import dev.joeherbert.availabliss.core.network.model.DateRange
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class RangeListTest {
    private val base = RequirementPresets.defaultDraft(LocalDate.of(2026, 11, 1), "Europe/London")
    private val christmas = DateRange("2026-12-19", "2027-01-03")

    @Test
    fun schoolHolidays_editOnlyTheCustomList() {
        val draft = RangeList.SchoolHolidays.with(base, listOf(christmas))
        assertEquals(listOf(christmas), draft.customSchoolHolidays)
        assertEquals(base.dates, draft.dates)
        assertEquals(listOf(christmas), RangeList.SchoolHolidays.of(draft))
    }

    @Test
    fun dates_editOnlyTheChosenDates() {
        val draft = RangeList.Dates.with(base, listOf(christmas))
        assertEquals(listOf(christmas), draft.dates)
        assertEquals(base.customSchoolHolidays, draft.customSchoolHolidays)
    }

    @Test
    fun customSchoolHolidays_reachTheRequirement() {
        val requirement = RangeList.SchoolHolidays.with(base, listOf(christmas)).toRequirement()
        assertEquals(listOf(christmas), requirement.customSchoolHolidays)
    }
}
