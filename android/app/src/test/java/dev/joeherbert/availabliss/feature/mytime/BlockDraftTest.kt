package dev.joeherbert.availabliss.feature.mytime

import dev.joeherbert.availabliss.core.common.DateTimes
import dev.joeherbert.availabliss.core.network.model.ManualBlock
import dev.joeherbert.availabliss.core.network.model.Repeat
import dev.joeherbert.availabliss.core.network.model.wireName
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
        val draft = BlockDraft.on(day)
        assertFalse(draft.range.allDay)
        assertEquals(day.atTime(9, 0), draft.range.start)
        assertEquals(day.atTime(10, 0), draft.range.end)
        assertEquals("Never", draft.repeatLabel)
    }

    @Test
    fun forRange_prefillsTheTappedHour() {
        val draft = BlockDraft.forRange(day, MinuteRange(840, 900))
        assertEquals(day.atTime(14, 0), draft.range.start)
        assertEquals(day.atTime(15, 0), draft.range.end)
    }

    @Test
    fun forRange_lastHourRunsToMidnight() {
        val draft = BlockDraft.forRange(day, MinuteRange(1380, 1440))
        assertEquals(day.atTime(23, 0), draft.range.start)
        assertEquals(day.plusDays(1).atStartOfDay(), draft.range.end)
    }

    @Test
    fun movingTheStart_keepsTheLengthAndDropsAnEarlierUntil() {
        val draft = BlockDraft.on(day).copy(repeat = Repeat.Weekly, until = day.plusDays(3))
        val moved = draft.withRange(draft.range.withStart(day.plusDays(7), LocalTime.of(23, 0)))
        assertEquals(day.plusDays(7).atTime(23, 0), moved.range.start)
        assertEquals(day.plusDays(8).atTime(0, 0), moved.range.end)
        assertNull(moved.until)
    }

    @Test
    fun neverClearsTheEndDate() {
        val draft = BlockDraft.on(day).withRepeat(Repeat.MonthlyWeekday).copy(until = day.plusMonths(2))
        assertEquals("Every month on the 1st Tuesday", draft.repeatLabel)
        assertEquals(day.plusMonths(2), draft.withRepeat(Repeat.Yearly).until)
        assertNull(draft.withRepeat(Repeat.None).until)
    }

    @Test
    fun customTimes_keepTimesRepeatAndNote() {
        val draft = draftOf(block(LocalTime.of(19, 0), LocalTime.of(21, 30), repeat = Repeat.Weekly, until = "2027-01-31"))
        assertEquals("b1", draft.editingId)
        assertEquals(day, draft.date)
        assertFalse(draft.range.allDay)
        assertEquals(LocalTime.of(19, 0), draft.range.startTime)
        assertEquals(LocalTime.of(21, 30), draft.range.endTime)
        assertEquals(Repeat.Weekly, draft.repeat)
        assertEquals(LocalDate.of(2027, 1, 31), draft.until)
        assertEquals("Choir", draft.note)
    }

    @Test
    fun allDayBlock_mapsToAllDay() {
        val allDay = ManualBlock("b2", iso(day, LocalTime.MIDNIGHT), iso(day.plusDays(1), LocalTime.MIDNIGHT), allDay = true)
        val draft = draftOf(allDay)
        assertTrue(draft.range.allDay)
        assertEquals(day, draft.range.endDate)
        assertTrue(draft.toRequest().allDay)
        // Switching all day off gives 09:00 – 10:00 rather than an empty range.
        val timed = draft.range.withAllDay(false)
        assertEquals(day.atTime(9, 0) to day.atTime(10, 0), timed.start to timed.end)
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
    fun away_coversEveryDayInclusive() {
        val draft = BlockDraft.away(day, day.plusDays(4))
        assertTrue(draft.range.allDay)
        assertTrue(draft.range.isMultiDay)
        assertEquals(day.atStartOfDay() to day.plusDays(5).atStartOfDay(), draft.range.start to draft.range.end)
        assertTrue(draft.toRequest().allDay)
        // One day picked is just that day.
        assertFalse(BlockDraft.away(day, day).range.isMultiDay)
        val one = BlockDraft.allDay(day).range
        assertEquals(day.atStartOfDay() to day.plusDays(1).atStartOfDay(), one.start to one.end)
    }

    @Test
    fun multiDayAllDayBlock_roundTrips() {
        val original = block(LocalTime.MIDNIGHT, LocalTime.MIDNIGHT, endDate = day.plusDays(3), allDay = true)
        val draft = draftOf(original)
        assertEquals(day.plusDays(2), draft.range.endDate)
        val request = draft.toRequest()
        assertEquals(original.start, request.start)
        assertEquals(original.end, request.end)
    }

    @Test
    fun repeatWireNames_matchTheApi() {
        assertEquals(
            listOf("none", "daily", "weekdays", "weekly", "fortnightly", "monthly", "monthlyWeekday", "yearly"),
            Repeat.choices.map { it.wireName },
        )
    }

    @Test
    fun repeat_decodesNewValuesAndUnknownOnesSafely() {
        val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }
        fun decode(repeat: String) = json.decodeFromString(ManualBlock.serializer(), """{"id":"b","start":"s","end":"e","repeat":"$repeat"}""").repeat
        assertEquals(Repeat.MonthlyWeekday, decode("monthlyWeekday"))
        assertEquals(Repeat.Weekdays, decode("weekdays"))
        assertEquals(Repeat.Unknown, decode("everyLeapSecond"))
        assertEquals("\"yearly\"", json.encodeToString(Repeat.serializer(), Repeat.Yearly))
    }
}
