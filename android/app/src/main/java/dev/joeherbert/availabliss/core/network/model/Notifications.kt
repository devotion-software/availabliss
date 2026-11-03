package dev.joeherbert.availabliss.core.network.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

/** [wire] matches the server's `kind` and the push payload. Unknown kinds decode as [Unknown]. */
@Serializable(with = NotificationKindSerializer::class)
enum class NotificationKind(override val wire: String) : WireEnum {
    ProposalCreated("proposal.created"),
    ProposalConfirmed("proposal.confirmed"),
    Nudge("nudge"),
    InviteAccepted("invite.accepted"),
    Message("message"),
    EventCreated("event.created"),

    /** A kind added on the server after this build. */
    Unknown(""),
}

object NotificationKindSerializer :
    LenientEnumSerializer<NotificationKind>("NotificationKind", NotificationKind.entries.toTypedArray(), NotificationKind.Unknown)

/** Named `AppNotification` to avoid clashing with `android.app.Notification`. */
@Serializable
data class AppNotification(
    val id: String,
    val groupId: String? = null,
    val kind: NotificationKind,
    val title: String,
    val body: String,
    val data: Map<String, String> = emptyMap(),
    val readAt: String? = null,
    val createdAt: String,
)

@Serializable
data class NotificationsResponse(val notifications: List<AppNotification>, val unread: Int)

@Serializable
data class ReadNotificationsRequest(val ids: List<String>? = null)

@Serializable
data class RegisterDeviceRequest(
    val platform: String,
    val token: String,
    val name: String? = null,
)

/** One SSE `data:` frame from `GET /api/v1/live`. */
@Serializable
data class LiveEvent(
    val type: String,
    val groupId: String? = null,
    val payload: JsonElement = JsonNull,
) {
    companion object Types {
        const val MESSAGE_CREATED = "message.created"
        const val MESSAGE_RECEIPTS = "message.receipts"
        const val POLL_UPDATED = "poll.updated"
        const val PROPOSAL_CREATED = "proposal.created"
        const val PROPOSAL_UPDATED = "proposal.updated"
        const val PROPOSAL_CONFIRMED = "proposal.confirmed"
        const val EVENT_CHANGED = "event.changed"
        const val GROUP_UPDATED = "group.updated"
        const val MEMBER_CHANGED = "member.changed"
        const val AVAILABILITY_CHANGED = "availability.changed"
        const val NOTIFICATION_CREATED = "notification.created"
    }
}
