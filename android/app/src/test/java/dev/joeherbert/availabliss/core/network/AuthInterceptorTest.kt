package dev.joeherbert.availabliss.core.network

import dev.joeherbert.availabliss.core.network.AuthInterceptor.Companion.isSessionExpiry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthInterceptorTest {
    @Test
    fun `401 for the current token ends the session`() {
        assertTrue(isSessionExpiry(401, "/api/v1/groups", sentToken = "a", currentToken = "a"))
        assertTrue(isSessionExpiry(401, "/api/v1/live", sentToken = "a", currentToken = "a"))
    }

    @Test
    fun `other status codes are not session expiry`() {
        assertFalse(isSessionExpiry(200, "/api/v1/groups", "a", "a"))
        assertFalse(isSessionExpiry(403, "/api/v1/groups", "a", "a"))
    }

    @Test
    fun `a late 401 from a previous session is ignored`() {
        // Slow planner request sent with the old token returns after re-sign-in.
        assertFalse(isSessionExpiry(401, "/api/v1/groups/g/planner", sentToken = "old", currentToken = "new"))
    }

    @Test
    fun `a 401 after the session was cleared is ignored`() {
        assertFalse(isSessionExpiry(401, "/api/v1/devices/t", sentToken = "a", currentToken = null))
    }

    @Test
    fun `unauthenticated requests are ignored`() {
        assertFalse(isSessionExpiry(401, "/api/v1/groups", sentToken = null, currentToken = null))
    }

    @Test
    fun `sign-in and account-link routes never end the session`() {
        assertFalse(isSessionExpiry(401, "/api/auth/sign-in/email-otp", "a", "a"))
        assertFalse(isSessionExpiry(401, "/api/auth/passkey/verify-registration", "a", "a"))
        assertFalse(isSessionExpiry(401, "/api/v1/auth/native/exchange", "a", "a"))
        assertFalse(isSessionExpiry(401, "/api/v1/me/accounts/link", "a", "a"))
    }

    @Test
    fun `other me routes still end the session`() {
        assertTrue(isSessionExpiry(401, "/api/v1/me", "a", "a"))
        assertTrue(isSessionExpiry(401, "/api/v1/me/accounts", "a", "a"))
    }
}
