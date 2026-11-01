package dev.joeherbert.availabliss.core.data

import dev.joeherbert.availabliss.core.network.ApiResult
import dev.joeherbert.availabliss.core.network.AvailablissApi
import dev.joeherbert.availabliss.core.network.map
import dev.joeherbert.availabliss.core.network.model.DetectedRegion
import dev.joeherbert.availabliss.core.network.model.Holiday
import dev.joeherbert.availabliss.core.network.model.NamedCode
import dev.joeherbert.availabliss.core.network.model.SchoolAuthority
import dev.joeherbert.availabliss.core.network.onSuccess
import javax.inject.Inject
import javax.inject.Singleton

/** Countries, subdivisions, school authorities and holidays (`/regions/*`, `/holidays`). */
@Singleton
class RegionsRepository @Inject constructor(
    private val api: AvailablissApi,
    private val call: ApiCaller,
) {
    private var countriesCache: List<NamedCode>? = null

    suspend fun detect(): ApiResult<DetectedRegion> = call { api.detectRegion() }

    suspend fun countries(): ApiResult<List<NamedCode>> {
        countriesCache?.let { return ApiResult.Success(it) }
        return call { api.getCountries() }.map { it.countries }.onSuccess { countriesCache = it }
    }

    suspend fun subdivisions(country: String): ApiResult<List<NamedCode>> =
        call { api.getSubdivisions(country) }.map { it.subdivisions }

    suspend fun schoolAuthorities(country: String): ApiResult<List<SchoolAuthority>> =
        call { api.getSchoolAuthorities(country) }.map { it.authorities }

    suspend fun holidays(country: String, subdivision: String?, from: String, to: String): ApiResult<List<Holiday>> =
        call { api.getHolidays(country, subdivision, from, to) }.map { it.holidays }
}
