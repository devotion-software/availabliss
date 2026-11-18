package dev.joeherbert.availabliss.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class MessageKind {
    @SerialName("text") Text,
    @SerialName("poll") Poll,
    @SerialName("proposal") Proposal,
    @SerialName("event") Event,
    @SerialName("system") System,
}

@Serializable
data class Message(
    val id: String,
    val groupId: String,
    /** null for system messages. */
    val authorId: String? = null,
    val kind: MessageKind,
    val body: String? = null,
    val poll: Poll? = null,
    val proposalId: String? = null,
    val eventId: String? = null,
    val createdAt: String,
    /** When the author last edited it. */
    val editedAt: String? = null,
    /** Deleted by its author: shown as "This message was deleted" ([body] is null). */
    val deleted: Boolean = false,
    /** Only on your own messages. */
    val receipt: Receipt? = null,
    /** The message this one replies to, as it is now. */
    val replyTo: MessageReply? = null,
    /** Photos, GIFs and files sent with it, in order (empty for most messages, and once deleted). */
    val attachments: List<Attachment> = emptyList(),
    /**
     * With [attachments]: the caption, or null. [body] is then the caption or a summary for older
     * apps ("📷 Photo"), so show this instead.
     */
    val caption: String? = null,
    /** A shared place (a one-off pin); [body] is then a fallback for older apps. Null once deleted. */
    val location: MessageLocation? = null,
)

/** A place shared in a chat message: where it is and, for a named place, what it's called. */
@Serializable
data class MessageLocation(
    val lat: Double,
    val lng: Double,
    /** A place's name, or null for "my location". */
    val name: String? = null,
    val address: String? = null,
    /** How precise a device's position was, in metres. */
    val accuracy: Double? = null,
) {
    /** "📍 Dishoom Shoreditch" or "📍 Location", as the server quotes it. */
    val summary: String get() = "\uD83D\uDCCD ${name ?: "Location"}"
}

/** Unknown kinds decode as [File]. */
@Serializable(with = AttachmentKindSerializer::class)
enum class AttachmentKind(override val wire: String) : WireEnum {
    Image("image"),
    Gif("gif"),
    File("file"),
    /** An Apple Wallet pass (.pkpass / .pkpasses), with [Attachment.pass] for its card. */
    Pass("pass"),
}

object AttachmentKindSerializer :
    LenientEnumSerializer<AttachmentKind>("AttachmentKind", AttachmentKind.entries.toTypedArray(), AttachmentKind.File)

/** A photo, GIF or file in a message. [url] / [thumbnailUrl] are signed links (no auth header needed) that last about a week. */
@Serializable
data class Attachment(
    val id: String,
    val kind: AttachmentKind = AttachmentKind.File,
    val name: String = "",
    val mime: String = "application/octet-stream",
    /** Bytes. */
    val size: Long = 0,
    /** Pixels, for images and GIFs. */
    val width: Int? = null,
    val height: Int? = null,
    val url: String,
    /** A smaller preview for photos, or a pass's logo; null for GIFs (show [url], animated) and files. */
    val thumbnailUrl: String? = null,
    /** For [AttachmentKind.Pass]: what its card shows. */
    val pass: WalletPass? = null,
) {
    /** Shown in the chat as a picture (photos and GIFs). */
    val isVisual: Boolean get() = kind == AttachmentKind.Image || kind == AttachmentKind.Gif
}

/** An Apple Wallet pass's details, read by the server from its pass.json. */
@Serializable
data class WalletPass(
    /** boardingPass, eventTicket, coupon, storeCard or generic. */
    val style: String = "generic",
    val organizationName: String = "",
    val description: String = "",
    val logoText: String? = null,
    val passTypeIdentifier: String = "",
    val serialNumber: String = "",
    /** ISO 8601 UTC. */
    val relevantDate: String? = null,
    val expirationDate: String? = null,
    val voided: Boolean = false,
    val eventName: String? = null,
    val venue: String? = null,
    val fields: List<Field> = emptyList(),
    /** "#rrggbb". */
    val backgroundColor: String? = null,
    val foregroundColor: String? = null,
    val labelColor: String? = null,
    /** Passes in the file (more than 1 for a .pkpasses bundle). */
    val count: Int = 1,
) {
    @Serializable
    data class Field(
        val label: String? = null,
        /** The text, or with [date] an ISO 8601 date-time. */
        val value: String = "",
        val date: Boolean = false,
    )

    /** "Concert ticket", "Boarding pass", "2 tickets". */
    val title: String
        get() = when {
            count > 1 -> "$count " + when (style) {
                "boardingPass" -> "boarding passes"
                "eventTicket" -> "tickets"
                else -> "passes"
            }
            description.isNotBlank() -> description
            else -> when (style) {
                "boardingPass" -> "Boarding pass"
                "coupon" -> "Coupon"
                "storeCard" -> "Card"
                "generic" -> "Pass"
                else -> "Ticket"
            }
        }
}

/** What the attachments are, in words: "Photo", "3 photos", "GIF", "Plan.pdf", "Concert ticket", "2 files", "2 passes", "2 attachments". Empty for none. */
fun attachmentsSummary(attachments: List<Attachment>): String {
    if (attachments.isEmpty()) return ""
    val n = attachments.size
    val kinds = attachments.map { it.kind }.toSet()
    return when {
        kinds == setOf(AttachmentKind.Pass) -> if (n == 1) attachments[0].pass?.title ?: "Ticket" else "$n passes"
        kinds == setOf(AttachmentKind.Image) -> if (n == 1) "Photo" else "$n photos"
        kinds == setOf(AttachmentKind.Gif) -> if (n == 1) "GIF" else "$n GIFs"
        kinds.all { it == AttachmentKind.Image || it == AttachmentKind.Gif } -> "$n photos"
        kinds == setOf(AttachmentKind.File) -> if (n == 1) attachments[0].name.ifBlank { "File" } else "$n files"
        kinds.all { it == AttachmentKind.File || it == AttachmentKind.Pass } -> "$n files"
        else -> "$n attachments"
    }
}

/** The text to show with the message: its caption when it has attachments, else its body. */
val Message.displayText: String?
    get() = when {
        location != null -> null
        attachments.isNotEmpty() -> caption?.takeIf { it.isNotBlank() }
        else -> body
    }

/** A quoted message above a reply: who sent it and one line of it. */
@Serializable
data class MessageReply(
    val id: String,
    val authorId: String? = null,
    val authorName: String? = null,
    val kind: MessageKind = MessageKind.Text,
    /** The text, or a poll's question; null once deleted. */
    val text: String? = null,
    val deleted: Boolean = false,
) {
    /** One line for the quote: "Deleted message", "Poll: Which park?" or the text. */
    val snippet: String
        get() = when {
            deleted -> "Deleted message"
            kind == MessageKind.Poll -> "Poll: ${text.orEmpty()}"
            kind == MessageKind.Proposal -> text ?: "Proposal"
            kind == MessageKind.Event -> text ?: "Event"
            else -> text.orEmpty()
        }
}

/** Can be replied to: text and polls that are still there. */
val Message.canReply: Boolean get() = (kind == MessageKind.Text || kind == MessageKind.Poll) && !deleted && authorId != null

/** This message as a quote (for "Replying to …", and to refresh replies after it changes). */
fun Message.asReply(authorName: String? = null): MessageReply = MessageReply(
    id = id,
    authorId = authorId,
    authorName = authorName,
    kind = kind,
    text = if (deleted) null else (
        when {
            kind == MessageKind.Poll -> poll?.question
            location != null -> location.summary
            attachments.isNotEmpty() -> displayText ?: attachmentsSummary(attachments)
            else -> body
        }
    )?.split(Regex("\\s+"))?.joinToString(" ")?.trim(),
    deleted = deleted,
)

@Serializable
data class Receipt(
    val delivered: Int,
    val seen: Int,
    val recipients: Int,
    /** User ids in the order they read it. */
    val seenBy: List<String> = emptyList(),
)

@Serializable
data class Poll(
    val id: String,
    val question: String,
    val multi: Boolean = false,
    val closed: Boolean = false,
    val options: List<PollOption>,
)

@Serializable
data class PollOption(val id: String, val label: String, val voterIds: List<String> = emptyList())

@Serializable
data class MessageInfo(
    val messageId: String,
    val sentAt: String,
    val seen: List<UserAt> = emptyList(),
    /** Delivered but not seen. */
    val delivered: List<UserAt> = emptyList(),
    /** Not delivered yet. */
    val pending: List<String> = emptyList(),
)

@Serializable
data class UserAt(val userId: String, val at: String)

@Serializable
data class MessagesResponse(val messages: List<Message>, val hasMore: Boolean)

/** A message's thread: the first message of its chain of replies, then every reply under it, oldest first. */
@Serializable
data class MessageThread(
    val rootId: String,
    val messages: List<Message>,
    /** More replies than the server returns (the earliest are kept). */
    val truncated: Boolean = false,
)

@Serializable
data class SendTextRequest(val body: String, val replyToId: String? = null)

/** Sends uploaded attachments (1–10) with an optional caption. */
@Serializable
data class SendAttachmentsRequest(val attachmentIds: List<String>, val body: String? = null, val replyToId: String? = null)

/** Shares a place. */
@Serializable
data class SendLocationRequest(val location: MessageLocation, val replyToId: String? = null)

@Serializable
data class SendPollRequest(val poll: NewPoll)

@Serializable
data class NewPoll(val question: String, val options: List<String>, val multi: Boolean = false)

@Serializable
enum class ReceiptState {
    @SerialName("delivered") Delivered,
    @SerialName("seen") Seen,
}

@Serializable
data class ReceiptsRequest(val upTo: String, val state: ReceiptState)

@Serializable
data class VoteRequest(val optionIds: List<String>)
