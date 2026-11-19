package dev.joeherbert.availabliss.feature.chat

import dev.joeherbert.availabliss.core.common.DateTimes
import dev.joeherbert.availabliss.core.data.ChatRepository
import dev.joeherbert.availabliss.core.data.EventsRepository
import dev.joeherbert.availabliss.core.data.GroupsRepository
import dev.joeherbert.availabliss.core.data.ProposalsRepository
import dev.joeherbert.availabliss.core.data.UserRepository
import dev.joeherbert.availabliss.core.network.ApiResult
import dev.joeherbert.availabliss.core.network.model.GroupEvent
import dev.joeherbert.availabliss.core.network.model.Message
import dev.joeherbert.availabliss.core.network.model.MessageReaction
import dev.joeherbert.availabliss.core.network.model.Poll
import dev.joeherbert.availabliss.core.network.model.Proposal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What ChatViewModel's collaborators share: the group, the one state they all update, the
 * ViewModel's scope, and the message operations several of them need.
 */
internal class ChatSession(
    val groupId: String,
    val data: MutableStateFlow<ChatData>,
    val scope: CoroutineScope,
    val chat: ChatRepository,
    private val groups: GroupsRepository,
    private val users: UserRepository,
    val references: ChatReferences,
) {
    val meId: String? get() = users.user.value?.id

    suspend fun refreshGroup() {
        (groups.refreshGroup(groupId) as? ApiResult.Success)?.value?.let { g -> data.update { it.copy(group = g) } }
    }

    /** Adds [message] (or replaces it if it's already there), and to the open thread if it replies within it. */
    fun appendMessage(message: Message) {
        data.update { d ->
            val messages = if (d.messages.any { it.id == message.id }) {
                d.messages.map { if (it.id == message.id) message else it }
            } else {
                d.messages + message
            }
            // A reply to something in the open thread joins it.
            d.copy(messages = messages, thread = d.thread?.let { t -> t.copy(messages = t.messages.withThreadMessage(message)) })
        }
        references.resolve()
    }

    /** A message's reactions, in the chat and the open thread. */
    fun setReactions(messageId: String, reactions: List<MessageReaction>) = data.update { d ->
        fun List<Message>.withReactions() = map { m -> if (m.id == messageId && m.reactions != reactions) m.copy(reactions = reactions) else m }
        d.copy(messages = d.messages.withReactions(), thread = d.thread?.let { it.copy(messages = it.messages.withReactions()) })
    }

    fun replacePoll(poll: Poll) = data.update { d ->
        fun List<Message>.withPoll() = map { m -> if (m.poll?.id == poll.id) m.copy(poll = poll) else m }
        d.copy(messages = d.messages.withPoll(), thread = d.thread?.let { it.copy(messages = it.messages.withPoll()) })
    }
}

/** Proposals, events and date poll conflicts referenced by messages, fetched lazily and once each. */
internal class ChatReferences(
    private val groupId: String,
    private val data: MutableStateFlow<ChatData>,
    private val scope: CoroutineScope,
    private val proposals: ProposalsRepository,
    private val events: EventsRepository,
) {
    private val requestedProposals = mutableSetOf<String>()
    private val requestedEvents = mutableSetOf<String>()
    /** Date poll times whose conflicts have been asked for, by [pollSlotKey]. */
    private val requestedConflicts = mutableSetOf<String>()
    private var conflictsJob: Job? = null

    /** Lazily fetches proposals and events referenced by messages. */
    fun resolve() {
        val messages = data.value.messages
        messages.mapNotNull { it.proposalId }.filter { requestedProposals.add(it) }.forEach { id ->
            scope.launch {
                (proposals.get(id) as? ApiResult.Success)?.value?.let(::putProposal)
            }
        }
        messages.mapNotNull { it.eventId }.filter { requestedEvents.add(it) }.forEach { id ->
            scope.launch {
                (events.get(id) as? ApiResult.Success)?.value?.let(::putEvent)
            }
        }
        resolveConflicts(messages)
    }

    /**
     * After a gap in the live stream: [messages]' proposals and events are fetched again, since
     * their updates may have been missed, and so are conflicts (busy times may have changed).
     */
    fun forget(messages: List<Message>) {
        messages.forEach { m ->
            m.proposalId?.let(requestedProposals::remove)
            m.eventId?.let(requestedEvents::remove)
        }
        requestedConflicts.clear()
    }

    /** Who's busy for each open date poll's times (the conflicts endpoint), fetched once each. */
    fun resolveConflicts(messages: List<Message>) {
        val now = java.time.Instant.now()
        messages.asSequence()
            .mapNotNull { it.poll?.takeIf { poll -> !poll.closed } }
            .flatMap { it.options.asSequence().mapNotNull { o -> o.slot } }
            .filter { slot -> DateTimes.parseInstant(slot.end)?.isAfter(now) ?: false }
            .filter { requestedConflicts.add(pollSlotKey(it)) }
            .toList()
            .forEach { slot ->
                scope.launch {
                    when (val result = proposals.conflicts(groupId, slot.start, slot.end)) {
                        is ApiResult.Success -> data.update { it.copy(conflicts = it.conflicts + (pollSlotKey(slot) to result.value)) }
                        // Asked again next time the chat catches up.
                        is ApiResult.Failure -> requestedConflicts.remove(pollSlotKey(slot))
                    }
                }
            }
    }

    /**
     * Busy times changed (someone synced): re-checks date poll options. Syncs come in bursts, so
     * they're coalesced, and nothing is fetched without an open date poll.
     */
    fun refreshConflicts() {
        if (data.value.messages.none { m -> m.poll?.let { !it.closed && it.options.any { o -> o.slot != null } } == true }) return
        conflictsJob?.cancel()
        conflictsJob = scope.launch {
            delay(CONFLICTS_DEBOUNCE_MS)
            requestedConflicts.clear()
            resolveConflicts(data.value.messages)
        }
    }

    fun putProposal(p: Proposal) = data.update { it.copy(proposals = it.proposals + (p.id to p)) }
    fun putEvent(e: GroupEvent) = data.update { it.copy(events = it.events + (e.id to e)) }

    private companion object {
        const val CONFLICTS_DEBOUNCE_MS = 1_000L
    }
}
