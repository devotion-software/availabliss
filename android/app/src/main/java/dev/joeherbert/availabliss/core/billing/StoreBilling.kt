package dev.joeherbert.availabliss.core.billing

import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import android.app.Activity
import android.content.Context
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.PurchasesException
import com.revenuecat.purchases.PurchasesTransactionException
import com.revenuecat.purchases.awaitLogIn
import com.revenuecat.purchases.awaitOfferings
import com.revenuecat.purchases.awaitPurchase
import com.revenuecat.purchases.awaitRestore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** A package from the current offering, ready to show and buy. */
data class StorePackage(
    val id: String,
    /** "Yearly" */
    val label: String,
    /** "£19.99" (localised by Google Play) */
    val price: String,
    /** "/year", "" */
    val per: String,
    val featured: Boolean,
    internal val raw: Package,
)

sealed interface PurchaseOutcome {
    data object Purchased : PurchaseOutcome
    data object Cancelled : PurchaseOutcome
    data class Failed(val message: String) : PurchaseOutcome
}

/**
 * Google Play purchases through RevenueCat. Configured once per signed-in session (see
 * SessionManager) when the server hands out an Android key, so a purchase interrupted
 * by the process dying is finished and acknowledged on the next launch rather than
 * refunded by Play after 3 days. Always uses the signed-in user's id as the app user id
 * so Pro follows the account to iOS and the web.
 */
@Singleton
class StoreBilling @Inject constructor(@ApplicationContext private val context: Context) {
    private val mutex = Mutex()

    /** Configures (or switches to) [userId]. Safe to call repeatedly. */
    suspend fun ensureConfigured(apiKey: String, userId: String) = mutex.withLock {
        withContext(Dispatchers.Main) {
            if (!Purchases.isConfigured) {
                Purchases.configure(PurchasesConfiguration.Builder(context, apiKey).appUserID(userId).build())
            } else if (Purchases.sharedInstance.appUserID != userId) {
                Purchases.sharedInstance.awaitLogIn(userId)
            }
        }
    }

    /** The current offering's packages, or empty when nothing is on sale. */
    suspend fun packages(): List<StorePackage> =
        Purchases.sharedInstance.awaitOfferings().current?.availablePackages.orEmpty().map { pkg ->
            val (label, per) = PlanLabels.packageLabel(pkg.identifier, pkg.product.title)
            StorePackage(pkg.identifier, label, pkg.product.price.formatted, per, PlanLabels.isFeatured(pkg.identifier), pkg)
        }

    suspend fun purchase(activity: Activity, pkg: StorePackage): PurchaseOutcome = try {
        Purchases.sharedInstance.awaitPurchase(PurchaseParams.Builder(activity, pkg.raw).build())
        PurchaseOutcome.Purchased
    } catch (e: PurchasesTransactionException) {
        if (e.userCancelled) PurchaseOutcome.Cancelled else PurchaseOutcome.Failed(e.error.message)
    }

    /** Restores Play purchases to this account. Null when it worked, else the error. */
    suspend fun restore(): String? = try {
        Purchases.sharedInstance.awaitRestore()
        null
    } catch (e: PurchasesException) {
        e.error.message
    }

    /** On sign-out, so the next account doesn't inherit this one's purchases. */
    fun logOut() {
        if (Purchases.isConfigured) runCatching { Purchases.sharedInstance.logOut() }
    }
}
