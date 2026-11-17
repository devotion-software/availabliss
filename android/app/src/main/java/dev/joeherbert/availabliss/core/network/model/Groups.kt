package dev.joeherbert.availabliss.core.network.model

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class GroupSummary(
    val id: String,
    val name: String,
    val color: TileColor = TileColor.T1,
    val monogram: String,
    /** The group's photo, shown instead of the monogram tile. Absent on older servers. */
    val image: String? = null,
    val role: Role,
    /** Archived by an admin: listed in the groups list's collapsed Archived section. Absent on older servers. */
    val archived: Boolean = false,
    val memberCount: Int,
    val syncedCount: Int,
    val requirementSummary: String,
    val status: GroupStatus,
    val unreadCount: Int = 0,
    /** First five members, for avatar stacks. */
    val memberPreview: List<MemberPreview> = emptyList(),
)

@Serializable
data class MemberPreview(val userId: String, val name: String, val image: String? = null)

/** Drives the badge on the groups list. Unknown kinds fall back to [Searching]. */
@Serializable(with = GroupStatusSerializer::class)
sealed interface GroupStatus {
    /** "2 weekends work". [noun] is weekend | evening | day | week | trip | slot. */
    @Serializable
    data class Matches(val count: Int, val noun: String = "slot") : GroupStatus {
        /** "2 weekends work", "1 evening works". */
        val label: String get() = "$count ${if (count == 1) noun else noun + "s"} ${if (count == 1) "works" else "work"}"
    }

    @Serializable
    data class NeedsReply(val proposalId: String) : GroupStatus

    @Serializable
    data class Confirmed(val eventId: String, val start: String) : GroupStatus

    @Serializable
    data object Searching : GroupStatus
}

internal object GroupStatusSerializer : JsonContentPolymorphicSerializer<GroupStatus>(GroupStatus::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<GroupStatus> =
        when (element.jsonObject["kind"]?.jsonPrimitive?.content) {
            "matches" -> GroupStatus.Matches.serializer()
            "needs_reply" -> GroupStatus.NeedsReply.serializer()
            "confirmed" -> GroupStatus.Confirmed.serializer()
            else -> GroupStatus.Searching.serializer()
        }
}

@Serializable
data class Group(
    val id: String,
    val name: String,
    val color: TileColor = TileColor.T1,
    val monogram: String,
    /** The group's photo, shown instead of the monogram tile. */
    val image: String? = null,
    val role: Role,
    val archived: Boolean = false,
    val memberCount: Int,
    val syncedCount: Int,
    val requirementSummary: String,
    val requirement: Requirement,
    val members: List<Member>,
    val createdAt: String,
)

/** `POST /requirements/preview` body. */
@Serializable
data class RequirementPreviewRequest(val requirement: Requirement)

/** Slots a requirement produces before anyone's busy time is considered. */
@Serializable
data class RequirementPreview(val count: Int, val from: String, val to: String, val summary: String)

@Serializable
data class GroupsResponse(val groups: List<GroupSummary>)

@Serializable
data class CreateGroupRequest(
    val name: String,
    val color: TileColor? = null,
    val requirement: Requirement? = null,
)

@Serializable
data class UpdateGroupRequest(
    val name: String? = null,
    val color: TileColor? = null,
    val requirement: Requirement? = null,
    /** true archives the group for everyone, false unarchives it. */
    val archived: Boolean? = null,
)

@Serializable
data class MemberRoleRequest(val role: Role)

@Serializable
data class NudgeResponse(val nudged: Int)

/**
 * Neither: a link anyone can use. Emails: one invite each, emailed, only for that person.
 * Usernames: one invite each, to the person's Activity, only for them. At most 20 people in all.
 */
@Serializable
data class CreateInviteRequest(val emails: List<String>? = null, val usernames: List<String>? = null)

@Serializable
data class InviteLink(
    val token: String,
    val url: String,
    val expiresAt: String,
    /** Every invite the request made (one per email). */
    val invites: List<GroupInvite> = emptyList(),
)

/** An invite that can still be used: a link anyone can use ([isLink]), or one for a person (by email or username). */
@Serializable
data class GroupInvite(
    val id: String,
    val email: String? = null,
    /** Who it was sent to by username (no "@"). */
    val username: String? = null,
    val url: String,
    val expiresAt: String,
) {
    val isLink: Boolean get() = email == null && username == null
}

@Serializable
data class GroupInvitesResponse(val invites: List<GroupInvite>)

@Serializable
data class InvitePreview(
    val groupName: String,
    val inviterName: String,
    val memberCount: Int,
    val expiresAt: String,
    /** Who an emailed invite is for; null for a link anyone can use. */
    val email: String? = null,
    /** Who an invite sent by username is for (no "@"); null otherwise. */
    val username: String? = null,
)

@Serializable
enum class DayStatus {
    @SerialName("hidden") Hidden,
    @SerialName("busy") Busy,
    @SerialName("free") Free,
    @SerialName("match") Match,
}

@Serializable
enum class SlotPosition {
    @SerialName("start") Start,
    @SerialName("middle") Middle,
    @SerialName("end") End,
    @SerialName("single") Single,
}

@Serializable
data class Availability(
    val from: String,
    val to: String,
    val visibleWeekdays: List<Int>,
    val days: List<AvailabilityDay>,
    val slots: List<Slot>,
    val syncedCount: Int,
    val memberCount: Int,
    val pendingMemberIds: List<String> = emptyList(),
    /** Synced members whose calendars are only known up to `until`, before the end of this range. */
    val checkedUntil: List<CoverageGap> = emptyList(),
)

@Serializable
data class CoverageGap(val userId: String, val until: String)

@Serializable
data class AvailabilityDay(
    val date: String,
    val status: DayStatus,
    val busyMemberIds: List<String> = emptyList(),
    val slotPosition: SlotPosition? = null,
    val holiday: String? = null,
    /** Members whose preferred times this day's time that works fits. */
    val preferredBy: List<String> = emptyList(),
)

/** `label` e.g. "Fri 13 – Sun 15 Nov". `preferredBy`: members whose preferred times it fits. */
@Serializable
data class Slot(val start: String, val end: String, val label: String, val preferredBy: List<String> = emptyList())

/** A member's preferred time for a group. A guideline: it never blocks a date. */
@Serializable
data class GroupPreference(
    val id: String,
    val userId: String,
    val kind: String,
    val from: String? = null,
    val to: String? = null,
    val days: List<Int> = emptyList(),
    val window: PreferenceWindow? = null,
    /** "Weekends after 16:00", "12 – 14 Dec". */
    val label: String,
)

@Serializable
data class PreferenceWindow(val from: String, val to: String)

@Serializable
data class GroupPreferencesResponse(val preferences: List<GroupPreference>)

/** `kind` "dates" (from, to) or "weekly" (days, window). */
@Serializable
data class NewGroupPreference(
    val kind: String,
    val from: String? = null,
    val to: String? = null,
    val days: List<Int>? = null,
    val window: PreferenceWindow? = null,
)

@Serializable
data class DayAvailability(
    val date: String,
    val window: Interval? = null,
    val members: List<DayMember>,
    val everyoneFree: List<Interval> = emptyList(),
)

@Serializable
data class DayMember(
    val userId: String,
    val pending: Boolean,
    val busy: List<DayBusy> = emptyList(),
)

@Serializable
data class DayBusy(val start: String, val end: String, val manual: Boolean)
