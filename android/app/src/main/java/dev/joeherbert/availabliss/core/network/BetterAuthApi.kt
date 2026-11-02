package dev.joeherbert.availabliss.core.network

import dev.joeherbert.availabliss.core.network.model.EmailOtpSignInRequest
import dev.joeherbert.availabliss.core.network.model.PasskeyRegisterRequest
import dev.joeherbert.availabliss.core.network.model.PasskeyVerifyRequest
import dev.joeherbert.availabliss.core.network.model.SendOtpRequest
import dev.joeherbert.availabliss.core.network.model.SignInResponse
import dev.joeherbert.availabliss.core.network.model.SocialIdTokenSignInRequest
import dev.joeherbert.availabliss.core.network.model.NativeSignInCodeRequest
import dev.joeherbert.availabliss.core.network.model.NativeSignInTokenResponse
import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * Better Auth endpoints under `/api/auth`. Sign-in calls return [Response] so the
 * `set-auth-token` header can be read (see [AUTH_TOKEN_HEADER]).
 */
interface BetterAuthApi {

    @POST("api/auth/email-otp/send-verification-otp")
    suspend fun sendOtp(@Body body: SendOtpRequest): Response<JsonObject>

    @POST("api/auth/sign-in/email-otp")
    suspend fun signInWithOtp(@Body body: EmailOtpSignInRequest): Response<SignInResponse>

    @POST("api/auth/sign-in/social")
    suspend fun signInWithIdToken(@Body body: SocialIdTokenSignInRequest): Response<SignInResponse>

    /** Completes a web-flow sign-in started at `/api/v1/auth/native/{provider}/start`. */
    @POST("api/v1/auth/native/exchange")
    suspend fun exchangeNativeSignInCode(@Body body: NativeSignInCodeRequest): Response<NativeSignInTokenResponse>

    /** WebAuthn `PublicKeyCredentialRequestOptionsJSON`, passed straight to Credential Manager. */
    @GET("api/auth/passkey/generate-authenticate-options")
    suspend fun passkeyAuthenticateOptions(): Response<JsonObject>

    @POST("api/auth/passkey/verify-authentication")
    suspend fun passkeyVerifyAuthentication(@Body body: PasskeyVerifyRequest): Response<SignInResponse>

    /** WebAuthn `PublicKeyCredentialCreationOptionsJSON` (signed in). */
    @GET("api/auth/passkey/generate-register-options")
    suspend fun passkeyRegisterOptions(): Response<JsonObject>

    @POST("api/auth/passkey/verify-registration")
    suspend fun passkeyVerifyRegistration(@Body body: PasskeyRegisterRequest): Response<JsonObject>

    @POST("api/auth/sign-out")
    suspend fun signOut(): Response<JsonObject>

    companion object {
        const val AUTH_TOKEN_HEADER = "set-auth-token"
        const val AUTH_PATH_PREFIX = "/api/auth/"
    }
}
