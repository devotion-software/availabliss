package dev.joeherbert.availabliss.feature.chat

import dev.joeherbert.availabliss.core.network.NetworkModule
import dev.joeherbert.availabliss.core.network.model.Member
import dev.joeherbert.availabliss.core.network.model.Message
import dev.joeherbert.availabliss.core.network.model.MessageLocation
import dev.joeherbert.availabliss.core.network.model.Role
import dev.joeherbert.availabliss.core.network.model.SendLocationRequest
import dev.joeherbert.availabliss.core.network.model.asReply
import dev.joeherbert.availabliss.core.network.model.displayText
import dev.joeherbert.availabliss.feature.common.MemberDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** Shared places in chat: the wire format, list rows, reply quotes and the "Open in" links. */
class ChatLocationTest {
    private val json = NetworkModule.provideJson()
    private val directory = MemberDirectory(
        listOf(
            Member("me", "Sam Taylor", role = Role.Owner, synced = true, joinedAt = "2026-01-01T00:00:00.000Z"),
            Member("p", "Priya Shah", role = Role.Member, synced = true, joinedAt = "2026-01-01T00:00:00.000Z"),
        ),
        meId = "me",
    )
    private val dishoom = MessageLocation(51.524545, -0.076855, "Dishoom Shoreditch", "7 Boundary St, London E2 7JE")

    @Test
    fun decodesALocationAndKeepsTheBodyForOlderApps() {
        val m = json.decodeFromString(
            Message.serializer(),
            """{"id":"1","groupId":"g","authorId":"p","kind":"text","body":"📍 Dishoom Shoreditch\nhttps://x","createdAt":"2026-11-16T12:00:00.000Z",
               "location":{"lat":51.524545,"lng":-0.076855,"name":"Dishoom Shoreditch","address":"7 Boundary St, London E2 7JE","accuracy":null}}""",
        )
        assertEquals(dishoom, m.location)
        assertNull(m.displayText)
        assertEquals("📍 Dishoom Shoreditch", m.asReply().snippet)
        assertEquals("📍 Location", m.copy(location = MessageLocation(1.0, 2.0, accuracy = 12.0)).asReply().snippet)
    }

    @Test
    fun sendsOnlyWhatItHas() {
        val body = json.encodeToString(SendLocationRequest.serializer(), SendLocationRequest(MessageLocation(1.5, -2.25, accuracy = 8.0)))
        assertEquals("""{"location":{"lat":1.5,"lng":-2.25,"accuracy":8.0}}""", body)
    }

    @Test
    fun aLocationRowHasTheCardAndCantBeEdited() {
        val own = Message("1", "g", "me", body = "📍 Dishoom Shoreditch", createdAt = "2026-11-16T12:00:00.000Z", location = dishoom, kind = dev.joeherbert.availabliss.core.network.model.MessageKind.Text)
        val item = buildChatItems(listOf(own), directory, memberCount = 2, proposals = emptyMap(), events = emptyMap(), today = LocalDate.of(2026, 11, 16))
            .filterIsInstance<ChatItem.Text>().single()
        assertEquals(dishoom, item.location)
        assertEquals("", item.text)
        assertFalse(item.canEdit)
    }

    @Test
    fun openInLinksCarryTheCoordinates() {
        assertEquals("https://www.google.com/maps/search/?api=1&query=51.524545,-0.076855", LocationLinks.googleMaps(dishoom))
        assertEquals("https://what3words.com/51.524545,-0.076855", LocationLinks.what3words(dishoom))
    }
}
