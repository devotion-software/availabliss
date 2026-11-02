package dev.joeherbert.availabliss.feature.mytime

import dev.joeherbert.availabliss.core.common.DateTimes
import dev.joeherbert.availabliss.core.network.model.ManualBlock
import dev.joeherbert.availabliss.core.network.model.Repeat
import dev.joeherbert.availabliss.core.network.model.wireName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class BlockDraftTest {
    private val day = LocalDate.of(2026, 11, 3)
    private fun iso(date: LocalDate, time: LocalTime) = DateTimes.toIso(date.atTime(time))
    private fun block(start: LocalTime, end: LocalTime, endDate: LocalDate = day, allDay: Boolean = false, repeat: Repeat = Repeat.None, until: String? = null) =
        ManualBlock("b1", iso(day, start), iso(endDate, end), allDay, repeat, until, note = "Choir")

    private fun draftOf(block: ManualBlock): BlockDraft = requireNotNull(BlockDraft.fromBlock(block))

    @Test
    fun newDraft_defaultsToNineToTen() {
        val draft = BlockDraft(date = day)
        assertEquals(BlockWhen.Times, draft.whenOption)
        assertEquals("09:00 – 10:00", draft.timeLabel)
    }

    @Test
    fun forRange_prefillsTheTappedHour() {
        val draft = BlockDraft.forRange(day, MinuteRange(840, 900))
        assertEquals(BlockWhen.Times, draft.whenOption)
        assertEquals(LocalTime.of(14, 0), draft.start)
        assertEquals(LocalTime.of(15, 0), draft.end)
        assertFalse(draft.endsNextDay)
    }

    @Test
    fun forRange_lastHourRunsToMidnight() {
        val draft = BlockDraft.forRange(day, MinuteRange(1380, 1440))
        assertEquals(LocalTime.of(23, 0), draft.start)
        assertEquals(LocalTime.MIDNIGHT, draft.end)
        val (start, end) = draft.range()
        assertEquals(day.atTime(23, 0), start)
        assertEquals(day.plusDays(1).atStartOfDay(), end)
    }

    @Test
    fun endBeforeStart_runsPastMidnight() {
        val draft = BlockDraft(date = day, start = LocalTime.of(22, 0), end = LocalTime.of(2, 0))
        assertTrue(draft.endsNextDay)
        assertTrue(draft.isValid)
        assertEquals(day.plusDays(1).atTime(2, 0), draft.range().second)
    }

    @Test
    fun withStart_keepsTheLength() {
        val draft = BlockDraft(date = day, start = LocalTime.of(9, 0), end = LocalTime.of(10, 30)).withStart(LocalTime.of(23, 0))
        assertEquals(LocalTime.of(23, 0), draft.start)
        assertEquals(LocalTime.of(0, 30), draft.end)
    }

    @Test
    fun customTimes_keepTimesRepeatAndNote() {
        val draft = draftOf(block(LocalTime.of(19, 0), LocalTime.of(21, 30), repeat = Repeat.Weekly, until = "2027-01-31"))
        assertEquals("b1", draft.editingId)
        assertEquals(day, draft.date)
        assertEquals(BlockWhen.Times, draft.whenOption)
        assertEquals(LocalTime.of(19, 0), draft.start)
        assertEquals(LocalTime.of(21, 30), draft.end)
        assertEquals(Repeat.Weekly, draft.repeat)
        assertEquals(LocalDate.of(2027, 1, 31), draft.until)
        assertEquals("Choir", draft.note)
    }

    @Test
    fun allDayBlock_mapsToAllDay() {
        val allDay = ManualBlock("b2", iso(day, LocalTime.MIDNIGHT), iso(day.plusDays(1), LocalTime.MIDNIGHT), allDay = true)
        val draft = draftOf(allDay)
        assertEquals(BlockWhen.AllDay, draft.whenOption)
        assertEquals("All day", draft.timeLabel)
        assertTrue(draft.toRequest().allDay)
    }

    @Test
    fun editedDraft_roundTripsToTheSameRequest() {
        val original = block(LocalTime.of(19, 0), LocalTime.of(21, 30), repeat = Repeat.Fortnightly, until = "2027-01-31")
        val request = draftOf(original).toRequest()
        assertEquals(original.start, request.start)
        assertEquals(original.end, request.end)
        assertFalse(request.allDay)
        assertEquals(Repeat.Fortnightly, request.repeat)
        assertEquals("2027-01-31", request.until)
        assertEquals("Choir", request.note)
    }

    @Test
    fun overnightBlock_roundTrips() {
        val original = block(LocalTime.of(22, 0), LocalTime.of(1, 0), endDate = day.plusDays(1))
        val request = draftOf(original).toRequest()
        assertEquals(original.start, request.start)
        assertEquals(original.end, request.end)
    }

    @Test
    fun repeatWireNames_matchTheApi() {
        assertEquals(listOf("none", "weekly", "fortnightly"), Repeat.entries.map { it.wireName })
    }
}
