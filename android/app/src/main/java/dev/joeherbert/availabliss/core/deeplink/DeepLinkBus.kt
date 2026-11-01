package dev.joeherbert.availabliss.core.deeplink

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the most recent unhandled [DeepLink] until the UI is ready to consume it
 * (e.g. after sign in finishes for an invite link).
 */
@Singleton
class DeepLinkBus @Inject constructor() {
    private val _pending = MutableStateFlow<DeepLink?>(null)
    val pending: StateFlow<DeepLink?> = _pending.asStateFlow()

    fun post(link: DeepLink) {
        _pending.value = link
    }

    /** Marks [link] handled (no-op if a newer link has arrived since). */
    fun consume(link: DeepLink) {
        _pending.compareAndSet(link, null)
    }
}
