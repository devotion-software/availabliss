package dev.joeherbert.availabliss.feature.mytime

import dev.joeherbert.availabliss.core.network.model.BusyInterval
import dev.joeherbert.availabliss.core.network.model.BusySource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

class DaySummaryTest {
    private val london = ZoneId.of("Europe/London")

    // Tuesday 17 November 2026 (GMT, so UTC = London).
    private val day = LocalDate.of(2026, 11, 17)

    private fun h(hour: Int, minute: Int = 0) = hour * 60 + minute
    private fun span(from: Int, to: Int) = BusySpan(from, to)

    private fun busy(start: String, end: String, source: BusySource = BusySource.Calendar, label: String? = null, ignored: Boolean = false) =
        BusyInterval(
            start,
            end,
            source,
            label = label,
            blockId = if (source == BusySource.Manual) "b" else null,
            ignoreId = if (ignored) "i" else null,
        )

    private fun row(vararg intervals: BusyInterval): DayRowUi {
        val layout = layoutDays(listOf(day), intervals.toList(), london)
        return dayRow(day, layout.allDay.getValue(day), layout.timed.getValue(day))
    }

    @Test
    fun nothingBusy_isFreeAllDay() {
        assertEquals("Free all day", summarise(emptyList()).label)
        // Busy only before 06:00 doesn't count against the day.
        assertEquals(DaySummary.FreeAllDay, summarise(listOf(span(h(1), h(2)))))
    }

    @Test
    fun wholeWindowBusy_isBusyAllDay() {
        assertEquals("Busy all day", summarise(listOf(span(0, DAY_MINUTES))).label)
        assertEquals(DaySummary.BusyAllDay, summarise(listOf(span(h(6), h(23, 55)))))
    }

    @Test
    fun freeEvening_isFreeFrom() {
        assertEquals("Free from 17:30", summarise(listOf(span(h(9), h(17, 30)))).label)
    }

    @Test
    fun freeMorning_isFreeUntil() {
        assertEquals("Free until 13:00", summarise(listOf(span(h(13), DAY_MINUTES))).label)
    }

    @Test
    fun longestStretchInTheMiddle_isFreeBetween() {
        val summary = summarise(listOf(span(h(6), h(12)), span(h(14), h(23))))
        assertEquals(DaySummary.FreeBetween(h(12), h(14)), summary)
        assertEquals("Free 12:00 – 14:00", summary.label)
    }

    @Test
    fun picksTheLongestStretch_tiesGoToTheLaterOne() {
        // Free 06–08 (2h), 12–13 (1h), 22–24 (2h): the evening wins the tie.
        assertEquals(DaySummary.FreeFrom(h(22)), summarise(listOf(span(h(8), h(12)), span(h(13), h(22)))))
    }

    @Test
    fun tinyGapsDontCountAsFree() {
        assertEquals(DaySummary.BusyAllDay, summarise(listOf(span(h(6), h(23, 59)))))
        assertEquals(DaySummary.BusyAllDay, summarise(listOf(span(h(6), h(12)), span(h(12, 10), DAY_MINUTES))))
    }

    @Test
    fun mergeSpans_joinsOverlapsAndTouchingSpans() {
        assertEquals(
            listOf(span(h(9), h(12)), span(h(13), h(14))),
            mergeSpans(listOf(span(h(10), h(12)), span(h(13), h(14)), span(h(9), h(11)), span(h(11), h(11, 30)))),
        )
    }

    @Test
    fun dayRow_splitsCalendarAndOwnBlocks_andIgnoresTimeMarkedNotBusy() {
        val r = row(
            busy("2026-11-17T09:00:00Z", "2026-11-17T17:00:00Z"),
            busy("2026-11-17T19:00:00Z", "2026-11-17T21:30:00Z", BusySource.Manual, "Choir"),
            busy("2026-11-17T22:00:00Z", "2026-11-17T23:00:00Z", ignored = true),
        )
        assertEquals(listOf(BusySpan(h(9), h(17))), r.calendarSpans)
        assertEquals(listOf(BusySpan(h(19), h(21, 30), manual = true)), r.manualSpans)
        // 06:00 – 09:00 is the longest free stretch.
        assertEquals(DaySummary.FreeUntil(h(9)), r.summary)
        assertEquals("Choir", r.names)
    }

    @Test
    fun dayRow_namesOnlyYourOwnBlocks() {
        val r = row(
            busy("2026-11-17T18:00:00Z", "2026-11-17T19:00:00Z", BusySource.Manual, "Gym"),
            busy("2026-11-17T09:00:00Z", "2026-11-17T10:00:00Z", BusySource.Manual, "Dentist"),
            busy("2026-11-17T11:00:00Z", "2026-11-17T12:00:00Z", label = "Work calendar"),
        )
        assertEquals("Dentist, Gym", r.names)
        assertNull(row(busy("2026-11-17T11:00:00Z", "2026-11-17T12:00:00Z", label = "Work calendar")).names)
    }

    @Test
    fun dayRow_allDayItemsMakeTheDayBusy() {
        val r = row(busy("2026-11-17T00:00:00Z", "2026-11-18T00:00:00Z", BusySource.Manual, "Away"))
        assertEquals(DaySummary.BusyAllDay, r.summary)
        assertEquals(listOf(BusySpan(0, DAY_MINUTES, manual = true)), r.manualSpans)
    }

    @Test
    fun accessibilityReadsTheBusyRangesAndTheFreeTime() {
        val default = Locale.getDefault()
        Locale.setDefault(Locale.UK)
        try {
            val busy = listOf(span(h(9), h(17)), span(h(19), h(21, 30)))
            assertEquals(
                "Tuesday 17 November, busy 09:00 to 17:00 and 19:00 to 21:30, free until 09:00",
                describeDay(day, busy, summarise(busy), names = null),
            )
            assertEquals("Today, Tuesday 17 November, free all day", describeDay(day, emptyList(), DaySummary.FreeAllDay, null, isToday = true))
            val allDay = listOf(span(0, DAY_MINUTES))
            assertEquals("Tuesday 17 November, busy all day, Away", describeDay(day, allDay, DaySummary.BusyAllDay, "Away"))
            val evening = listOf(span(h(18), DAY_MINUTES))
            assertEquals("Tuesday 17 November, busy 18:00 to midnight, free until 18:00", describeDay(day, evening, summarise(evening), null))
        } finally {
            Locale.setDefault(default)
        }
    }

    @Test
    fun weekHeadings() {
        val today = LocalDate.of(2026, 11, 18) // Wednesday
        assertEquals("This week", weekHeading(LocalDate.of(2026, 11, 16), today))
        assertEquals("Next week", weekHeading(LocalDate.of(2026, 11, 23), today))
        val default = Locale.getDefault()
        Locale.setDefault(Locale.UK)
        try {
            assertEquals("30 Nov – 6 Dec", weekHeading(LocalDate.of(2026, 11, 30), today))
        } finally {
            Locale.setDefault(default)
        }
    }

    @Test
    fun dateChunksStayWithinTheLimit() {
        val from = LocalDate.of(2026, 11, 17)
        val chunks = dateChunks(from, from.plusDays(249), 112)
        assertEquals(3, chunks.size)
        assertEquals(from, chunks[0].start)
        assertEquals(from.plusDays(111), chunks[0].endInclusive)
        assertEquals(from.plusDays(112), chunks[1].start)
        assertEquals(from.plusDays(249), chunks[2].endInclusive)
        assertEquals(1, dateChunks(from, from, 112).size)
        assertEquals(LocalDate.of(2026, 11, 22), endOfWeek(from))
    }
}
