package dev.joeherbert.availabliss.core.widget

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.widgetStore: DataStore<Preferences> by preferencesDataStore(name = "widgets")

/**
 * The [WidgetSnapshot] the home-screen widgets draw from, as JSON in its own DataStore file.
 * Plain (not injected) so a widget can open it from just a [Context]; the DataStore itself is
 * one per process either way.
 */
class WidgetSnapshotStore(context: Context) {
    private val store = context.applicationContext.widgetStore

    /** The current snapshot, or null when the app has never written one (e.g. just installed). */
    val snapshot: Flow<WidgetSnapshot?> = store.data.map { prefs -> prefs[KEY]?.let(::decode) }

    suspend fun current(): WidgetSnapshot? = snapshot.first()

    /**
     * Replaces the snapshot with [transform] of the current one (or of an empty signed-in one).
     * Returns whether it changed.
     */
    suspend fun update(transform: (WidgetSnapshot) -> WidgetSnapshot): Boolean {
        var changed = false
        store.edit { prefs ->
            val before = prefs[KEY]?.let(::decode)
            val after = transform(before ?: WidgetSnapshot(signedIn = true))
            if (after != before) {
                prefs[KEY] = json.encodeToString(WidgetSnapshot.serializer(), after)
                changed = true
            }
        }
        return changed
    }

    private fun decode(value: String): WidgetSnapshot? =
        runCatching { json.decodeFromString(WidgetSnapshot.serializer(), value) }.getOrNull()

    private companion object {
        val KEY = stringPreferencesKey("snapshot")
        val json = Json { ignoreUnknownKeys = true }
    }
}
