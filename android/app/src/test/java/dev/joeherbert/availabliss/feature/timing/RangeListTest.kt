package dev.joeherbert.availabliss.feature.timing

import dev.joeherbert.availabliss.core.network.model.DateRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun ranges_alreadyCoveredAreFound() {
        assertEquals(christmas, DateRanges.covering(christmas, listOf(christmas)))
        assertEquals(christmas, DateRanges.covering(DateRange("2026-12-24", "2026-12-26"), listOf(christmas)))
        assertNull(DateRanges.covering(DateRange("2026-12-18", "2026-12-20"), listOf(christmas)))
    }

    @Test
    fun ranges_thatOverlapOrTouchAreJoined() {
        val merged = DateRanges.merging(
            listOf(DateRange("2027-01-04", "2027-01-06"), christmas, DateRange("2026-12-10", "2026-12-20"), DateRange("2027-02-01", "2027-02-02")),
        )
        assertEquals(listOf(DateRange("2026-12-10", "2027-01-06"), DateRange("2027-02-01", "2027-02-02")), merged)
        assertEquals(1, DateRanges.joining(DateRange("2027-01-04", "2027-01-05"), listOf(christmas)).size)
        assertNull(DateRanges.note(DateRange("2027-01-05", "2027-01-06"), listOf(christmas)))
    }
}
