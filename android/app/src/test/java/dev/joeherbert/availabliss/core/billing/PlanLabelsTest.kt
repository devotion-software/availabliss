package dev.joeherbert.availabliss.core.billing

import dev.joeherbert.availabliss.core.network.ApiError
import dev.joeherbert.availabliss.core.network.NetworkModule
import dev.joeherbert.availabliss.core.network.model.Plan
import dev.joeherbert.availabliss.core.network.parseHttpError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanLabelsTest {
    private val json = NetworkModule.provideJson()
    private val dayMonth: (String) -> String? = { "2 Dec" }

    @Test
    fun decodesThePlanFromTheApi() {
        val body = """{"tier":"free","proUntil":null,"lifetime":false,"willRenew":false,"store":null,"groupLimit":3,"groupsOwned":2,"canCreateGroup":true,"aiPlanner":{"available":false,"included":false},"purchase":{"ios":null,"android":null,"web":null,"entitlement":"pro"}}"""
        val plan = json.decodeFromString(Plan.serializer(), body)
        assertFalse(plan.isPro)
        assertEquals(3, plan.groupLimit)
        assertNull(plan.purchase.android)
        assertTrue(plan.canCreateGroup)
    }

    @Test
    fun summaries() {
        assertEquals("2 of 3 groups created", PlanLabels.summary(Plan(groupLimit = 3, groupsOwned = 2), dayMonth))
        assertEquals("Unlimited groups · renews 2 Dec", PlanLabels.summary(Plan(tier = "pro", proUntil = "x", willRenew = true), dayMonth))
        assertEquals("Unlimited groups · ends 2 Dec", PlanLabels.summary(Plan(tier = "pro", proUntil = "x"), dayMonth))
        assertEquals("Unlimited groups, for life", PlanLabels.summary(Plan(tier = "pro", lifetime = true), dayMonth))
        assertEquals("It never expires.", PlanLabels.proStatus(Plan(tier = "pro", lifetime = true), dayMonth))
        assertEquals("Renews 2 Dec.", PlanLabels.proStatus(Plan(tier = "pro", proUntil = "x", willRenew = true), dayMonth))
    }

    @Test
    fun packagesAndStores() {
        assertEquals("Yearly" to "/year", PlanLabels.packageLabel("\$rc_annual", "Pro (Availabliss)"))
        assertEquals("Pro custom" to "", PlanLabels.packageLabel("custom", "Pro custom"))
        assertTrue(PlanLabels.isFeatured("\$rc_annual"))
        assertEquals("Google Play", PlanLabels.storeName("play_store"))
        assertNull(PlanLabels.storeName("stripe"))
    }

    @Test
    fun groupLimitIsUpgradeRequired() {
        val error = parseHttpError(json, 402, """{"message":"Free accounts can create up to 3 groups.","code":"upgrade_required"}""")
        assertTrue(error is ApiError.UpgradeRequired)
        assertEquals("Free accounts can create up to 3 groups.", error.message)
    }
}
