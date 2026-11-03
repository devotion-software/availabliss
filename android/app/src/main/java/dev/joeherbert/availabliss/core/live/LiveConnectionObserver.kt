package dev.joeherbert.availabliss.core.live

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps [LiveEvents] open only while the app is foregrounded; push covers the rest.
 * Registered on `ProcessLifecycleOwner` by the Application.
 *
 * A process started in the background (push, WorkManager, the calendar observer)
 * never reaches ON_START, so [foreground] stays false and nothing opens the stream.
 */
@Singleton
class LiveConnectionObserver @Inject constructor(
    private val liveEvents: LiveEvents,
) : DefaultLifecycleObserver {
    private val _foreground = MutableStateFlow(false)

    /** True between the process's ON_START and ON_STOP. */
    val foreground: StateFlow<Boolean> = _foreground.asStateFlow()

    val isForeground: Boolean get() = _foreground.value

    override fun onStart(owner: LifecycleOwner) {
        _foreground.value = true
        liveEvents.start()
    }

    override fun onStop(owner: LifecycleOwner) {
        _foreground.value = false
        liveEvents.stop()
    }
}
