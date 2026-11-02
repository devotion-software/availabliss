package dev.joeherbert.availabliss.feature.mytime

import dev.joeherbert.availabliss.core.network.model.BusyInterval
import dev.joeherbert.availabliss.core.network.model.BusySource
import dev.joeherbert.availabliss.core.network.model.ManualBlock
import dev.joeherbert.availabliss.core.network.model.Repeat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.ZoneId

class IntervalTapTest {
    private val london = ZoneId.of("Europe/London")
    private val choir = ManualBlock("b1", "2026-11-03T19:00:00Z", "2026-11-03T21:30:00Z", false, Repeat.Weekly, null, note = "Choir")

    @Test
    fun manualTime_editsItsBlock_evenForALaterOccurrence() {
        val nextWeek = BusyInterval("2026-11-10T19:00:00Z", "2026-11-10T21:30:00Z", BusySource.Manual, "Choir", blockId = "b1")
        val tap = resolveIntervalTap(nextWeek, listOf(choir), london)
        assertSame(choir, (tap as IntervalTap.EditBlock).block)
    }

    @Test
    fun manualTime_withoutALoadedBlock_isMissing() {
        val gone = BusyInterval("2026-11-03T09:00:00Z", "2026-11-03T10:00:00Z", BusySource.Manual, null, blockId = "deleted")
        assertEquals(IntervalTap.MissingBlock, resolveIntervalTap(gone, listOf(choir), london))
    }

    @Test
    fun calendarTime_explainsWithNameAndRange() {
        val dentist = BusyInterval("2026-11-04T10:00:00Z", "2026-11-04T11:00:00Z", BusySource.Calendar, "Work", calendarId = "c1")
        val tap = resolveIntervalTap(dentist, listOf(choir), london) as IntervalTap.CalendarBusy
        assertEquals("Work", tap.info.calendarName)
        assertEquals("Wed 4 Nov · 10:00 – 11:00", tap.info.timeLabel)
    }

    @Test
    fun calendarTime_withoutALabel_andWholeDays() {
        val away = BusyInterval("2026-11-07T00:00:00Z", "2026-11-09T00:00:00Z", BusySource.Calendar, null)
        val tap = resolveIntervalTap(away, emptyList(), london) as IntervalTap.CalendarBusy
        assertEquals("A connected calendar", tap.info.calendarName)
        assertEquals("Sat 7 – Sun 8 Nov", tap.info.timeLabel)
    }
}
