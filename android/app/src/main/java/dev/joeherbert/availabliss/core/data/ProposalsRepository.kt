package dev.joeherbert.availabliss.core.data

import dev.joeherbert.availabliss.core.network.ApiResult
import dev.joeherbert.availabliss.core.network.AvailablissApi
import dev.joeherbert.availabliss.core.network.JsonPatchBuilder
import dev.joeherbert.availabliss.core.network.jsonPatch
import dev.joeherbert.availabliss.core.network.map
import dev.joeherbert.availabliss.core.network.model.CreateEventRequest
import dev.joeherbert.availabliss.core.network.model.CreateProposalRequest
import dev.joeherbert.availabliss.core.network.model.GroupEvent
import dev.joeherbert.availabliss.core.network.model.OverrideRequest
import dev.joeherbert.availabliss.core.network.model.Proposal
import dev.joeherbert.availabliss.core.network.model.ProposalStatus
import dev.joeherbert.availabliss.core.network.model.RespondRequest
import dev.joeherbert.availabliss.core.network.model.ResponseKind
import javax.inject.Inject
import javax.inject.Singleton

/** Proposals (`/groups/:id/proposals`, `/proposals/:id`). */
@Singleton
class ProposalsRepository @Inject constructor(
    private val api: AvailablissApi,
    private val call: ApiCaller,
) {
    suspend fun list(groupId: String, status: ProposalStatus? = null): ApiResult<List<Proposal>> =
        call { api.getProposals(groupId, status?.name?.lowercase()) }.map { it.proposals }

    suspend fun create(groupId: String, request: CreateProposalRequest): ApiResult<Proposal> =
        call { api.createProposal(groupId, request) }

    suspend fun get(id: String): ApiResult<Proposal> = call { api.getProposal(id) }

    /** [response] must be accepted or declined. */
    suspend fun respond(id: String, response: ResponseKind): ApiResult<Proposal> =
        call { api.respond(id, RespondRequest(response)) }

    suspend fun override(id: String, userId: String, response: ResponseKind): ApiResult<Proposal> =
        call { api.overrideResponse(id, OverrideRequest(userId, response)) }

    suspend fun cancel(id: String): ApiResult<Unit> = call { api.cancelProposal(id) }
}

/** Group events (`/groups/:id/events`, `/events/:id`, `/me/upcoming`). */
@Singleton
class EventsRepository @Inject constructor(
    private val api: AvailablissApi,
    private val call: ApiCaller,
) {
    suspend fun list(groupId: String, from: String? = null, to: String? = null): ApiResult<List<GroupEvent>> =
        call { api.getEvents(groupId, from, to) }.map { it.events }

    suspend fun get(id: String): ApiResult<GroupEvent> = call { api.getEvent(id) }

    suspend fun upcoming(): ApiResult<List<GroupEvent>> = call { api.getUpcoming() }.map { it.events }

    suspend fun create(groupId: String, request: CreateEventRequest): ApiResult<GroupEvent> =
        call { api.createEvent(groupId, request) }

    suspend fun update(id: String, block: JsonPatchBuilder.() -> Unit): ApiResult<GroupEvent> =
        call { api.updateEvent(id, jsonPatch(block)) }

    suspend fun delete(id: String): ApiResult<Unit> = call { api.deleteEvent(id) }
}
