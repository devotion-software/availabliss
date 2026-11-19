package dev.joeherbert.availabliss.feature.chat

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.joeherbert.availabliss.core.calendar.CalendarEventDraft
import dev.joeherbert.availabliss.core.common.UserMessage
import dev.joeherbert.availabliss.core.data.AttachmentFiles
import dev.joeherbert.availabliss.core.data.ChatRepository
import dev.joeherbert.availabliss.core.data.EventsRepository
import dev.joeherbert.availabliss.core.data.FoundPlace
import dev.joeherbert.availabliss.core.data.GroupsRepository
import dev.joeherbert.availabliss.core.data.PlaceSearch
import dev.joeherbert.availabliss.core.data.ProposalsRepository
import dev.joeherbert.availabliss.core.data.UserRepository
import dev.joeherbert.availabliss.core.live.LiveEvents
import dev.joeherbert.availabliss.core.network.ApiResult
import dev.joeherbert.availabliss.core.network.model.Attachment
import dev.joeherbert.availabliss.core.network.model.MessageLocation
import dev.joeherbert.availabliss.core.share.ShareInbox
import dev.joeherbert.availabliss.feature.common.AddToCalendarDelegate
import dev.joeherbert.availabliss.feature.proposal.ProposalPrefill
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * A group's chat. Loading pages and the screen's state live here; the rest is split by job into
 * collaborators sharing one [ChatSession]: [ChatComposer] (sending, replying, editing),
 * [ChatAttachments], [ChatPolls], [ChatThreads], [ChatLocation], [ChatReceipts],
 * [ChatLiveUpdates] and [ChatReferences]. The UI-state types are in ChatUiModels.kt, the
 * internal state in ChatData.kt and the mapping between them in ChatUiMapping.kt.
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val chat: ChatRepository,
    private val groups: GroupsRepository,
    proposals: ProposalsRepository,
    events: EventsRepository,
    users: UserRepository,
    private val addToCalendar: AddToCalendarDelegate,
    files: AttachmentFiles,
    places: PlaceSearch,
    liveEvents: LiveEvents,
    shareInbox: ShareInbox,
) : ViewModel() {

    private val groupId = savedStateHandle.get<String>("groupId").orEmpty()
    private val data = MutableStateFlow(ChatData(group = groups.cachedGroup(groupId)))

    private val references = ChatReferences(groupId, data, viewModelScope, proposals, events)
    private val session = ChatSession(groupId, data, viewModelScope, chat, groups, users, references)
    private val receipts = ChatReceipts(session)
    private val composer = ChatComposer(session)
    private val attachments = ChatAttachments(session, files)
    private val polls = ChatPolls(session)
    private val reactions = ChatReactions(session)
    private val threads = ChatThreads(session)
    private val location = ChatLocation(session, places)
    private val search = ChatSearch(session, threads)
    private val media = ChatMedia(session, threads, attachments)
    private val live = ChatLiveUpdates(session, groups, receipts, composer)

    private val meIds = users.user.map { it?.id }.distinctUntilChanged()

    /**
     * The message list (newest first), rebuilt off the main thread only when messages,
     * referenced proposals / events, the group or the signed-in user change; not when
     * the menu, a sheet or the sending flag does.
     */
    private val items = combine(
        // A short debounce turns the burst of card fetches when a chat opens into one rebuild.
        data.map { ItemInputs(it.group, it.messages, it.proposals, it.events, it.conflicts) }.distinctUntilChanged().debounce(24),
        meIds,
    ) { inputs, me -> inputs.toItems(me) }.flowOn(Dispatchers.Default)

    // Built off the main thread too: it runs on every change (menu, sheets, upload progress).
    val state: StateFlow<ChatUiState> = combine(data, items, meIds, addToCalendar.state) { d, items, me, cal -> d.toUi(items, me, cal) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState())

    /**
     * The composer text. Plain Compose state, updated synchronously, rather than part of
     * [state]: a keystroke neither goes through an async flow (which drops characters and
     * moves the caret) nor rebuilds the message list.
     */
    val draft: String get() = composer.draft

    /** The thread sheet's composer text, kept apart from the chat's. */
    val threadDraft: String get() = threads.threadDraft

    init {
        load()
        // Shared from another app and sent here from "Send to…": attached, ready to send.
        shareInbox.takeFor(groupId)?.let { shared ->
            if (shared.uris.isNotEmpty()) onPicked(shared.uris)
            shared.text?.takeIf { it.isNotBlank() }?.let(::onDraftChange)
        }
        viewModelScope.launch { addToCalendar.loadCalendars() }
        viewModelScope.launch {
            liveEvents.payloadsFor(groupId).collect { live.onLive(it) }
        }
        viewModelScope.launch { liveEvents.reconnected.collect { catchUp() } }
    }

    // region Loading
    fun load() {
        viewModelScope.launch {
            data.update { it.copy(loading = true, error = null) }
            if (data.value.group == null) session.refreshGroup()
            when (val result = chat.page(groupId)) {
                is ApiResult.Success -> {
                    data.update { it.copy(messages = result.value.messages, hasMore = result.value.hasMore, loading = false) }
                    references.resolve()
                    receipts.schedule()
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
                    references.resolve()
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
            references.forget(page.messages)
            references.resolve()
            receipts.schedule()
            session.refreshGroup()
        }
    }
    // endregion

    // region Receipts
    /** Called by the screen on resume / pause so "seen" is only sent while the chat is on screen. */
    fun onVisibilityChanged(isVisible: Boolean) = receipts.onVisibilityChanged(isVisible)
    // endregion

    // region Composer
    fun onDraftChange(value: String) = composer.onDraftChange(value)

    fun onToggleMenu() = data.update { it.copy(menuOpen = !it.menuOpen) }

    fun closeMenu() = data.update { it.copy(menuOpen = false) }

    fun send() = composer.send()

    /** Replies to [messageId] with the next message (swipe right, or Reply in its menu). */
    fun startReply(messageId: String) = composer.startReply(messageId)

    fun cancelReply() = composer.cancelReply()

    /** Puts the composer into edit mode with the message's text; the current draft comes back afterwards. */
    fun startEdit(messageId: String) = composer.startEdit(messageId)

    /** Leaves edit mode, restoring whatever draft was there before. */
    fun cancelEdit() = composer.cancelEdit()

    fun requestDelete(messageId: String) = composer.requestDelete(messageId)

    fun dismissDelete() = composer.dismissDelete()

    fun confirmDelete() = composer.confirmDelete()
    // endregion

    // region Attachments
    /** Photos or files picked to send: each starts uploading straight away. */
    fun onPicked(uris: List<Uri>) = attachments.onPicked(uris)

    fun removeAttachment(localId: String) = attachments.removeAttachment(localId)

    fun retryAttachment(localId: String) = attachments.retryAttachment(localId)

    /** Taps on a photo (opens the viewer over the message's photos) or a file (downloads and opens it). */
    fun openAttachment(messageId: String, attachmentId: String) = attachments.openAttachment(messageId, attachmentId)

    fun closeViewer() = attachments.closeViewer()

    fun shareAttachment(attachment: Attachment) = attachments.shareAttachment(attachment)

    fun saveAttachment(attachment: Attachment) = attachments.saveAttachment(attachment)
    // endregion

    // region Location
    fun openMedia() = media.open()
    fun closeMedia() = media.close()
    fun showMediaKind(kind: MediaKind) = media.showKind(kind)
    fun loadMoreMedia() = media.loadMore()
    fun viewMediaPhoto(index: Int) = media.viewPhoto(index)
    fun openMediaFile(attachment: Attachment) = media.openFile(attachment)
    fun showMediaInChat(messageId: String) = media.showInChat(messageId)

    fun openSearch() = search.open()
    fun closeSearch() = search.close()
    fun onSearchQuery(value: String) = search.onQuery(value)
    fun pickSearchResult(messageId: String) = search.pick(messageId)

    fun openLocationSheet() = location.openLocationSheet()
    fun closeLocationSheet() = location.closeLocationSheet()
    fun onPlaceQuery(value: String) = location.onPlaceQuery(value)
    fun clearPlaceSearch() = location.clearPlaceSearch()

    /** Searches for [LocationSheetUi.query], nearer [near] (lat, lng) first. */
    fun searchPlaces(near: Pair<Double, Double>?) = location.searchPlaces(near)

    fun pickPlace(place: FoundPlace) = location.pickPlace(place)

    /** Shares [location] (as a reply, if one is being written), then closes the sheet. */
    fun sendLocation(location: MessageLocation) = this.location.sendLocation(location)
    // endregion

    // region Polls
    fun openPollSheet() = polls.openPollSheet()
    fun closePollSheet() = polls.closePollSheet()
    fun onPollQuestion(value: String) = polls.onPollQuestion(value)
    fun onPollOption(index: Int, value: String) = polls.onPollOption(index, value)
    fun onAddPollOption() = polls.onAddPollOption()
    fun onRemovePollOption(index: Int) = polls.onRemovePollOption(index)
    fun onPollMulti(value: Boolean) = polls.onPollMulti(value)
    fun submitPoll() = polls.submitPoll()

    /** Tap an option: toggles your vote (single-choice polls move it). Optimistic, then reconciled. */
    fun onVote(messageId: String, optionId: String) = polls.onVote(messageId, optionId)

    /** "Propose this" on a date poll's option: its time for the propose form, or null if it's gone or has no time. */
    fun proposalFor(messageId: String, optionId: String): ProposalPrefill? = polls.proposalFor(messageId, optionId)

    fun onClosePoll(messageId: String) = polls.onClosePoll(messageId)
    fun onEditPoll(messageId: String) = polls.openEditPoll(messageId)
    // endregion

    // region Reactions
    /** Reacts with [emoji], or takes it back if it's already yours (a double tap is ❤️). */
    fun react(messageId: String, emoji: String) = reactions.react(messageId, emoji)
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

    // region Thread
    /** Opens the thread [messageId] is part of (fetched, since it may reach past what's loaded). */
    fun openThread(messageId: String) = threads.openThread(messageId)

    fun closeThread() = threads.closeThread()

    fun onThreadDraftChange(value: String) = threads.onThreadDraftChange(value)

    /** Your next reply in the thread goes to [messageId]; null goes back to the latest. */
    fun replyInThread(messageId: String?) = threads.replyInThread(messageId)

    fun openThreadActions(messageId: String) = threads.openThreadActions(messageId)

    fun closeThreadActions() = threads.closeThreadActions()

    fun sendInThread() = threads.sendInThread()

    /** Closes the thread and goes to [messageId] in the chat, paging back to it if needed. */
    fun showInChat(messageId: String) = threads.showInChat(messageId)

    fun onShowInChatHandled() = threads.onShowInChatHandled()
    // endregion

    // region Add to calendar
    fun onAddToCalendar(eventId: String) {
        val event = data.value.events[eventId] ?: return
        val draft = CalendarEventDraft.from(event) ?: return
        viewModelScope.launch { addToCalendar.add(eventId, draft) }
    }

    fun onCalendarIntentHandled() = addToCalendar.onIntentHandled()

    fun chooseCalendar(id: Long) {
        viewModelScope.launch { addToCalendar.choose(id) }
    }

    fun cancelCalendarChoice() = addToCalendar.cancelChoice()
    // endregion

    fun onMessageShown(message: UserMessage) = data.update { if (it.message?.id == message.id) it.copy(message = null) else it }
}
