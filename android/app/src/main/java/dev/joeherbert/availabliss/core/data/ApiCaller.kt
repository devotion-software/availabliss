package dev.joeherbert.availabliss.core.data

import dev.joeherbert.availabliss.core.network.ApiResult
import dev.joeherbert.availabliss.core.network.apiCall
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** Shared entry point for repositories: wraps a Retrofit call in an [ApiResult]. */
@Singleton
class ApiCaller @Inject constructor(private val json: Json) {
    suspend operator fun <T> invoke(block: suspend () -> T): ApiResult<T> = apiCall(json, block)
}
