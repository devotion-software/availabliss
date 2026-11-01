package dev.joeherbert.availabliss.core.live

import dev.joeherbert.availabliss.core.network.model.AppNotification
import dev.joeherbert.availabliss.core.network.model.Group
import dev.joeherbert.availabliss.core.network.model.GroupEvent
import dev.joeherbert.availabliss.core.network.model.LiveEvent
import dev.joeherbert.availabliss.core.network.model.Member
import dev.joeherbert.availabliss.core.network.model.Message
import dev.joeherbert.availabliss.core.network.model.Poll
import dev.joeherbert.availabliss.core.network.model.Proposal
import dev.joeherbert.availabliss.core.network.model.ReceiptState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive

/** `proposal.confirmed` payload. */
@Serializable
data class ProposalConfirmedPayload(val proposal: Proposal, val event: GroupEvent)

/** `message.receipts` payload. */
@Serializable
data class ReceiptsPayload(val userId: String, val state: ReceiptState, val receipts: List<MessageReceipt>)

@Serializable
data class MessageReceipt(
    val messageId: String,
    val delivered: Int,
    val seen: Int,
    val recipients: Int,
    val seenBy: List<String> = emptyList(),
) {
    fun toReceipt() = dev.joeherbert.availabliss.core.network.model.Receipt(delivered, seen, recipients, seenBy)
}

/** `availability.changed` payload: just refetch. */
@Serializable
data class AvailabilityChangedPayload(val userId: String)

/**
 * A [LiveEvent] with its payload decoded per API.md "Live updates". Unknown types or
 * payloads that fail to decode become [Unknown] so a bad frame never breaks the stream.
 */
sealed interface LivePayload {
    data class MessageCreated(val message: Message) : LivePayload
    data class MessageReceipts(val payload: ReceiptsPayload) : LivePayload
    data class PollUpdated(val poll: Poll) : LivePayload
    data class ProposalChanged(val proposal: Proposal, val created: Boolean) : LivePayload
    data class ProposalConfirmed(val proposal: Proposal, val event: GroupEvent) : LivePayload
    data class EventChanged(val event: GroupEvent) : LivePayload
    data class EventDeleted(val id: String) : LivePayload
    data class GroupUpdated(val group: Group) : LivePayload
    data class MemberChanged(val member: Member) : LivePayload
    data class MemberRemoved(val userId: String) : LivePayload
    data class AvailabilityChanged(val userId: String) : LivePayload
    data class NotificationCreated(val notification: AppNotification) : LivePayload
    data class Unknown(val type: String) : LivePayload
}

fun LiveEvent.decode(json: Json): LivePayload = runCatching {
    val obj = payload as? JsonObject
    fun flag(key: String) = obj?.get(key)?.jsonPrimitive?.booleanOrNull == true
    fun str(key: String) = obj?.get(key)?.jsonPrimitive?.content
    when (type) {
        LiveEvent.MESSAGE_CREATED -> LivePayload.MessageCreated(json.decodeFromJsonElement(Message.serializer(), payload))
        LiveEvent.MESSAGE_RECEIPTS -> LivePayload.MessageReceipts(json.decodeFromJsonElement(ReceiptsPayload.serializer(), payload))
        LiveEvent.POLL_UPDATED -> LivePayload.PollUpdated(json.decodeFromJsonElement(Poll.serializer(), payload))
        LiveEvent.PROPOSAL_CREATED, LiveEvent.PROPOSAL_UPDATED -> LivePayload.ProposalChanged(
            json.decodeFromJsonElement(Proposal.serializer(), payload),
            created = type == LiveEvent.PROPOSAL_CREATED,
        )
        LiveEvent.PROPOSAL_CONFIRMED -> json.decodeFromJsonElement(ProposalConfirmedPayload.serializer(), payload)
            .let { LivePayload.ProposalConfirmed(it.proposal, it.event) }
        LiveEvent.EVENT_CHANGED -> if (flag("deleted")) LivePayload.EventDeleted(str("id").orEmpty())
            else LivePayload.EventChanged(json.decodeFromJsonElement(GroupEvent.serializer(), payload))
        LiveEvent.GROUP_UPDATED -> LivePayload.GroupUpdated(json.decodeFromJsonElement(Group.serializer(), payload))
        LiveEvent.MEMBER_CHANGED -> if (flag("removed")) LivePayload.MemberRemoved(str("userId").orEmpty())
            else LivePayload.MemberChanged(json.decodeFromJsonElement(Member.serializer(), payload))
        LiveEvent.AVAILABILITY_CHANGED -> LivePayload.AvailabilityChanged(str("userId").orEmpty())
        LiveEvent.NOTIFICATION_CREATED -> LivePayload.NotificationCreated(json.decodeFromJsonElement(AppNotification.serializer(), payload))
        else -> LivePayload.Unknown(type)
    }
}.getOrElse { LivePayload.Unknown(type) }
