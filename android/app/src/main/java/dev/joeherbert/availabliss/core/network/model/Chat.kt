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
    /** Only on your own messages. */
    val receipt: Receipt? = null,
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
data class SendTextRequest(val body: String)

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
