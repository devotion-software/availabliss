package dev.joeherbert.availabliss.core.data

import dev.joeherbert.availabliss.core.network.ApiResult
import dev.joeherbert.availabliss.core.network.AvailablissApi
import dev.joeherbert.availabliss.core.network.model.NotificationsResponse
import dev.joeherbert.availabliss.core.network.model.ReadNotificationsRequest
import dev.joeherbert.availabliss.core.network.onSuccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** The Activity tab (`/notifications`). Exposes the unread count for the tab badge. */
@Singleton
class NotificationsRepository @Inject constructor(
    private val api: AvailablissApi,
    private val call: ApiCaller,
) {
    private val _unread = MutableStateFlow(0)
    val unread: StateFlow<Int> = _unread.asStateFlow()

    suspend fun refresh(): ApiResult<NotificationsResponse> =
        call { api.getNotifications() }.onSuccess { _unread.value = it.unread }

    /** Marks [ids] read, or everything when null. */
    suspend fun markRead(ids: List<String>? = null): ApiResult<Unit> =
        call { api.markNotificationsRead(ReadNotificationsRequest(ids)) }.onSuccess {
            _unread.value = if (ids == null) 0 else (_unread.value - ids.size).coerceAtLeast(0)
        }

    fun clear() {
        _unread.value = 0
    }
}
