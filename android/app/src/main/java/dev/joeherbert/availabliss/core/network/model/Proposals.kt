package dev.joeherbert.availabliss.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ResponseKind {
    @SerialName("pending") Pending,
    @SerialName("accepted") Accepted,
    @SerialName("declined") Declined,
}

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
    val responses: List<ProposalResponse> = emptyList(),
    /** Open proposals: members whose calendars or blocked time clash with it (ids only). */
    val busyMemberIds: List<String> = emptyList(),
)

@Serializable
data class ConflictsResponse(val busyMemberIds: List<String> = emptyList())

@Serializable
data class ProposalResponse(
    val userId: String,
    val response: ResponseKind,
    val setBy: String? = null,
    val updatedAt: String,
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
    val createdBy: String,
    val createdAt: String,
    /** Only present on `GET /me/upcoming`. */
    val groupName: String? = null,
)

@Serializable
data class EventsResponse(val events: List<GroupEvent>)

@Serializable
data class CreateEventRequest(
    val title: String,
    val start: String,
    val end: String,
    val allDay: Boolean = false,
    val location: String? = null,
    val note: String? = null,
)
