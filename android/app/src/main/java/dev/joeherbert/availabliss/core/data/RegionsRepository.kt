package dev.joeherbert.availabliss.core.data

import dev.joeherbert.availabliss.core.common.ApplicationScope
import dev.joeherbert.availabliss.core.network.ApiResult
import dev.joeherbert.availabliss.core.network.AvailablissApi
import dev.joeherbert.availabliss.core.network.getOrNull
import dev.joeherbert.availabliss.core.network.map
import dev.joeherbert.availabliss.core.network.model.DetectedRegion
import dev.joeherbert.availabliss.core.network.model.Holiday
import dev.joeherbert.availabliss.core.network.model.NamedCode
import dev.joeherbert.availabliss.core.network.model.SchoolAuthority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Countries, subdivisions, school authorities and holidays (the `/regions` and `/holidays` endpoints).
 *
 * The region lists need no sign-in, so sign-in starts them early ([prefetch]) and the sign-up
 * wizard and timing editor find them ready. Each is fetched once: asking again while it's in flight
 * joins that request, and a failure is tried again next time.
 */
@Singleton
class RegionsRepository @Inject constructor(
    private val api: AvailablissApi,
    private val call: ApiCaller,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val requests = ConcurrentHashMap<String, Deferred<ApiResult<*>>>()

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T> shared(key: String, fetch: suspend () -> ApiResult<T>): ApiResult<T> {
        val request = requests.computeIfAbsent(key) { scope.async { fetch() } }
        val result = request.await() as ApiResult<T>
        if (result is ApiResult.Failure) requests.remove(key, request)
        return result
    }

    /** Starts where you seem to be, the countries, and that country's regions and school authorities. */
    fun prefetch() {
        scope.launch { countries() }
        scope.launch {
            val country = detect().getOrNull()?.country ?: return@launch
            prefetchDetails(country)
        }
    }

    fun prefetchDetails(country: String) {
        scope.launch { subdivisions(country) }
        scope.launch { schoolAuthorities(country) }
    }

    suspend fun detect(): ApiResult<DetectedRegion> = shared("detect") { call { api.detectRegion() } }

    suspend fun countries(): ApiResult<List<NamedCode>> = shared("countries") { call { api.getCountries() }.map { it.countries } }

    suspend fun subdivisions(country: String): ApiResult<List<NamedCode>> =
        shared("subdivisions:$country") { call { api.getSubdivisions(country) }.map { it.subdivisions } }

    suspend fun schoolAuthorities(country: String): ApiResult<List<SchoolAuthority>> =
        shared("authorities:$country") { call { api.getSchoolAuthorities(country) }.map { it.authorities } }

    suspend fun holidays(country: String, subdivision: String?, from: String, to: String): ApiResult<List<Holiday>> =
        call { api.getHolidays(country, subdivision, from, to) }.map { it.holidays }
}
