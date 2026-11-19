package dev.joeherbert.availabliss.feature.chat

import dev.joeherbert.availabliss.core.network.NetworkModule
import dev.joeherbert.availabliss.core.network.model.Attachment
import dev.joeherbert.availabliss.core.network.model.AttachmentKind
import dev.joeherbert.availabliss.core.network.model.Member
import dev.joeherbert.availabliss.core.network.model.Message
import dev.joeherbert.availabliss.core.network.model.MessageKind
import dev.joeherbert.availabliss.core.network.model.Role
import dev.joeherbert.availabliss.core.network.model.SendAttachmentsRequest
import dev.joeherbert.availabliss.core.network.model.asReply
import dev.joeherbert.availabliss.core.network.model.attachmentsSummary
import dev.joeherbert.availabliss.designsystem.component.chat.AttachmentUiKind
import dev.joeherbert.availabliss.designsystem.component.chat.ComposerAttachment
import dev.joeherbert.availabliss.designsystem.component.chat.FileIcon
import dev.joeherbert.availabliss.designsystem.component.chat.composerCanSend
import dev.joeherbert.availabliss.designsystem.component.chat.fileIconFor
import dev.joeherbert.availabliss.designsystem.component.chat.formatFileSize
import dev.joeherbert.availabliss.designsystem.component.chat.galleryOverflow
import dev.joeherbert.availabliss.designsystem.component.chat.galleryRows
import dev.joeherbert.availabliss.designsystem.component.chat.middleTruncate
import dev.joeherbert.availabliss.designsystem.component.chat.singleImageSize
import dev.joeherbert.availabliss.feature.common.MemberDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Photos, GIFs and files in chat: the wire format, list rows, summaries and the bubble / composer layout rules. */
class ChatAttachmentsTest {
    private val json = NetworkModule.provideJson()
    private val directory = MemberDirectory(
        listOf(
            Member("me", "Sam Taylor", role = Role.Owner, synced = true, joinedAt = "2026-01-01T00:00:00.000Z"),
            Member("p", "Priya Shah", role = Role.Member, synced = true, joinedAt = "2026-01-01T00:00:00.000Z"),
        ),
        meId = "me",
    )

    private fun photo(id: String = "a1") = Attachment(id, AttachmentKind.Image, "IMG_1.jpg", "image/jpeg", 120_000, 4032, 3024, "https://x/a/$id", "https://x/a/$id/thumb")
    private fun gif(id: String = "g1") = Attachment(id, AttachmentKind.Gif, "dance.gif", "image/gif", 900_000, 320, 240, "https://x/a/$id")
    private fun file(id: String = "f1", name: String = "Plan.pdf") = Attachment(id, AttachmentKind.File, name, "application/pdf", 1_234_567, url = "https://x/a/$id")

    private fun msg(id: String, author: String, body: String?, attachments: List<Attachment> = emptyList(), caption: String? = null, deleted: Boolean = false) = Message(
        id = id, groupId = "g", authorId = author, kind = MessageKind.Text, body = body,
        createdAt = "2026-11-16T12:00:00.000Z", attachments = attachments, caption = caption, deleted = deleted,
    )

    private fun build(messages: List<Message>) = buildChatItems(
        messages,
        directory,
        memberCount = 2,
        proposals = emptyMap(),
        events = emptyMap(),
        today = LocalDate.of(2026, 11, 16),
    ).filterIsInstance<ChatItem.Text>()

    @Test
    fun decodesWithAndWithoutAttachments() {
        val old = json.decodeFromString(Message.serializer(), """{"id":"1","groupId":"g","authorId":"me","kind":"text","body":"Hi","createdAt":"2026-11-16T12:00:00.000Z"}""")
        assertTrue(old.attachments.isEmpty())
        assertNull(old.caption)

        val m = json.decodeFromString(
            Message.serializer(),
            """{"id":"2","groupId":"g","authorId":"me","kind":"text","body":"📷 Photo","caption":null,"createdAt":"2026-11-16T12:00:00.000Z",
               "attachments":[{"id":"a","kind":"image","name":"IMG.jpg","mime":"image/jpeg","size":2048,"width":10,"height":20,"url":"https://x/a","thumbnailUrl":"https://x/t"},
                              {"id":"b","kind":"sticker","name":"x.bin","mime":"application/octet-stream","size":1,"width":null,"height":null,"url":"https://x/b","thumbnailUrl":null}]}""",
        )
        assertEquals(2, m.attachments.size)
        assertEquals(AttachmentKind.Image, m.attachments[0].kind)
        assertEquals(20, m.attachments[0].height)
        // A kind this build doesn't know is shown as a file.
        assertEquals(AttachmentKind.File, m.attachments[1].kind)
    }

    @Test
    fun sendRequestOmitsAnEmptyCaption() {
        assertEquals("""{"attachmentIds":["a","b"]}""", json.encodeToString(SendAttachmentsRequest.serializer(), SendAttachmentsRequest(listOf("a", "b"))))
        assertEquals(
            """{"attachmentIds":["a"],"body":"Look","replyToId":"1"}""",
            json.encodeToString(SendAttachmentsRequest.serializer(), SendAttachmentsRequest(listOf("a"), "Look", "1")),
        )
    }

    @Test
    fun summaries() {
        assertEquals("", attachmentsSummary(emptyList()))
        assertEquals("Photo", attachmentsSummary(listOf(photo())))
        assertEquals("3 photos", attachmentsSummary(listOf(photo("1"), photo("2"), photo("3"))))
        assertEquals("GIF", attachmentsSummary(listOf(gif())))
        assertEquals("2 photos", attachmentsSummary(listOf(photo(), gif())))
        assertEquals("Plan.pdf", attachmentsSummary(listOf(file())))
        assertEquals("2 files", attachmentsSummary(listOf(file("1"), file("2"))))
        assertEquals("2 attachments", attachmentsSummary(listOf(photo(), file())))
    }

    @Test
    fun rowsShowAttachmentsWithTheCaptionNotTheFallbackBody() {
        val items = build(
            listOf(
                msg("1", "p", "📷 Photo", listOf(photo())),
                msg("2", "me", "Our tickets", listOf(file()), caption = "Our tickets"),
                msg("3", "me", null, deleted = true),
            ),
        )
        val noCaption = items.single { it.id == "1" }
        assertEquals("", noCaption.text)
        assertEquals(AttachmentUiKind.Image, noCaption.attachments.single().kind)
        // The bubble shows the small preview; the viewer loads the full photo.
        assertEquals("https://x/a/a1/thumb", noCaption.attachments.single().preview)
        assertEquals("Priya: Photo", noCaption.accessibilityLabel)

        val withCaption = items.single { it.id == "2" }
        assertEquals("Our tickets", withCaption.text)
        assertEquals("1.2 MB", withCaption.attachments.single().sizeLabel)
        assertEquals(FileIcon.Document, withCaption.attachments.single().icon)
        assertTrue(withCaption.accessibilityLabel.startsWith("Your message: File: Plan.pdf, 1.2 MB, Our tickets"))

        assertTrue(items.single { it.id == "3" }.attachments.isEmpty())
    }

    @Test
    fun gifsShowTheAnimatedFile() {
        assertEquals("https://x/a/g1", build(listOf(msg("1", "p", "GIF", listOf(gif())))).single().attachments.single().preview)
    }

    @Test
    fun quotesAndDeletes() {
        assertEquals("Photo", msg("1", "p", "📷 Photo", listOf(photo())).asReply().text)
        assertEquals("Look at this", msg("1", "p", "Look at this", listOf(photo()), caption = "Look at this").asReply().text)

        val deleted = listOf(msg("1", "me", "Hi", listOf(photo()), caption = "Hi")).withDeleted("1").single()
        assertTrue(deleted.attachments.isEmpty())
        assertNull(deleted.caption)
    }

    @Test
    fun fileSizes() {
        assertEquals("0 bytes", formatFileSize(0))
        assertEquals("1 byte", formatFileSize(1))
        assertEquals("999 bytes", formatFileSize(999))
        assertEquals("12 KB", formatFileSize(12_345))
        assertEquals("1 MB", formatFileSize(999_999))
        assertEquals("1.2 MB", formatFileSize(1_234_567))
        assertEquals("15 MB", formatFileSize(15_000_000))
        assertEquals("2.5 GB", formatFileSize(2_500_000_000))
    }

    @Test
    fun longNamesAreShortenedInTheMiddle() {
        assertEquals("Plan.pdf", middleTruncate("Plan.pdf"))
        val short = middleTruncate("A very long name for the quarterly planning report final v2.pdf", 30)
        assertEquals(30, short.length)
        assertTrue(short.endsWith(".pdf"))
        assertTrue(short.startsWith("A very long"))
        assertTrue("…" in short)
    }

    @Test
    fun fileIcons() {
        assertEquals(FileIcon.Document, fileIconFor("Plan.PDF", ""))
        assertEquals(FileIcon.Document, fileIconFor("sheet.xlsx", ""))
        assertEquals(FileIcon.Archive, fileIconFor("photos.zip", "application/zip"))
        assertEquals(FileIcon.Audio, fileIconFor("memo.m4a", ""))
        assertEquals(FileIcon.Video, fileIconFor("clip", "video/mp4"))
        assertEquals(FileIcon.Generic, fileIconFor("thing.bin", "application/octet-stream"))
    }

    @Test
    fun singlePhotoKeepsItsShapeWithinTheBubble() {
        assertEquals(240f to 180f, singleImageSize(4032, 3024))
        // Tall: capped at 320 high.
        assertEquals(180f to 320f, singleImageSize(900, 1600))
        // Very wide: no thinner than 96.
        assertEquals(240f to 96f, singleImageSize(4000, 500))
        assertEquals(240f to 180f, singleImageSize(null, null))
    }

    @Test
    fun galleryLayout() {
        assertEquals(emptyList<Int>(), galleryRows(0))
        assertEquals(listOf(1), galleryRows(1))
        assertEquals(listOf(2), galleryRows(2))
        assertEquals(listOf(2, 1), galleryRows(3))
        assertEquals(listOf(2, 2), galleryRows(4))
        assertEquals(listOf(2, 2), galleryRows(10))
        assertEquals(0, galleryOverflow(4))
        assertEquals(6, galleryOverflow(10))
    }

    @Test
    fun sendNeedsTextOrUploadedAttachmentsAndNothingPending() {
        val ready = ComposerAttachment("1", AttachmentUiKind.Image, "a.jpg", uploaded = true)
        val uploading = ComposerAttachment("2", AttachmentUiKind.Image, "b.jpg", progress = 0.5f)
        val failed = ComposerAttachment("3", AttachmentUiKind.File, "c.pdf", error = "Too big")
        assertFalse(composerCanSend("", emptyList(), sending = false))
        assertTrue(composerCanSend("Hi", emptyList(), sending = false))
        assertTrue(composerCanSend("", listOf(ready), sending = false))
        assertFalse(composerCanSend("Hi", listOf(ready, uploading), sending = false))
        assertFalse(composerCanSend("", listOf(failed), sending = false))
        assertFalse(composerCanSend("", listOf(ready), sending = true))
        // Editing a photo's caption: it can be emptied.
        assertTrue(composerCanSend("", emptyList(), sending = false, allowEmptyText = true))
    }

    @Test
    fun uploadLimits() {
        assertNull(uploadLimitError("image/jpeg", 40_000_000))
        assertEquals("GIFs can be up to 15 MB", uploadLimitError("image/gif", 16L * 1024 * 1024))
        assertEquals("Files can be up to 25 MB", uploadLimitError("application/pdf", 26L * 1024 * 1024))
        assertNull(uploadLimitError("video/mp4", 400_000_000))
        assertNull(uploadLimitError("application/pdf", 1_000))
        assertNull(uploadLimitError("application/pdf", null))
    }

    @Test
    fun walletPassDecodesAndDrawsAsACard() {
        val a = json.decodeFromString(
            Attachment.serializer(),
            """{"id":"w","kind":"pass","name":"Gig.pkpass","mime":"application/vnd.apple.pkpass","size":1882,"url":"u","thumbnailUrl":"logo",
              "pass":{"style":"eventTicket","organizationName":"Roundhouse","description":"Concert ticket","logoText":null,"passTypeIdentifier":"p","serialNumber":"s",
              "relevantDate":"2026-12-02T00:30:00.000Z","expirationDate":null,"voided":false,"eventName":"Winter Gig","venue":"Roundhouse, London",
              "fields":[{"label":"Event","value":"Winter Gig","date":false},{"label":"Seat","value":"12A","date":false}],
              "backgroundColor":"#201840","foregroundColor":"#ffffff","labelColor":null,"count":1,"future":true}}""",
        )
        assertEquals(AttachmentKind.Pass, a.kind)
        assertFalse(a.isVisual)
        assertEquals("Concert ticket", attachmentsSummary(listOf(a)))
        assertEquals("2 passes", attachmentsSummary(listOf(a, a.copy(id = "x"))))
        val ui = a.toUi()
        assertEquals(AttachmentUiKind.File, ui.kind)
        assertEquals(FileIcon.Ticket, ui.icon)
        assertNotNull(ui.pass)
        val pass = ui.pass!!
        assertEquals("Winter Gig", pass.headline)
        assertEquals(listOf("Seat" to "12A"), pass.fields)
        assertEquals("logo", pass.logo)
        assertNotNull(pass.background)
        assertNull(pass.label)
        // A plain file, or an old server's: no card.
        assertNull(a.copy(kind = AttachmentKind.File).toUi().pass)
    }
}
