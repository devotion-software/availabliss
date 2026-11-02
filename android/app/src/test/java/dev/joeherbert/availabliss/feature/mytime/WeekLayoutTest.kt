package dev.joeherbert.availabliss.feature.mytime

import dev.joeherbert.availabliss.core.network.model.BusyInterval
import dev.joeherbert.availabliss.core.network.model.BusySource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/** Ported from web `mytime/parts.test.ts`. */
class WeekLayoutTest {
    private val london = ZoneId.of("Europe/London")

    // Monday 2 November 2026 (GMT, so UTC = London).
    private val week = LocalDate.of(2026, 11, 2)

    private fun busy(start: String, end: String, source: BusySource = BusySource.Manual) =
        BusyInterval(start, end, source, label = null, calendarId = null, blockId = if (source == BusySource.Manual) "b" else null)

    private fun date(s: String) = LocalDate.parse(s)

    @Test
    fun placesIntervalsAtExactMinutesOnTheirDay() {
        val layout = layoutWeek(week, listOf(busy("2026-11-03T19:00:00Z", "2026-11-03T21:30:00Z")), london)
        assertEquals(7, layout.days.size)
        val tue = layout.timed.getValue(date("2026-11-03"))
        assertEquals(1, tue.size)
        with(tue[0]) {
            assertEquals(19f * 60, from)
            assertEquals(21f * 60 + 30, to)
            assertEquals(0, lane)
            assertEquals(1, lanes)
            assertFalse(continuesBefore)
            assertFalse(continuesAfter)
        }
        assertTrue(layout.timed.getValue(date("2026-11-02")).isEmpty())
        assertFalse(layout.hasAllDay)
    }

    @Test
    fun splitsIntervalsAcrossMidnight() {
        val layout = layoutWeek(week, listOf(busy("2026-11-06T22:00:00Z", "2026-11-07T02:00:00Z")), london)
        val fri = layout.timed.getValue(date("2026-11-06")).single()
        assertEquals(22f * 60, fri.from)
        assertEquals(1440f, fri.to)
        assertTrue(fri.continuesAfter)
        val sat = layout.timed.getValue(date("2026-11-07")).single()
        assertEquals(0f, sat.from)
        assertEquals(120f, sat.to)
        assertTrue(sat.continuesBefore)
    }

    @Test
    fun wholeDaysGoToTheAllDayRow() {
        val layout = layoutWeek(week, listOf(busy("2026-11-07T00:00:00Z", "2026-11-09T00:00:00Z", BusySource.Calendar)), london)
        assertEquals(1, layout.allDay.getValue(date("2026-11-07")).size)
        assertEquals(1, layout.allDay.getValue(date("2026-11-08")).size)
        assertTrue(layout.timed.getValue(date("2026-11-07")).isEmpty())
        assertTrue(layout.hasAllDay)
    }

    @Test
    fun usesTheGivenTimeZone() {
        val layout = layoutWeek(week, listOf(busy("2026-11-03T09:00:00Z", "2026-11-03T10:00:00Z")), ZoneId.of("America/New_York"))
        val p = layout.timed.getValue(date("2026-11-03")).single()
        assertEquals(4f * 60, p.from)
        assertEquals(5f * 60, p.to)
    }

    @Test
    fun dstDaysKeepWallClockPositionsOnThe24HourAxis() {
        // 25 October 2026: clocks go back in London, so the day is 25 hours long.
        assertEquals(1500, dayLength(date("2026-10-25"), london))
        val layout = layoutWeek(date("2026-10-19"), listOf(busy("2026-10-25T23:00:00Z", "2026-10-25T23:59:00Z")), london)
        val sun = layout.timed.getValue(date("2026-10-25")).single()
        // 23:00 UTC is 23:00 GMT, the 24th hour of a 25-hour day: scaled to 23:00 on the axis.
        assertEquals((1440 * (24f / 25)).roundToInt(), sun.from.roundToInt())
    }

    @Test
    fun overlapsShareAClusterLaneCount_backToBackItemsDoNot() {
        data class Item(val id: String, val from: Float, val to: Float)
        val out = assignLanes(
            listOf(Item("a", 60f, 180f), Item("b", 120f, 240f), Item("c", 200f, 260f), Item("d", 260f, 300f)),
            { it.from },
            { it.to },
        ).associateBy { it.item.id }
        assertEquals(listOf(0, 1, 0), listOf("a", "b", "c").map { out.getValue(it).lane })
        assertEquals(listOf(2, 2, 2), listOf("a", "b", "c").map { out.getValue(it).lanes })
        assertEquals(0, out.getValue("d").lane)
        assertEquals(1, out.getValue("d").lanes)
    }

    @Test
    fun tapRangeSuggestsTheHourTapped() {
        assertEquals(MinuteRange(840, 900), tapRange(14f * 60 + 40))
        assertEquals(MinuteRange(1380, 1440), tapRange(1439f))
        assertEquals(MinuteRange(0, 60), tapRange(0f))
    }

    @Test
    fun initialScrollStartsAnHourBeforeTheEarliestItem_noLaterThan0600() {
        assertEquals(6 * 60, initialScrollMinutes(layoutWeek(week, emptyList(), london)))
        val early = layoutWeek(week, listOf(busy("2026-11-04T05:30:00Z", "2026-11-04T06:00:00Z")), london)
        assertEquals(4 * 60, initialScrollMinutes(early))
        val late = layoutWeek(week, listOf(busy("2026-11-02T09:00:00Z", "2026-11-02T17:30:00Z")), london)
        assertEquals(6 * 60, initialScrollMinutes(late))
    }

    @Test
    fun clockFormatsMinutes() {
        assertEquals("09:15", clock(555f))
        assertEquals("24:00", clock(1440f))
    }

    @Test
    fun blockedCountsDistinctManualOccurrences() {
        val intervals = listOf(
            busy("2026-11-02T09:00:00Z", "2026-11-02T17:30:00Z"),
            busy("2026-11-02T09:00:00Z", "2026-11-02T17:30:00Z"),
            busy("2026-11-09T09:00:00Z", "2026-11-09T17:30:00Z"),
            busy("2026-11-03T09:00:00Z", "2026-11-03T10:00:00Z", BusySource.Calendar),
        )
        assertEquals(2, blockedOccurrences(intervals))
    }
}
