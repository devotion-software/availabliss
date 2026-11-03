package dev.joeherbert.availabliss.core.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.GeneralSecurityException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores the Better Auth bearer token in [EncryptedSharedPreferences] (AES-256, key in
 * the Android Keystore). The token is cached in memory so the OkHttp interceptor can
 * read it synchronously.
 */
@Singleton
class TokenStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs: SharedPreferences by lazy { openPrefs() }
    private val _token = MutableStateFlow<String?>(null)
    @Volatile private var loaded = false

    /** The current token, observable. */
    val tokenFlow: StateFlow<String?>
        get() {
            ensureLoaded()
            return _token.asStateFlow()
        }

    fun token(): String? {
        ensureLoaded()
        return _token.value
    }

    fun save(token: String) {
        prefs.edit { putString(KEY_TOKEN, token) }
        _token.value = token
    }

    fun clear() {
        prefs.edit { remove(KEY_TOKEN) }
        _token.value = null
    }

    /**
     * The PKCE verifier for a web-flow sign-in in progress. Kept on disk so the
     * sign-in still completes if the process dies while the Custom Tab is open.
     */
    fun saveSignInVerifier(verifier: String) = prefs.edit { putString(KEY_SIGN_IN_VERIFIER, verifier) }

    /** Returns and forgets the pending verifier (single use). */
    fun takeSignInVerifier(): String? =
        prefs.getString(KEY_SIGN_IN_VERIFIER, null).also { prefs.edit { remove(KEY_SIGN_IN_VERIFIER) } }

    private fun ensureLoaded() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            _token.value = prefs.getString(KEY_TOKEN, null)
            loaded = true
        }
    }

    private fun openPrefs(): SharedPreferences = try {
        createEncrypted()
    } catch (e: GeneralSecurityException) {
        recover(e)
    } catch (e: IOException) {
        recover(e)
    }

    /** A corrupted keyset (e.g. after a restore) can't be decrypted: start fresh. */
    private fun recover(error: Exception): SharedPreferences {
        Log.w(TAG, "Encrypted prefs unreadable, resetting", error)
        context.deleteSharedPreferences(FILE_NAME)
        return createEncrypted()
    }

    private fun createEncrypted(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private companion object {
        const val TAG = "TokenStore"
        const val FILE_NAME = "availabliss_secure"
        const val KEY_TOKEN = "auth_token"
        const val KEY_SIGN_IN_VERIFIER = "sign_in_verifier"
    }
}
