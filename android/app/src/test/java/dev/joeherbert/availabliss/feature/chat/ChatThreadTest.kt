package dev.joeherbert.availabliss.feature.chat

import dev.joeherbert.availabliss.core.network.NetworkModule
import dev.joeherbert.availabliss.core.network.model.Member
import dev.joeherbert.availabliss.core.network.model.Message
import dev.joeherbert.availabliss.core.network.model.MessageKind
import dev.joeherbert.availabliss.core.network.model.MessageReply
import dev.joeherbert.availabliss.core.network.model.MessageThread
import dev.joeherbert.availabliss.core.network.model.Role
import dev.joeherbert.availabliss.feature.common.MemberDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Threads: which live messages join an open thread, who a reply in it goes to, and View thread. */
class ChatThreadTest {
    private fun msg(id: String, author: String?, replyTo: String? = null, deleted: Boolean = false) = Message(
        id = id,
        groupId = "g",
        authorId = author,
        kind = MessageKind.Text,
        body = if (deleted) null else "Hello $id",
        createdAt = "2026-11-16T12:00:00.000Z",
        deleted = deleted,
        replyTo = replyTo?.let { MessageReply(id = it, authorId = "p", authorName = null, kind = MessageKind.Text, text = "Hello $it") },
    )

    private val thread = listOf(msg("1", "p"), msg("2", "me", replyTo = "1"), msg("3", "p", replyTo = "2"))

    @Test
    fun repliesToTheThreadJoinIt() {
        assertEquals(listOf("1", "2", "3", "4"), thread.withThreadMessage(msg("4", "t", replyTo = "2")).map { it.id })
        // Unrelated messages, and replies to messages outside the thread, stay out.
        assertEquals(thread, thread.withThreadMessage(msg("5", "t")))
        assertEquals(thread, thread.withThreadMessage(msg("6", "t", replyTo = "99")))
        // An update to one already there replaces it.
        assertTrue(thread.withThreadMessage(msg("3", "p", replyTo = "2", deleted = true))[2].deleted)
    }

    @Test
    fun repliesGoToThePickedMessageElseTheLatestFromSomeoneElse() {
        assertEquals("3", threadReplyTarget(thread, pickedId = null, meId = "me")?.id)
        assertEquals("2", threadReplyTarget(thread, pickedId = "2", meId = "me")?.id)
        // Straight after you send, not to yourself.
        val afterMine = thread + msg("4", "me", replyTo = "3")
        assertEquals("3", threadReplyTarget(afterMine, pickedId = null, meId = "me")?.id)
        // A picked message deleted since falls back to the latest.
        val deleted = listOf(msg("1", "p"), msg("2", "t", replyTo = "1", deleted = true))
        assertEquals("1", threadReplyTarget(deleted, pickedId = "2", meId = "me")?.id)
        // Only your own messages: the latest of those.
        assertEquals("2", threadReplyTarget(listOf(msg("1", "me"), msg("2", "me", replyTo = "1")), null, "me")?.id)
    }

    @Test
    fun repliesAndRepliedToMessagesOfferViewThread() {
        val directory = MemberDirectory(
            listOf(Member("me", "Sam Taylor", role = Role.Owner, synced = true, joinedAt = "2026-01-01T00:00:00.000Z")),
            meId = "me",
        )
        val items = buildChatItems(
            thread + msg("9", "p"),
            directory,
            memberCount = 2,
            proposals = emptyMap(),
            events = emptyMap(),
            today = LocalDate.of(2026, 11, 16),
        ).filterIsInstance<ChatItem.Text>().associateBy { it.id }
        assertTrue(items.getValue("1").inThread)
        assertTrue(items.getValue("3").inThread)
        assertFalse(items.getValue("9").inThread)
    }

    @Test
    fun decodesAThread() {
        val json = NetworkModule.provideJson()
        val decoded = json.decodeFromString(
            MessageThread.serializer(),
            """{"rootId":"1","truncated":false,"messages":[{"id":"1","groupId":"g","authorId":"p","kind":"text","body":"Hi",
               "poll":null,"proposalId":null,"eventId":null,"createdAt":"2026-11-16T12:00:00.000Z","editedAt":null,
               "deleted":false,"receipt":null,"replyTo":null}]}""",
        )
        assertEquals("1", decoded.rootId)
        assertEquals(listOf("1"), decoded.messages.map { it.id })
    }
}
