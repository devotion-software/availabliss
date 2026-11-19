package dev.joeherbert.availabliss.core.network.model

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
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
    /** "2 weekends work". [noun] is weekend | evening | day | week | date | slot ("trip" from older servers reads as "date"). */
    @Serializable
    data class Matches(val count: Int, val noun: String = "slot") : GroupStatus {
        /** "2 weekends work", "1 evening works". */
        val label: String get() {
            val word = if (noun == "trip") "date" else noun
            return "$count ${if (count == 1) word else word + "s"} ${if (count == 1) "works" else "work"}"
        }
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
    /** Archive it automatically once its event has passed and nothing else is planned (older servers omit it). */
    val autoArchive: Boolean = false,
    /** How often it meets, or null (older servers omit it). */
    val rhythm: Rhythm? = null,
    val createdAt: String,
    /** Only from accepting an invite: false when you were already in the group (null from older servers). */
    val joined: Boolean? = null,
)

/**
 * How often a group meets ([every]: `week`, `two-weeks`, `three-weeks`, `month`, `two-months`, `three-months`, `six-months` or `year`, see [frequency]). With [prompt],
 * once a plan has ended and nothing newer is planned, the server suggests the next one in the chat.
 */
@Serializable
data class Rhythm(val every: String, val prompt: Boolean = false) {
    /** Null for a frequency this build doesn't know. */
    val frequency: RhythmEvery? get() = RhythmEvery.entries.firstOrNull { it.wire == every }

    /** As sent in a PATCH: both fields, always (the shared Json leaves out values equal to their defaults). */
    fun toJson(): JsonObject = buildJsonObject {
        put("every", JsonPrimitive(every))
        put("prompt", JsonPrimitive(prompt))
    }
}

/** [Rhythm.every]'s values, as offered in Group settings. */
enum class RhythmEvery(val wire: String, val label: String) {
    Week("week", "Every week"),
    TwoWeeks("two-weeks", "Every two weeks"),
    ThreeWeeks("three-weeks", "Every three weeks"),
    Month("month", "Every month"),
    TwoMonths("two-months", "Every two months"),
    ThreeMonths("three-months", "Every three months"),
    SixMonths("six-months", "Every six months"),
    Year("year", "Every year"),
}

/** `GET /groups/:id/next`: the free time nearest the group's next meet-up, or null without a rhythm. */
@Serializable
data class NextSuggestionResponse(val suggestion: NextSuggestion? = null)

/** [target] (`YYYY-MM-DD`) is when the rhythm says the next one falls; [slot] the nearest free time, if any. */
@Serializable
data class NextSuggestion(val target: String, val slot: Slot? = null)

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
    /** Archive it automatically once its event has passed. */
    val autoArchive: Boolean? = null,
    /** A [Rhythm] as JSON, or [kotlinx.serialization.json.JsonNull] to stop it; left out (null) to keep it. */
    val rhythm: JsonElement? = null,
)

@Serializable
data class MemberRoleRequest(val role: Role)

/** The name you go by in one group; null goes back to your account name (sent as `null`, which the server needs). */
@Serializable
data class MemberNameRequest(val displayName: String?)

/** Leaving: the owner names the admin who becomes the owner (null otherwise, left out of the JSON). */
@Serializable
data class LeaveGroupRequest(val newOwnerId: String? = null)

@Serializable
data class NudgeResponse(val nudged: Int)

/**
 * Neither: a link anyone can use. Emails: one invite each, emailed, only for that person.
 * Usernames: one invite each, to the person's Activity, only for them. User ids: the same, for
 * people already in a group with you. At most 20 people in all.
 */
@Serializable
data class CreateInviteRequest(
    val emails: List<String>? = null,
    val usernames: List<String>? = null,
    val userIds: List<String>? = null,
    /** For a link: how many people can join with it, or [kotlinx.serialization.json.JsonNull] for no limit. Left out: one. */
    val maxUses: JsonElement? = null,
)

/** The most people one invite link can be for (short of Unlimited). */
const val MAX_LINK_USES = 1000

/** Keeps what's typed in the "people" field to digits, at most as many as [MAX_LINK_USES] has. */
fun linkLimitInput(text: String): String = text.filter(Char::isDigit).take(MAX_LINK_USES.toString().length)

/** How many people a link is for, from what's typed: a whole number clamped to 1–[MAX_LINK_USES]; one when empty. */
fun linkLimitFrom(text: String): Int {
    val digits = text.filter(Char::isDigit).trimStart('0')
    if (digits.isEmpty()) return 1
    if (digits.length > MAX_LINK_USES.toString().length) return MAX_LINK_USES
    return digits.toInt().coerceIn(1, MAX_LINK_USES)
}

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
    /** The account name of the person it was sent to in Activity (older servers omit it). */
    val name: String? = null,
    val url: String,
    /** How many people can join with it (null: no limit). Older servers omit it and [uses]: single-use. */
    val maxUses: Int? = null,
    /** How many people have joined with it so far. */
    val uses: Int? = null,
    val expiresAt: String,
) {
    val isLink: Boolean get() = email == null && username == null && name == null

    /** How many people can still join with it (null: no limit). */
    val usesLeft: Int? get() = if (uses == null) 1 else maxUses?.let { (it - uses).coerceAtLeast(0) }

    /** "One person can join with this link." / "5 people can…" / "3 of 5 people can still…" / "Anyone with this link can join." */
    val linkSummary: String
        get() {
            val left = usesLeft ?: return "Anyone with this link can join."
            val total = maxUses ?: 1
            return when {
                total == 1 -> "One person can join with this link."
                left == total -> "$total people can join with this link."
                else -> "$left of $total people can still join with this link."
            }
        }

    /** For the invites list: "Link for one person" / "3 of 5 left" / "Unlimited · 4 joined". */
    val linkUsage: String
        get() {
            val left = usesLeft ?: return if ((uses ?: 0) > 0) "Unlimited · $uses joined" else "Unlimited"
            val total = maxUses ?: 1
            return if (total == 1) "Link for one person" else "$left of $total left"
        }
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
    /** Sent to one person's Activity, so they can decline it (older servers omit it). */
    val personal: Boolean = false,
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
    /** A bank holiday in the group owner's region ([holiday] is then its name). */
    val bankHoliday: Boolean = false,
    /** Members whose preferred times this day's time that works fits. */
    val preferredBy: List<String> = emptyList(),
    /** A `match` day whose time only works with someone missing (a quorum or optional members). */
    val partial: Boolean = false,
)

/** `label` e.g. "Fri 13 – Sun 15 Nov". `preferredBy`: members whose preferred times it fits. */
@Serializable
data class Slot(
    val start: String,
    val end: String,
    val label: String,
    val preferredBy: List<String> = emptyList(),
    /** Synced members busy then (with a quorum or optional members); empty when everyone's free. */
    val missingMemberIds: List<String> = emptyList(),
    /** Pro: the "best date" score (higher is better); null without Pro or on older servers. */
    val score: Double? = null,
    /** Pro: people this time suits who've compromised in the group's recent plans. */
    val fairFor: List<String> = emptyList(),
)

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
    /** With a quorum or optional members: when enough of the group is free (includes [everyoneFree]). */
    val mostFree: List<Interval> = emptyList(),
)

@Serializable
data class DayMember(
    val userId: String,
    val pending: Boolean,
    val busy: List<DayBusy> = emptyList(),
)

@Serializable
data class DayBusy(val start: String, val end: String, val manual: Boolean)
