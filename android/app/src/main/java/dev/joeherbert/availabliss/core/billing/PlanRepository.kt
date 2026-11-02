package dev.joeherbert.availabliss.core.billing

import dev.joeherbert.availabliss.core.data.ApiCaller
import dev.joeherbert.availabliss.core.network.ApiResult
import dev.joeherbert.availabliss.core.network.AvailablissApi
import dev.joeherbert.availabliss.core.network.model.Plan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Your plan (`/me/plan`). The server enforces limits; the app only shows this state. */
@Singleton
class PlanRepository @Inject constructor(
    private val api: AvailablissApi,
    private val call: ApiCaller,
) {
    private val _plan = MutableStateFlow<Plan?>(null)
    val plan: StateFlow<Plan?> = _plan.asStateFlow()

    suspend fun refresh(): ApiResult<Plan> = remember(call { api.getPlan() })

    /** After a purchase or restore: the server re-reads RevenueCat straight away. */
    suspend fun refreshAfterPurchase(): ApiResult<Plan> = remember(call { api.refreshPlan() })

    fun clear() {
        _plan.value = null
    }

    private fun remember(result: ApiResult<Plan>): ApiResult<Plan> {
        if (result is ApiResult.Success) _plan.value = result.value
        return result
    }
}
