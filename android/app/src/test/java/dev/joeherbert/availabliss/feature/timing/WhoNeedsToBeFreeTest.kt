package dev.joeherbert.availabliss.feature.timing

import dev.joeherbert.availabliss.core.network.NetworkModule
import dev.joeherbert.availabliss.core.network.model.Requirement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WhoNeedsToBeFreeTest {
    private val base = RequirementPresets.defaultDraft(LocalDate.of(2026, 11, 1), "Europe/London")
    private val members = listOf("ana", "sam", "tom", "priya", "leo")

    @Test
    fun atLeast_startsOneShortOfEveryone() {
        val d = WhoNeedsToBeFree.setEveryone(base, everyone = false, members)
        assertEquals(4, d.quorum)
        assertNull(WhoNeedsToBeFree.setEveryone(d, everyone = true, members).quorum)
    }

    @Test
    fun atLeast_keepsAnExistingQuorum() {
        val d = base.copy(quorum = 2)
        assertEquals(2, WhoNeedsToBeFree.setEveryone(d, everyone = false, members).quorum)
    }

    @Test
    fun quorum_staysBetweenOneAndTheRequiredMembers() {
        var d = base.copy(quorum = 4)
        d = WhoNeedsToBeFree.changeQuorum(d, 5, members)
        assertEquals(5, d.quorum)
        d = WhoNeedsToBeFree.changeQuorum(d, -10, members)
        assertEquals(1, d.quorum)
    }

    @Test
    fun changeQuorum_doesNothingForEveryone() {
        assertNull(WhoNeedsToBeFree.changeQuorum(base, 1, members).quorum)
    }

    @Test
    fun optionalMembers_dontCountAndShrinkTheQuorum() {
        var d = base.copy(quorum = 5)
        d = WhoNeedsToBeFree.setOptional(d, "leo", optional = true, members)
        d = WhoNeedsToBeFree.setOptional(d, "tom", optional = true, members)
        assertEquals(setOf("leo", "tom"), d.optionalMemberIds)
        assertEquals(3, WhoNeedsToBeFree.requiredCount(d, members))
        assertEquals(3, d.quorum)
        d = WhoNeedsToBeFree.setOptional(d, "tom", optional = false, members)
        assertEquals(setOf("leo"), d.optionalMemberIds)
        // Not raised again: the admin chose the lower number.
        assertEquals(3, d.quorum)
    }

    @Test
    fun quorumAndOptionalMembers_roundTripThroughTheRequirement() {
        val d = base.copy(quorum = 3, optionalMemberIds = setOf("tom", "ana"))
        val r = d.toRequirement()
        assertEquals(3, r.quorum)
        assertEquals(listOf("ana", "tom"), r.optionalMemberIds)
        assertEquals(d, RequirementPresets.fromRequirement(r))
    }

    @Test
    fun wire_everyoneLeavesTheFieldsOutAndOlderServersDecode() {
        val json = NetworkModule.provideJson()
        val everyone = json.encodeToString(Requirement.serializer(), base.toRequirement())
        assertFalse("quorum" in everyone)
        assertFalse("optionalMemberIds" in everyone)
        val decoded = json.decodeFromString(Requirement.serializer(), everyone)
        assertNull(decoded.quorum)
        assertTrue(decoded.optionalMemberIds.isEmpty())

        val most = json.encodeToString(Requirement.serializer(), base.copy(quorum = 3, optionalMemberIds = setOf("tom")).toRequirement())
        assertTrue("\"quorum\":3" in most)
        assertTrue("\"optionalMemberIds\":[\"tom\"]" in most)
        assertEquals(3, json.decodeFromString(Requirement.serializer(), most).quorum)
    }

    @Test
    fun templates_ignoreWhoNeedsToBeFree() {
        val plain = base.toRequirement()
        val most = base.copy(quorum = 2, optionalMemberIds = setOf("tom")).toRequirement()
        assertTrue(Templates.sameTiming(plain, most))
        val fromTemplate = Templates.requirementFromTemplate(most, LocalDate.of(2026, 11, 1), "Europe/London")
        assertNull(fromTemplate.quorum)
        assertTrue(fromTemplate.optionalMemberIds.isEmpty())
    }
}
