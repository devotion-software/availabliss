package dev.joeherbert.availabliss.core.network

import dev.joeherbert.availabliss.core.network.model.CreateInviteRequest
import dev.joeherbert.availabliss.core.network.model.GroupInvite
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class InviteLinkTest {
    private val json = NetworkModule.provideJson()

    @Test
    fun `a link's limit is sent as a number, null for no limit, and left out otherwise`() {
        assertEquals("""{"maxUses":5}""", json.encodeToString(CreateInviteRequest.serializer(), CreateInviteRequest(maxUses = JsonPrimitive(5))))
        assertEquals("""{"maxUses":null}""", json.encodeToString(CreateInviteRequest.serializer(), CreateInviteRequest(maxUses = JsonNull)))
        assertEquals("""{"emails":["a@b.c"]}""", json.encodeToString(CreateInviteRequest.serializer(), CreateInviteRequest(emails = listOf("a@b.c"))))
    }

    @Test
    fun `links describe how many people can still join`() {
        fun link(maxUses: Int?, uses: Int?) = GroupInvite(id = "i", url = "u", maxUses = maxUses, uses = uses, expiresAt = "2026-12-01T00:00:00Z")
        assertEquals("Link for one person", link(null, null).linkUsage) // an older server: single-use
        assertEquals("One person can join with this link.", link(1, 0).linkSummary)
        assertEquals("5 people can join with this link.", link(5, 0).linkSummary)
        assertEquals("3 of 5 people can still join with this link.", link(5, 2).linkSummary)
        assertEquals("3 of 5 left", link(5, 2).linkUsage)
        assertEquals("Anyone with this link can join.", link(null, 4).linkSummary)
        assertEquals("Unlimited · 4 joined", link(null, 4).linkUsage)
        assertEquals("Unlimited", link(null, 0).linkUsage)
    }
}
