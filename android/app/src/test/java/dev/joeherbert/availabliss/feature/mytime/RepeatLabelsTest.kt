package dev.joeherbert.availabliss.feature.mytime

import dev.joeherbert.availabliss.core.common.DateTimes
import dev.joeherbert.availabliss.core.network.model.ManualBlock
import dev.joeherbert.availabliss.core.network.model.Repeat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RepeatLabelsTest {
    /** Monday 16 November 2026: the 3rd Monday. */
    private val monday = LocalDate.of(2026, 11, 16)

    @Test
    fun menuOptions_readFromTheStartDate() {
        assertEquals(
            listOf(
                "Never",
                "Every day",
                "Every weekday (Mon–Fri)",
                "Every week on Monday",
                "Every 2 weeks on Monday",
                "Every month on the 16th",
                "Every month on the 3rd Monday",
                "Every year on 16 November",
            ),
            Repeat.choices.map { repeatOptionLabel(it, monday) },
        )
        assertEquals("Repeats", repeatOptionLabel(Repeat.Unknown, monday))
    }

    @Test
    fun monthlyWeekday_isTheLastOneInTheMonthsLastSevenDays() {
        // Mon 30 Nov 2026 (5th Monday) and Tue 24 Nov (4th, but within the last 7 days); Mon 23 Nov is the 4th.
        assertEquals("Every month on the last Monday", repeatOptionLabel(Repeat.MonthlyWeekday, LocalDate.of(2026, 11, 30)))
        assertEquals("Every month on the last Tuesday", repeatOptionLabel(Repeat.MonthlyWeekday, LocalDate.of(2026, 11, 24)))
        assertEquals("Every month on the 4th Monday", repeatOptionLabel(Repeat.MonthlyWeekday, LocalDate.of(2026, 11, 23)))
        assertTrue(isLastWeekdayOfMonth(LocalDate.of(2026, 2, 22)))
        assertFalse(isLastWeekdayOfMonth(LocalDate.of(2026, 2, 21)))
        assertEquals("Every month on the 1st Sunday", repeatOptionLabel(Repeat.MonthlyWeekday, LocalDate.of(2026, 11, 1)))
    }

    @Test
    fun ordinals() {
        assertEquals(listOf("1st", "2nd", "3rd", "4th", "11th", "12th", "13th", "21st", "22nd", "23rd", "31st"), listOf(1, 2, 3, 4, 11, 12, 13, 21, 22, 23, 31).map(::ordinal))
    }

    private fun block(repeat: Repeat, allDay: Boolean = false, until: String? = null): ManualBlock {
        val (s, e) = if (allDay) monday.atStartOfDay() to monday.plusDays(1).atStartOfDay() else monday.atTime(7, 0) to monday.atTime(8, 0)
        return ManualBlock("b", DateTimes.toIso(s), DateTimes.toIso(e), allDay, repeat, until, null)
    }

    @Test
    fun scheduleLines() {
        assertEquals("Every day · 07:00 – 08:00", block(Repeat.Daily).scheduleLabel())
        assertEquals("Weekdays · 07:00 – 08:00", block(Repeat.Weekdays).scheduleLabel())
        assertEquals("Every Monday · 07:00 – 08:00", block(Repeat.Weekly).scheduleLabel())
        assertEquals("Every other Monday · 07:00 – 08:00", block(Repeat.Fortnightly).scheduleLabel())
        assertEquals("Monthly on the 16th · All day", block(Repeat.Monthly, allDay = true).scheduleLabel())
        assertEquals("Monthly on the 3rd Monday · 07:00 – 08:00 · until 20 Dec", block(Repeat.MonthlyWeekday, until = "2026-12-20").scheduleLabel())
        assertEquals("Yearly on 16 Nov · All day", block(Repeat.Yearly, allDay = true).scheduleLabel())
        assertEquals("Repeats · 07:00 – 08:00", block(Repeat.Unknown).scheduleLabel())
        assertEquals("Mon 16 Nov · 07:00 – 08:00", block(Repeat.None, until = "2026-12-20").scheduleLabel())
    }

    @Test
    fun agendaTags() {
        assertEquals(
            listOf("Your block", "Daily", "Weekdays", "Weekly", "Every other week", "Monthly", "Monthly", "Yearly", "Repeats"),
            Repeat.entries.map { agendaTag(it).label },
        )
        assertEquals(AgendaTag.Block, agendaTag(null))
    }
}
