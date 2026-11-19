package dev.joeherbert.availabliss.core.network.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import java.time.Instant

/** [wire] matches the server's `kind` and the push payload. Unknown kinds decode as [Unknown]. */
@Serializable(with = NotificationKindSerializer::class)
enum class NotificationKind(override val wire: String) : WireEnum {
    ProposalCreated("proposal.created"),
    ProposalConfirmed("proposal.confirmed"),
    /** A confirmed plan changed: someone can't make it any more, or it was cancelled. */
    ProposalChanged("proposal.changed"),
    Nudge("nudge"),
    InviteAccepted("invite.accepted"),
    /** Someone invited you by username; `data.token` is the invite. */
    InviteReceived("invite.received"),
    Message("message"),
    EventCreated("event.created"),
    /** Your role in a group changed (made an admin, no longer one, or handed ownership); `data.role` is the new one. */
    RoleChanged("role.changed"),
    /**
     * Before a plan: `data.reminder` is `day` / `hour` (a confirmed plan you're going to, with
     * `eventId` and `proposalId` when it came from one) or `reply` (an open proposal you haven't answered).
     */
    Reminder("reminder"),

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
    /** The group's tile colour token (`t1`–`t5`), behind the item's icon. A string so an unknown one can't fail the list. */
    val groupColor: String? = null,
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
        const val MESSAGE_UPDATED = "message.updated"
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

/**
 * A kind of push you can turn off (`NotificationSettings.off`). Invites and role changes always
 * push, so they have no category.
 */
enum class NotificationCategory(override val wire: String) : WireEnum {
    /** New messages in a group's chat. */
    Chat("chat"),
    /** A new proposal, or a change to one (someone can't make it, it was cancelled). */
    Proposals("proposals"),
    /** A proposal was confirmed, or an event was added. */
    Plans("plans"),
    /** Before plans you're going to, and proposals you haven't answered. */
    Reminders("reminders"),
    /** Someone asked you to add your busy times. */
    Nudges("nudges"),
}

/**
 * No pushes between [from] and [to] (`HH:MM` in your time zone; may wrap midnight). With [catchUp], one
 * push when they end says what came in meanwhile (missing from an older server: off).
 */
@Serializable
data class QuietHours(val from: String, val to: String, val catchUp: Boolean = false)

/** A group you've muted, [until] an ISO instant or for good when null. */
@Serializable
data class GroupMute(val groupId: String, val until: String? = null)

/**
 * `GET /me/notification-settings`. [off] holds category wire values as sent, so one added on the
 * server after this build survives a save; read it through [isOff].
 */
@Serializable
data class NotificationSettings(
    val off: List<String> = emptyList(),
    val quietHours: QuietHours? = null,
    /** Active mutes only. */
    val mutes: List<GroupMute> = emptyList(),
) {
    fun isOff(category: NotificationCategory): Boolean = category.wire in off

    /** [off] with [category] turned [on] or off, keeping anything this build doesn't know. */
    fun offWith(category: NotificationCategory, on: Boolean): List<String> =
        if (on) off - category.wire else (off + category.wire).distinct()

    /**
     * Whether [groupId] is muted at [now]. The server only returns active mutes, but a cached copy
     * can outlive one that has since run out.
     */
    fun isMuted(groupId: String, now: Instant = Instant.now()): Boolean =
        mutes.any { mute ->
            mute.groupId == groupId &&
                (mute.until == null || runCatching { Instant.parse(mute.until) }.getOrNull()?.isAfter(now) != false)
        }
}

/** `PUT /groups/:id/mute`: [until] an ISO instant, or null to mute until unmuted. */
@Serializable
data class MuteGroupRequest(val until: String?)
