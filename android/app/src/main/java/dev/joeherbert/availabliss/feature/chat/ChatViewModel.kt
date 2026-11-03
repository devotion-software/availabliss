package dev.joeherbert.availabliss.feature.chat

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.joeherbert.availabliss.core.common.DateTimes
import dev.joeherbert.availabliss.core.common.UserMessage
import dev.joeherbert.availabliss.core.common.firstName
import dev.joeherbert.availabliss.core.common.joinNamesSummary
import dev.joeherbert.availabliss.core.data.ChatRepository
import dev.joeherbert.availabliss.core.data.EventsRepository
import dev.joeherbert.availabliss.core.data.GroupsRepository
import dev.joeherbert.availabliss.core.data.ProposalsRepository
import dev.joeherbert.availabliss.core.data.UserRepository
import dev.joeherbert.availabliss.core.live.LiveEvents
import dev.joeherbert.availabliss.core.live.LivePayload
import dev.joeherbert.availabliss.core.network.ApiResult
import dev.joeherbert.availabliss.core.network.model.Group
import dev.joeherbert.availabliss.core.network.model.GroupEvent
import dev.joeherbert.availabliss.core.network.model.Message
import dev.joeherbert.availabliss.core.network.model.MessageInfo
import dev.joeherbert.availabliss.core.network.model.Poll
import dev.joeherbert.availabliss.core.network.model.Proposal
import dev.joeherbert.availabliss.core.network.model.ReceiptState
import dev.joeherbert.availabliss.core.network.model.TileColor
import dev.joeherbert.availabliss.designsystem.component.AvatarPerson
import dev.joeherbert.availabliss.feature.common.MemberDirectory
import dev.joeherbert.availabliss.navigation.GroupRoute
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A person row in the Message info sheet. */
data class InfoPerson(val person: AvatarPerson, val name: String, val time: String)

data class MessageInfoUi(
    val text: String,
    val sentLabel: String,
    val loading: Boolean,
    val seen: List<InfoPerson> = emptyList(),
    val delivered: List<InfoPerson> = emptyList(),
    val pending: List<InfoPerson> = emptyList(),
    val error: String? = null,
)

/** Poll creation form. */
data class PollDraft(
    val question: String = "",
    val options: List<String> = listOf("", ""),
    val multi: Boolean = false,
    val sending: Boolean = false,
) {
    val canAddOption: Boolean get() = options.size < MAX_OPTIONS
    val canSubmit: Boolean get() = question.isNotBlank() && options.count { it.isNotBlank() } >= 2 && !sending

    companion object {
        const val MAX_OPTIONS = 10
    }
}

data class ChatUiState(
    val groupName: String = "",
    val monogram: String = "",
    val tileColor: TileColor = TileColor.T1,
    val membersSummary: String = "",
    /** Newest first (the list is reverse-laid-out). */
    val items: List<ChatItem> = emptyList(),
    val loading: Boolean = true,
    val loadingOlder: Boolean = false,
    val hasMore: Boolean = false,
    val error: String? = null,
    val menuOpen: Boolean = false,
    val sending: Boolean = false,
    /** Non-null while the poll sheet is open. */
    val pollDraft: PollDraft? = null,
    /** Non-null while the Message info sheet is open. */
    val info: MessageInfoUi? = null,
    val message: UserMessage? = null,
)

private data class InfoSheet(val messageId: String, val info: MessageInfo? = null, val error: String? = null)

private data class ChatData(
    val group: Group? = null,
    val messages: List<Message> = emptyList(),
    val proposals: Map<String, Proposal> = emptyMap(),
    val events: Map<String, GroupEvent> = emptyMap(),
    val hasMore: Boolean = false,
    val loading: Boolean = true,
    val loadingOlder: Boolean = false,
    val error: String? = null,
    val menuOpen: Boolean = false,
    val sending: Boolean = false,
    val pollDraft: PollDraft? = null,
    val infoSheet: InfoSheet? = null,
    val message: UserMessage? = null,
)

/** What the message list is built from; everything else in [ChatData] changes without rebuilding it. */
private data class ItemInputs(
    val group: Group?,
    val messages: List<Message>,
    val proposals: Map<String, Proposal>,
    val events: Map<String, GroupEvent>,
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val chat: ChatRepository,
    private val groups: GroupsRepository,
    private val proposals: ProposalsRepository,
    private val events: EventsRepository,
    private val users: UserRepository,
    liveEvents: LiveEvents,
) : ViewModel() {

    private val groupId = savedStateHandle.toRoute<GroupRoute>().groupId
    private val data = MutableStateFlow(ChatData(group = groups.cachedGroup(groupId)))
    private val meId: String? get() = users.user.value?.id

    private val requestedProposals = mutableSetOf<String>()
    private val requestedEvents = mutableSetOf<String>()
    private var visible = false
    private var lastSeenSent: String? = null
    private var receiptJob: Job? = null

    private val meIds = users.user.map { it?.id }.distinctUntilChanged()

    /**
     * The message list (newest first), rebuilt off the main thread only when messages,
     * referenced proposals / events, the group or the signed-in user change; not when
     * the menu, a sheet or the sending flag does.
     */
    private val items = combine(
        data.map { ItemInputs(it.group, it.messages, it.proposals, it.events) }.distinctUntilChanged(),
        meIds,
    ) { inputs, me -> inputs.toItems(me) }.flowOn(Dispatchers.Default)

    val state: StateFlow<ChatUiState> = combine(data, items, meIds) { d, items, me -> d.toUi(items, me) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState())

    /**
     * The composer text. Plain Compose state, updated synchronously, rather than part of
     * [state]: a keystroke neither goes through an async flow (which drops characters and
     * moves the caret) nor rebuilds the message list.
     */
    var draft by mutableStateOf("")
        private set

    init {
        load()
        viewModelScope.launch {
            liveEvents.payloadsFor(groupId).collect { onLive(it) }
        }
        viewModelScope.launch { liveEvents.reconnected.collect { catchUp() } }
    }

    // region Loading
    fun load() {
        viewModelScope.launch {
            data.update { it.copy(loading = true, error = null) }
            if (data.value.group == null) refreshGroup()
            when (val result = chat.page(groupId)) {
                is ApiResult.Success -> {
                    data.update { it.copy(messages = result.value.messages, hasMore = result.value.hasMore, loading = false) }
                    resolveReferences()
                    scheduleReceipts()
                }
                is ApiResult.Failure -> data.update { it.copy(loading = false, error = result.error.message) }
            }
        }
    }

    fun loadOlder() {
        val d = data.value
        if (d.loadingOlder || !d.hasMore || d.loading) return
        val oldest = d.messages.firstOrNull()?.id ?: return
        viewModelScope.launch {
            data.update { it.copy(loadingOlder = true) }
            when (val result = chat.page(groupId, before = oldest)) {
                is ApiResult.Success -> {
                    data.update { cur ->
                        val known = cur.messages.mapTo(HashSet()) { it.id }
                        cur.copy(
                            messages = result.value.messages.filterNot { it.id in known } + cur.messages,
                            hasMore = result.value.hasMore,
                            loadingOlder = false,
                        )
                    }
                    resolveReferences()
                }
                is ApiResult.Failure -> data.update { it.copy(loadingOlder = false, message = UserMessage(result.error.message)) }
            }
        }
    }

    /**
     * After a gap in the live stream: fetches the newest page and splices it onto
     * what's loaded, keeping older pages the user scrolled back through when the two
     * overlap. Referenced proposals and events in that page are refetched too, since
     * their updates may also have been missed.
     */
    private fun catchUp() {
        if (data.value.loading) return
        viewModelScope.launch {
            val page = (chat.page(groupId) as? ApiResult.Success)?.value ?: return@launch
            data.update { cur ->
                val oldestFresh = page.messages.firstOrNull()?.id
                val overlapAt = cur.messages.indexOfFirst { it.id == oldestFresh }
                if (oldestFresh != null && overlapAt >= 0) {
                    cur.copy(messages = cur.messages.take(overlapAt) + page.messages)
                } else {
                    cur.copy(messages = page.messages, hasMore = page.hasMore)
                }
            }
            page.messages.forEach { m ->
                m.proposalId?.let(requestedProposals::remove)
                m.eventId?.let(requestedEvents::remove)
            }
            resolveReferences()
            scheduleReceipts()
            refreshGroup()
        }
    }

    private suspend fun refreshGroup() {
        (groups.refreshGroup(groupId) as? ApiResult.Success)?.value?.let { g -> data.update { it.copy(group = g) } }
    }

    /** Lazily fetches proposals and events referenced by messages. */
    private fun resolveReferences() {
        val messages = data.value.messages
        messages.mapNotNull { it.proposalId }.filter { requestedProposals.add(it) }.forEach { id ->
            viewModelScope.launch {
                (proposals.get(id) as? ApiResult.Success)?.value?.let(::putProposal)
            }
        }
        messages.mapNotNull { it.eventId }.filter { requestedEvents.add(it) }.forEach { id ->
            viewModelScope.launch {
                (events.get(id) as? ApiResult.Success)?.value?.let(::putEvent)
            }
        }
    }

    private fun putProposal(p: Proposal) = data.update { it.copy(proposals = it.proposals + (p.id to p)) }
    private fun putEvent(e: GroupEvent) = data.update { it.copy(events = it.events + (e.id to e)) }
    // endregion

    // region Live updates
    private suspend fun onLive(payload: LivePayload) {
        when (payload) {
            is LivePayload.MessageCreated -> {
                appendMessage(payload.message)
                if (visible) scheduleReceipts()
                else if (payload.message.authorId != meId) chat.markReceipts(groupId, payload.message.id, ReceiptState.Delivered)
            }
            is LivePayload.MessageReceipts -> {
                val updates = payload.payload.receipts.associateBy { it.messageId }
                data.update { d ->
                    d.copy(messages = d.messages.map { m -> updates[m.id]?.let { m.copy(receipt = it.toReceipt()) } ?: m })
                }
            }
            is LivePayload.PollUpdated -> replacePoll(payload.poll)
            is LivePayload.ProposalChanged -> putProposal(payload.proposal)
            is LivePayload.ProposalConfirmed -> {
                putProposal(payload.proposal)
                putEvent(payload.event)
            }
            is LivePayload.EventChanged -> putEvent(payload.event)
            is LivePayload.EventDeleted -> data.update { it.copy(events = it.events - payload.id) }
            is LivePayload.GroupUpdated -> {
                val group = groups.applyLiveUpdate(payload.group, meId)
                data.update { it.copy(group = group) }
            }
            is LivePayload.MemberChanged, is LivePayload.MemberRemoved -> refreshGroup()
            else -> Unit
        }
    }

    private fun appendMessage(message: Message) {
        data.update { d ->
            if (d.messages.any { it.id == message.id }) {
                d.copy(messages = d.messages.map { if (it.id == message.id) message else it })
            } else {
                d.copy(messages = d.messages + message)
            }
        }
        resolveReferences()
    }

    private fun replacePoll(poll: Poll) = data.update { d ->
        d.copy(messages = d.messages.map { m -> if (m.poll?.id == poll.id) m.copy(poll = poll) else m })
    }
    // endregion

    // region Receipts
    /** Called by the screen on resume / pause so "seen" is only sent while the chat is on screen. */
    fun onVisibilityChanged(isVisible: Boolean) {
        visible = isVisible
        if (isVisible) scheduleReceipts()
    }

    /** Marks everything up to the newest message from someone else as delivered, then seen. */
    private fun scheduleReceipts() {
        if (!visible) return
        val me = meId
        val newest = data.value.messages.lastOrNull { it.authorId != null && it.authorId != me } ?: return
        if (newest.id == lastSeenSent) return
        receiptJob?.cancel()
        receiptJob = viewModelScope.launch {
            delay(RECEIPT_DEBOUNCE_MS)
            chat.markReceipts(groupId, newest.id, ReceiptState.Delivered)
            if (chat.markReceipts(groupId, newest.id, ReceiptState.Seen) is ApiResult.Success) lastSeenSent = newest.id
        }
    }
    // endregion

    // region Composer
    fun onDraftChange(value: String) {
        draft = value.take(4000)
    }

    fun onToggleMenu() = data.update { it.copy(menuOpen = !it.menuOpen) }

    fun closeMenu() = data.update { it.copy(menuOpen = false) }

    fun send() {
        val text = draft.trim()
        if (text.isEmpty() || data.value.sending) return
        draft = ""
        data.update { it.copy(sending = true, menuOpen = false) }
        viewModelScope.launch {
            when (val result = chat.sendText(groupId, text)) {
                is ApiResult.Success -> {
                    appendMessage(result.value)
                    data.update { it.copy(sending = false) }
                }
                is ApiResult.Failure -> {
                    if (draft.isEmpty()) draft = text
                    data.update { it.copy(sending = false, message = UserMessage(result.error.message)) }
                }
            }
        }
    }
    // endregion

    // region Polls
    fun openPollSheet() = data.update { it.copy(pollDraft = PollDraft(), menuOpen = false) }
    fun closePollSheet() = data.update { it.copy(pollDraft = null) }
    fun onPollQuestion(value: String) = updatePoll { it.copy(question = value.take(200)) }
    fun onPollOption(index: Int, value: String) = updatePoll { p ->
        p.copy(options = p.options.mapIndexed { i, o -> if (i == index) value.take(100) else o })
    }
    fun onAddPollOption() = updatePoll { if (it.canAddOption) it.copy(options = it.options + "") else it }
    fun onRemovePollOption(index: Int) = updatePoll { p ->
        if (p.options.size <= 2) p else p.copy(options = p.options.filterIndexed { i, _ -> i != index })
    }
    fun onPollMulti(value: Boolean) = updatePoll { it.copy(multi = value) }

    fun submitPoll() {
        val draft = data.value.pollDraft ?: return
        if (!draft.canSubmit) return
        updatePoll { it.copy(sending = true) }
        viewModelScope.launch {
            val options = draft.options.map { it.trim() }.filter { it.isNotEmpty() }
            when (val result = chat.sendPoll(groupId, draft.question, options, draft.multi)) {
                is ApiResult.Success -> {
                    appendMessage(result.value)
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
        val me = meId ?: return
        val poll = data.value.messages.firstOrNull { it.id == messageId }?.poll ?: return
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
        replacePoll(optimistic)
        viewModelScope.launch {
            when (val result = chat.vote(poll.id, selection.toList())) {
                is ApiResult.Success -> replacePoll(result.value)
                is ApiResult.Failure -> {
                    replacePoll(poll)
                    data.update { it.copy(message = UserMessage(result.error.message)) }
                }
            }
        }
    }

    fun onClosePoll(messageId: String) {
        val poll = data.value.messages.firstOrNull { it.id == messageId }?.poll ?: return
        viewModelScope.launch {
            when (val result = chat.closePoll(poll.id)) {
                is ApiResult.Success -> replacePoll(result.value)
                is ApiResult.Failure -> data.update { it.copy(message = UserMessage(result.error.message)) }
            }
        }
    }
    // endregion

    // region Message info
    fun openInfo(messageId: String) {
        data.update { it.copy(infoSheet = InfoSheet(messageId), menuOpen = false) }
        viewModelScope.launch {
            val result = chat.info(messageId)
            data.update { d ->
                if (d.infoSheet?.messageId != messageId) return@update d
                when (result) {
                    is ApiResult.Success -> d.copy(infoSheet = InfoSheet(messageId, info = result.value))
                    is ApiResult.Failure -> d.copy(infoSheet = InfoSheet(messageId, error = result.error.message))
                }
            }
        }
    }

    fun closeInfo() = data.update { it.copy(infoSheet = null) }
    // endregion

    fun onMessageShown(message: UserMessage) = data.update { if (it.message?.id == message.id) it.copy(message = null) else it }

    private fun ItemInputs.toItems(me: String?): List<ChatItem> = buildChatItems(
        messages = messages,
        directory = MemberDirectory(group?.members.orEmpty(), me),
        memberCount = group?.memberCount ?: 0,
        proposals = proposals,
        events = events,
        isAdmin = group?.role?.isAdmin == true,
    ).asReversed()

    private fun ChatData.toUi(items: List<ChatItem>, me: String?): ChatUiState {
        val g = group
        val others = g?.members.orEmpty().filter { it.userId != me }.map { it.name.firstName() }
        return ChatUiState(
            groupName = g?.name.orEmpty(),
            monogram = g?.monogram.orEmpty(),
            tileColor = g?.color ?: TileColor.T1,
            membersSummary = others.joinNamesSummary(),
            items = items,
            loading = loading,
            loadingOlder = loadingOlder,
            hasMore = hasMore,
            error = error,
            menuOpen = menuOpen,
            sending = sending,
            pollDraft = pollDraft,
            info = infoSheet?.let { sheet ->
                sheet.toUi(messages.firstOrNull { it.id == sheet.messageId }, MemberDirectory(g?.members.orEmpty(), me))
            },
            message = message,
        )
    }

    private fun InfoSheet.toUi(message: Message?, directory: MemberDirectory): MessageInfoUi {
        fun person(userId: String, time: String) = InfoPerson(directory.avatar(userId), directory.displayName(userId), time)
        fun clock(iso: String) = DateTimes.localDateTime(iso)?.let(DateTimes::time).orEmpty()
        val text = message?.body ?: message?.poll?.question.orEmpty()
        return MessageInfoUi(
            text = text,
            sentLabel = "Sent " + sentAtLabel(info?.sentAt ?: message?.createdAt.orEmpty()),
            loading = info == null && error == null,
            seen = info?.seen.orEmpty().map { person(it.userId, clock(it.at)) },
            delivered = info?.delivered.orEmpty().map { person(it.userId, "Delivered ${clock(it.at)}") },
            pending = info?.pending.orEmpty().map { person(it, "Not delivered yet") },
            error = error,
        )
    }

    private companion object {
        const val RECEIPT_DEBOUNCE_MS = 600L
    }
}
