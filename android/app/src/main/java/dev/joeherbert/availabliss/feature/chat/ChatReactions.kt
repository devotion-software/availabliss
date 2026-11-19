package dev.joeherbert.availabliss.feature.chat

import dev.joeherbert.availabliss.core.common.UserMessage
import dev.joeherbert.availabliss.core.network.ApiResult
import dev.joeherbert.availabliss.core.network.model.Reactions
import dev.joeherbert.availabliss.core.network.model.canReact
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Emoji reactions: shown straight away, then the server's copy; put back if it fails. */
internal class ChatReactions(private val session: ChatSession) {
    private val data get() = session.data

    /** Reacts to [messageId] with [emoji], or takes it back if it's already yours. */
    fun react(messageId: String, emoji: String) {
        val me = session.meId ?: return
        val message = data.value.allMessages.firstOrNull { it.id == messageId }?.takeIf { it.canReact } ?: return
        val before = message.reactions
        val next = Reactions.toggled(before, me, emoji)
        session.setReactions(messageId, Reactions.setting(before, me, next))
        session.scope.launch {
            when (val result = session.chat.react(messageId, next)) {
                is ApiResult.Success -> session.setReactions(result.value.messageId, result.value.reactions)
                is ApiResult.Failure -> {
                    session.setReactions(messageId, before)
                    data.update { it.copy(message = UserMessage(result.error.message)) }
                }
            }
        }
    }
}
