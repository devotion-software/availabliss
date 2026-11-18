package dev.joeherbert.availabliss.core.network

import dev.joeherbert.availabliss.core.network.model.CalendarProvider
import dev.joeherbert.availabliss.core.network.model.ConnectionsResponse
import dev.joeherbert.availabliss.core.network.model.NotificationKind
import dev.joeherbert.availabliss.core.network.model.NotificationsResponse
import dev.joeherbert.availabliss.core.network.model.Proposal
import dev.joeherbert.availabliss.core.network.model.RespondRequest
import dev.joeherbert.availabliss.core.network.model.ResponseKind
import org.junit.Assert.assertEquals
import org.junit.Test

class LenientEnumTest {
    private val json = NetworkModule.provideJson()

    @Test
    fun `notification kinds decode by wire value and unknown kinds fall back`() {
        val body = """{"unread":2,"notifications":[
            {"id":"1","kind":"proposal.confirmed","title":"t","body":"b","createdAt":"2026-11-03T10:00:00Z"},
            {"id":"2","kind":"poll.closed","title":"t","body":"b","createdAt":"2026-11-03T10:00:00Z"},
            {"id":"3","kind":"invite.received","title":"t","body":"b","data":{"token":"abc"},"createdAt":"2026-11-03T10:00:00Z"}]}"""
        val kinds = json.decodeFromString(NotificationsResponse.serializer(), body).notifications.map { it.kind }
        assertEquals(listOf(NotificationKind.ProposalConfirmed, NotificationKind.Unknown, NotificationKind.InviteReceived), kinds)
    }

    @Test
    fun `calendar providers decode by wire value and unknown providers fall back`() {
        val body = """{"connections":[
            {"id":"a","provider":"caldav","label":"iCloud"},
            {"id":"b","provider":"fastmail","label":"Fastmail"}]}"""
        val providers = json.decodeFromString(ConnectionsResponse.serializer(), body).connections.map { it.provider }
        assertEquals(listOf(CalendarProvider.CalDav, CalendarProvider.Unknown), providers)
        assertEquals("\"microsoft\"", json.encodeToString(CalendarProvider.serializer(), CalendarProvider.Microsoft))
    }

    @Test
    fun `proposal replies decode by wire value, maybe included, and unknown replies count as pending`() {
        val body = """{"id":"p","groupId":"g","start":"2026-11-20T18:00:00Z","end":"2026-11-20T21:00:00Z",
            "status":"open","createdBy":"a","createdAt":"2026-11-18T10:00:00Z","responses":[
            {"userId":"a","response":"accepted","updatedAt":"2026-11-18T10:00:00Z"},
            {"userId":"b","response":"maybe","updatedAt":"2026-11-18T10:00:00Z"},
            {"userId":"c","response":"declined","updatedAt":"2026-11-18T10:00:00Z"},
            {"userId":"d","response":"pending","updatedAt":"2026-11-18T10:00:00Z"},
            {"userId":"e","response":"tentative","updatedAt":"2026-11-18T10:00:00Z"}]}"""
        val responses = json.decodeFromString(Proposal.serializer(), body).responses.map { it.response }
        assertEquals(
            listOf(ResponseKind.Accepted, ResponseKind.Maybe, ResponseKind.Declined, ResponseKind.Pending, ResponseKind.Pending),
            responses,
        )
        assertEquals("""{"response":"maybe"}""", json.encodeToString(RespondRequest.serializer(), RespondRequest(ResponseKind.Maybe)))
    }
}
