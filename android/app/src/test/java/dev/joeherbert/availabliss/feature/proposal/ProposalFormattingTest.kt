package dev.joeherbert.availabliss.feature.proposal

import dev.joeherbert.availabliss.core.network.model.Proposal
import dev.joeherbert.availabliss.core.network.model.ProposalResponse
import dev.joeherbert.availabliss.core.network.model.ProposalStatus
import dev.joeherbert.availabliss.core.network.model.ResponseKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Maybes (soft yeses) are counted apart from firm yeses wherever replies are counted. */
class ProposalFormattingTest {
    private fun proposal(vararg replies: ResponseKind, status: ProposalStatus = ProposalStatus.Open) = Proposal(
        id = "p", groupId = "g", start = "2026-11-20T18:00:00Z", end = "2026-11-20T21:00:00Z",
        status = status, createdBy = "u0", createdAt = "2026-11-18T10:00:00Z",
        responses = replies.mapIndexed { i, r -> ProposalResponse("u$i", r, updatedAt = "2026-11-18T10:00:00Z") },
    )

    @Test
    fun maybesAreCountedApartFromYeses() {
        val p = proposal(ResponseKind.Accepted, ResponseKind.Accepted, ResponseKind.Maybe, ResponseKind.Declined, ResponseKind.Pending)
        assertEquals(2, ProposalFormatting.acceptedCount(p))
        assertEquals(1, ProposalFormatting.maybeCount(p))
    }

    @Test
    fun withMaybesOnlyAddsThemWhenThereAreAny() {
        assertEquals("3 of 5 in", ProposalFormatting.withMaybes("3 of 5 in", 0))
        assertEquals("3 of 5 in · 1 maybe", ProposalFormatting.withMaybes("3 of 5 in", 1))
        assertEquals("3 of 5 in · 2 maybe", ProposalFormatting.withMaybes("3 of 5 in", 2))
    }

    @Test
    fun aMaybeIsntEveryoneIn() {
        assertTrue(proposal(ResponseKind.Accepted, ResponseKind.Accepted).everyoneIn)
        assertFalse(proposal(ResponseKind.Accepted, ResponseKind.Maybe).everyoneIn)
    }

    @Test
    fun cardStatusShowsMaybesBesideTheYeses() {
        val open = proposal(ResponseKind.Accepted, ResponseKind.Maybe, ResponseKind.Pending)
        assertEquals("1 of 3 accepted · 1 maybe", ProposalFormatting.card(open, null, memberCount = 3).status)
        assertEquals("1 of 2 accepted", ProposalFormatting.card(proposal(ResponseKind.Accepted, ResponseKind.Pending), null, 2).status)
    }

    @Test
    fun confirmedStatusCountsMaybes() {
        val anyway = proposal(ResponseKind.Accepted, ResponseKind.Accepted, ResponseKind.Maybe, ResponseKind.Declined, status = ProposalStatus.Confirmed)
        assertEquals("It's on · 2 of 4 coming · 1 maybe", ProposalFormatting.confirmedStatus(anyway))
        val everyone = proposal(ResponseKind.Accepted, ResponseKind.Accepted, status = ProposalStatus.Confirmed)
        assertEquals("Everyone's in", ProposalFormatting.confirmedStatus(everyone))
    }
}
