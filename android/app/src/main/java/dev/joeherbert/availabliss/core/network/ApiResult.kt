package dev.joeherbert.availabliss.core.network

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/** Outcome of an API call: either a value or a typed [ApiError]. */
sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>
    data class Failure(val error: ApiError) : ApiResult<Nothing>
}

inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Success -> ApiResult.Success(transform(value))
    is ApiResult.Failure -> this
}

inline fun <T> ApiResult<T>.onSuccess(action: (T) -> Unit): ApiResult<T> {
    if (this is ApiResult.Success) action(value)
    return this
}

fun <T> ApiResult<T>.getOrNull(): T? = (this as? ApiResult.Success)?.value

/**
 * Runs a Retrofit call and converts every failure mode (HTTP error body, I/O, bad JSON)
 * into an [ApiError]. Coroutine cancellation is rethrown.
 */
suspend fun <T> apiCall(json: Json, block: suspend () -> T): ApiResult<T> = try {
    ApiResult.Success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: HttpException) {
    ApiResult.Failure(parseHttpError(json, e.code(), e.response()?.errorBody()?.string()))
} catch (e: IOException) {
    ApiResult.Failure(ApiError.Network())
} catch (e: SerializationException) {
    ApiResult.Failure(ApiError.Unknown("We couldn't read the server's response."))
}

/** Converts a non-successful raw [Response] (used where headers are needed) into an [ApiError]. */
fun Response<*>.toApiError(json: Json): ApiError = parseHttpError(json, code(), errorBody()?.string())

internal fun parseHttpError(json: Json, code: Int, body: String?): ApiError {
    val parsed = body?.takeIf { it.isNotBlank() }?.let {
        runCatching { json.decodeFromString(ApiErrorBody.serializer(), it) }.getOrNull()
    }
    return ApiError.from(code, parsed)
}
