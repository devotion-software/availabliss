package dev.joeherbert.availabliss.feature.mytime

import dev.joeherbert.availabliss.core.common.DateTimes
import dev.joeherbert.availabliss.core.network.model.ManualBlock
import dev.joeherbert.availabliss.core.network.model.Repeat
import dev.joeherbert.availabliss.core.network.model.wireName
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class BlockDraftTest {
    private val day = LocalDate.of(2026, 11, 3)
    private fun iso(date: LocalDate, time: LocalTime) = DateTimes.toIso(date.atTime(time))
    private fun block(start: LocalTime, end: LocalTime, allDay: Boolean = false, repeat: Repeat = Repeat.None, until: String? = null) =
        ManualBlock("b1", iso(day, start), iso(day, end), allDay, repeat, until, note = "Choir")

    private fun draftOf(block: ManualBlock): BlockDraft = requireNotNull(BlockDraft.fromBlock(block))

    @Test
    fun customTimes_keepTimesRepeatAndNote() {
        val draft = draftOf(block(LocalTime.of(19, 0), LocalTime.of(21, 30), repeat = Repeat.Weekly, until = "2027-01-31"))
        assertEquals("b1", draft.editingId)
        assertEquals(day, draft.date)
        assertEquals(WhenOption.Custom, draft.whenOption)
        assertEquals(LocalTime.of(19, 0), draft.customStart)
        assertEquals(LocalTime.of(21, 30), draft.customEnd)
        assertEquals(Repeat.Weekly, draft.repeat)
        assertEquals(LocalDate.of(2027, 1, 31), draft.until)
        assertEquals("Choir", draft.note)
    }

    @Test
    fun dayPartTimes_mapToTheirOption() {
        assertEquals(WhenOption.Morning, draftOf(block(DayPart.Morning.start, DayPart.Morning.end)).whenOption)
        assertEquals(WhenOption.Evening, draftOf(block(DayPart.Evening.start, DayPart.Evening.end)).whenOption)
    }

    @Test
    fun allDayBlock_mapsToAllDay() {
        val allDay = ManualBlock("b2", iso(day, LocalTime.MIDNIGHT), iso(day.plusDays(1), LocalTime.MIDNIGHT), allDay = true)
        assertEquals(WhenOption.AllDay, draftOf(allDay).whenOption)
    }

    @Test
    fun editedDraft_roundTripsToTheSameRequest() {
        val original = block(LocalTime.of(19, 0), LocalTime.of(21, 30), repeat = Repeat.Fortnightly, until = "2027-01-31")
        val request = draftOf(original).toRequest()
        assertEquals(original.start, request.start)
        assertEquals(original.end, request.end)
        assertEquals(Repeat.Fortnightly, request.repeat)
        assertEquals("2027-01-31", request.until)
        assertEquals("Choir", request.note)
    }

    @Test
    fun repeatWireNames_matchTheApi() {
        assertEquals(listOf("none", "weekly", "fortnightly"), Repeat.entries.map { it.wireName })
    }
}
