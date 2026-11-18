package dev.joeherbert.availabliss.feature.availability

import dev.joeherbert.availabliss.core.network.model.NextSuggestion
import dev.joeherbert.availabliss.core.network.model.Rhythm
import dev.joeherbert.availabliss.core.network.model.Slot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** Shortlisting Best options for a date poll, and the "Next one" card's wording. */
class ShortlistTest {
    private fun slot(day: Int, missing: List<String> = emptyList()) =
        Slot("2026-11-%02dT18:00:00Z".format(day), "2026-11-%02dT22:00:00Z".format(day), "Day $day", missingMemberIds = missing)

    @Test
    fun toggleAddsSoonestFirstAndTakesOff() {
        var picked = Shortlist.toggle(emptyList(), slot(14))
        picked = Shortlist.toggle(picked, slot(12))
        assertEquals(listOf("Day 12", "Day 14"), picked.map { it.label })
        // The same time again (say, refetched) takes it off.
        assertEquals(listOf("Day 14"), Shortlist.toggle(picked, slot(12).copy(label = "Refetched")).map { it.label })
    }

    @Test
    fun atMostFiveAndAskingNeedsTwo() {
        var picked = emptyList<Slot>()
        (11..16).forEach { picked = Shortlist.toggle(picked, slot(it)) }
        assertEquals(Shortlist.MAX, picked.size)
        assertFalse(picked.any { it.label == "Day 16" })
        assertTrue(Shortlist.canAsk(picked))
        assertFalse(Shortlist.canAsk(picked.take(1)))
        assertTrue(Shortlist.canAsk(picked.take(2)))
        assertEquals("3 picked · Ask the group", Shortlist.barLabel(3))
    }

    @Test
    fun bestOptionsMarkShortlistedRows() {
        val now = Instant.parse("2026-11-10T12:00:00Z")
        val options = BestOptions.options(listOf(slot(11), slot(12)), now, { it }, shortlist = listOf(slot(12)))
        assertEquals(listOf(false, true), options.map { it.shortlisted })
    }

    @Test
    fun nextOneSaysWhoCanMakeItOrThatNothingWorks() {
        val nameOf = { id: String -> mapOf("ana" to "Ana").getValue(id) }
        val weekly = Rhythm("week", prompt = true)
        val free = NextOne.ui(NextSuggestion("2026-11-20", slot(20)), weekly, nameOf)!!
        assertEquals(LocalDate.of(2026, 11, 20), free.target)
        assertEquals("Every week", free.rhythmLabel)
        assertEquals("Everyone's free", free.note)

        val missing = NextOne.ui(NextSuggestion("2026-11-20", slot(21, missing = listOf("ana"))), Rhythm("month"), nameOf)!!
        assertEquals("Ana can't make it", missing.note)
        assertEquals("Every month", missing.rhythmLabel)

        val none = NextOne.ui(NextSuggestion("2026-11-20", null), Rhythm("two-weeks"), nameOf)!!
        assertNull(none.slot)
        assertNull(none.note)
        assertEquals("Every two weeks", none.rhythmLabel)

        // A frequency this build doesn't know still shows the card.
        assertEquals("Regularly", NextOne.ui(NextSuggestion("2026-11-20"), Rhythm("fortnightly"), nameOf)!!.rhythmLabel)
        assertNull(NextOne.ui(NextSuggestion("soon"), weekly, nameOf))
    }
}
