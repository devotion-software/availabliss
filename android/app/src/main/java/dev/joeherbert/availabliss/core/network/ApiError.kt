package dev.joeherbert.availabliss.core.network

import kotlinx.serialization.Serializable

/** Error body of every non-2xx response (API.md "Errors"). */
@Serializable
data class ApiErrorBody(
    val message: String? = null,
    val code: String? = null,
    val issues: List<ApiIssue>? = null,
)

@Serializable
data class ApiIssue(val path: String, val message: String)

/** Typed failure for every API call. [message] is safe to show to the user. */
sealed interface ApiError {
    val message: String

    data class Unauthorized(override val message: String = "Please sign in again.") : ApiError
    data class Forbidden(override val message: String = "You don't have permission to do that.") : ApiError
    data class NotFound(override val message: String = "That couldn't be found.") : ApiError
    data class Validation(
        override val message: String = "Please check the details and try again.",
        val issues: List<ApiIssue> = emptyList(),
    ) : ApiError {
        /** First issue message for a given field path, for inline form errors. */
        fun issueFor(path: String): String? = issues.firstOrNull { it.path == path }?.message
    }
    data class Conflict(override val message: String = "That conflicts with something that already exists.") : ApiError
    /** 402 `upgrade_required`: the free plan's limit (e.g. 3 groups) or a Pro-only feature. */
    data class UpgradeRequired(override val message: String = "That's part of Availabliss Pro.") : ApiError
    data class RateLimited(override val message: String = "Slow down a little and try again shortly.") : ApiError
    data class Server(override val message: String = "Something went wrong on our side. Please try again.") : ApiError
    data class Network(override val message: String = "You appear to be offline. Check your connection and try again.") : ApiError
    data class Unknown(override val message: String = "Something went wrong. Please try again.", val httpCode: Int? = null) : ApiError

    companion object {
        /** Maps an HTTP status + parsed body to the matching error. Falls back to the status code. */
        fun from(httpCode: Int, body: ApiErrorBody?): ApiError {
            val msg = body?.message?.takeIf { it.isNotBlank() }
            val code = body?.code ?: when (httpCode) {
                400, 422 -> "validation_error"
                401 -> "unauthorized"
                402 -> "upgrade_required"
                403 -> "forbidden"
                404 -> "not_found"
                409 -> "conflict"
                429 -> "rate_limited"
                in 500..599 -> "server_error"
                else -> null
            }
            return when (code) {
                "unauthorized" -> msg?.let(::Unauthorized) ?: Unauthorized()
                "forbidden" -> msg?.let(::Forbidden) ?: Forbidden()
                "not_found" -> msg?.let(::NotFound) ?: NotFound()
                "validation_error" -> Validation(msg ?: Validation().message, body?.issues.orEmpty())
                "conflict" -> msg?.let(::Conflict) ?: Conflict()
                "rate_limited" -> msg?.let(::RateLimited) ?: RateLimited()
                "upgrade_required" -> msg?.let(::UpgradeRequired) ?: UpgradeRequired()
                "server_error" -> msg?.let(::Server) ?: Server()
                else -> Unknown(msg ?: Unknown().message, httpCode)
            }
        }
    }
}
