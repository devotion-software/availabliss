package dev.joeherbert.availabliss.core.push

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.joeherbert.availabliss.BuildConfig
import dev.joeherbert.availabliss.core.auth.TokenStore
import dev.joeherbert.availabliss.core.data.ApiCaller
import dev.joeherbert.availabliss.core.data.AppPreferences
import dev.joeherbert.availabliss.core.network.AvailablissApi
import dev.joeherbert.availabliss.core.network.ApiResult
import dev.joeherbert.availabliss.core.network.model.RegisterDeviceRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registers this install's FCM token with `POST /devices` and removes it on sign out.
 * Does nothing when the build has no `google-services.json` (Firebase not initialised).
 */
@Singleton
class PushRegistrar @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: AvailablissApi,
    private val call: ApiCaller,
    private val prefs: AppPreferences,
    private val tokenStore: TokenStore,
) {
    private val isAvailable: Boolean
        get() = BuildConfig.FCM_ENABLED && FirebaseApp.getApps(context).isNotEmpty()

    /** Fetches the current FCM token and registers it (idempotent server side). */
    suspend fun register() {
        if (!isAvailable || tokenStore.token() == null) return
        val token = try {
            FirebaseMessaging.getInstance().token.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't get FCM token", e)
            return
        }
        onNewToken(token)
    }

    /** Called by the messaging service when FCM rotates the token. */
    suspend fun onNewToken(token: String) {
        if (tokenStore.token() == null) {
            prefs.setPushToken(null)
            return
        }
        val result = call { api.registerDevice(RegisterDeviceRequest(platform = "android", token = token, name = deviceName(context))) }
        if (result is ApiResult.Success) prefs.setPushToken(token)
    }

    /** Best-effort: tells the server to stop pushing to this device. Call before clearing the session. */
    suspend fun unregister() {
        val token = prefs.pushToken() ?: return
        call { api.unregisterDevice(token) }
        if (isAvailable) runCatching { FirebaseMessaging.getInstance().deleteToken().await() }
        prefs.setPushToken(null)
    }

    private companion object {
        const val TAG = "PushRegistrar"
    }
}

/** "Sam's Pixel 8" where the user named the device, else the model name. */
fun deviceName(context: Context): String =
    Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)?.takeIf { it.isNotBlank() }
        ?: listOf(Build.MANUFACTURER.replaceFirstChar { it.uppercase() }, Build.MODEL)
            .distinct().joinToString(" ").take(80)
