package dev.joeherbert.availabliss.feature.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.joeherbert.availabliss.core.common.UserMessage
import dev.joeherbert.availabliss.core.network.ApiResult
import dev.joeherbert.availabliss.core.network.model.Message
import dev.joeherbert.availabliss.core.network.model.MessageKind
import dev.joeherbert.availabliss.core.network.model.canReply
import dev.joeherbert.availabliss.core.network.model.displayText
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The chat's composer: the draft, sending text and attachments, replying, and editing or deleting your messages. */
internal class ChatComposer(private val session: ChatSession) {
    private val data get() = session.data
    private val chat get() = session.chat

    /**
     * The composer text. Plain Compose state, updated synchronously, rather than part of the
     * ViewModel's state flow: a keystroke neither goes through an async flow (which drops
     * characters and moves the caret) nor rebuilds the message list.
     */
    var draft by mutableStateOf("")
        private set

    fun onDraftChange(value: String) {
        draft = value.take(4000)
    }

    // region Sending
    fun send() {
        if (data.value.editing != null) return saveEdit()
        if (data.value.pending.isNotEmpty()) return sendAttachments()
        val text = draft.trim()
        if (text.isEmpty() || data.value.sending) return
        draft = ""
        val replyTo = data.value.replyingToId
        data.update { it.copy(sending = true, menuOpen = false, replyingToId = null) }
        session.scope.launch {
            when (val result = chat.sendText(session.groupId, text, replyTo)) {
                is ApiResult.Success -> {
                    session.appendMessage(result.value)
                    data.update { it.copy(sending = false) }
                }
                is ApiResult.Failure -> {
                    if (draft.isEmpty()) draft = text
                    data.update { it.copy(sending = false, replyingToId = it.replyingToId ?: replyTo, message = UserMessage(result.error.message)) }
                }
            }
        }
    }

    private fun sendAttachments() {
        val d = data.value
        if (d.sending || d.pending.any { it.uploaded == null }) return
        val ids = d.pending.mapNotNull { it.uploaded?.id }
        val text = draft.trim()
        draft = ""
        val replyTo = d.replyingToId
        data.update { it.copy(sending = true, menuOpen = false, replyingToId = null) }
        session.scope.launch {
            when (val result = chat.sendAttachments(session.groupId, ids, text, replyTo)) {
                is ApiResult.Success -> {
                    session.appendMessage(result.value)
                    data.update { cur -> cur.copy(sending = false, pending = cur.pending.filterNot { it.uploaded?.id in ids }) }
                }
                is ApiResult.Failure -> {
                    if (draft.isEmpty()) draft = text
                    data.update { it.copy(sending = false, replyingToId = it.replyingToId ?: replyTo, message = UserMessage(result.error.message)) }
                }
            }
        }
    }
    // endregion

    // region Reply
    /** Replies to [messageId] with the next message (swipe right, or Reply in its menu). */
    fun startReply(messageId: String) {
        val m = data.value.messages.firstOrNull { it.id == messageId }?.takeIf { it.canReply } ?: return
        cancelEdit()
        data.update { it.copy(replyingToId = m.id, menuOpen = false) }
    }

    fun cancelReply() = data.update { it.copy(replyingToId = null) }
    // endregion

    // region Edit & delete
    /** Your own, non-deleted text message, or null. */
    private fun editable(messageId: String): Message? = data.value.messages.firstOrNull {
        it.id == messageId && it.kind == MessageKind.Text && !it.deleted && it.authorId != null && it.authorId == session.meId
    }

    /** Your own text message that can be edited: not a shared place (those can only be deleted). */
    private fun canEdit(messageId: String): Message? = editable(messageId)?.takeIf { it.location == null }

    /** Puts the composer into edit mode with the message's text; the current draft comes back afterwards. */
    fun startEdit(messageId: String) {
        val m = canEdit(messageId) ?: return
        val stash = data.value.editing?.stashedDraft ?: draft
        val text = m.displayText.orEmpty()
        data.update { it.copy(editing = EditSession(m.id, text, stash, hasAttachments = m.attachments.isNotEmpty()), menuOpen = false, replyingToId = null) }
        draft = text
    }

    /** Leaves edit mode, restoring whatever draft was there before. */
    fun cancelEdit() {
        val edit = data.value.editing ?: return
        draft = edit.stashedDraft
        data.update { it.copy(editing = null) }
    }

    private fun saveEdit() {
        val edit = data.value.editing ?: return
        val text = draft.trim()
        if ((text.isEmpty() && !edit.hasAttachments) || data.value.sending) return
        if (text == edit.original.trim()) return cancelEdit()
        data.update { it.copy(sending = true) }
        session.scope.launch {
            val result = chat.edit(edit.messageId, text)
            when (result) {
                is ApiResult.Success -> {
                    data.update { it.copy(messages = it.messages.withUpdated(result.value), sending = false) }
                    if (data.value.editing?.messageId == edit.messageId) cancelEdit()
                }
                is ApiResult.Failure -> data.update { it.copy(sending = false, message = UserMessage(result.error.message)) }
            }
        }
    }

    fun requestDelete(messageId: String) {
        if (editable(messageId) == null) return
        data.update { it.copy(confirmDeleteId = messageId, menuOpen = false) }
    }

    fun dismissDelete() = data.update { it.copy(confirmDeleteId = null) }

    fun confirmDelete() {
        val id = data.value.confirmDeleteId ?: return
        data.update { it.copy(confirmDeleteId = null) }
        session.scope.launch {
            when (val result = chat.delete(id)) {
                is ApiResult.Success -> {
                    data.update { it.copy(messages = it.messages.withDeleted(id), thread = it.thread?.let { t -> t.copy(messages = t.messages.withDeleted(id)) }) }
                    if (data.value.editing?.messageId == id) cancelEdit()
                    if (data.value.replyingToId == id) cancelReply()
                }
                is ApiResult.Failure -> data.update { it.copy(message = UserMessage(result.error.message)) }
            }
        }
    }
    // endregion
}
