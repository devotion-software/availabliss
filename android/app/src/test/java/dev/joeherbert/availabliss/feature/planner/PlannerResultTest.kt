package dev.joeherbert.availabliss.feature.planner

import dev.joeherbert.availabliss.core.network.NetworkModule
import dev.joeherbert.availabliss.core.network.model.PlannerRequest
import dev.joeherbert.availabliss.core.network.model.PlannerResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlannerResultTest {
    private val json = NetworkModule.provideJson()

    @Test
    fun parsesThePlannerResult() {
        val r = json.decodeFromString(PlannerResult.serializer(), PlannerFixtures.RESULT_JSON)
        assertEquals("Fri 27 – Sun 29 Nov", r.best.slot.label)
        assertEquals("2026-11-27T18:00:00.000Z", r.best.slot.start)
        assertTrue(r.best.why.startsWith("The only weekend"))
        assertEquals(1, r.alternatives.size)
        assertEquals(listOf("Catbells at sunrise", "Pub quiz in Keswick"), r.ideas.map { it.title })
        assertEquals(3, r.checklist.size)
        assertEquals("Lakes weekend", r.proposalTitle)
    }

    @Test
    fun missingListsDefaultToEmpty() {
        val minimal = """{"best":{"slot":{"start":"a","end":"b","label":"c"},"why":"d"},"proposalTitle":"t","proposalNote":"n"}"""
        val r = json.decodeFromString(PlannerResult.serializer(), minimal)
        assertTrue(r.alternatives.isEmpty() && r.ideas.isEmpty() && r.checklist.isEmpty())
    }

    @Test
    fun noNotesAreLeftOutOfTheRequest() {
        assertEquals("{}", json.encodeToString(PlannerRequest.serializer(), PlannerRequest()))
        assertEquals("""{"notes":"Keep it cheap"}""", json.encodeToString(PlannerRequest.serializer(), PlannerRequest("Keep it cheap")))
    }
}
