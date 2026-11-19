package dev.joeherbert.availabliss.core.network

import dev.joeherbert.availabliss.core.network.model.CreateInviteRequest
import dev.joeherbert.availabliss.core.network.model.GroupInvite
import dev.joeherbert.availabliss.core.network.model.MAX_LINK_USES
import dev.joeherbert.availabliss.core.network.model.linkLimitFrom
import dev.joeherbert.availabliss.core.network.model.linkLimitInput
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

    @Test
    fun `a link's typed limit is a whole number from 1 to 1000, one when empty`() {
        assertEquals(1, linkLimitFrom(""))
        assertEquals(1, linkLimitFrom("0"))
        assertEquals(1, linkLimitFrom("000"))
        assertEquals(7, linkLimitFrom("7"))
        assertEquals(12, linkLimitFrom("012"))
        assertEquals(1000, linkLimitFrom("1000"))
        assertEquals(MAX_LINK_USES, linkLimitFrom("9999"))
        assertEquals(MAX_LINK_USES, linkLimitFrom("99999999999999999999"))
        assertEquals(25, linkLimitFrom(" 2-5 "))
    }

    @Test
    fun `the limit field keeps only digits, four at most`() {
        assertEquals("12", linkLimitInput("1a2"))
        assertEquals("1234", linkLimitInput("123456"))
        assertEquals("", linkLimitInput("-."))
    }
}
