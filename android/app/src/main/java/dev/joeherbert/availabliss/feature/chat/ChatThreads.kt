package dev.joeherbert.availabliss.feature.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.joeherbert.availabliss.core.common.UserMessage
import dev.joeherbert.availabliss.core.network.ApiResult
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The thread sheet: a message's chain of replies, replying within it, and "Show in chat". */
internal class ChatThreads(private val session: ChatSession) {
    private val data get() = session.data
    private val chat get() = session.chat

    /** The thread sheet's composer text, kept apart from the chat's. */
    var threadDraft by mutableStateOf("")
        private set

    /** Opens the thread [messageId] is part of (fetched, since it may reach past what's loaded). */
    fun openThread(messageId: String) {
        threadDraft = ""
        data.update { it.copy(thread = ThreadSession(messageId), menuOpen = false) }
        session.scope.launch {
            val result = chat.thread(messageId)
            data.update { d ->
                val t = d.thread?.takeIf { it.focusId == messageId } ?: return@update d
                when (result) {
                    is ApiResult.Success -> d.copy(thread = t.copy(messages = result.value.messages, truncated = result.value.truncated, loading = false))
                    is ApiResult.Failure -> d.copy(thread = null, message = UserMessage(result.error.message))
                }
            }
        }
    }

    fun closeThread() {
        threadDraft = ""
        data.update { it.copy(thread = null) }
    }

    fun onThreadDraftChange(value: String) {
        threadDraft = value.take(4000)
    }

    /** Your next reply in the thread goes to [messageId]; null goes back to the latest. */
    fun replyInThread(messageId: String?) = data.update { d ->
        d.copy(thread = d.thread?.copy(pickedId = messageId, actionsForId = null))
    }

    fun openThreadActions(messageId: String) = data.update { d -> d.copy(thread = d.thread?.copy(actionsForId = messageId)) }

    fun closeThreadActions() = data.update { d -> d.copy(thread = d.thread?.copy(actionsForId = null)) }

    fun sendInThread() {
        val t = data.value.thread ?: return
        val text = threadDraft.trim()
        if (text.isEmpty() || t.sending) return
        val target = threadReplyTarget(t.messages, t.pickedId, session.meId) ?: return
        threadDraft = ""
        data.update { d -> d.copy(thread = d.thread?.copy(sending = true)) }
        session.scope.launch {
            when (val result = chat.sendText(session.groupId, text, target.id)) {
                is ApiResult.Success -> {
                    session.appendMessage(result.value)
                    data.update { d -> d.copy(thread = d.thread?.copy(sending = false, pickedId = null)) }
                }
                is ApiResult.Failure -> {
                    if (threadDraft.isEmpty()) threadDraft = text
                    data.update { d -> d.copy(thread = d.thread?.copy(sending = false), message = UserMessage(result.error.message)) }
                }
            }
        }
    }

    /** Closes the thread and goes to [messageId] in the chat, paging back to it if needed. */
    fun showInChat(messageId: String) {
        closeThread()
        session.scope.launch {
            var pages = 0
            while (data.value.messages.none { it.id == messageId } && data.value.hasMore && pages < MAX_PAGES_TO_FIND) {
                val oldest = data.value.messages.firstOrNull()?.id ?: break
                val page = (chat.page(session.groupId, before = oldest) as? ApiResult.Success)?.value ?: break
                data.update { cur ->
                    val known = cur.messages.mapTo(HashSet()) { it.id }
                    cur.copy(messages = page.messages.filterNot { it.id in known } + cur.messages, hasMore = page.hasMore)
                }
                pages++
            }
            session.references.resolve()
            if (data.value.messages.any { it.id == messageId }) data.update { it.copy(showInChat = ShowInChatRequest(messageId)) }
        }
    }

    fun onShowInChatHandled() = data.update { it.copy(showInChat = null) }

    private companion object {
        /** How many older pages "Show in chat" loads looking for a message. */
        const val MAX_PAGES_TO_FIND = 20
    }
}
