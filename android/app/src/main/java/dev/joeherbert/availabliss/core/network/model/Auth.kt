package dev.joeherbert.availabliss.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/* Better Auth request / response bodies (the `/api/auth` endpoints). */

@Serializable
data class SendOtpRequest(val email: String, val type: String) {
    companion object {
        fun signIn(email: String) = SendOtpRequest(email, "sign-in")
    }
}

@Serializable
data class EmailOtpSignInRequest(val email: String, val otp: String)

@Serializable
data class SocialIdToken(val token: String, val nonce: String? = null)

/** Native ID-token sign in (Google). */
@Serializable
data class SocialIdTokenSignInRequest(val provider: String, val idToken: SocialIdToken)

/** Swaps the one-time code from a web-flow sign-in (Apple on Android) for a bearer token. */
@Serializable
data class NativeSignInCodeRequest(
    val code: String,
    @SerialName("code_verifier") val codeVerifier: String,
)

@Serializable
data class NativeSignInTokenResponse(val token: String)

/** All sign-in calls return `{ user, ... }` plus the `set-auth-token` header. */
@Serializable
data class SignInResponse(val user: User? = null, val token: String? = null)

/** `{ response: AuthenticationResponseJSON }`; the WebAuthn JSON is passed through verbatim. */
@Serializable
data class PasskeyVerifyRequest(val response: JsonObject)

@Serializable
data class PasskeyRegisterRequest(val response: JsonObject, val name: String? = null)
