package dev.joeherbert.availabliss.core.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PkceTest {
    @Test
    fun `challenge is base64url SHA-256 matching the server`() {
        // Same vector as web/src/lib/server/native-auth.test.ts (checked with openssl).
        assertEquals("V3SuLHIK7yk4RyrjZ3sQ8mzmOovJLsWYWwmh1P1rX4M", Pkce.challenge("dBjftJeZ4CVP-mJ92K9gYqF5yI1gG8ZoMAvHOn0vR3U"))
    }

    @Test
    fun `verifiers are random 43-character base64url strings`() {
        val a = Pkce.newVerifier()
        val b = Pkce.newVerifier()
        assertNotEquals(a, b)
        assertTrue(Regex("^[A-Za-z0-9_-]{43}$").matches(a))
        assertTrue(Regex("^[A-Za-z0-9_-]{43}$").matches(Pkce.challenge(a)))
    }
}
