package dev.joeherbert.availabliss.core.data

import dev.joeherbert.availabliss.core.network.NetworkModule
import dev.joeherbert.availabliss.core.network.model.LinkAccountRequest
import dev.joeherbert.availabliss.core.network.model.LinkedAccounts
import dev.joeherbert.availabliss.core.network.model.MergeCandidate
import dev.joeherbert.availabliss.feature.you.mergePrompt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkAccountTest {
    private val json = NetworkModule.provideJson()
    private val request = LinkAccountRequest("google", "id-token-123")

    @Test
    fun `decodes linked accounts`() {
        val accounts = json.decodeFromString(
            LinkedAccounts.serializer(),
            """{"linked":[{"provider":"apple","linkedAt":"2026-10-01T09:00:00.000Z"}],"available":["apple","google"]}""",
        )
        assertTrue(accounts.isLinked("apple"))
        assertFalse(accounts.isLinked("google"))
        assertEquals(listOf("apple", "google"), accounts.available)
    }

    @Test
    fun `link request omits nonce and merge unless set`() {
        assertEquals(
            """{"provider":"google","idToken":"id-token-123"}""",
            json.encodeToString(LinkAccountRequest.serializer(), request),
        )
        assertEquals(
            """{"provider":"google","idToken":"id-token-123","merge":true}""",
            json.encodeToString(LinkAccountRequest.serializer(), request.copy(merge = true)),
        )
    }

    @Test
    fun `409 with an account asks to merge the same request`() {
        val body = """{"message":"That sign-in already has its own Availabliss account","code":"conflict","account":{"name":"Sam","email":"sam@x.com","groups":3}}"""
        val result = linkFailure(json, 409, body, request)
        assertEquals(LinkResult.NeedsMerge(MergeCandidate("Sam", "sam@x.com", 3), request), result)
    }

    @Test
    fun `409 without an account, or after merging, is an error`() {
        val plain = """{"message":"That sign-in is already linked to another account","code":"conflict"}"""
        assertEquals(LinkResult.Failed("That sign-in is already linked to another account"), linkFailure(json, 409, plain, request))
        val withAccount = """{"message":"Taken","code":"conflict","account":{"name":"Sam","email":"sam@x.com","groups":1}}"""
        assertEquals(LinkResult.Failed("Taken"), linkFailure(json, 409, withAccount, request.copy(merge = true)))
    }

    @Test
    fun `other errors use the normal error body`() {
        val body = """{"message":"apple sign-in isn't set up on this server","code":"validation_error"}"""
        assertEquals(LinkResult.Failed("apple sign-in isn't set up on this server"), linkFailure(json, 400, body, request))
    }

    @Test
    fun `merge prompt names the account`() {
        assertEquals(
            "Merge Sam (sam@x.com, in 3 groups) into this account? Its groups, busy times, calendars, chats and Pro " +
                "move here, then the other account is deleted. This can't be undone.",
            mergePrompt(MergeCandidate("Sam", "sam@x.com", 3)),
        )
        assertTrue(mergePrompt(MergeCandidate("Al", "a@x.com", 1)).contains("in 1 group)"))
    }
}
