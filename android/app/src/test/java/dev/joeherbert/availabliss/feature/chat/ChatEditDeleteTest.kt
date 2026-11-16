package dev.joeherbert.availabliss.feature.chat

import dev.joeherbert.availabliss.core.live.LivePayload
import dev.joeherbert.availabliss.core.live.decode
import dev.joeherbert.availabliss.core.network.NetworkModule
import dev.joeherbert.availabliss.core.network.model.LiveEvent
import dev.joeherbert.availabliss.core.network.model.Member
import dev.joeherbert.availabliss.core.network.model.Message
import dev.joeherbert.availabliss.core.network.model.MessageKind
import dev.joeherbert.availabliss.core.network.model.Receipt
import dev.joeherbert.availabliss.core.network.model.Role
import dev.joeherbert.availabliss.feature.common.MemberDirectory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Edited / deleted messages: list building, applying updates, and decoding `message.updated`. */
class ChatEditDeleteTest {
    private val directory = MemberDirectory(
        listOf(
            Member("me", "Sam Taylor", role = Role.Owner, synced = true, joinedAt = "2026-01-01T00:00:00.000Z"),
            Member("p", "Priya Shah", role = Role.Member, synced = true, joinedAt = "2026-01-01T00:00:00.000Z"),
        ),
        meId = "me",
    )
    private val today = LocalDate.of(2026, 11, 16)
    private val receipt = Receipt(delivered = 1, seen = 1, recipients = 1, seenBy = listOf("p"))

    private fun msg(
        id: String,
        author: String?,
        body: String? = "Hello $id",
        editedAt: String? = null,
        deleted: Boolean = false,
        receipt: Receipt? = null,
        kind: MessageKind = MessageKind.Text,
    ) = Message(
        id = id, groupId = "g", authorId = author, kind = kind, body = body,
        createdAt = "2026-11-16T12:00:00.000Z", editedAt = editedAt, deleted = deleted, receipt = receipt,
    )

    private fun build(messages: List<Message>) =
        buildChatItems(messages, directory, memberCount = 2, proposals = emptyMap(), events = emptyMap(), today = today)

    private fun List<ChatItem>.text(id: String) = filterIsInstance<ChatItem.Text>().single { it.id == id }
    private fun List<ChatItem>.receipt() = filterIsInstance<ChatItem.Receipt>().singleOrNull()

    @Test
    fun ownTextMessagesCanBeEditedOthersCannot() {
        val items = build(listOf(msg("1", "p"), msg("2", "me", receipt = receipt)))
        assertFalse(items.text("1").canEdit)
        assertTrue(items.text("2").canEdit)
    }

    @Test
    fun editedIncomingMessageShowsEditedUnderTheBubble() {
        val items = build(listOf(msg("1", "p", body = "Fixed", editedAt = "2026-11-16T12:05:00.000Z")))
        val t = items.text("1")
        assertTrue(t.edited)
        assertEquals("Fixed", t.text)
        assertEquals("Priya: Fixed, edited", t.accessibilityLabel)
    }

    @Test
    fun editedLatestOwnMessageSaysEditedOnTheReceiptLine() {
        val items = build(listOf(msg("1", "me", editedAt = "2026-11-16T12:05:00.000Z", receipt = receipt)))
        assertFalse(items.text("1").edited)
        assertEquals("Edited · Seen by 1 of 1", items.receipt()?.label)
    }

    @Test
    fun editedOlderOwnMessageShowsEditedUnderTheBubble() {
        val items = build(
            listOf(
                msg("1", "me", editedAt = "2026-11-16T12:05:00.000Z", receipt = receipt),
                msg("2", "me", receipt = receipt),
            ),
        )
        assertTrue(items.text("1").edited)
        assertFalse(items.text("2").edited)
        assertEquals("Seen by 1 of 1", items.receipt()?.label)
    }

    @Test
    fun deletedMessagesShowPlaceholderAndCannotBeEdited() {
        val items = build(
            listOf(
                msg("1", "p", body = null, deleted = true, editedAt = "2026-11-16T12:05:00.000Z"),
                msg("2", "me", body = null, deleted = true, receipt = receipt),
            ),
        )
        val theirs = items.text("1")
        assertTrue(theirs.deleted)
        assertFalse(theirs.edited)
        assertEquals("This message was deleted", theirs.text)
        assertEquals("Message deleted", theirs.accessibilityLabel)
        // Incoming deleted messages keep the avatar / name layout.
        assertEquals("Priya Shah", theirs.authorName)

        val mine = items.text("2")
        assertTrue(mine.deleted)
        assertFalse(mine.canEdit)
        assertEquals("You deleted this message", mine.text)
        // No receipt under a deleted message.
        assertNull(items.receipt())
    }

    @Test
    fun receiptMovesToTheLatestOwnMessageThatIsNotDeleted() {
        val items = build(listOf(msg("1", "me", receipt = receipt), msg("2", "me", body = null, deleted = true, receipt = receipt)))
        assertEquals("1", items.receipt()?.messageId)
    }

    @Test
    fun withUpdatedReplacesByIdButKeepsTheReceipt() {
        val list = listOf(msg("1", "me", receipt = receipt), msg("2", "p"))
        val updated = list.withUpdated(msg("1", "me", body = "Edited text", editedAt = "2026-11-16T12:05:00.000Z", receipt = null))
        assertEquals("Edited text", updated[0].body)
        assertEquals("2026-11-16T12:05:00.000Z", updated[0].editedAt)
        assertEquals(receipt, updated[0].receipt)
        assertEquals(list[1], updated[1])
    }

    @Test
    fun withUpdatedTakesANewReceiptWhenItHasOne() {
        val newer = receipt.copy(seen = 0, seenBy = emptyList())
        val updated = listOf(msg("1", "me", receipt = receipt)).withUpdated(msg("1", "me", body = "x", receipt = newer))
        assertEquals(newer, updated[0].receipt)
    }

    @Test
    fun withUpdatedAppliesDeletes() {
        val updated = listOf(msg("1", "me", receipt = receipt)).withUpdated(msg("1", "me", body = null, deleted = true))
        assertTrue(updated[0].deleted)
        assertNull(updated[0].body)
        assertEquals(receipt, updated[0].receipt)
    }

    @Test
    fun withUpdatedIgnoresUnknownIds() {
        val list = listOf(msg("1", "me"))
        assertEquals(list, list.withUpdated(msg("9", "me", body = "x")))
    }

    @Test
    fun withDeletedClearsTheBody() {
        val updated = listOf(msg("1", "me", receipt = receipt), msg("2", "me")).withDeleted("1")
        assertTrue(updated[0].deleted)
        assertNull(updated[0].body)
        assertEquals(receipt, updated[0].receipt)
        assertFalse(updated[1].deleted)
    }

    @Test
    fun decodesMessageUpdated() {
        val json = NetworkModule.provideJson()
        val payload = json.parseToJsonElement(
            """{"id":"1","groupId":"g","authorId":"me","kind":"text","body":null,"poll":null,"proposalId":null,
               "eventId":null,"createdAt":"2026-11-16T12:00:00.000Z","editedAt":"2026-11-16T12:05:00.000Z",
               "deleted":true,"receipt":null}""",
        ) as JsonObject
        val decoded = LiveEvent(type = LiveEvent.MESSAGE_UPDATED, groupId = "g", payload = payload).decode(json)
        val m = (decoded as LivePayload.MessageUpdated).message
        assertTrue(m.deleted)
        assertNull(m.body)
        assertEquals("2026-11-16T12:05:00.000Z", m.editedAt)
    }

    @Test
    fun oldMessagesWithoutTheNewFieldsDecode() {
        val m = Json { ignoreUnknownKeys = true }.decodeFromString(
            Message.serializer(),
            """{"id":"1","groupId":"g","authorId":"me","kind":"text","body":"hi","createdAt":"2026-11-16T12:00:00.000Z"}""",
        )
        assertNull(m.editedAt)
        assertFalse(m.deleted)
    }
}
