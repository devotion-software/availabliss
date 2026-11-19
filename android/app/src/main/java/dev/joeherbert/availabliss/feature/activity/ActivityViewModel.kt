package dev.joeherbert.availabliss.feature.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.joeherbert.availabliss.core.common.DateTimes
import dev.joeherbert.availabliss.core.common.UserMessage
import dev.joeherbert.availabliss.core.data.ChatRepository
import dev.joeherbert.availabliss.core.data.NotificationsRepository
import dev.joeherbert.availabliss.core.data.UserRepository
import dev.joeherbert.availabliss.core.deeplink.DeepLink
import dev.joeherbert.availabliss.core.live.LiveEvents
import dev.joeherbert.availabliss.core.live.LivePayload
import dev.joeherbert.availabliss.core.network.ApiResult
import dev.joeherbert.availabliss.core.network.model.AppNotification
import dev.joeherbert.availabliss.core.network.model.ChatSummary
import dev.joeherbert.availabliss.core.network.model.NotificationKind
import dev.joeherbert.availabliss.core.network.model.TileColor
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NotificationUi(
    val id: String,
    val kind: NotificationKind,
    val title: String,
    val body: String,
    /** "2h ago" */
    val time: String,
    val unread: Boolean,
    val link: DeepLink?,
    /** "3 new" on a group's unread chat item with more than one message. */
    val newCount: Int? = null,
    /** The group's colour behind the icon; null without a group. */
    val groupColor: TileColor? = null,
)

/** A chat in Activity's Chats tab. */
data class ChatRowUi(
    val groupId: String,
    val name: String,
    val monogram: String,
    val color: TileColor,
    val image: String?,
    /** "Mo: See you there", or "No messages yet. Say hello!" */
    val preview: String,
    /** "2h ago"; empty before any message. */
    val time: String,
    val unread: Int,
)

enum class ActivityTab { Chats, Updates }

data class ActivityUiState(
    val loading: Boolean = true,
    val tab: ActivityTab = ActivityTab.Chats,
    val chats: List<ChatRowUi> = emptyList(),
    val chatsLoaded: Boolean = false,
    val chatsError: String? = null,
    /** Everything but chats (they have their own tab). */
    val items: List<NotificationUi> = emptyList(),
    val unread: Int = 0,
    val error: String? = null,
    val message: UserMessage? = null,
)

@HiltViewModel
class ActivityViewModel @Inject constructor(
    private val notifications: NotificationsRepository,
    private val chat: ChatRepository,
    private val users: UserRepository,
    liveEvents: LiveEvents,
) : ViewModel() {

    private var chatsJob: Job? = null

    private var raw: List<AppNotification> = emptyList()

    private val _state = MutableStateFlow(ActivityUiState())
    val state: StateFlow<ActivityUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            liveEvents.payloads.filterIsInstance<LivePayload.NotificationCreated>().collect { event ->
                upsert(event.notification)
                notifications.refresh()
            }
        }
        // A message arrived, changed or was read: the chats list a moment later (one request for a burst).
        viewModelScope.launch {
            liveEvents.payloads.collect { payload ->
                if (payload is LivePayload.MessageCreated || payload is LivePayload.MessageUpdated || payload is LivePayload.NotificationCreated) {
                    chatsJob?.cancel()
                    chatsJob = launch {
                        delay(CHATS_REFRESH_MS)
                        loadChats()
                    }
                }
            }
        }
        viewModelScope.launch {
            liveEvents.reconnected.collect {
                refresh()
                loadChats()
            }
        }
        viewModelScope.launch {
            notifications.unread.collect { count -> _state.update { it.copy(unread = count) } }
        }
    }

    /**
     * Each time Activity shows: load it (catching up, e.g. an invite opened from here was joined or
     * declined), then mark everything but chats read, as iOS and web do. The items keep their unread
     * look until you next come back, so you can still see what's new.
     */
    fun resumed() {
        viewModelScope.launch { loadChats() }
        viewModelScope.launch {
            if (load()) markSeen()
        }
    }

    fun showTab(tab: ActivityTab) = _state.update { it.copy(tab = tab) }

    fun retryChats() {
        viewModelScope.launch { loadChats() }
    }

    private suspend fun loadChats() {
        when (val result = chat.chats()) {
            is ApiResult.Success -> {
                val me = users.user.value?.id
                _state.update { s -> s.copy(chats = result.value.chats.map { it.toUi(me) }, chatsLoaded = true, chatsError = null) }
            }
            is ApiResult.Failure -> _state.update { s -> s.copy(chatsLoaded = true, chatsError = if (s.chats.isEmpty()) result.error.message else null) }
        }
    }

    private fun ChatSummary.toUi(me: String?) = ChatRowUi(
        groupId = group.id,
        name = group.name,
        monogram = group.monogram,
        color = group.color,
        image = group.image,
        preview = lastMessage?.preview(me) ?: "No messages yet. Say hello!",
        time = lastMessage?.let { DateTimes.relative(it.createdAt) }.orEmpty(),
        unread = unreadCount,
    )

    fun refresh() {
        viewModelScope.launch { load() }
    }

    private suspend fun load(): Boolean = when (val result = notifications.refresh()) {
        is ApiResult.Success -> {
            raw = result.value.notifications.sortedByDescending { it.createdAt }
            _state.update { it.copy(loading = false, error = null) }
            publish()
            true
        }
        is ApiResult.Failure -> {
            _state.update { it.copy(loading = false, error = result.error.message) }
            false
        }
    }

    /** Everything but chats, which stay unread until the chat is read. */
    private suspend fun markSeen() {
        if (raw.none { it.readAt == null && it.kind != NotificationKind.Message }) return
        if (notifications.markRead(null) is ApiResult.Success) notifications.refresh()
    }

    /** Marks [item] read and returns where it should open. */
    fun open(item: NotificationUi): DeepLink? {
        if (item.unread) {
            raw = raw.map { if (it.id == item.id) it.copy(readAt = it.readAt ?: DateTimes.toIso(java.time.Instant.now())) else it }
            publish()
            viewModelScope.launch { notifications.markRead(listOf(item.id)) }
        }
        return item.link
    }

    /**
     * A new item, or an update to one we have (a group's chat item gets a new message, or is
     * read): replaced in place, or moved to the top when newer.
     */
    private fun upsert(notification: AppNotification) {
        val prev = raw.firstOrNull { it.id == notification.id }
        raw = when {
            prev == null -> listOf(notification) + raw
            notification.createdAt > prev.createdAt -> listOf(notification) + raw.filter { it.id != notification.id }
            else -> raw.map { if (it.id == notification.id) notification else it }
        }
        publish()
    }

    private companion object {
        const val CHATS_REFRESH_MS = 400L
    }

    fun messageShown(message: UserMessage) = _state.update { if (it.message?.id == message.id) it.copy(message = null) else it }

    private fun publish() {
        _state.update { s -> s.copy(items = raw.filter { it.kind != NotificationKind.Message }.map { it.toUi() }) }
    }

    private fun AppNotification.toUi() = NotificationUi(
        id = id,
        kind = kind,
        title = title,
        body = body,
        time = DateTimes.relative(createdAt),
        unread = readAt == null,
        groupColor = TileColor.entries.firstOrNull { it.name.equals(groupColor, ignoreCase = true) },
        newCount = data["count"]?.toIntOrNull()?.takeIf { kind == NotificationKind.Message && readAt == null && it > 1 },
        link = DeepLink.fromPushData(
            buildMap {
                putAll(data)
                if (kind != NotificationKind.Unknown) put("kind", kind.wire)
                groupId?.let { put("groupId", it) }
            },
        ),
    )
}
