package dev.joeherbert.availabliss.core.data

import android.content.Context
import dev.joeherbert.availabliss.core.auth.CredentialAuthenticator
import dev.joeherbert.availabliss.core.auth.CredentialOutcome
import dev.joeherbert.availabliss.core.network.ApiResult
import dev.joeherbert.availabliss.core.network.AvailablissApi
import dev.joeherbert.availabliss.core.network.model.LinkAccountRequest
import dev.joeherbert.availabliss.core.network.model.LinkConflictBody
import dev.joeherbert.availabliss.core.network.model.LinkProviders
import dev.joeherbert.availabliss.core.network.model.LinkedAccounts
import dev.joeherbert.availabliss.core.network.model.MergeCandidate
import dev.joeherbert.availabliss.core.network.parseHttpError
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** Outcome of `POST /me/accounts/link`. */
sealed interface LinkResult {
    data class Linked(val merged: Boolean) : LinkResult

    /** The sign-in has its own account; ask, then resend [request] with `merge: true`. */
    data class NeedsMerge(val account: MergeCandidate, val request: LinkAccountRequest) : LinkResult

    /** The user dismissed the system sheet; show nothing. */
    data object Cancelled : LinkResult
    data class Failed(val message: String) : LinkResult
}

/**
 * Apple / Google sign-ins linked to your account (`/me/accounts`). On Android only
 * Google can be linked: its id token comes from Credential Manager, as for sign-in.
 * Sign in with Apple runs as a web flow here, which never hands the app an id token.
 */
@Singleton
class AccountsRepository @Inject constructor(
    private val api: AvailablissApi,
    private val call: ApiCaller,
    private val credentials: CredentialAuthenticator,
    private val json: Json,
) {
    /** Providers this app can get an id token for. */
    fun canLink(provider: String): Boolean = provider == LinkProviders.GOOGLE && credentials.isGoogleConfigured

    suspend fun list(): ApiResult<LinkedAccounts> = call { api.getLinkedAccounts() }

    /** Shows the Google sheet anchored to [activity] (not retained; run it in a UI-scoped coroutine). */
    suspend fun googleCredential(activity: Context): CredentialOutcome<String> = credentials.googleIdToken(activity)

    /** Links the Google sign-in from [googleCredential]. */
    suspend fun linkGoogle(outcome: CredentialOutcome<String>): LinkResult = when (outcome) {
        is CredentialOutcome.Success -> link(LinkAccountRequest(LinkProviders.GOOGLE, outcome.value))
        CredentialOutcome.Cancelled -> LinkResult.Cancelled
        is CredentialOutcome.Failed -> LinkResult.Failed(outcome.message)
    }

    /** Sends [request] as is (after a [LinkResult.NeedsMerge], pass it back with `merge = true`). */
    suspend fun link(request: LinkAccountRequest): LinkResult =
        when (val result = call { api.linkAccount(request) }) {
            is ApiResult.Failure -> LinkResult.Failed(result.error.message)
            is ApiResult.Success -> {
                val response = result.value
                if (response.isSuccessful) {
                    LinkResult.Linked(merged = response.body()?.merged == true)
                } else {
                    linkFailure(json, response.code(), response.errorBody()?.string(), request)
                }
            }
        }
}

/** Maps a non-2xx link response: a 409 naming another account asks to merge; the rest are errors. */
internal fun linkFailure(json: Json, code: Int, body: String?, request: LinkAccountRequest): LinkResult {
    if (code == 409 && request.merge != true && !body.isNullOrBlank()) {
        val account = runCatching { json.decodeFromString(LinkConflictBody.serializer(), body) }.getOrNull()?.account
        if (account != null) return LinkResult.NeedsMerge(account, request)
    }
    return LinkResult.Failed(parseHttpError(json, code, body).message)
}
