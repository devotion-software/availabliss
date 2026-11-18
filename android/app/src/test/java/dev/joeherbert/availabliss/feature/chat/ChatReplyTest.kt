package dev.joeherbert.availabliss.feature.chat

import dev.joeherbert.availabliss.core.live.LivePayload
import dev.joeherbert.availabliss.core.live.decode
import dev.joeherbert.availabliss.core.network.NetworkModule
import dev.joeherbert.availabliss.core.network.model.LiveEvent
import dev.joeherbert.availabliss.core.network.model.Member
import dev.joeherbert.availabliss.core.network.model.Message
import dev.joeherbert.availabliss.core.network.model.MessageKind
import dev.joeherbert.availabliss.core.network.model.MessageReply
import dev.joeherbert.availabliss.core.network.model.Poll
import dev.joeherbert.availabliss.core.network.model.PollOption
import dev.joeherbert.availabliss.core.network.model.Role
import dev.joeherbert.availabliss.core.network.model.SendTextRequest
import dev.joeherbert.availabliss.core.network.model.canReply
import dev.joeherbert.availabliss.designsystem.component.chat.BubbleQuote
import dev.joeherbert.availabliss.feature.common.MemberDirectory
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Replies: quotes in the list, keeping them current after edits and deletes, and the wire format. */
class ChatReplyTest {
    private val directory = MemberDirectory(
        listOf(
            Member("me", "Sam Taylor", role = Role.Owner, synced = true, joinedAt = "2026-01-01T00:00:00.000Z"),
            Member("p", "Priya Shah", role = Role.Member, synced = true, joinedAt = "2026-01-01T00:00:00.000Z"),
        ),
        meId = "me",
    )

    private fun msg(id: String, author: String?, body: String? = "Hello $id", replyTo: MessageReply? = null, deleted: Boolean = false) = Message(
        id = id,
        groupId = "g",
        authorId = author,
        kind = MessageKind.Text,
        body = body,
        createdAt = "2026-11-16T12:00:00.000Z",
        deleted = deleted,
        replyTo = replyTo,
    )

    private fun quoteOf(id: String, author: String?, text: String?, authorName: String? = null) =
        MessageReply(id = id, authorId = author, authorName = authorName, kind = MessageKind.Text, text = text)

    private fun build(messages: List<Message>) = buildChatItems(
        messages,
        directory,
        memberCount = 2,
        proposals = emptyMap(),
        events = emptyMap(),
        today = LocalDate.of(2026, 11, 16),
    )

    private fun List<ChatItem>.text(id: String) = filterIsInstance<ChatItem.Text>().single { it.id == id }

    @Test
    fun repliesQuoteTheOriginalWithItsAuthor() {
        val items = build(
            listOf(
                msg("1", "p", "Who's bringing snacks?"),
                msg("2", "me", "Me!", replyTo = quoteOf("1", "p", "Who's bringing snacks?")),
                msg("3", "p", "Great", replyTo = quoteOf("2", "me", "Me!")),
                msg("4", "me", "Hi", replyTo = quoteOf("0", "gone", "Old news", authorName = "Leo Gone")),
            ),
        )
        assertEquals(BubbleQuote("1", "Priya Shah", "Who's bringing snacks?"), items.text("2").quote)
        assertEquals("You", items.text("3").quote?.name)
        // Someone who has left: the name the server sent.
        assertEquals("Leo Gone", items.text("4").quote?.name)
        assertNull(items.text("1").quote)
        assertTrue(items.text("1").canReply)
    }

    @Test
    fun quotesFollowEditsAndDeletes() {
        val list = listOf(msg("1", "p", "Snacks?"), msg("2", "me", "Me!", replyTo = quoteOf("1", "p", "Snacks?")))
        val edited = list.withUpdated(msg("1", "p", "Drinks?  And\nsnacks?"))
        assertEquals("Drinks? And snacks?", edited[1].replyTo?.text)

        val deleted = edited.withDeleted("1")
        assertTrue(deleted[1].replyTo!!.deleted)
        assertNull(deleted[1].replyTo!!.text)
        assertEquals("Deleted message", build(deleted).text("2").quote?.snippet)

        // A live delete of an original that isn't loaded still updates the quote.
        val onlyReply = listOf(msg("2", "me", "Me!", replyTo = quoteOf("1", "p", "Snacks?")))
        assertTrue(onlyReply.withUpdated(msg("1", "p", body = null, deleted = true))[0].replyTo!!.deleted)
    }

    @Test
    fun pollQuotesShowTheQuestion() {
        val reply = MessageReply("1", "p", kind = MessageKind.Poll, text = "Which park?")
        assertEquals("Poll: Which park?", reply.snippet)
        val poll = Message(
            id = "1",
            groupId = "g",
            authorId = "p",
            kind = MessageKind.Poll,
            createdAt = "2026-11-16T12:00:00.000Z",
            poll = Poll("q", "Which park?", options = listOf(PollOption("a", "North"))),
        )
        assertTrue(poll.canReply)
        assertFalse(msg("2", "p", body = null, deleted = true).canReply)
    }

    @Test
    fun decodesReplyToAndOmitsANullReplyId() {
        val json = NetworkModule.provideJson()
        val payload = json.parseToJsonElement(
            """{"id":"2","groupId":"g","authorId":"me","kind":"text","body":"Me!","poll":null,"proposalId":null,
               "eventId":null,"createdAt":"2026-11-16T12:00:00.000Z","editedAt":null,"deleted":false,"receipt":null,
               "replyTo":{"id":"1","authorId":"p","authorName":"Priya Shah","kind":"text","text":"Snacks?","deleted":false}}""",
        ) as JsonObject
        val m = (LiveEvent(type = LiveEvent.MESSAGE_CREATED, groupId = "g", payload = payload).decode(json) as LivePayload.MessageCreated).message
        assertEquals(MessageReply("1", "p", "Priya Shah", MessageKind.Text, "Snacks?", false), m.replyTo)

        assertEquals("""{"body":"Hi"}""", json.encodeToString(SendTextRequest.serializer(), SendTextRequest("Hi")))
        assertEquals("""{"body":"Hi","replyToId":"1"}""", json.encodeToString(SendTextRequest.serializer(), SendTextRequest("Hi", "1")))
    }
}
