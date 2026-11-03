package dev.joeherbert.availabliss.core.data

import dev.joeherbert.availabliss.core.network.ApiResult
import dev.joeherbert.availabliss.core.network.AvailablissApi
import dev.joeherbert.availabliss.core.network.map
import dev.joeherbert.availabliss.core.network.model.CalDavRequest
import dev.joeherbert.availabliss.core.network.model.CalendarBusyRequest
import dev.joeherbert.availabliss.core.network.model.CalendarConnection
import dev.joeherbert.availabliss.core.network.model.DeviceSync
import dev.joeherbert.availabliss.core.network.model.OAuthCompleteRequest
import dev.joeherbert.availabliss.core.network.model.OAuthUrlRequest
import dev.joeherbert.availabliss.core.network.onSuccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/** Connected calendars (`/calendars`). */
@Singleton
class CalendarsRepository @Inject constructor(
    private val api: AvailablissApi,
    private val call: ApiCaller,
) {
    private val _connections = MutableStateFlow<List<CalendarConnection>>(emptyList())
    val connections: StateFlow<List<CalendarConnection>> = _connections.asStateFlow()

    suspend fun refresh(): ApiResult<List<CalendarConnection>> =
        call { api.getConnections() }.map { it.connections }.onSuccess { _connections.value = it }

    suspend fun connectCalDav(username: String, appPassword: String, serverUrl: String?): ApiResult<CalendarConnection> =
        call { api.connectCalDav(CalDavRequest(username, appPassword, serverUrl?.takeIf { it.isNotBlank() })) }
            .onSuccess(::upsert)

    suspend fun uploadDeviceBusy(sync: DeviceSync): ApiResult<CalendarConnection> =
        call { api.syncDevice(sync) }.onSuccess(::upsert)

    suspend fun setBusy(connectionId: String, calendarId: String, busy: Boolean): ApiResult<CalendarConnection> =
        call { api.setCalendarBusy(connectionId, calendarId, CalendarBusyRequest(busy)) }.onSuccess(::upsert)

    suspend fun sync(connectionId: String): ApiResult<CalendarConnection> =
        call { api.syncConnection(connectionId) }.onSuccess(::upsert)

    suspend fun disconnect(connectionId: String): ApiResult<Unit> =
        call { api.deleteConnection(connectionId) }.onSuccess {
            _connections.update { list -> list.filterNot { it.id == connectionId } }
        }

    /**
     * Google / Microsoft consent URL (`provider` = google | microsoft) with signed state,
     * fetched with the bearer token and then opened in a Custom Tab. The server redirects
     * back to `availabliss://calendars?complete=<code>&provider=<provider>` (or `?error=`);
     * finish with [completeOAuth].
     */
    suspend fun oauthUrl(provider: String): ApiResult<String> =
        call { api.calendarOAuthUrl(provider, OAuthUrlRequest(OAUTH_REDIRECT)) }.map { it.url }

    /** Links the calendar. Sent with our bearer token, so only the user who started the flow can finish it. */
    suspend fun completeOAuth(provider: String, code: String): ApiResult<CalendarConnection> =
        call { api.completeCalendarOAuth(provider, OAuthCompleteRequest(code)) }.onSuccess(::upsert)

    fun clear() {
        _connections.value = emptyList()
    }

    private fun upsert(connection: CalendarConnection) {
        _connections.update { list ->
            if (list.any { it.id == connection.id }) list.map { if (it.id == connection.id) connection else it }
            else list + connection
        }
    }

    companion object {
        const val OAUTH_REDIRECT = "availabliss://calendars"
    }
}
