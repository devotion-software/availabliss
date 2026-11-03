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
                if (raw.none { it.id == event.notification.id }) {
                    raw = listOf(event.notification) + raw
                    publish()
                }
                notifications.refresh()
            }
        }
        viewModelScope.launch { liveEvents.reconnected.collect { refresh() } }
        viewModelScope.launch {
            notifications.unread.collect { count -> _state.update { it.copy(unread = count) } }
        }
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

    fun markAllRead() {
        if (raw.none { it.readAt == null }) return
        val now = DateTimes.toIso(java.time.Instant.now())
        val before = raw
        raw = raw.map { it.copy(readAt = it.readAt ?: now) }
        publish()
        viewModelScope.launch {
            val result = notifications.markRead(null)
            if (result is ApiResult.Failure) {
                raw = before
                publish()
                _state.update { it.copy(message = UserMessage(result.error.message)) }
            }
        }
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
        link = DeepLink.fromPushData(
            buildMap {
                putAll(data)
                if (kind != NotificationKind.Unknown) put("kind", kind.wire)
                groupId?.let { put("groupId", it) }
            },
        ),
    )
}

