package dev.joeherbert.availabliss.core.billing

import dev.joeherbert.availabliss.core.common.DateTimes
import dev.joeherbert.availabliss.core.network.model.Plan

/** Copy for plans and store packages (mirrors web `client/plan.ts`). Pure, so it's unit tested. */
object PlanLabels {
    const val FREE_GROUP_LIMIT = 3

    /** RevenueCat package identifiers → ("Yearly", "/year"). Unknown packages use the product title. */
    private val PACKAGES = mapOf(
        "\$rc_monthly" to ("Monthly" to "/month"),
        "\$rc_annual" to ("Yearly" to "/year"),
        "\$rc_six_month" to ("6 months" to "/6 months"),
        "\$rc_three_month" to ("3 months" to "/3 months"),
        "\$rc_weekly" to ("Weekly" to "/week"),
        "\$rc_lifetime" to ("Lifetime" to " once"),
    )

    fun packageLabel(identifier: String, productTitle: String): Pair<String, String> =
        PACKAGES[identifier] ?: (productTitle to "")

    /** The yearly package is the one we emphasise. */
    fun isFeatured(identifier: String) = identifier == "\$rc_annual"

    /** "Google Play" / "the App Store" for subscriptions managed in a store; null otherwise. */
    fun storeName(store: String?): String? = when (store) {
        "play_store" -> "Google Play"
        "app_store", "mac_app_store" -> "the App Store"
        else -> null
    }

    /** "2 of 3 groups created", "Unlimited groups · renews 2 Dec", "Unlimited groups, for life". */
    fun summary(plan: Plan, dayMonth: (String) -> String? = ::dayMonth): String = when {
        !plan.isPro -> "${plan.groupsOwned} of ${plan.groupLimit ?: FREE_GROUP_LIMIT} groups created"
        plan.lifetime -> "Unlimited groups, for life"
        else -> plan.proUntil?.let(dayMonth)?.let { "Unlimited groups · ${if (plan.willRenew) "renews" else "ends"} $it" } ?: "Unlimited groups"
    }

    /** "Renews 2 Dec." / "Ends 2 Dec." / "It never expires." */
    fun proStatus(plan: Plan, dayMonth: (String) -> String? = ::dayMonth): String = when {
        plan.lifetime -> "It never expires."
        else -> plan.proUntil?.let(dayMonth)?.let { "${if (plan.willRenew) "Renews" else "Ends"} $it." }.orEmpty()
    }

    private fun dayMonth(iso: String): String? = DateTimes.localDate(iso)?.let(DateTimes::dayMonth)
}
