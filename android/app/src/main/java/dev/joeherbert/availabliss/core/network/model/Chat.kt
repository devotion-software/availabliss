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
)

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
    text = if (deleted) null else (if (kind == MessageKind.Poll) poll?.question else body)?.split(Regex("\\s+"))?.joinToString(" ")?.trim(),
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

@Serializable
data class SendTextRequest(val body: String, val replyToId: String? = null)

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
