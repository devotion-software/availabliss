package dev.joeherbert.availabliss.feature.plan

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.joeherbert.availabliss.core.billing.PlanLabels
import dev.joeherbert.availabliss.core.billing.PlanRepository
import dev.joeherbert.availabliss.core.billing.PurchaseOutcome
import dev.joeherbert.availabliss.core.billing.StoreBilling
import dev.joeherbert.availabliss.core.billing.StorePackage
import dev.joeherbert.availabliss.core.data.UserRepository
import dev.joeherbert.availabliss.core.network.ApiResult
import dev.joeherbert.availabliss.core.network.model.Plan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the upgrade sheet can offer for buying. */
sealed interface PurchaseUi {
    data object Loading : PurchaseUi

    /** No Android key on the server: point at the web. */
    data object Unavailable : PurchaseUi

    /** The offering is empty. */
    data object NotOnSale : PurchaseUi

    data class Packages(val packages: List<StorePackage>) : PurchaseUi

    data class Failed(val message: String) : PurchaseUi
}

data class UpgradeUiState(
    val plan: Plan? = null,
    val loadError: String? = null,
    val purchase: PurchaseUi = PurchaseUi.Loading,
    /** Package being bought. */
    val buying: String? = null,
    val restoring: Boolean = false,
    /** Shown inside the sheet (a failed purchase, nothing to restore). */
    val note: String? = null,
    /** Set when Pro switched on (or a payment went through): the host closes the sheet and shows it. */
    val finished: String? = null,
) {
    val busy: Boolean get() = buying != null || restoring

    /** Pro was bought somewhere other than Google Play. */
    val managedOnWeb: Boolean get() = plan?.store == "stripe" || plan?.store == "rc_billing"
    val managedInPlay: Boolean get() = plan?.store == "play_store"
    val otherStore: String? get() = PlanLabels.storeName(plan?.store)?.takeIf { !managedInPlay }
}

/** "Availabliss Pro": perks, the Play Store offering, restore, and the Pro status when you have it. */
@HiltViewModel
class UpgradeViewModel @Inject constructor(
    private val plans: PlanRepository,
    private val billing: StoreBilling,
    private val users: UserRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(UpgradeUiState(plan = plans.plan.value))
    val state: StateFlow<UpgradeUiState> = _state.asStateFlow()

    /** Called each time the sheet opens. */
    fun load() {
        _state.update { UpgradeUiState(plan = plans.plan.value) }
        viewModelScope.launch {
            val plan = when (val result = plans.refresh()) {
                is ApiResult.Success -> result.value
                is ApiResult.Failure -> return@launch _state.update { it.copy(loadError = result.error.message) }
            }
            _state.update { it.copy(plan = plan) }
            if (plan.isPro) return@launch
            _state.update { it.copy(purchase = loadPackages(plan)) }
        }
    }

    private suspend fun loadPackages(plan: Plan): PurchaseUi {
        val key = plan.purchase.android ?: return PurchaseUi.Unavailable
        val userId = users.user.value?.id ?: return PurchaseUi.Failed("Couldn't load prices. Try again in a moment.")
        return try {
            billing.ensureConfigured(key, userId)
            billing.packages().let { if (it.isEmpty()) PurchaseUi.NotOnSale else PurchaseUi.Packages(it) }
        } catch (e: Exception) {
            PurchaseUi.Failed("Couldn't load prices: ${e.message ?: "Google Play didn't answer."}")
        }
    }

    /** [activity] hosts Google Play's purchase sheet; it isn't retained. */
    fun buy(activity: Activity, pkg: StorePackage) {
        if (_state.value.busy) return
        _state.update { it.copy(buying = pkg.id, note = null) }
        viewModelScope.launch {
            when (val outcome = billing.purchase(activity, pkg)) {
                PurchaseOutcome.Cancelled -> _state.update { it.copy(buying = null) }
                is PurchaseOutcome.Failed -> _state.update { it.copy(buying = null, note = outcome.message) }
                PurchaseOutcome.Purchased -> {
                    val pro = (plans.refreshAfterPurchase() as? ApiResult.Success)?.value?.isPro == true
                    _state.update {
                        it.copy(buying = null, finished = if (pro) WELCOME else "Payment received. Pro will switch on in a moment.")
                    }
                }
            }
        }
    }

    fun restore() {
        if (_state.value.busy) return
        _state.update { it.copy(restoring = true, note = null) }
        viewModelScope.launch {
            val error = billing.restore()
            if (error != null) return@launch _state.update { it.copy(restoring = false, note = error) }
            val pro = (plans.refreshAfterPurchase() as? ApiResult.Success)?.value?.isPro == true
            _state.update {
                if (pro) it.copy(restoring = false, finished = WELCOME)
                else it.copy(restoring = false, note = "There's no Pro purchase on this Google account to restore.")
            }
        }
    }

    fun finishedHandled() = _state.update { it.copy(finished = null) }

    companion object {
        const val WELCOME = "Welcome to Pro"
        const val PLAY_SUBSCRIPTIONS_URL = "https://play.google.com/store/account/subscriptions"
    }
}
