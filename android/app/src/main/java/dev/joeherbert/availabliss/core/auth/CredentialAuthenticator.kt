package dev.joeherbert.availabliss.core.auth

import android.content.Context
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import dev.joeherbert.availabliss.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

/** Result of a Credential Manager prompt. */
sealed interface CredentialOutcome<out T> {
    data class Success<T>(val value: T) : CredentialOutcome<T>
    /** The user dismissed the sheet; not an error worth showing. */
    data object Cancelled : CredentialOutcome<Nothing>
    data class Failed(val message: String) : CredentialOutcome<Nothing>
}

/**
 * Thin wrapper over [CredentialManager] for Google ID tokens and passkeys.
 * Every call needs an Activity [Context] to anchor the system sheet; it is not retained.
 */
@Singleton
class CredentialAuthenticator @Inject constructor() {

    val isGoogleConfigured: Boolean get() = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()

    /** "Sign in with Google" button flow; returns the Google ID token. */
    suspend fun googleIdToken(activity: Context): CredentialOutcome<String> {
        if (!isGoogleConfigured) return CredentialOutcome.Failed("Google sign-in isn't configured for this build.")
        val option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        return try {
            val credential = CredentialManager.create(activity).getCredential(activity, request).credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                CredentialOutcome.Success(GoogleIdTokenCredential.createFrom(credential.data).idToken)
            } else {
                CredentialOutcome.Failed("Unexpected credential from Google.")
            }
        } catch (e: GetCredentialCancellationException) {
            CredentialOutcome.Cancelled
        } catch (e: GoogleIdTokenParsingException) {
            CredentialOutcome.Failed("Google returned an invalid sign-in token.")
        } catch (e: GetCredentialException) {
            CredentialOutcome.Failed(e.errorMessage?.toString() ?: "Couldn't sign in with Google.")
        }
    }

    /**
     * Passkey assertion. [requestJson] is the server's `PublicKeyCredentialRequestOptionsJSON`;
     * returns the `AuthenticationResponseJSON` string.
     */
    suspend fun passkeyAssertion(activity: Context, requestJson: String): CredentialOutcome<String> {
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetPublicKeyCredentialOption(requestJson = requestJson))
            .build()
        return try {
            val credential = CredentialManager.create(activity).getCredential(activity, request).credential
            if (credential is PublicKeyCredential) {
                CredentialOutcome.Success(credential.authenticationResponseJson)
            } else {
                CredentialOutcome.Failed("Unexpected credential type.")
            }
        } catch (e: GetCredentialCancellationException) {
            CredentialOutcome.Cancelled
        } catch (e: NoCredentialException) {
            CredentialOutcome.Failed("No passkey for Availabliss on this device yet. Sign in another way, then add one from You.")
        } catch (e: GetCredentialException) {
            CredentialOutcome.Failed(e.errorMessage?.toString() ?: "Couldn't use your passkey.")
        }
    }

    /**
     * Passkey registration. [requestJson] is the server's `PublicKeyCredentialCreationOptionsJSON`;
     * returns the `RegistrationResponseJSON` string.
     */
    suspend fun passkeyRegistration(activity: Context, requestJson: String): CredentialOutcome<String> = try {
        val response = CredentialManager.create(activity)
            .createCredential(activity, CreatePublicKeyCredentialRequest(requestJson = requestJson))
        if (response is CreatePublicKeyCredentialResponse) {
            CredentialOutcome.Success(response.registrationResponseJson)
        } else {
            CredentialOutcome.Failed("Unexpected response while creating the passkey.")
        }
    } catch (e: CreateCredentialCancellationException) {
        CredentialOutcome.Cancelled
    } catch (e: CreateCredentialException) {
        CredentialOutcome.Failed(e.errorMessage?.toString() ?: "Couldn't create a passkey.")
    }
}
