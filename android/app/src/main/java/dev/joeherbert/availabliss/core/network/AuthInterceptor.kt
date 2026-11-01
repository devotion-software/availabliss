package dev.joeherbert.availabliss.core.network

import dev.joeherbert.availabliss.BuildConfig
import dev.joeherbert.availabliss.core.auth.TokenStore
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adds `Authorization: Bearer <token>` to every request and reports 401s from the
 * v1 API so the session can be cleared.
 *
 * Better Auth calls also get an `Origin` header for its CSRF origin check, which
 * expects one whenever a cookie (the passkey challenge) is sent.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val tokenStore: TokenStore,
) : Interceptor {

    private val _unauthorized = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Emits when an authenticated `/api/v1` request comes back 401. */
    val unauthorized: SharedFlow<Unit> = _unauthorized.asSharedFlow()

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val token = tokenStore.token()
        val isAuthPath = original.url.encodedPath.startsWith(BetterAuthApi.AUTH_PATH_PREFIX)
        val request = original.newBuilder().apply {
            if (token != null && original.header("Authorization") == null) {
                header("Authorization", "Bearer $token")
            }
            if (isAuthPath && original.header("Origin") == null) {
                header("Origin", BuildConfig.API_BASE_URL)
            }
        }.build()

        val response = chain.proceed(request)
        if (response.code == 401 && token != null && !isAuthPath) {
            _unauthorized.tryEmit(Unit)
        }
        return response
    }
}
