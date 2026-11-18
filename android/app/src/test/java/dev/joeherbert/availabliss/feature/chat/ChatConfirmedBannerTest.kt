package dev.joeherbert.availabliss.feature.chat

import dev.joeherbert.availabliss.core.network.model.GroupEvent
import dev.joeherbert.availabliss.core.network.model.Member
import dev.joeherbert.availabliss.core.network.model.Message
import dev.joeherbert.availabliss.core.network.model.MessageKind
import dev.joeherbert.availabliss.core.network.model.Role
import dev.joeherbert.availabliss.feature.common.MemberDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** "Everyone's in": confirmation system messages become the banner once their event is loaded. */
class ChatConfirmedBannerTest {
    private val directory = MemberDirectory(
        listOf(Member("me", "Sam Taylor", role = Role.Owner, synced = true, joinedAt = "2026-01-01T00:00:00.000Z")),
        meId = "me",
    )
    private val today = LocalDate.of(2026, 11, 16)
    private val now = Instant.parse("2026-11-16T12:00:00Z")

    private fun system(
        id: String,
        eventId: String?,
        createdAt: String = "2026-11-16T11:00:00.000Z",
        body: String = "Everyone's in for Thu 26 Nov · from 18:00",
    ) = Message(
        id = id, groupId = "g", authorId = null, kind = MessageKind.System,
        body = body, eventId = eventId, createdAt = createdAt,
    )

    private fun event(location: String? = null, proposalId: String? = "p1") = GroupEvent(
        id = "e1", groupId = "g", proposalId = proposalId, title = "Book club", location = location,
        start = "2026-11-26T18:00:00Z", end = "2026-11-26T18:00:00Z", createdBy = "me", createdAt = "2026-11-16T11:00:00.000Z",
    )

    private fun build(messages: List<Message>, events: Map<String, GroupEvent>) =
        buildChatItems(messages, directory, memberCount = 1, proposals = emptyMap(), events = events, today = today, now = now)

    @Test
    fun confirmationWithLoadedEventBecomesBanner() {
        val e = event(location = "Priya's flat")
        val banner = build(listOf(system("m1", "e1")), mapOf("e1" to e)).filterIsInstance<ChatItem.Confirmed>().single()
        assertEquals("m1", banner.id)
        assertEquals("e1", banner.eventId)
        assertEquals("p1", banner.proposalId)
        assertEquals("Book club", banner.title)
        assertTrue(banner.detail.endsWith(" · Priya's flat"))
        assertTrue(banner.detail.startsWith("Thu 26 Nov"))
        assertTrue(banner.recent)
    }

    @Test
    fun confirmedAnywaySaysItsOn() {
        val everyone = build(listOf(system("m1", "e1")), mapOf("e1" to event())).filterIsInstance<ChatItem.Confirmed>().single()
        assertEquals("Everyone's in", everyone.headline)
        val anyway = build(listOf(system("m1", "e1", body = "It's on for Thu 26 Nov · from 18:00 (3 of 4 can make it)")), mapOf("e1" to event()))
            .filterIsInstance<ChatItem.Confirmed>().single()
        assertEquals("It's on", anyway.headline)
    }

    @Test
    fun withoutLocationTheDetailIsJustWhen() {
        val banner = build(listOf(system("m1", "e1")), mapOf("e1" to event())).filterIsInstance<ChatItem.Confirmed>().single()
        assertFalse(banner.detail.contains("·  "))
        assertFalse(banner.detail.endsWith(" · "))
    }

    @Test
    fun confirmationWithoutLoadedEventStaysAPlainLine() {
        val items = build(listOf(system("m1", "e1")), emptyMap())
        assertTrue(items.none { it is ChatItem.Confirmed })
        assertEquals("Everyone's in for Thu 26 Nov · from 18:00", items.filterIsInstance<ChatItem.System>().single().text)
    }

    @Test
    fun systemMessageWithoutEventIdStaysAPlainLine() {
        val items = build(listOf(system("m1", null)), mapOf("e1" to event()))
        assertTrue(items.none { it is ChatItem.Confirmed })
        assertEquals(1, items.filterIsInstance<ChatItem.System>().size)
    }

    @Test
    fun onlyConfirmationsFromTheLastDayAreRecent() {
        val old = build(listOf(system("m1", "e1", createdAt = "2026-11-15T11:59:00.000Z")), mapOf("e1" to event()))
            .filterIsInstance<ChatItem.Confirmed>().single()
        assertFalse(old.recent)
    }

    @Test
    fun eventWithoutProposalHasNoProposalToOpen() {
        val banner = build(listOf(system("m1", "e1")), mapOf("e1" to event(proposalId = null))).filterIsInstance<ChatItem.Confirmed>().single()
        assertNull(banner.proposalId)
    }
}
