package dev.joeherbert.availabliss.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class CalendarProvider {
    @SerialName("google") Google,
    @SerialName("microsoft") Microsoft,
    @SerialName("caldav") CalDav,
    @SerialName("device") Device,
}

@Serializable
enum class ConnectionStatus {
    @SerialName("ok") Ok,
    @SerialName("error") Error,
    @SerialName("reauth_required") ReauthRequired,
}

@Serializable
data class CalendarConnection(
    val id: String,
    val provider: CalendarProvider,
    val label: String,
    val status: ConnectionStatus = ConnectionStatus.Ok,
    val lastSyncedAt: String? = null,
    val calendars: List<ConnectedCalendar> = emptyList(),
)

@Serializable
data class ConnectedCalendar(
    val id: String,
    val name: String,
    val color: String? = null,
    val busy: Boolean,
)

@Serializable
data class ConnectionsResponse(val connections: List<CalendarConnection>)

@Serializable
data class CalDavRequest(
    val username: String,
    val appPassword: String,
    val serverUrl: String? = null,
)

/** `POST /calendars/oauth/:provider/url` body / response. */
@Serializable
data class OAuthUrlRequest(val redirect: String? = null)

@Serializable
data class OAuthUrlResponse(val url: String)

@Serializable
data class CalendarBusyRequest(val busy: Boolean)

/** Native upload of on-device busy time. Titles never leave the device. */
@Serializable
data class DeviceSync(
    val deviceId: String,
    val deviceName: String,
    val rangeStart: String,
    val rangeEnd: String,
    val calendars: List<DeviceCalendar>,
    val busy: List<DeviceBusy>,
)

@Serializable
data class DeviceCalendar(
    val externalId: String,
    val name: String,
    val color: String?,
    val busy: Boolean? = null,
)

@Serializable
data class DeviceBusy(val calendarExternalId: String, val start: String, val end: String)
