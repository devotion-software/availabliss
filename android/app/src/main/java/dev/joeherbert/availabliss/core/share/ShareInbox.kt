package dev.joeherbert.availabliss.core.share

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Photos, files or passes (and any text) another app shared with us, to send to a group chat. */
data class SharedContent(val uris: List<Uri>, val text: String?) {
    val isEmpty: Boolean get() = uris.isEmpty() && text.isNullOrBlank()
}

/**
 * Holds what was shared from another app's share sheet while you pick a group chat, then hands it
 * to that chat once (see ChatViewModel), which attaches it to the composer ready to send.
 */
@Singleton
class ShareInbox @Inject constructor() {
    private val _pending = MutableStateFlow<SharedContent?>(null)
    val pending: StateFlow<SharedContent?> = _pending.asStateFlow()
    private var target: String? = null

    fun receive(content: SharedContent) {
        target = null
        _pending.value = content.takeUnless { it.isEmpty }
    }

    /** The group picked in the share screen: its chat takes the content when it opens. */
    fun sendTo(groupId: String) {
        target = groupId
    }

    fun cancel() {
        target = null
        _pending.value = null
    }

    /** The content picked for [groupId]'s chat, once. */
    fun takeFor(groupId: String): SharedContent? {
        if (target != groupId) return null
        val content = _pending.value
        cancel()
        return content
    }
}
