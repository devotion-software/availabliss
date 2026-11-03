package dev.joeherbert.availabliss.core.auth

import android.content.Context
import dev.joeherbert.availabliss.BuildConfig
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
import dev.joeherbert.availabliss.core.network.model.NativeSignInCodeRequest
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
    //
    // Each flow is split in two: the system sheet (needs an Activity, so callers run it in a
    // UI-scoped coroutine) and the network step that completes it (safe in viewModelScope).

    /** Shows the Google sheet anchored to [activity] (not retained). */
    suspend fun googleCredential(activity: Context): CredentialOutcome<String> = credentials.googleIdToken(activity)

    suspend fun completeGoogleSignIn(outcome: CredentialOutcome<String>): SignInResult = when (outcome) {
        is CredentialOutcome.Success -> completeSignIn {
            authApi.signInWithIdToken(SocialIdTokenSignInRequest("google", SocialIdToken(outcome.value)))
        }
        CredentialOutcome.Cancelled -> SignInResult.Cancelled
        is CredentialOutcome.Failed -> SignInResult.Failed(outcome.message)
    }
    // endregion

    // region Passkeys
    /**
     * Fetches the server's challenge and shows the passkey sheet anchored to [activity]
     * (not retained). The challenge cookie is kept for [completePasskeySignIn].
     */
    suspend fun passkeyAssertion(activity: Context): CredentialOutcome<String> {
        cookieJar.clear()
        val options = when (val result = authCall { authApi.passkeyAuthenticateOptions() }) {
            is ApiResult.Success -> result.value.body()
            is ApiResult.Failure -> return CredentialOutcome.Failed(result.error.message)
        } ?: return CredentialOutcome.Failed("Couldn't start passkey sign in.")
        return keepCookiesOnSuccess { credentials.passkeyAssertion(activity, options.toString()) }
    }

    suspend fun completePasskeySignIn(outcome: CredentialOutcome<String>): SignInResult = try {
        when (outcome) {
            is CredentialOutcome.Success -> completeSignIn {
                authApi.passkeyVerifyAuthentication(PasskeyVerifyRequest(json.parseToJsonElement(outcome.value).jsonObject))
            }
            CredentialOutcome.Cancelled -> SignInResult.Cancelled
            is CredentialOutcome.Failed -> SignInResult.Failed(outcome.message)
        }
    } finally {
        cookieJar.clear()
    }

    /** Fetches registration options and shows the create-passkey sheet anchored to [activity] (not retained). */
    suspend fun passkeyRegistration(activity: Context): CredentialOutcome<String> {
        cookieJar.clear()
        val options = when (val result = authCall { authApi.passkeyRegisterOptions() }) {
            is ApiResult.Success -> result.value.body()
            is ApiResult.Failure -> return CredentialOutcome.Failed(result.error.message)
        } ?: return CredentialOutcome.Failed("Couldn't start creating a passkey.")
        return keepCookiesOnSuccess { credentials.passkeyRegistration(activity, options.toString()) }
    }

    /** Adds the passkey to the signed-in account. Returns null on success, else a message (or "" if cancelled). */
    suspend fun completePasskeyRegistration(outcome: CredentialOutcome<String>, name: String?): String? = try {
        when (outcome) {
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

    /** Clears the challenge cookie unless the sheet produced a credential to verify. */
    private suspend fun keepCookiesOnSuccess(block: suspend () -> CredentialOutcome<String>): CredentialOutcome<String> {
        var outcome: CredentialOutcome<String>? = null
        try {
            return block().also { outcome = it }
        } finally {
            if (outcome !is CredentialOutcome.Success) cookieJar.clear()
        }
    }
    // endregion

    // region Apple (web flow in a Custom Tab)
    /**
     * Sign in with Apple has no Android SDK, so it runs as a web flow in a Custom Tab.
     * The server starts it and finishes by redirecting to
     * `availabliss://auth/callback?code=<one-time code>`; see [completeWebSignIn].
     *
     * PKCE: a random verifier stays on the device and only its SHA-256 goes in the URL,
     * so another app that catches the `availabliss://` redirect can't redeem the code.
     */
    fun appleSignInUrl(): String {
        val verifier = Pkce.newVerifier()
        tokenStore.saveSignInVerifier(verifier)
        return "${BuildConfig.API_BASE_URL}/api/v1/auth/native/apple/start?code_challenge=${Pkce.challenge(verifier)}"
    }

    /** Completes a web sign-in from the `availabliss://auth/callback` deep link. */
    suspend fun completeWebSignIn(code: String?, error: String?): SignInResult {
        val verifier = tokenStore.takeSignInVerifier()
        if (code.isNullOrBlank()) return SignInResult.Failed(error ?: "Sign in didn't finish. Please try again.")
        if (verifier == null) return SignInResult.Failed("Sign in didn't finish. Please try again.")
        val token = when (val result = authCall { authApi.exchangeNativeSignInCode(NativeSignInCodeRequest(code, verifier)) }) {
            is ApiResult.Success -> result.value.body()?.token
            is ApiResult.Failure -> return SignInResult.Failed(result.error.message)
        } ?: return SignInResult.Failed("Sign in didn't finish. Please try again.")
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
}
