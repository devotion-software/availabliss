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
 * Adds `Authorization: Bearer <token>` to every request and reports 401s that mean
 * the current session has expired so it can be cleared.
 *
 * Better Auth calls also get an `Origin` header for its CSRF origin check, which
 * expects one whenever a cookie (the passkey challenge) is sent.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val tokenStore: TokenStore,
) : Interceptor {

    private val _unauthorized = MutableSharedFlow<String>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Emits the rejected token when a request sent with the current token comes back 401. */
    val unauthorized: SharedFlow<String> = _unauthorized.asSharedFlow()

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val token = tokenStore.token()
        val path = original.url.encodedPath
        val explicit = original.header("Authorization")
        val request = original.newBuilder().apply {
            if (token != null && explicit == null) {
                header("Authorization", "Bearer $token")
            }
            if (path.startsWith(BetterAuthApi.AUTH_PATH_PREFIX) && original.header("Origin") == null) {
                header("Origin", BuildConfig.API_BASE_URL)
            }
        }.build()

        val response = chain.proceed(request)
        // Read the token again: a sign-in or sign-out may have happened while this was in flight.
        val sentToken = explicit?.removePrefix("Bearer ")?.trim() ?: token
        if (isSessionExpiry(response.code, path, sentToken, tokenStore.token())) {
            _unauthorized.tryEmit(sentToken!!)
        }
        return response
    }

    companion object {
        /**
         * Sign-in and account-link routes answer 401 for a bad credential or code, not
         * because the bearer token is invalid, so they never end the session.
         */
        private val NON_SESSION_PATHS = listOf(
            BetterAuthApi.AUTH_PATH_PREFIX,
            "/api/v1/auth/",
            "/api/v1/me/accounts/link",
        )

        /**
         * Whether a response means the session is over. Only a 401 for the token we
         * still hold counts: a slow request from a previous session must not sign
         * out the one that replaced it, and once the session is cleared (sign out,
         * account deleted) late 401s are moot.
         */
        internal fun isSessionExpiry(code: Int, path: String, sentToken: String?, currentToken: String?): Boolean =
            code == 401 &&
                sentToken != null &&
                sentToken == currentToken &&
                NON_SESSION_PATHS.none { path.startsWith(it) }
    }
}
