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
    val role: Role,
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
    val role: Role,
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
)

@Serializable
data class MemberRoleRequest(val role: Role)

@Serializable
data class NudgeResponse(val nudged: Int)

@Serializable
data class CreateInviteRequest(val email: String? = null)

@Serializable
data class InviteLink(val token: String, val url: String, val expiresAt: String)

@Serializable
data class InvitePreview(
    val groupName: String,
    val inviterName: String,
    val memberCount: Int,
    val expiresAt: String,
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
)

/** `label` e.g. "Fri 13 – Sun 15 Nov". */
@Serializable
data class Slot(val start: String, val end: String, val label: String)

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
