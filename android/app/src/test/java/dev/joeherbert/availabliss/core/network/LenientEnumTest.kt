package dev.joeherbert.availabliss.core.network

import dev.joeherbert.availabliss.core.network.model.CalendarProvider
import dev.joeherbert.availabliss.core.network.model.ConnectionsResponse
import dev.joeherbert.availabliss.core.network.model.NotificationKind
import dev.joeherbert.availabliss.core.network.model.NotificationsResponse
import org.junit.Assert.assertEquals
import org.junit.Test

class LenientEnumTest {
    private val json = NetworkModule.provideJson()

    @Test
    fun `notification kinds decode by wire value and unknown kinds fall back`() {
        val body = """{"unread":2,"notifications":[
            {"id":"1","kind":"proposal.confirmed","title":"t","body":"b","createdAt":"2026-11-03T10:00:00Z"},
            {"id":"2","kind":"poll.closed","title":"t","body":"b","createdAt":"2026-11-03T10:00:00Z"}]}"""
        val kinds = json.decodeFromString(NotificationsResponse.serializer(), body).notifications.map { it.kind }
        assertEquals(listOf(NotificationKind.ProposalConfirmed, NotificationKind.Unknown), kinds)
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
}
