package dev.joeherbert.availabliss.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.joeherbert.availabliss.core.network.model.ColorTheme
import dev.joeherbert.availabliss.core.network.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "availabliss")

/** Locally cached appearance, applied before the account loads. */
data class Appearance(
    val mode: ThemeMode = ThemeMode.System,
    val theme: ColorTheme = ColorTheme.Dawn,
)

/**
 * Non-secret local state in DataStore. Secrets (the auth token) live in
 * [dev.joeherbert.availabliss.core.auth.TokenStore].
 */
@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val store get() = context.dataStore

    val appearance: Flow<Appearance> = store.data.map { prefs ->
        Appearance(
            mode = prefs[KEY_THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.System,
            theme = prefs[KEY_COLOR_THEME]?.let { runCatching { ColorTheme.valueOf(it) }.getOrNull() } ?: ColorTheme.Dawn,
        )
    }

    suspend fun setAppearance(appearance: Appearance) {
        store.edit {
            it[KEY_THEME_MODE] = appearance.mode.name
            it[KEY_COLOR_THEME] = appearance.theme.name
        }
    }

    val cachedUserJson: Flow<String?> = store.data.map { it[KEY_USER] }

    suspend fun setCachedUserJson(json: String?) {
        store.edit { if (json == null) it.remove(KEY_USER) else it[KEY_USER] = json }
    }

    /** Stable per-install id for `PUT /calendars/device`. */
    suspend fun deviceId(): String {
        store.data.first()[KEY_DEVICE_ID]?.let { return it }
        val id = UUID.randomUUID().toString()
        store.edit { prefs -> if (prefs[KEY_DEVICE_ID] == null) prefs[KEY_DEVICE_ID] = id }
        return store.data.first()[KEY_DEVICE_ID] ?: id
    }

    /**
     * Device calendar ids the user marked as "busy". `null` = never chosen, in which
     * case [dev.joeherbert.availabliss.core.calendar.DeviceCalendarReader] applies defaults.
     */
    val busyDeviceCalendarIds: Flow<Set<String>?> = store.data.map { it[KEY_BUSY_CALENDARS] }

    suspend fun setBusyDeviceCalendarIds(ids: Set<String>) {
        store.edit { it[KEY_BUSY_CALENDARS] = ids }
    }

    val lastDeviceSyncAt: Flow<Long?> = store.data.map { it[KEY_LAST_DEVICE_SYNC] }

    /**
     * What each range of the last device calendar upload sent (JSON, owned by
     * [dev.joeherbert.availabliss.core.calendar.DeviceCalendarSync]), to skip re-sending unchanged ones.
     */
    val deviceSyncRanges: Flow<String?> = store.data.map { it[KEY_DEVICE_SYNC_RANGES] }

    /** How far ahead the server last asked this phone to read (epoch ms). */
    val deviceSyncHorizon: Flow<Long?> = store.data.map { it[KEY_DEVICE_SYNC_HORIZON] }

    suspend fun setDeviceSyncRanges(ranges: String) {
        store.edit {
            it[KEY_DEVICE_SYNC_RANGES] = ranges
            it.remove(KEY_LAST_DEVICE_SYNC_HASH)
        }
    }

    suspend fun setLastDeviceSyncAt(epochMillis: Long) {
        store.edit { it[KEY_LAST_DEVICE_SYNC] = epochMillis }
    }

    suspend fun setDeviceSyncHorizon(epochMillis: Long) {
        store.edit { it[KEY_DEVICE_SYNC_HORIZON] = epochMillis }
    }

    suspend fun clearLastDeviceSyncAt() {
        store.edit {
            it.remove(KEY_LAST_DEVICE_SYNC)
            it.remove(KEY_LAST_DEVICE_SYNC_HASH)
            it.remove(KEY_DEVICE_SYNC_RANGES)
        }
    }

    suspend fun pushToken(): String? = store.data.first()[KEY_PUSH_TOKEN]

    suspend fun setPushToken(token: String?) {
        store.edit { if (token == null) it.remove(KEY_PUSH_TOKEN) else it[KEY_PUSH_TOKEN] = token }
    }

    /** Clears account-scoped data on sign out. Device id and appearance are kept. */
    suspend fun clearSession() {
        store.edit {
            it.remove(KEY_USER)
            it.remove(KEY_BUSY_CALENDARS)
            it.remove(KEY_LAST_DEVICE_SYNC)
            it.remove(KEY_LAST_DEVICE_SYNC_HASH)
            it.remove(KEY_DEVICE_SYNC_RANGES)
            it.remove(KEY_DEVICE_SYNC_HORIZON)
            it.remove(KEY_PUSH_TOKEN)
        }
    }

    private companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_COLOR_THEME = stringPreferencesKey("color_theme")
        val KEY_USER = stringPreferencesKey("cached_user")
        val KEY_DEVICE_ID = stringPreferencesKey("device_id")
        val KEY_BUSY_CALENDARS = stringSetPreferencesKey("busy_device_calendars")
        val KEY_LAST_DEVICE_SYNC = longPreferencesKey("last_device_sync")
        /** Before uploads were split into ranges; removed when next written. */
        val KEY_LAST_DEVICE_SYNC_HASH = stringPreferencesKey("last_device_sync_hash")
        val KEY_DEVICE_SYNC_RANGES = stringPreferencesKey("device_sync_ranges")
        val KEY_DEVICE_SYNC_HORIZON = longPreferencesKey("device_sync_horizon")
        val KEY_PUSH_TOKEN = stringPreferencesKey("push_token")
    }
}
