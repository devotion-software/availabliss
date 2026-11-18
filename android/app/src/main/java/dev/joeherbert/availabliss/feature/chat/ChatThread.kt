package dev.joeherbert.availabliss.feature.chat

import dev.joeherbert.availabliss.core.network.model.Message
import dev.joeherbert.availabliss.core.network.model.canReply

/**
 * A message arriving while a thread is open: replaces its copy if it's already in the thread,
 * joins the end if it replies to something in it, and is otherwise left out.
 */
fun List<Message>.withThreadMessage(message: Message): List<Message> = when {
    any { it.id == message.id } -> map { if (it.id == message.id) message.copy(receipt = message.receipt ?: it.receipt) else it }
    message.replyTo?.id?.let { parent -> any { it.id == parent } } == true -> this + message
    else -> this
}

/**
 * Who a reply in the thread goes to: the message you picked (while it can still be replied to),
 * else the latest one from someone else (so it isn't to yourself straight after you send),
 * else the latest one.
 */
fun threadReplyTarget(thread: List<Message>, pickedId: String?, meId: String?): Message? =
    pickedId?.let { id -> thread.firstOrNull { it.id == id && it.canReply } }
        ?: thread.lastOrNull { it.canReply && it.authorId != meId }
        ?: thread.lastOrNull { it.canReply }
