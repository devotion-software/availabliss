package dev.joeherbert.availabliss.core.auth

import android.content.Context
import dev.joeherbert.availabliss.core.data.UserRepository
import dev.joeherbert.availabliss.core.network.ApiError
import dev.joeherbert.availabliss.core.network.ApiResult
import dev.joeherbert.availabliss.core.network.BetterAuthApi
import dev.joeherbert.availabliss.core.network.PasskeyCookieJar
import dev.joeherbert.availabliss.core.network.apiCall
import dev.joeherbert.availabliss.core.network.map
import dev.joeherbert.availabliss.core.network.model.EmailOtpSignInRequest
import dev.joeherbert.availabliss.core.network.model.PasskeyRegisterRequest
import dev.joeherbert.availabliss.core.network.model.PasskeyVerifyRequest
import dev.joeherbert.availabliss.core.network.model.SendOtpRequest
import dev.joeherbert.availabliss.core.network.model.SignInResponse
import dev.joeherbert.availabliss.core.network.model.SocialIdToken
import dev.joeherbert.availabliss.core.network.model.SocialIdTokenSignInRequest
import dev.joeherbert.availabliss.core.network.model.SocialRedirectSignInRequest
import dev.joeherbert.availabliss.core.network.model.User
import dev.joeherbert.availabliss.core.network.toApiError
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

/** Outcome of a sign-in attempt. */
sealed interface SignInResult {
    data class Success(val user: User) : SignInResult
    /** The user dismissed a system sheet; show nothing. */
    data object Cancelled : SignInResult
    data class Failed(val message: String) : SignInResult
}

/**
 * Better Auth sign-in flows (API.md "Sign-in flows"). Every successful call stores the
 * `set-auth-token` header in [TokenStore] and loads the full API user from `GET /me`.
 */
@Singleton
class AuthRepository @Inject constructor(
    private val authApi: BetterAuthApi,
    private val tokenStore: TokenStore,
    private val users: UserRepository,
    private val credentials: CredentialAuthenticator,
    private val cookieJar: PasskeyCookieJar,
    private val json: Json,
) {
    val isGoogleAvailable: Boolean get() = credentials.isGoogleConfigured

    // region Email one-time code
    suspend fun sendCode(email: String): ApiResult<Unit> =
        authCall { authApi.sendOtp(SendOtpRequest.signIn(email.trim())) }.map { }

    suspend fun verifyCode(email: String, code: String): SignInResult =
        completeSignIn { authApi.signInWithOtp(EmailOtpSignInRequest(email.trim(), code.trim())) }
    // endregion

    // region Google (Credential Manager → ID token → POST /api/auth/sign-in/social)
    suspend fun signInWithGoogle(activity: Context): SignInResult =
        when (val outcome = credentials.googleIdToken(activity)) {
            is CredentialOutcome.Success -> completeSignIn {
                authApi.signInWithIdToken(SocialIdTokenSignInRequest("google", SocialIdToken(outcome.value)))
            }
            CredentialOutcome.Cancelled -> SignInResult.Cancelled
            is CredentialOutcome.Failed -> SignInResult.Failed(outcome.message)
        }
    // endregion

    // region Passkeys
    suspend fun signInWithPasskey(activity: Context): SignInResult {
        cookieJar.clear()
        val options = when (val result = authCall { authApi.passkeyAuthenticateOptions() }) {
            is ApiResult.Success -> result.value.body()
            is ApiResult.Failure -> return SignInResult.Failed(result.error.message)
        } ?: return SignInResult.Failed("Couldn't start passkey sign in.")

        return try {
            when (val outcome = credentials.passkeyAssertion(activity, options.toString())) {
                is CredentialOutcome.Success -> completeSignIn {
                    authApi.passkeyVerifyAuthentication(PasskeyVerifyRequest(json.parseToJsonElement(outcome.value).jsonObject))
                }
                CredentialOutcome.Cancelled -> SignInResult.Cancelled
                is CredentialOutcome.Failed -> SignInResult.Failed(outcome.message)
            }
        } finally {
            cookieJar.clear()
        }
    }

    /** Adds a passkey to the signed-in account. Returns null on success, else a message (or "" if cancelled). */
    suspend fun registerPasskey(activity: Context, name: String?): String? {
        cookieJar.clear()
        val options = when (val result = authCall { authApi.passkeyRegisterOptions() }) {
            is ApiResult.Success -> result.value.body()
            is ApiResult.Failure -> return result.error.message
        } ?: return "Couldn't start creating a passkey."

        return try {
            when (val outcome = credentials.passkeyRegistration(activity, options.toString())) {
                is CredentialOutcome.Success -> {
                    val body = PasskeyRegisterRequest(json.parseToJsonElement(outcome.value).jsonObject, name)
                    when (val result = authCall { authApi.passkeyVerifyRegistration(body) }) {
                        is ApiResult.Success -> null
                        is ApiResult.Failure -> result.error.message
                    }
                }
                CredentialOutcome.Cancelled -> ""
                is CredentialOutcome.Failed -> outcome.message
            }
        } finally {
            cookieJar.clear()
        }
    }
    // endregion

    // region Apple (web flow in a Custom Tab)
    /**
     * Starts Sign in with Apple and returns Apple's authorize URL to open in a Custom Tab.
     * When the user finishes, the server is expected to redirect to
     * `availabliss://auth/callback?token=<bearer>`; see [completeWebSignIn] and the README.
     */
    suspend fun startAppleSignIn(): ApiResult<String> {
        val request = SocialRedirectSignInRequest(provider = "apple", callbackURL = APPLE_CALLBACK, disableRedirect = true)
        return when (val result = authCall { authApi.startSocialRedirect(request) }) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> result.value.body()?.url?.let { ApiResult.Success(it) }
                ?: ApiResult.Failure(ApiError.Unknown("Sign in with Apple isn't available right now."))
        }
    }

    /** Completes a web sign-in from the `availabliss://auth/callback` deep link. */
    suspend fun completeWebSignIn(token: String?, error: String?): SignInResult {
        if (token.isNullOrBlank()) return SignInResult.Failed(error ?: "Sign in didn't finish. Please try again.")
        tokenStore.save(token)
        return when (val me = users.refresh()) {
            is ApiResult.Success -> SignInResult.Success(me.value)
            is ApiResult.Failure -> {
                tokenStore.clear()
                SignInResult.Failed(me.error.message)
            }
        }
    }
    // endregion

    /** `POST /api/auth/sign-out`, best effort. Local cleanup is done by [SessionManager]. */
    suspend fun signOutRemote() {
        authCall { authApi.signOut() }
    }

    /**
     * Runs a sign-in call, stores the `set-auth-token` header, then loads `/me`
     * (the sign-in payload is Better Auth's user, not the full API [User]).
     */
    private suspend fun completeSignIn(block: suspend () -> Response<SignInResponse>): SignInResult {
        val response = when (val result = authCall(block)) {
            is ApiResult.Success -> result.value
            is ApiResult.Failure -> return SignInResult.Failed(result.error.message)
        }
        val token = response.headers()[BetterAuthApi.AUTH_TOKEN_HEADER]
            ?: response.body()?.token
            ?: return SignInResult.Failed("The server didn't return a session.")
        tokenStore.save(token)
        return when (val me = users.refresh()) {
            is ApiResult.Success -> SignInResult.Success(me.value)
            is ApiResult.Failure -> response.body()?.user?.let { fallback ->
                users.store(fallback)
                SignInResult.Success(fallback)
            } ?: run {
                tokenStore.clear()
                SignInResult.Failed(me.error.message)
            }
        }
    }

    /** Like [apiCall] but for raw [Response]s: non-2xx responses become [ApiResult.Failure]. */
    private suspend fun <T> authCall(block: suspend () -> Response<T>): ApiResult<Response<T>> =
        when (val result = apiCall(json, block)) {
            is ApiResult.Failure -> result
            is ApiResult.Success ->
                if (result.value.isSuccessful) result else ApiResult.Failure(result.value.toApiError(json))
        }

    companion object {
        const val APPLE_CALLBACK = "availabliss://auth/callback"
    }
}
