package dev.joeherbert.availabliss.feature.chat

import dev.joeherbert.availabliss.core.live.LivePayload
import dev.joeherbert.availabliss.core.live.decode
import dev.joeherbert.availabliss.core.network.NetworkModule
import dev.joeherbert.availabliss.core.network.model.LiveEvent
import dev.joeherbert.availabliss.core.network.model.Member
import dev.joeherbert.availabliss.core.network.model.Message
import dev.joeherbert.availabliss.core.network.model.MessageKind
import dev.joeherbert.availabliss.core.network.model.MessageReaction
import dev.joeherbert.availabliss.core.network.model.Reactions
import dev.joeherbert.availabliss.core.network.model.Role
import dev.joeherbert.availabliss.feature.common.MemberDirectory
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Emoji reactions: one per person per message. Matches web `reactions.test.ts` and iOS `ReactionTests`. */
class ChatReactionsTest {
    private fun r(emoji: String, vararg userIds: String) = MessageReaction(emoji, userIds.toList())

    @Test fun `adds a new emoji at the end`() {
        assertEquals(listOf(r("👍", "a"), r("❤️", "me")), Reactions.setting(listOf(r("👍", "a")), "me", "❤️"))
    }

    @Test fun `joins an emoji someone already used`() {
        assertEquals(listOf(r("👍", "a", "me")), Reactions.setting(listOf(r("👍", "a")), "me", "👍"))
    }

    @Test fun `replaces your earlier reaction, dropping empty ones`() {
        assertEquals(listOf(r("👍", "a", "me")), Reactions.setting(listOf(r("😂", "me"), r("👍", "a")), "me", "👍"))
    }

    @Test fun `null takes yours back`() {
        assertEquals(listOf(r("👍", "a")), Reactions.setting(listOf(r("👍", "a", "me")), "me", null))
    }

    @Test fun `picking your own again takes it back`() {
        assertNull(Reactions.toggled(listOf(r("❤️", "me")), "me", "❤️"))
        assertEquals("👍", Reactions.toggled(listOf(r("❤️", "me")), "me", "👍"))
        assertEquals("👍", Reactions.mine(listOf(r("❤️", "a"), r("👍", "me")), "me"))
        assertNull(Reactions.mine(emptyList(), "me"))
    }

    @Test fun `single emoji`() {
        assertEquals("👍🏽", Reactions.single(" 👍🏽 "))
        assertEquals("👨‍👩‍👧", Reactions.single("👨‍👩‍👧"))
        assertEquals("🇬🇧", Reactions.single("🇬🇧"))
        assertEquals("❤️", Reactions.single("❤️"))
        assertNull(Reactions.single("👍👍"))
        assertNull(Reactions.single("a"))
        assertNull(Reactions.single("1"))
        assertNull(Reactions.single(""))
    }

    @Test fun `names put you first`() {
        val names = mapOf("a" to "Ana", "b" to "Sam", "c" to "Kim", "d" to "Jo")
        val name = { id: String -> names[id] ?: "Someone" }
        assertEquals("Ana", Reactions.names(listOf("a"), "me", name))
        assertEquals("You and Ana", Reactions.names(listOf("a", "me"), "me", name))
        assertEquals("You, Ana and Sam", Reactions.names(listOf("a", "b", "me"), "me", name))
        assertEquals("Ana, Sam and 2 others", Reactions.names(listOf("a", "b", "c", "d"), "me", name))
    }

    @Test fun `bubbles show reactions with yours marked, and none once deleted`() {
        val directory = MemberDirectory(
            listOf(
                Member("me", "Sam Taylor", role = Role.Owner, synced = true, joinedAt = "2026-01-01T00:00:00.000Z"),
                Member("p", "Priya Shah", role = Role.Member, synced = true, joinedAt = "2026-01-01T00:00:00.000Z"),
            ),
            meId = "me",
        )
        fun msg(id: String, deleted: Boolean = false) = Message(
            id = id,
            groupId = "g",
            authorId = "p",
            kind = MessageKind.Text,
            body = if (deleted) null else "Hi",
            deleted = deleted,
            createdAt = "2026-11-16T12:00:00.000Z",
            reactions = listOf(r("👍", "p", "me"), r("😂", "p")),
        )
        val items = buildChatItems(listOf(msg("m1"), msg("m2", deleted = true)), directory, 2, emptyMap(), emptyMap(), today = LocalDate.of(2026, 11, 16))
            .filterIsInstance<ChatItem.Text>()
        val live = items.first { it.id == "m1" }
        assertEquals(2, live.reactions.size)
        assertTrue(live.reactions[0].mine)
        assertEquals(2, live.reactions[0].count)
        assertEquals("You and Priya", live.reactions[0].who)
        assertEquals("👍", live.myReaction)
        assertTrue(live.canReact)
        assertTrue(items.first { it.id == "m2" }.reactions.isEmpty())
    }

    @Test fun `decodes message reactions events and messages without them`() {
        val json = NetworkModule.provideJson()
        val payload = json.parseToJsonElement("""{"messageId":"m1","reactions":[{"emoji":"👍","userIds":["a","b"]}]}""") as JsonObject
        val decoded = LiveEvent(type = LiveEvent.MESSAGE_REACTIONS, groupId = "g", payload = payload).decode(json)
        assertTrue(decoded is LivePayload.MessageReactionsChanged)
        assertEquals(listOf(r("👍", "a", "b")), (decoded as LivePayload.MessageReactionsChanged).payload.reactions)
        val older = json.decodeFromString(
            Message.serializer(),
            """{"id":"m1","groupId":"g","authorId":"a","kind":"text","body":"Hi","createdAt":"2026-11-16T12:00:00.000Z"}""",
        )
        assertTrue(older.reactions.isEmpty())
    }
}
