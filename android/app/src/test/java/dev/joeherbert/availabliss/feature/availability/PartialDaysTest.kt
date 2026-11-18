package dev.joeherbert.availabliss.feature.availability

import dev.joeherbert.availabliss.core.network.NetworkModule
import dev.joeherbert.availabliss.core.network.model.Availability
import dev.joeherbert.availabliss.core.network.model.AvailabilityDay
import dev.joeherbert.availabliss.core.network.model.DayAvailability
import dev.joeherbert.availabliss.core.network.model.DayStatus
import dev.joeherbert.availabliss.core.network.model.Interval
import dev.joeherbert.availabliss.core.network.model.Slot
import dev.joeherbert.availabliss.core.network.model.SlotPosition
import dev.joeherbert.availabliss.designsystem.component.availability.CellState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class PartialDaysTest {
    private val today = LocalDate.of(2026, 11, 1)

    private fun availability(days: List<AvailabilityDay>, slots: List<Slot>) =
        Availability("2026-11-01", "2026-11-30", (0..6).toList(), days, slots, syncedCount = 4, memberCount = 4)

    private fun cells(a: Availability) =
        AvailabilityUiMapper.monthRows(YearMonth.of(2026, 11), a, group = null, meId = null, selected = null, today = today).flatten().filterNotNull()

    @Test
    fun partialMatchDay_isMarkedWithWhoIsMissing() {
        val a = availability(
            days = listOf(
                AvailabilityDay("2026-11-13", DayStatus.Match, listOf("a", "b"), SlotPosition.Single, partial = true),
                AvailabilityDay("2026-11-14", DayStatus.Match, slotPosition = SlotPosition.Single),
            ),
            slots = listOf(
                Slot("2026-11-13T00:00:00Z", "2026-11-14T00:00:00Z", "Fri 13 Nov", missingMemberIds = listOf("a", "b")),
                Slot("2026-11-14T00:00:00Z", "2026-11-15T00:00:00Z", "Sat 14 Nov"),
            ),
        )
        val cells = cells(a)
        val partial = cells.single { it.date == LocalDate.of(2026, 11, 13) }
        assertEquals(CellState.Match, partial.state)
        assertTrue(partial.partial)
        assertEquals("2 missing", partial.note)
        val full = cells.single { it.date == LocalDate.of(2026, 11, 14) }
        assertFalse(full.partial)
        assertTrue(AvailabilityUiMapper.hasPartial(AvailabilityUiMapper.monthRows(YearMonth.of(2026, 11), a, null, null, null, today)))
    }

    @Test
    fun noPartialDays_noMostFreeLegend() {
        val a = availability(listOf(AvailabilityDay("2026-11-14", DayStatus.Match, slotPosition = SlotPosition.Single)), emptyList())
        assertFalse(AvailabilityUiMapper.hasPartial(AvailabilityUiMapper.monthRows(YearMonth.of(2026, 11), a, null, null, null, today)))
    }

    @Test
    fun partialFlagIgnoredOnDaysThatDontMatch() {
        val a = availability(listOf(AvailabilityDay("2026-11-14", DayStatus.Busy, listOf("a"), partial = true)), emptyList())
        assertFalse(cells(a).single { it.date == LocalDate.of(2026, 11, 14) }.partial)
    }

    @Test
    fun oneMissing_namesThem() {
        assertEquals("Someone missing", AvailabilityUiMapper.missingNote(listOf("a"), group = null, meId = null))
    }

    @Test
    fun mostFree_drawnUnderEveryoneFree() {
        val day = DayAvailability(
            date = "2026-11-13",
            members = emptyList(),
            everyoneFree = listOf(Interval("2026-11-13T18:00:00Z", "2026-11-13T20:00:00Z")),
            mostFree = listOf(Interval("2026-11-13T16:00:00Z", "2026-11-13T22:00:00Z")),
        )
        val bands = AvailabilityUiMapper.everyoneBands(day)
        assertEquals(listOf(false, true), bands.map { it.everyone })
    }

    @Test
    fun olderServers_decodeWithoutTheNewFields() {
        val json = NetworkModule.provideJson()
        val a = json.decodeFromString(
            Availability.serializer(),
            """{"from":"2026-11-01","to":"2026-11-30","visibleWeekdays":[4],"days":[{"date":"2026-11-13","status":"match","busyMemberIds":[],"slotPosition":"single","holiday":null}],
               "slots":[{"start":"2026-11-13T18:00:00Z","end":"2026-11-13T22:00:00Z","label":"Fri 13"}],"syncedCount":2,"memberCount":2}""",
        )
        assertFalse(a.days.single().partial)
        assertTrue(a.slots.single().missingMemberIds.isEmpty())
        assertEquals(null, a.slots.single().score)
        val d = json.decodeFromString(DayAvailability.serializer(), """{"date":"2026-11-13","members":[],"everyoneFree":[]}""")
        assertTrue(d.mostFree.isEmpty())
    }
}
