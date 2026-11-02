package dev.joeherbert.availabliss.core.network.model

import kotlinx.serialization.Serializable

/*
 * Linking Apple / Google to your account (API.md "Linking Apple / Google").
 * Providers stay plain strings ("apple", "google") so a provider added on the
 * server later doesn't break decoding; see [LinkProviders].
 */

object LinkProviders {
    const val APPLE = "apple"
    const val GOOGLE = "google"

    fun label(provider: String): String = when (provider) {
        APPLE -> "Apple"
        GOOGLE -> "Google"
        else -> provider.replaceFirstChar { it.uppercase() }
    }
}

@Serializable
data class LinkedAccount(val provider: String, val linkedAt: String)

/** `GET /me/accounts`. [available] = providers this server can link. */
@Serializable
data class LinkedAccounts(
    val linked: List<LinkedAccount> = emptyList(),
    val available: List<String> = emptyList(),
) {
    fun isLinked(provider: String): Boolean = linked.any { it.provider == provider }
}

/** `POST /me/accounts/link`. Null [nonce] / [merge] are omitted (encodeDefaults = false). */
@Serializable
data class LinkAccountRequest(
    val provider: String,
    val idToken: String,
    val nonce: String? = null,
    val merge: Boolean? = null,
)

@Serializable
data class LinkAccountResponse(val linked: Boolean = true, val merged: Boolean = false)

/** The account a sign-in already belongs to, shown before merging it into yours. */
@Serializable
data class MergeCandidate(val name: String, val email: String, val groups: Int = 0)

/** 409 body of `POST /me/accounts/link`: the normal error body plus [account]. */
@Serializable
data class LinkConflictBody(
    val message: String? = null,
    val code: String? = null,
    val account: MergeCandidate? = null,
)
