package dev.joeherbert.availabliss.feature.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.joeherbert.availabliss.core.common.DateTimes
import dev.joeherbert.availabliss.core.common.UserMessage
import dev.joeherbert.availabliss.core.data.NotificationsRepository
import dev.joeherbert.availabliss.core.deeplink.DeepLink
import dev.joeherbert.availabliss.core.live.LiveEvents
import dev.joeherbert.availabliss.core.live.LivePayload
import dev.joeherbert.availabliss.core.network.ApiResult
import dev.joeherbert.availabliss.core.network.model.AppNotification
import dev.joeherbert.availabliss.core.network.model.NotificationKind
import dev.joeherbert.availabliss.core.network.model.TileColor
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

data class ActivityUiState(
    val loading: Boolean = true,
    val items: List<NotificationUi> = emptyList(),
    val unread: Int = 0,
    val error: String? = null,
    val message: UserMessage? = null,
)

@HiltViewModel
class ActivityViewModel @Inject constructor(
    private val notifications: NotificationsRepository,
    liveEvents: LiveEvents,
) : ViewModel() {

    private var raw: List<AppNotification> = emptyList()

    private val _state = MutableStateFlow(ActivityUiState())
    val state: StateFlow<ActivityUiState> = _state.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            liveEvents.payloads.filterIsInstance<LivePayload.NotificationCreated>().collect { event ->
                upsert(event.notification)
                notifications.refresh()
            }
        }
        viewModelScope.launch { liveEvents.reconnected.collect { refresh() } }
        viewModelScope.launch {
            notifications.unread.collect { count -> _state.update { it.copy(unread = count) } }
        }
    }

    private var resumedBefore = false

    /**
     * Back on Activity (after the first time): catch up, e.g. an invite opened from here was joined or
     * declined (a declined one's item goes).
     */
    fun resumed() {
        if (resumedBefore) refresh() else resumedBefore = true
    }

    fun refresh() {
        viewModelScope.launch {
            when (val result = notifications.refresh()) {
                is ApiResult.Success -> {
                    raw = result.value.notifications.sortedByDescending { it.createdAt }
                    _state.update { it.copy(loading = false, error = null) }
                    publish()
                }
                is ApiResult.Failure -> _state.update { it.copy(loading = false, error = result.error.message) }
            }
        }
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

    /** Everything but chats, which stay unread until the chat is read. */
    fun markAllRead() {
        if (raw.none { it.readAt == null && it.kind != NotificationKind.Message }) return
        val now = DateTimes.toIso(java.time.Instant.now())
        val before = raw
        raw = raw.map { if (it.kind == NotificationKind.Message) it else it.copy(readAt = it.readAt ?: now) }
        publish()
        viewModelScope.launch {
            val result = notifications.markRead(null)
            notifications.refresh()
            if (result is ApiResult.Failure) {
                raw = before
                publish()
                _state.update { it.copy(message = UserMessage(result.error.message)) }
            }
        }
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

    fun messageShown(message: UserMessage) = _state.update { if (it.message?.id == message.id) it.copy(message = null) else it }

    private fun publish() {
        _state.update { s -> s.copy(items = raw.map { it.toUi() }) }
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
