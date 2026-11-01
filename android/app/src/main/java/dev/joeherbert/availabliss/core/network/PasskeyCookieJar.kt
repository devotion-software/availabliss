package dev.joeherbert.availabliss.core.network

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Better Auth's passkey plugin keeps the WebAuthn challenge in a short-lived signed
 * cookie set by `generate-*-options` and read by `verify-*`. Native clients are
 * otherwise cookie-less (bearer auth), so this jar only remembers cookies for the
 * passkey endpoints, in memory, and nothing else.
 */
@Singleton
class PasskeyCookieJar @Inject constructor() : CookieJar {
    private val cookies = mutableMapOf<String, Cookie>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (!url.isPasskey()) return
        cookies.forEach { this.cookies[it.name] = it }
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        if (!url.isPasskey()) return emptyList()
        val now = System.currentTimeMillis()
        cookies.values.removeAll { it.expiresAt < now }
        return cookies.values.filter { it.matches(url) }
    }

    @Synchronized
    fun clear() = cookies.clear()

    private fun HttpUrl.isPasskey() = encodedPath.startsWith("/api/auth/passkey/")
}
