package dev.joeherbert.availabliss.core.data

import dev.joeherbert.availabliss.core.network.ApiResult
import dev.joeherbert.availabliss.core.network.AvailablissApi
import dev.joeherbert.availabliss.core.network.model.PlannerRequest
import dev.joeherbert.availabliss.core.network.model.PlannerResult
import javax.inject.Inject
import javax.inject.Singleton

/** The optional AI planner (Pro): `POST /groups/:id/planner`. Runs only when someone asks. */
@Singleton
class PlannerRepository @Inject constructor(
    private val api: AvailablissApi,
    private val call: ApiCaller,
) {
    /** 402 (UpgradeRequired) without Pro; 409 (Conflict) when no time works yet. */
    suspend fun plan(groupId: String, notes: String?): ApiResult<PlannerResult> =
        call { api.planWithAi(groupId, PlannerRequest(notes?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_NOTES))) }

    companion object {
        const val MAX_NOTES = 500
    }
}
