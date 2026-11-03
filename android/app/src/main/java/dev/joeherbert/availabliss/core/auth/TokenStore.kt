package dev.joeherbert.availabliss.core.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.joeherbert.availabliss.core.common.ApplicationScope
import dev.joeherbert.availabliss.core.common.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.IOException
import java.security.GeneralSecurityException
import javax.inject.Inject
import javax.inject.Singleton

/** Whether the stored token has been read yet. */
sealed interface TokenState {
    /** Still opening the Keystore-backed prefs (off the main thread). */
    data object Loading : TokenState
    data class Ready(val token: String?) : TokenState
}

/**
 * Stores the Better Auth bearer token in [EncryptedSharedPreferences] (AES-256, key in
 * the Android Keystore). The token is cached in memory so the OkHttp interceptor can
 * read it synchronously.
 *
 * Opening the prefs (MasterKey generation, keyset decryption) is slow on some devices,
 * so it starts on an IO thread as soon as this is created; [state] is [TokenState.Loading]
 * until then. If the prefs can't be opened even after a reset, the token lives in memory
 * for this process only, rather than crashing the app at launch.
 */
@Singleton
class TokenStore @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope scope: CoroutineScope,
    @IoDispatcher io: CoroutineDispatcher,
) {
    private var prefs: SharedPreferences? = null
    private val _state = MutableStateFlow<TokenState>(TokenState.Loading)
    @Volatile private var loaded = false

    /** In-memory stand-in for the verifier when the encrypted prefs are unusable. */
    @Volatile private var memoryVerifier: String? = null

    val state: StateFlow<TokenState> = _state.asStateFlow()

    /** The current token, observable; emits nothing until it has been read. */
    val tokenFlow: Flow<String?> = _state.filterIsInstance<TokenState.Ready>().map { it.token }

    init {
        scope.launch(io) { ensureLoaded() }
    }

    /** The current token. Blocks until it has been read: don't call it on the main thread at startup. */
    fun token(): String? {
        ensureLoaded()
        return currentToken()
    }

    /** The token if it has been read yet, else null. Never blocks. */
    fun currentToken(): String? = (_state.value as? TokenState.Ready)?.token

    fun save(token: String) {
        ensureLoaded()
        prefs?.edit { putString(KEY_TOKEN, token) }
        _state.value = TokenState.Ready(token)
    }

    fun clear() {
        ensureLoaded()
        prefs?.edit { remove(KEY_TOKEN) }
        _state.value = TokenState.Ready(null)
    }

    /**
     * The PKCE verifier for a web-flow sign-in in progress. Kept on disk so the
     * sign-in still completes if the process dies while the Custom Tab is open.
     */
    fun saveSignInVerifier(verifier: String) {
        ensureLoaded()
        val p = prefs
        if (p == null) memoryVerifier = verifier else p.edit { putString(KEY_SIGN_IN_VERIFIER, verifier) }
    }

    /** Returns and forgets the pending verifier (single use). */
    fun takeSignInVerifier(): String? {
        ensureLoaded()
        val p = prefs ?: return memoryVerifier.also { memoryVerifier = null }
        return p.getString(KEY_SIGN_IN_VERIFIER, null).also { p.edit { remove(KEY_SIGN_IN_VERIFIER) } }
    }

    private fun ensureLoaded() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val p = openPrefs()
            prefs = p
            _state.value = TokenState.Ready(p?.getString(KEY_TOKEN, null))
            loaded = true
        }
    }

    private fun openPrefs(): SharedPreferences? = try {
        createEncrypted()
    } catch (e: GeneralSecurityException) {
        recover(e)
    } catch (e: IOException) {
        recover(e)
    }

    /** A corrupted keyset (e.g. after a restore) can't be decrypted: start fresh. */
    private fun recover(error: Exception): SharedPreferences? {
        Log.w(TAG, "Encrypted prefs unreadable, resetting", error)
        context.deleteSharedPreferences(FILE_NAME)
        return try {
            createEncrypted()
        } catch (e: Exception) {
            Log.e(TAG, "Encrypted prefs still unusable after a reset; the session won't be saved", e)
            null
        }
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
