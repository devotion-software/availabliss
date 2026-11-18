package dev.joeherbert.availabliss.core.calendar

import dev.joeherbert.availabliss.core.data.AppPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A plan this phone put in a device calendar: its row in `CalendarContract.Events`. Plans the
 * calendar app added (no permission to insert directly) can't be found again, so aren't kept.
 */
@Serializable
data class AddedCalendarEvent(val eventId: Long)

/** Plans added to a device calendar from this phone, by key (event or proposal id), kept across launches. */
@Singleton
class AddedCalendarEvents @Inject constructor(
    private val prefs: AppPreferences,
    private val json: Json,
) {
    private val mutex = Mutex()
    private val serializer = MapSerializer(String.serializer(), AddedCalendarEvent.serializer())

    suspend fun all(): Map<String, AddedCalendarEvent> =
        prefs.addedCalendarEvents.first()?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }.orEmpty()

    suspend fun set(key: String, event: AddedCalendarEvent) = mutex.withLock {
        prefs.setAddedCalendarEvents(json.encodeToString(serializer, all() + (key to event)))
    }
}
