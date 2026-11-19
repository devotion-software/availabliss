package dev.joeherbert.availabliss.feature.chat

import dev.joeherbert.availabliss.core.network.model.Attachment
import dev.joeherbert.availabliss.core.network.model.AttachmentKind
import dev.joeherbert.availabliss.core.network.model.Message
import dev.joeherbert.availabliss.core.network.model.MessageKind
import dev.joeherbert.availabliss.core.network.model.MessageLocation
import dev.joeherbert.availabliss.core.network.model.Poll
import dev.joeherbert.availabliss.core.network.model.PollOption
import org.junit.Assert.assertEquals
import org.junit.Test

/** Search results' lines. Same cases as web's `searchSnippet` tests and iOS's `ChatSearchTests`. */
class ChatSearchTest {
    private fun message(body: String?, kind: MessageKind = MessageKind.Text) =
        Message(id = "1", groupId = "g", authorId = "me", kind = kind, body = body, createdAt = "2026-11-16T12:00:00.000Z")

    @Test
    fun picksOutTheMatchKeepingItsCase() {
        assertEquals(SearchSnippet("Shall we try the ", "Crown", "?"), message("Shall we try the Crown?").searchSnippet("crown"))
    }

    @Test
    fun aLongMessageIsCutAtAWordBeforeTheMatch() {
        val s = message("We could go somewhere near the station after work on Friday, maybe the Crown").searchSnippet("crown")
        assertEquals("…work on Friday, maybe the ", s.before)
        assertEquals("Crown", s.match)
    }

    @Test
    fun pollOptionsPlacesAndFileNames() {
        val poll = message(null, MessageKind.Poll).copy(poll = Poll("p", "Which pub?", options = listOf(PollOption("o", "Red Lion"))))
        assertEquals(SearchSnippet("Which pub? · Red ", "Lion", ""), poll.searchSnippet("lion"))
        val place = message("📍 The Crown").copy(location = MessageLocation(1.0, 2.0, "The Crown", "1 High St"))
        assertEquals("High", place.searchSnippet("high").match)
        val file = message("📎 Crown menu.pdf").copy(attachments = listOf(Attachment(id = "f", kind = AttachmentKind.File, name = "Crown menu.pdf", size = 1, url = "https://x")))
        assertEquals(SearchSnippet("Crown ", "menu", ".pdf"), file.searchSnippet("menu"))
    }

    @Test
    fun noMatchShowsTheTextFromTheStart() {
        assertEquals(SearchSnippet("hello there", "", ""), message("hello there").searchSnippet("zzz"))
    }
}
