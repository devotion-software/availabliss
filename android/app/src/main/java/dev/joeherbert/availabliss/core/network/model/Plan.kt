package dev.joeherbert.availabliss.core.network.model

import kotlinx.serialization.Serializable

/** `GET /me/plan`: your tier, group allowance and how to buy Pro (API.md "Plans (Pro)"). */
@Serializable
data class Plan(
    /** "free" or "pro". */
    val tier: String = "free",
    /** When Pro renews or ends; null on free or lifetime. */
    val proUntil: String? = null,
    val lifetime: Boolean = false,
    val willRenew: Boolean = false,
    /** app_store, play_store, stripe, rc_billing, promotional. */
    val store: String? = null,
    /** Null = unlimited. */
    val groupLimit: Int? = null,
    val groupsOwned: Int = 0,
    val canCreateGroup: Boolean = true,
    val purchase: PlanPurchase = PlanPurchase(),
    val aiPlanner: AiPlannerAccess = AiPlannerAccess(),
) {
    val isPro: Boolean get() = tier == "pro"
}

/** Public RevenueCat SDK keys; a null key hides purchasing on that platform. */
@Serializable
data class PlanPurchase(
    val ios: String? = null,
    val android: String? = null,
    val web: String? = null,
    val entitlement: String = "pro",
)

/** The optional AI planner: set up on the server / usable on this plan. */
@Serializable
data class AiPlannerAccess(val available: Boolean = false, val included: Boolean = false)
