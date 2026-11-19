package dev.joeherbert.availabliss.feature.chat

import dev.joeherbert.availabliss.core.common.UserMessage
import dev.joeherbert.availabliss.core.network.ApiResult
import dev.joeherbert.availabliss.core.network.model.PollOptionEdit
import dev.joeherbert.availabliss.core.network.model.UpdatePollRequest
import dev.joeherbert.availabliss.feature.proposal.ProposalPrefill
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Polls: the create sheet, voting, closing, and "Propose this" on a date poll's option. */
internal class ChatPolls(private val session: ChatSession) {
    private val data get() = session.data

    fun openPollSheet() = data.update { it.copy(pollDraft = PollDraft(), menuOpen = false) }
    fun closePollSheet() = data.update { it.copy(pollDraft = null) }
    fun onPollQuestion(value: String) = updatePoll { it.copy(question = value.take(200)) }
    fun onPollOption(index: Int, value: String) = updatePoll { p ->
        p.copy(options = p.options.mapIndexed { i, o -> if (i == index) value.take(100) else o })
    }
    fun onAddPollOption() = updatePoll { if (it.canAddOption) it.copy(options = it.options + "", optionIds = it.optionIds + null) else it }
    fun onRemovePollOption(index: Int) = updatePoll { p ->
        if (p.options.size <= 2) p else p.copy(options = p.options.filterIndexed { i, _ -> i != index }, optionIds = p.optionIds.filterIndexed { i, _ -> i != index })
    }

    /** "Edit" on your poll: the sheet starts from it. */
    fun openEditPoll(messageId: String) {
        val poll = data.value.allMessages.firstOrNull { it.id == messageId }?.poll ?: return
        data.update {
            it.copy(
                pollDraft = PollDraft(
                    question = poll.question,
                    options = poll.options.map { o -> o.label },
                    multi = poll.multi,
                    editingPollId = poll.id,
                    optionIds = poll.options.map { o -> o.id },
                    dated = poll.options.any { o -> o.slot != null },
                ),
                menuOpen = false,
            )
        }
    }
    fun onPollMulti(value: Boolean) = updatePoll { it.copy(multi = value) }

    fun submitPoll() {
        val draft = data.value.pollDraft ?: return
        if (!draft.canSubmit) return
        updatePoll { it.copy(sending = true) }
        draft.editingPollId?.let { pollId ->
            session.scope.launch {
                val options = draft.options.mapIndexed { i, label -> PollOptionEdit(draft.optionIds.getOrNull(i), label.trim()) }.filter { it.label.isNotEmpty() }
                val body = UpdatePollRequest(draft.question.trim(), draft.multi, options.takeUnless { draft.dated })
                when (val result = session.chat.updatePoll(pollId, body)) {
                    is ApiResult.Success -> {
                        session.replacePoll(result.value)
                        data.update { it.copy(pollDraft = null) }
                    }
                    is ApiResult.Failure -> {
                        updatePoll { it.copy(sending = false) }
                        data.update { it.copy(message = UserMessage(result.error.message)) }
                    }
                }
            }
            return
        }
        session.scope.launch {
            val options = draft.options.map { it.trim() }.filter { it.isNotEmpty() }
            when (val result = session.chat.sendPoll(session.groupId, draft.question, options, draft.multi)) {
                is ApiResult.Success -> {
                    session.appendMessage(result.value)
                    data.update { it.copy(pollDraft = null) }
                }
                is ApiResult.Failure -> {
                    updatePoll { it.copy(sending = false) }
                    data.update { it.copy(message = UserMessage(result.error.message)) }
                }
            }
        }
    }

    private fun updatePoll(transform: (PollDraft) -> PollDraft) = data.update { d -> d.copy(pollDraft = d.pollDraft?.let(transform)) }

    /** Tap an option: toggles your vote (single-choice polls move it). Optimistic, then reconciled. */
    fun onVote(messageId: String, optionId: String) {
        val me = session.meId ?: return
        val poll = data.value.allMessages.firstOrNull { it.id == messageId }?.poll ?: return
        if (poll.closed) return
        val mine = poll.options.filter { me in it.voterIds }.map { it.id }.toSet()
        val selection = when {
            optionId in mine -> mine - optionId
            poll.multi -> mine + optionId
            else -> setOf(optionId)
        }
        val optimistic = poll.copy(
            options = poll.options.map { o ->
                o.copy(voterIds = o.voterIds.filterNot { it == me } + if (o.id in selection) listOf(me) else emptyList())
            },
        )
        session.replacePoll(optimistic)
        session.scope.launch {
            when (val result = session.chat.vote(poll.id, selection.toList())) {
                is ApiResult.Success -> session.replacePoll(result.value)
                is ApiResult.Failure -> {
                    session.replacePoll(poll)
                    data.update { it.copy(message = UserMessage(result.error.message)) }
                }
            }
        }
    }

    /**
     * "Propose this" on a date poll's option: its time for the propose form, or null if it's
     * gone or has no time.
     */
    fun proposalFor(messageId: String, optionId: String): ProposalPrefill? {
        val poll = data.value.messages.firstOrNull { it.id == messageId }?.poll ?: return null
        val slot = poll.options.firstOrNull { it.id == optionId }?.slot ?: return null
        return ProposalPrefill(slot.start, slot.end, slot.allDay)
    }

    fun onClosePoll(messageId: String) {
        val poll = data.value.allMessages.firstOrNull { it.id == messageId }?.poll ?: return
        session.scope.launch {
            when (val result = session.chat.closePoll(poll.id)) {
                is ApiResult.Success -> session.replacePoll(result.value)
                is ApiResult.Failure -> data.update { it.copy(message = UserMessage(result.error.message)) }
            }
        }
    }
}
