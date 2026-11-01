package dev.joeherbert.availabliss.core.network.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/* Better Auth request / response bodies (`/api/auth/*`). */

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

/** Web-redirect sign in (Apple on Android); the response carries the provider `url`. */
@Serializable
data class SocialRedirectSignInRequest(
    val provider: String,
    val callbackURL: String,
    /** Must be sent explicitly (properties equal to their default are omitted). */
    val disableRedirect: Boolean,
)

@Serializable
data class SocialRedirectResponse(val url: String? = null, val redirect: Boolean = false)

/** All sign-in calls return `{ user, ... }` plus the `set-auth-token` header. */
@Serializable
data class SignInResponse(val user: User? = null, val token: String? = null)

/** `{ response: AuthenticationResponseJSON }`; the WebAuthn JSON is passed through verbatim. */
@Serializable
data class PasskeyVerifyRequest(val response: JsonObject)

@Serializable
data class PasskeyRegisterRequest(val response: JsonObject, val name: String? = null)
