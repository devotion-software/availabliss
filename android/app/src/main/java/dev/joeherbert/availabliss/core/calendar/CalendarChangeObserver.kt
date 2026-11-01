package dev.joeherbert.availabliss.core.calendar

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Watches the device calendar provider while the process is alive and schedules a
 * (debounced) upload when anything changes.
 */
@Singleton
class CalendarChangeObserver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val reader: DeviceCalendarReader,
    private val scheduler: CalendarSyncScheduler,
) {
    private var registered = false

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            scheduler.syncSoon(delaySeconds = DEBOUNCE_SECONDS)
        }
    }

    /** Safe to call repeatedly; registers once READ_CALENDAR is granted. */
    @Synchronized
    fun ensureRegistered() {
        if (registered || !reader.hasReadPermission()) return
        context.contentResolver.registerContentObserver(CalendarContract.CONTENT_URI, true, observer)
        registered = true
    }

    @Synchronized
    fun unregister() {
        if (!registered) return
        context.contentResolver.unregisterContentObserver(observer)
        registered = false
    }

    private companion object {
        const val DEBOUNCE_SECONDS = 10L
    }
}
