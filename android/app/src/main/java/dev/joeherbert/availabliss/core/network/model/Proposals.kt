package dev.joeherbert.availabliss.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A member's reply to a proposal. [Maybe] is a soft yes ("I can make it if I must"): counted
 * apart from [Accepted], and it doesn't confirm a proposal on its own. Replies this build
 * doesn't know decode as [Pending].
 */
@Serializable(with = ResponseKindSerializer::class)
enum class ResponseKind(override val wire: String) : WireEnum {
    Pending("pending"),
    Accepted("accepted"),
    Maybe("maybe"),
    Declined("declined"),
}

object ResponseKindSerializer :
    LenientEnumSerializer<ResponseKind>("ResponseKind", ResponseKind.entries.toTypedArray(), ResponseKind.Pending)

@Serializable
enum class ProposalStatus {
    @SerialName("open") Open,
    @SerialName("confirmed") Confirmed,
    @SerialName("cancelled") Cancelled,
}

@Serializable
data class Proposal(
    val id: String,
    val groupId: String,
    val title: String? = null,
    val location: String? = null,
    val note: String? = null,
    val start: String,
    val end: String,
    val allDay: Boolean = false,
    val status: ProposalStatus,
    val createdBy: String,
    val createdAt: String,
    val eventId: String? = null,
    /** Confirmed before everyone accepted ("Confirm anyway"): who did it. Null otherwise, and on older servers. */
    val confirmedBy: String? = null,
    val responses: List<ProposalResponse> = emptyList(),
    /** Confirmed: someone who was coming can't make it any more; the proposer or an admin should keep or cancel it. */
    val needsReview: Boolean = false,
    /** Open proposals: members whose calendars or blocked time clash with it (ids only). */
    val busyMemberIds: List<String> = emptyList(),
) {
    /**
     * Everyone it was put to firmly accepted; false once confirmed anyway with someone who can't
     * make it, said maybe or never replied.
     */
    val everyoneIn: Boolean get() = responses.all { it.response == ResponseKind.Accepted }
}

@Serializable
data class ConflictsResponse(val busyMemberIds: List<String> = emptyList())

@Serializable
data class ProposalResponse(
    val userId: String,
    val response: ResponseKind,
    val setBy: String? = null,
    val updatedAt: String,
    /** Confirmed: said they can't make it after it was confirmed, not yet reviewed. */
    val droppedOut: Boolean = false,
)

@Serializable
data class ProposalsResponse(val proposals: List<Proposal>)

@Serializable
data class CreateProposalRequest(
    val start: String,
    val end: String,
    val allDay: Boolean = false,
    val title: String? = null,
    val location: String? = null,
    val note: String? = null,
)

@Serializable
data class RespondRequest(val response: ResponseKind)

@Serializable
data class OverrideRequest(val userId: String, val response: ResponseKind)

@Serializable
data class GroupEvent(
    val id: String,
    val groupId: String,
    val proposalId: String? = null,
    val title: String,
    val location: String? = null,
    val note: String? = null,
    val start: String,
    val end: String,
    val allDay: Boolean = false,
    /** Cover photo. Absent on older servers. */
    val image: String? = null,
    val createdBy: String,
    val createdAt: String,
    /** Only present on `GET /me/upcoming`. */
    val groupName: String? = null,
)

@Serializable
data class EventsResponse(val events: List<GroupEvent>)

/**
 * A plan in `GET /me/plans`: a [GroupEvent] with its group's name and your reply to its proposal
 * ([myResponse] null when it was added straight to the calendar, with no proposal).
 */
@Serializable
data class MyPlan(
    val id: String,
    val groupId: String,
    val proposalId: String? = null,
    val title: String,
    val location: String? = null,
    val note: String? = null,
    val start: String,
    val end: String,
    val allDay: Boolean = false,
    val image: String? = null,
    val createdBy: String = "",
    val createdAt: String = "",
    val groupName: String = "",
    val myResponse: ResponseKind? = null,
) {
    /** As a plain event (for adding it to a calendar). */
    val event: GroupEvent
        get() = GroupEvent(id, groupId, proposalId, title, location, note, start, end, allDay, image, createdBy, createdAt, groupName)
}

/** `GET /me/plans`: [going] soonest first, [notGoing] upcoming ones you can't make, [past] newest first. */
@Serializable
data class MyPlansResponse(
    val going: List<MyPlan> = emptyList(),
    val notGoing: List<MyPlan> = emptyList(),
    val past: List<MyPlan> = emptyList(),
)

@Serializable
data class CreateEventRequest(
    val title: String,
    val start: String,
    val end: String,
    val allDay: Boolean = false,
    val location: String? = null,
    val note: String? = null,
)
