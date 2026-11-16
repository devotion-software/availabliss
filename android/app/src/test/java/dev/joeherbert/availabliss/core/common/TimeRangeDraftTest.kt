package dev.joeherbert.availabliss.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class TimeRangeDraftTest {
    private val day = LocalDate.of(2026, 11, 16)
    private val evening = TimeRangeDraft.timed(day.atTime(18, 0), day.atTime(21, 0))

    @Test
    fun movingTheStartTime_movesTheEndBySameAmount() {
        val later = evening.withStart(day, LocalTime.of(19, 30))
        assertEquals(day.atTime(19, 30), later.start)
        assertEquals(day.atTime(22, 30), later.end)
        val earlier = evening.withStart(day, LocalTime.of(9, 0))
        assertEquals(day.atTime(12, 0), earlier.end)
    }

    @Test
    fun startAfterTheEnd_pushesTheEndAlong() {
        val moved = evening.withStart(day, LocalTime.of(23, 0))
        assertEquals(day.plusDays(1).atTime(2, 0), moved.end)
        assertTrue(moved.isValid)
    }

    @Test
    fun movingTheStartDate_movesTheEndDate() {
        val moved = evening.withStart(day.plusDays(5))
        assertEquals(day.plusDays(5).atTime(18, 0), moved.start)
        assertEquals(day.plusDays(5).atTime(21, 0), moved.end)
    }

    @Test
    fun allDay_movingTheStartKeepsTheDayCount() {
        val away = TimeRangeDraft.allDay(day, day.plusDays(2)).withStart(day.plusDays(10))
        assertEquals(day.plusDays(10), away.startDate)
        assertEquals(day.plusDays(12), away.endDate)
        assertEquals(day.plusDays(10).atStartOfDay(), away.start)
        assertEquals(day.plusDays(13).atStartOfDay(), away.end)
    }

    @Test
    fun endBeforeTheStart_keepsTheOldLength() {
        val snapped = evening.withEnd(day, LocalTime.of(17, 0))
        assertEquals(day.atTime(18, 0), snapped.start)
        assertEquals(day.atTime(21, 0), snapped.end)
        // Equal to the start counts as before.
        assertEquals(day.atTime(21, 0), evening.withEnd(day, LocalTime.of(18, 0)).end)
    }

    @Test
    fun endOnALaterDay_isKept() {
        val overnight = evening.withEnd(day.plusDays(1), LocalTime.of(2, 0))
        assertEquals(day.plusDays(1).atTime(2, 0), overnight.end)
        assertTrue(overnight.isValid)
    }

    @Test
    fun allDayEnd_neverBeforeTheStart() {
        val range = TimeRangeDraft.allDay(day, day.plusDays(2))
        assertEquals(day, range.withEnd(day.minusDays(3)).endDate)
        assertEquals(day.plusDays(4), range.withEnd(day.plusDays(4)).endDate)
    }

    @Test
    fun allDayToggle_keepsDatesAndRestoresTimes() {
        val allDay = evening.withAllDay(true)
        assertTrue(allDay.allDay)
        assertEquals(day.atStartOfDay() to day.plusDays(1).atStartOfDay(), allDay.start to allDay.end)
        assertFalse(allDay.isMultiDay)
        assertEquals(evening, allDay.withAllDay(false))
    }

    @Test
    fun fromIso_allDayEndIsTheLastDay() {
        val range = TimeRangeDraft.fromIso(DateTimes.toIso(day.atStartOfDay()), DateTimes.toIso(day.plusDays(3).atStartOfDay()), allDay = true)!!
        assertEquals(day.plusDays(2), range.endDate)
        assertEquals(DateTimes.toIso(day.plusDays(3).atStartOfDay()), range.endIso())
    }
}
