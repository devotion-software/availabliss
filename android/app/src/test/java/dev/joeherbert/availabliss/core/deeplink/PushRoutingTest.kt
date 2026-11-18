package dev.joeherbert.availabliss.core.deeplink

import org.junit.Assert.assertEquals
import org.junit.Test

class PushRoutingTest {
    private fun route(vararg pairs: Pair<String, String>) = DeepLink.fromPushData(mapOf(*pairs))

    @Test
    fun `plan reminders open the proposal when there is one, else the group`() {
        assertEquals(
            DeepLink.Proposal("p1"),
            route("kind" to "reminder", "groupId" to "g1", "eventId" to "e1", "proposalId" to "p1", "reminder" to "day"),
        )
        assertEquals(DeepLink.Proposal("p2"), route("kind" to "reminder", "groupId" to "g1", "proposalId" to "p2", "reminder" to "reply"))
        assertEquals(DeepLink.GroupCalendar("g1"), route("kind" to "reminder", "groupId" to "g1", "eventId" to "e1", "reminder" to "hour"))
        assertEquals(DeepLink.Activity, route("kind" to "reminder"))
    }

    @Test
    fun `unknown kinds still open the proposal, the group or Activity`() {
        assertEquals(DeepLink.Proposal("p1"), route("kind" to "poll.closed", "groupId" to "g1", "proposalId" to "p1"))
        assertEquals(DeepLink.GroupCalendar("g1"), route("kind" to "poll.closed", "groupId" to "g1"))
        assertEquals(DeepLink.Activity, route("kind" to "poll.closed"))
    }

    @Test
    fun `existing kinds keep their routes`() {
        assertEquals(DeepLink.GroupChat("g1"), route("kind" to "message", "groupId" to "g1"))
        assertEquals(DeepLink.GroupCalendar("g1"), route("kind" to "role.changed", "groupId" to "g1"))
        assertEquals(DeepLink.MyTime, route("kind" to "nudge"))
    }
}
