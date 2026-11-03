package dev.joeherbert.availabliss.feature.calendars

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.joeherbert.availabliss.core.calendar.CalendarChangeObserver
import dev.joeherbert.availabliss.core.calendar.CalendarSyncScheduler
import dev.joeherbert.availabliss.core.calendar.DeviceCalendarInfo
import dev.joeherbert.availabliss.core.calendar.DeviceCalendarReader
import dev.joeherbert.availabliss.core.calendar.DeviceCalendarSync
import dev.joeherbert.availabliss.core.calendar.DeviceSyncResult
import dev.joeherbert.availabliss.core.common.UserMessage
import dev.joeherbert.availabliss.core.data.AppPreferences
import dev.joeherbert.availabliss.core.data.CalendarsRepository
import dev.joeherbert.availabliss.core.deeplink.DeepLink
import dev.joeherbert.availabliss.core.deeplink.DeepLinkBus
import dev.joeherbert.availabliss.core.network.ApiResult
import dev.joeherbert.availabliss.core.network.model.CalendarConnection
import dev.joeherbert.availabliss.core.network.model.CalendarProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A device calendar row. */
data class DeviceCalendarRow(val externalId: String, val name: String, val color: String?, val busy: Boolean)

/** Device calendars from one account ("Google · sam@gmail.com"). */
data class DeviceSource(val title: String, val account: String, val calendars: List<DeviceCalendarRow>)

/** Which page of the connect sheet is showing. */
enum class ConnectSheet { Choose, ICloud }

data class CalDavForm(
    val username: String = "",
    val appPassword: String = "",
    val submitting: Boolean = false,
    val error: String? = null,
) {
    val canSubmit: Boolean get() = username.isNotBlank() && appPassword.isNotBlank() && !submitting
}

data class CalendarSourcesUiState(
    val loading: Boolean = true,
    val hasPermission: Boolean = false,
    /**
     * The system won't show the READ_CALENDAR prompt again (denied twice, or "Don't
     * ask again"), so the request has to go through app settings instead.
     */
    val permissionBlocked: Boolean = false,
    val deviceSources: List<DeviceSource> = emptyList(),
    /** Google / Microsoft / CalDAV connections (this phone's own upload is shown as [deviceSources]). */
    val connections: List<CalendarConnection> = emptyList(),
    val lastDeviceSyncAt: Long? = null,
    val syncingDevice: Boolean = false,
    /** Connection ids with a request in flight. */
    val busyConnections: Set<String> = emptySet(),
    val sheet: ConnectSheet? = null,
    val calDav: CalDavForm = CalDavForm(),
    /** OAuth start URL for the UI to open in a Custom Tab. */
    val pendingUrl: String? = null,
    val message: UserMessage? = null,
) {
    val hasAnySource: Boolean get() = deviceSources.any { s -> s.calendars.any { it.busy } } || connections.isNotEmpty()
}

/**
 * Busy-calendar selection shared by onboarding step 2 and the Calendars settings
 * screen: device calendars (READ_CALENDAR, uploaded as busy ranges only) and server
 * connections (Google / Microsoft via OAuth in a Custom Tab, iCloud via CalDAV).
 */
@HiltViewModel
class CalendarSourcesViewModel @Inject constructor(
    private val reader: DeviceCalendarReader,
    private val deviceSync: DeviceCalendarSync,
    private val scheduler: CalendarSyncScheduler,
    private val observer: CalendarChangeObserver,
    private val calendars: CalendarsRepository,
    private val prefs: AppPreferences,
    private val deepLinks: DeepLinkBus,
) : ViewModel() {

    private val _state = MutableStateFlow(CalendarSourcesUiState())
    val state: StateFlow<CalendarSourcesUiState> = _state.asStateFlow()

    private var deviceCalendars: List<DeviceCalendarInfo> = emptyList()
    private var refreshJob: Job? = null

    init {
        viewModelScope.launch {
            calendars.connections.collect { list ->
                _state.update { it.copy(connections = list.filter { c -> c.provider != CalendarProvider.Device }) }
            }
        }
        viewModelScope.launch {
            prefs.lastDeviceSyncAt.collect { at -> _state.update { it.copy(lastDeviceSyncAt = at) } }
        }
        // OAuth return: availabliss://calendars?complete=<code>&provider=<provider> or ?error=
        viewModelScope.launch {
            deepLinks.pending.collect { link ->
                if (link is DeepLink.Calendars) {
                    deepLinks.consume(link)
                    val text = when {
                        link.error != null -> "Couldn't connect that calendar (${link.error})."
                        link.complete != null && link.provider != null ->
                            when (val result = calendars.completeOAuth(link.provider, link.complete)) {
                                is ApiResult.Success -> "Calendar connected."
                                is ApiResult.Failure -> result.error.message
                            }
                        else -> null
                    }
                    _state.update { it.copy(sheet = null, message = text?.let(::UserMessage) ?: it.message) }
                    refresh(force = true)
                }
            }
        }
        refresh()
    }

    /**
     * Reloads device calendars and server connections. Call on resume too. A call
     * while a refresh is already running is dropped, so the init load and the first
     * ON_RESUME don't query the device and `GET /calendars` twice; [force] restarts it
     * instead, for when something has changed since it began.
     */
    fun refresh(force: Boolean = false) {
        if (refreshJob?.isActive == true) {
            if (!force) return
            refreshJob?.cancel()
        }
        refreshJob = viewModelScope.launch {
            loadDevice()
            when (val result = calendars.refresh()) {
                is ApiResult.Success -> Unit
                is ApiResult.Failure -> _state.update { it.copy(message = UserMessage(result.error.message)) }
            }
            _state.update { it.copy(loading = false) }
        }
    }

    /** [canAskAgain] is false when the system will deny further requests without asking. */
    fun onPermissionResult(granted: Boolean, canAskAgain: Boolean = true) {
        if (!granted) {
            val text = if (canAskAgain) {
                "Calendar access is off. You can still connect a calendar or mark busy times yourself."
            } else {
                "Calendar access is off. Turn it on in Settings › Permissions › Calendar, or connect a calendar instead."
            }
            _state.update { it.copy(hasPermission = false, permissionBlocked = !canAskAgain, message = UserMessage(text)) }
            // Removes what this phone uploaded before, if anything (see DeviceCalendarSync).
            scheduler.syncSoon()
            return
        }
        observer.ensureRegistered()
        scheduler.schedulePeriodic()
        viewModelScope.launch {
            loadDevice()
            syncDeviceNow(quiet = true)
        }
    }

    fun toggleDeviceCalendar(externalId: String, busy: Boolean) {
        viewModelScope.launch {
            val current = deviceSync.busyCalendarIds(deviceCalendars)
            val next = if (busy) current + externalId else current - externalId
            deviceSync.setBusyCalendarIds(next)
            _state.update { s -> s.copy(deviceSources = s.deviceSources.withBusy(externalId, busy)) }
            scheduler.syncSoon(delaySeconds = 2)
        }
    }

    fun toggleConnectionCalendar(connectionId: String, calendarId: String, busy: Boolean) {
        withConnectionBusy(connectionId) { calendars.setBusy(connectionId, calendarId, busy) }
    }

    fun syncConnection(connectionId: String) {
        withConnectionBusy(connectionId) { calendars.sync(connectionId) }
    }

    fun disconnect(connectionId: String) {
        withConnectionBusy(connectionId) { calendars.disconnect(connectionId) }
    }

    fun syncDeviceNow(quiet: Boolean = false) {
        if (_state.value.syncingDevice) return
        viewModelScope.launch {
            _state.update { it.copy(syncingDevice = true) }
            // A tap on Sync always uploads; the quiet sync after granting access skips an unchanged one.
            val result = deviceSync.sync(force = !quiet)
            val text = when (result) {
                is DeviceSyncResult.Synced, DeviceSyncResult.Unchanged -> if (quiet) null else "Synced this phone's busy times."
                DeviceSyncResult.Skipped, DeviceSyncResult.Cleared -> null
                is DeviceSyncResult.Failed -> result.error.message
            }
            _state.update { it.copy(syncingDevice = false, message = text?.let(::UserMessage) ?: it.message) }
        }
    }

    // region Connect sheet
    fun openConnectSheet() = _state.update { it.copy(sheet = ConnectSheet.Choose) }

    fun closeConnectSheet() = _state.update { it.copy(sheet = null, calDav = CalDavForm()) }

    fun chooseICloud() = _state.update { it.copy(sheet = ConnectSheet.ICloud) }

    fun backToProviders() = _state.update { it.copy(sheet = ConnectSheet.Choose) }

    /** [provider] is `google` or `microsoft`. */
    fun connectProvider(provider: String) {
        viewModelScope.launch {
            when (val result = calendars.oauthUrl(provider)) {
                is ApiResult.Success -> _state.update { it.copy(pendingUrl = result.value, sheet = null) }
                is ApiResult.Failure -> _state.update { it.copy(message = UserMessage(result.error.message)) }
            }
        }
    }

    fun onUrlOpened() = _state.update { it.copy(pendingUrl = null) }

    fun onCalDavUsername(value: String) = _state.update { it.copy(calDav = it.calDav.copy(username = value, error = null)) }

    fun onCalDavPassword(value: String) = _state.update { it.copy(calDav = it.calDav.copy(appPassword = value, error = null)) }

    fun submitCalDav() {
        val form = _state.value.calDav
        if (!form.canSubmit) return
        viewModelScope.launch {
            _state.update { it.copy(calDav = form.copy(submitting = true, error = null)) }
            when (val result = calendars.connectCalDav(form.username.trim(), form.appPassword.trim(), null)) {
                is ApiResult.Success -> _state.update {
                    it.copy(sheet = null, calDav = CalDavForm(), message = UserMessage("iCloud connected."))
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(calDav = it.calDav.copy(submitting = false, error = result.error.message))
                }
            }
        }
    }
    // endregion

    fun onMessageShown(message: UserMessage) =
        _state.update { if (it.message?.id == message.id) it.copy(message = null) else it }

    private suspend fun loadDevice() {
        val granted = reader.hasReadPermission()
        deviceCalendars = if (granted) reader.calendars() else emptyList()
        val busyIds = deviceSync.busyCalendarIds(deviceCalendars)
        val sources = deviceCalendars
            .groupBy { it.accountName to it.accountType }
            .map { (key, cals) ->
                DeviceSource(
                    title = accountTitle(key.second),
                    account = key.first,
                    calendars = cals.map { DeviceCalendarRow(it.externalId, it.name, it.color, it.externalId in busyIds) },
                )
            }
        _state.update { it.copy(hasPermission = granted, permissionBlocked = it.permissionBlocked && !granted, deviceSources = sources) }
    }

    private fun withConnectionBusy(connectionId: String, block: suspend () -> ApiResult<*>) {
        if (connectionId in _state.value.busyConnections) return
        viewModelScope.launch {
            _state.update { it.copy(busyConnections = it.busyConnections + connectionId) }
            val result = block()
            _state.update {
                it.copy(
                    busyConnections = it.busyConnections - connectionId,
                    message = (result as? ApiResult.Failure)?.let { f -> UserMessage(f.error.message) } ?: it.message,
                )
            }
        }
    }

    private fun List<DeviceSource>.withBusy(externalId: String, busy: Boolean) = map { source ->
        source.copy(calendars = source.calendars.map { if (it.externalId == externalId) it.copy(busy = busy) else it })
    }

    companion object {
        /** Friendly label for a CalendarContract account type. */
        fun accountTitle(accountType: String): String = when {
            accountType == "com.google" -> "Google"
            accountType.contains("exchange", ignoreCase = true) || accountType.contains("outlook", ignoreCase = true) ||
                accountType.contains("microsoft", ignoreCase = true) -> "Outlook"
            accountType.contains("samsung", ignoreCase = true) -> "Samsung"
            accountType.contains("caldav", ignoreCase = true) -> "CalDAV"
            accountType == "com.android.contacts" -> "Contacts"
            else -> "On this phone"
        }
    }
}
