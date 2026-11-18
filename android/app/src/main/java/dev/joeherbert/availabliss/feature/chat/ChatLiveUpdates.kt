package dev.joeherbert.availabliss.feature.chat

import dev.joeherbert.availabliss.core.data.GroupsRepository
import dev.joeherbert.availabliss.core.live.LivePayload
import dev.joeherbert.availabliss.core.network.ApiResult
import dev.joeherbert.availabliss.core.network.model.ReceiptState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Applies the group's live events to the chat. */
internal class ChatLiveUpdates(
    private val session: ChatSession,
    private val groups: GroupsRepository,
    private val receipts: ChatReceipts,
    private val composer: ChatComposer,
) {
    private val data get() = session.data

    suspend fun onLive(payload: LivePayload) {
        when (payload) {
            is LivePayload.MessageCreated -> {
                session.appendMessage(payload.message)
                if (receipts.visible) receipts.schedule()
                else if (payload.message.authorId != session.meId) session.chat.markReceipts(session.groupId, payload.message.id, ReceiptState.Delivered)
            }
            is LivePayload.MessageUpdated -> {
                val m = payload.message
                data.update { it.copy(messages = it.messages.withUpdated(m), thread = it.thread?.let { t -> t.copy(messages = t.messages.withUpdated(m)) }) }
                // Deleted elsewhere (e.g. on another device) while you were editing it.
                if (m.deleted && data.value.editing?.messageId == m.id) composer.cancelEdit()
                if (m.deleted && data.value.replyingToId == m.id) composer.cancelReply()
            }
            is LivePayload.MessageReceipts -> {
                val updates = payload.payload.receipts.associateBy { it.messageId }
                data.update { d ->
                    d.copy(messages = d.messages.map { m -> updates[m.id]?.let { m.copy(receipt = it.toReceipt()) } ?: m })
                }
            }
            is LivePayload.PollUpdated -> {
                session.replacePoll(payload.poll)
                session.references.resolveConflicts(data.value.messages)
            }
            is LivePayload.AvailabilityChanged -> session.references.refreshConflicts()
            is LivePayload.ProposalChanged -> session.references.putProposal(payload.proposal)
            is LivePayload.ProposalConfirmed -> {
                session.references.putProposal(payload.proposal)
                session.references.putEvent(payload.event)
            }
            is LivePayload.EventChanged -> session.references.putEvent(payload.event)
            is LivePayload.EventDeleted -> data.update { it.copy(events = it.events - payload.id) }
            is LivePayload.GroupUpdated -> {
                val group = groups.applyLiveUpdate(payload.group, session.meId)
                data.update { it.copy(group = group) }
            }
            is LivePayload.MemberChanged, is LivePayload.MemberRemoved -> session.refreshGroup()
            else -> Unit
        }
    }
}

/** Delivered and seen receipts for messages from others, sent only while the chat is on screen. */
internal class ChatReceipts(private val session: ChatSession) {
    var visible = false
        private set
    private var lastSeenSent: String? = null
    private var receiptJob: Job? = null

    fun onVisibilityChanged(isVisible: Boolean) {
        visible = isVisible
        if (isVisible) schedule()
    }

    /** Marks everything up to the newest message from someone else as delivered, then seen. */
    fun schedule() {
        if (!visible) return
        val me = session.meId
        val newest = session.data.value.messages.lastOrNull { it.authorId != null && it.authorId != me } ?: return
        if (newest.id == lastSeenSent) return
        receiptJob?.cancel()
        receiptJob = session.scope.launch {
            delay(RECEIPT_DEBOUNCE_MS)
            session.chat.markReceipts(session.groupId, newest.id, ReceiptState.Delivered)
            if (session.chat.markReceipts(session.groupId, newest.id, ReceiptState.Seen) is ApiResult.Success) lastSeenSent = newest.id
        }
    }

    private companion object {
        const val RECEIPT_DEBOUNCE_MS = 600L
    }
}
