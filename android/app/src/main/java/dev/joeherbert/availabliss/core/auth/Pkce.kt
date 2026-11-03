package dev.joeherbert.availabliss.core.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** RFC 7636 (S256) helpers for the web-flow sign-in hand-off. */
object Pkce {
    private val random = SecureRandom()
    private val encoder = Base64.getUrlEncoder().withoutPadding()

    /** 32 random bytes, base64url: 43 characters. */
    fun newVerifier(): String = ByteArray(32).also(random::nextBytes).let(encoder::encodeToString)

    /** base64url(SHA-256(verifier)). */
    fun challenge(verifier: String): String =
        encoder.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
}
