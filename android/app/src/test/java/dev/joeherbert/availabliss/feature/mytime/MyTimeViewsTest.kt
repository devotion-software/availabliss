package dev.joeherbert.availabliss.feature.mytime

import dev.joeherbert.availabliss.core.network.model.BusyInterval
import dev.joeherbert.availabliss.core.network.model.BusySource
import dev.joeherbert.availabliss.core.network.model.Repeat
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale

class MyTimeViewsTest {
    private val london = ZoneId.of("Europe/London")

    // Tuesday 17 November 2026 (GMT, so UTC = London).
    private val today = LocalDate.of(2026, 11, 17)
    private val last = today.plusDays(MY_TIME_DAYS_AHEAD)
    private fun h(hour: Int, minute: Int = 0) = hour * 60 + minute
    private fun span(from: Int, to: Int) = BusySpan(from, to)

    private var defaultLocale: Locale = Locale.getDefault()

    @Before
    fun ukLocale() {
        defaultLocale = Locale.getDefault()
        Locale.setDefault(Locale.UK)
    }

    @After
    fun restoreLocale() = Locale.setDefault(defaultLocale)

    private fun busy(start: String, end: String, source: BusySource = BusySource.Calendar, label: String? = null, blockId: String? = null, ignored: Boolean = false) =
        BusyInterval(start, end, source, label = label, blockId = blockId, ignoreId = if (ignored) "i" else null)

    private fun row(day: LocalDate, vararg intervals: BusyInterval): DayRowUi {
        val layout = layoutDays(listOf(day), intervals.toList(), london)
        return dayRow(day, layout.allDay.getValue(day), layout.timed.getValue(day))
    }

    // region Free-time level
    @Test
    fun freeMinutes_countsOnly0600To2400_andOverlapsOnce() {
        assertEquals(WINDOW_MINUTES, freeMinutes(emptyList()))
        assertEquals(1080, WINDOW_MINUTES)
        // Before 06:00 doesn't count; 09–17 and 12–18 overlap into 09–18.
        assertEquals(1080 - 9 * 60, freeMinutes(listOf(span(h(1), h(5)), span(h(9), h(17)), span(h(12), h(18)))))
        assertEquals(0, freeMinutes(listOf(span(0, DAY_MINUTES))))
    }

    @Test
    fun freeLevel_thresholds() {
        assertEquals(4, freeLevel(1080))
        assertEquals(3, freeLevel(1080 - 60)) // an hour busy
        assertEquals(2, freeLevel(1080 - 3 * 60)) // exactly 3h busy
        assertEquals(2, freeLevel(1080 - 6 * 60))
        assertEquals(1, freeLevel(1080 - 8 * 60)) // exactly 8h busy
        assertEquals(1, freeLevel(30))
        assertEquals(0, freeLevel(29))
        assertEquals(0, freeLevel(0))
    }

    @Test
    fun dayRow_carriesTheLevel() {
        // Busy 09:00 – 17:30: 8.5h of 18h busy (8h or more), 9.5h free → level 1.
        val workday = row(today, busy("2026-11-17T09:00:00Z", "2026-11-17T17:30:00Z"))
        assertEquals(1080 - 510, workday.freeMinutes)
        assertEquals(1, workday.level)
        assertFalse(workday.hasBlock)
        assertTrue(workday.hasCalendarBusy)
        // Time marked not busy doesn't count.
        assertEquals(4, row(today, busy("2026-11-17T09:00:00Z", "2026-11-17T17:30:00Z", ignored = true)).level)
        // An all-day block of your own: busy all day, with a block.
        val away = row(today, busy("2026-11-17T00:00:00Z", "2026-11-18T00:00:00Z", BusySource.Manual, "Away", "b1"))
        assertEquals(0, away.level)
        assertTrue(away.hasBlock)
    }
    // endregion

    // region Month
    @Test
    fun monthWeeks_startOnMonday_withBlanksAround() {
        // November 2026 starts on a Sunday and ends on a Monday.
        val weeks = monthWeeks(YearMonth.of(2026, 11), emptyMap(), today)
        assertEquals(6, weeks.size)
        assertTrue(weeks.all { it.size == 7 })
        assertTrue(weeks[0].take(6).all { it == null })
        assertEquals(LocalDate.of(2026, 11, 1), weeks[0][6]?.date)
        assertEquals(LocalDate.of(2026, 11, 30), weeks[5][0]?.date)
        assertTrue(weeks[5].drop(1).all { it == null })
    }

    @Test
    fun monthWeeks_pastToday_andNotLoadedDays() {
        val busyDay = today.plusDays(1)
        val rows = mapOf(
            today.minusDays(1) to row(today.minusDays(1)),
            today to row(today),
            busyDay to row(busyDay, busy("2026-11-18T06:00:00Z", "2026-11-19T00:00:00Z", BusySource.Manual, "Trip", "b1")),
        )
        val cells = monthWeeks(YearMonth.of(2026, 11), rows, today).flatten().filterNotNull().associateBy { it.date }
        val yesterday = cells.getValue(today.minusDays(1))
        assertTrue(yesterday.past)
        assertEquals(0, yesterday.level)
        assertEquals("Monday 16 November, past", yesterday.accessibility)

        val t = cells.getValue(today)
        assertTrue(t.today)
        assertEquals(4, t.level)
        assertEquals("Today, Tuesday 17 November, free all day", t.accessibility)

        val b = cells.getValue(busyDay)
        assertEquals(0, b.level)
        assertTrue(b.hasBlock)
        assertEquals("Wednesday 18 November, busy all day, Trip", b.accessibility)

        // Not loaded yet: level 0, no summary.
        val later = cells.getValue(LocalDate.of(2026, 11, 25))
        assertEquals(0, later.level)
        assertFalse(later.past)
        assertEquals("Wednesday 25 November", later.accessibility)
    }

    @Test
    fun monthRange_runsFromThisMonthToAYearAhead() {
        val range = monthRange(today)
        assertEquals(YearMonth.of(2026, 11), range.start)
        assertEquals(YearMonth.of(2027, 11), range.endInclusive)
    }
    // endregion

    // region Agenda
    @Test
    fun agendaItems_allDayFirst_thenInTimeOrder_withoutTimeMarkedNotBusy() {
        val layout = layoutDays(
            listOf(today),
            listOf(
                busy("2026-11-17T19:00:00Z", "2026-11-17T21:30:00Z", BusySource.Manual, "Choir", "weekly"),
                busy("2026-11-17T09:00:00Z", "2026-11-17T17:30:00Z", label = "Work calendar"),
                busy("2026-11-17T12:00:00Z", "2026-11-17T13:00:00Z", ignored = true),
                busy("2026-11-17T00:00:00Z", "2026-11-18T00:00:00Z", BusySource.Manual, "  ", "once"),
                busy("2026-11-17T07:00:00Z", "2026-11-17T08:00:00Z", BusySource.Manual, "Run", "fortnightly"),
            ),
            london,
        )
        val items = agendaItems(
            layout.allDay.getValue(today),
            layout.timed.getValue(today),
            mapOf("weekly" to Repeat.Weekly, "fortnightly" to Repeat.Fortnightly, "once" to Repeat.None),
        )
        assertEquals(listOf("Busy", "Run", "Busy", "Choir"), items.map { it.title })
        assertEquals(listOf(AgendaTag.Block, AgendaTag.Fortnightly, AgendaTag.Calendar, AgendaTag.Weekly), items.map { it.tag })
        assertTrue(items[0].allDay)
        assertEquals(h(9) to h(17, 30), items[2].from to items[2].to)
        assertEquals("Every other week", AgendaTag.Fortnightly.label)
    }

    @Test
    fun blockDraftForFree_prefillsTheLongestStretch() {
        assertNull(blockDraftForFree(today, DaySummary.BusyAllDay))
        assertEquals(true, blockDraftForFree(today, DaySummary.FreeAllDay)?.range?.allDay)

        val evening = requireNotNull(blockDraftForFree(today, DaySummary.FreeFrom(h(17, 30))))
        assertEquals(today.atTime(17, 30), evening.range.start)
        assertEquals(today.plusDays(1).atStartOfDay(), evening.range.end)
        assertEquals(today, evening.date)

        val morning = requireNotNull(blockDraftForFree(today, DaySummary.FreeUntil(h(9))))
        assertEquals(LocalTime.of(6, 0) to LocalTime.of(9, 0), morning.range.startTime to morning.range.endTime)

        val lunch = requireNotNull(blockDraftForFree(today, DaySummary.FreeBetween(h(12), h(14))))
        assertEquals(LocalTime.of(12, 0) to LocalTime.of(14, 0), lunch.range.startTime to lunch.range.endTime)
    }
    // endregion

    // region Week view
    @Test
    fun steppedDay_staysBetweenTodayAndAYearAhead() {
        assertNull(steppedDay(today, -1, today, last))
        assertEquals(today.plusDays(1), steppedDay(today, 1, today, last))
        assertNull(steppedDay(last, 1, today, last))
    }

    @Test
    fun steppedWeek_keepsTheWeekday_clampedToToday() {
        // Today is a Tuesday; from next Monday a week back lands on this Monday, which is past: today.
        assertEquals(today, steppedWeek(LocalDate.of(2026, 11, 23), -1, today, last))
        assertEquals(LocalDate.of(2026, 11, 26), steppedWeek(LocalDate.of(2026, 11, 19), 1, today, last))
        // No week before this one.
        assertNull(steppedWeek(LocalDate.of(2026, 11, 19), -1, today, last))
        // Past the last day's week: none; within it, clamped to the last day.
        assertNull(steppedWeek(last, 1, today, last))
        assertEquals(last, steppedWeek(last.minusDays(6), 1, today, last))
    }

    @Test
    fun weekSummaryLine_readsTodayTheSummaryAndYourBlocks() {
        val r = row(today, busy("2026-11-17T19:00:00Z", "2026-11-17T21:30:00Z", BusySource.Manual, "Choir", "b1"))
        assertEquals("Today · Free until 19:00 · Choir", weekSummaryLine(r, isToday = true))
        assertEquals("Free all day", weekSummaryLine(row(today), isToday = false))
        assertEquals("Loading…", weekSummaryLine(null, isToday = false))
    }
    // endregion
}
