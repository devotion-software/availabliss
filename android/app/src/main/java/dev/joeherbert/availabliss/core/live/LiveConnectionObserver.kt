package dev.joeherbert.availabliss.core.live

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps [LiveEvents] open only while the app is foregrounded; push covers the rest.
 * Registered on `ProcessLifecycleOwner` by the Application.
 */
@Singleton
class LiveConnectionObserver @Inject constructor(
    private val liveEvents: LiveEvents,
) : DefaultLifecycleObserver {
    var isForeground: Boolean = false
        private set

    override fun onStart(owner: LifecycleOwner) {
        isForeground = true
        liveEvents.start()
    }

    override fun onStop(owner: LifecycleOwner) {
        isForeground = false
        liveEvents.stop()
    }
}
