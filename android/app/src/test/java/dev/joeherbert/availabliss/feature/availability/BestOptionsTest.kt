package dev.joeherbert.availabliss.feature.availability

import dev.joeherbert.availabliss.core.network.model.Slot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class BestOptionsTest {
    private val now = Instant.parse("2026-11-10T12:00:00Z")
    private val names = mapOf("me" to "You", "ana" to "Ana", "tom" to "Tom", "sam" to "Sam")
    private val nameOf = { id: String -> names[id] ?: "Someone" }

    private fun slot(day: Int, missing: List<String> = emptyList(), score: Double? = null, fairFor: List<String> = emptyList(), preferredBy: List<String> = emptyList()) =
        Slot("2026-11-%02dT18:00:00Z".format(day), "2026-11-%02dT22:00:00Z".format(day), "Day $day", preferredBy, missing, score, fairFor)

    @Test
    fun rank_fewerMissingThenScoreThenPreferencesThenSoonest() {
        val slots = listOf(
            slot(12, missing = listOf("ana")),
            slot(13, score = 1.0),
            slot(14, score = 3.0),
            slot(15),
            slot(16, preferredBy = listOf("ana")),
            slot(11, missing = listOf("ana", "tom"), score = 9.0),
        )
        assertEquals(listOf(14, 13, 16, 15, 12, 11), BestOptions.rank(slots).map { it.label.removePrefix("Day ").toInt() })
    }

    @Test
    fun options_skipPastSlotsAndKeepTheTopThree() {
        val slots = listOf(slot(9), slot(11), slot(12), slot(13), slot(14))
        val options = BestOptions.options(slots, now, nameOf)
        assertEquals(listOf("Day 11", "Day 12", "Day 13"), options.map { it.label })
        // No scores (not Pro): nothing is "Best for the group".
        assertFalse(options.any { it.best })
    }

    @Test
    fun options_topScoredSlotIsBestForTheGroup() {
        val options = BestOptions.options(listOf(slot(11, score = 1.0), slot(12, score = 2.0, fairFor = listOf("ana"))), now, nameOf)
        assertEquals("Day 12", options[0].label)
        assertTrue(options[0].best)
        assertEquals("Fair for Ana, who missed out last time", options[0].fair)
        assertFalse(options[1].best)
    }

    @Test
    fun options_slotWithSomeoneMissingIsNeverBest() {
        val options = BestOptions.options(listOf(slot(11, missing = listOf("tom"), score = 5.0)), now, nameOf)
        assertFalse(options.single().best)
        assertEquals("Tom can't make it", options.single().missing)
    }

    @Test
    fun missingLine_wording() {
        assertNull(BestOptions.missingLine(emptyList(), nameOf))
        assertEquals("You can't make it", BestOptions.missingLine(listOf("me"), nameOf))
        assertEquals("Ana and Tom can't make it", BestOptions.missingLine(listOf("ana", "tom"), nameOf))
        assertEquals("3 people can't make it", BestOptions.missingLine(listOf("ana", "tom", "sam"), nameOf))
    }

    @Test
    fun fairLine_wording() {
        assertNull(BestOptions.fairLine(emptyList(), nameOf))
        assertEquals("Fair for you and Ana, who missed out last time", BestOptions.fairLine(listOf("me", "ana"), nameOf))
    }
}
