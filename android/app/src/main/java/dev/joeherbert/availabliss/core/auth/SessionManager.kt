package dev.joeherbert.availabliss.core.auth

import dev.joeherbert.availabliss.core.calendar.CalendarChangeObserver
import dev.joeherbert.availabliss.core.calendar.CalendarSyncScheduler
import dev.joeherbert.availabliss.core.common.ApplicationScope
import dev.joeherbert.availabliss.core.data.AppPreferences
import dev.joeherbert.availabliss.core.data.CalendarsRepository
import dev.joeherbert.availabliss.core.data.GroupsRepository
import dev.joeherbert.availabliss.core.data.NotificationsRepository
import dev.joeherbert.availabliss.core.data.UserRepository
import dev.joeherbert.availabliss.core.live.LiveEvents
import dev.joeherbert.availabliss.core.network.ApiResult
import dev.joeherbert.availabliss.core.network.AuthInterceptor
import dev.joeherbert.availabliss.core.network.PasskeyCookieJar
import dev.joeherbert.availabliss.core.push.PushRegistrar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

enum class AuthState { SignedOut, SignedIn }

/**
 * Owns the session lifecycle: what happens after sign in (push registration, calendar
 * sync, live updates) and the cleanup on sign out or when the token is rejected.
 */
@Singleton
class SessionManager @Inject constructor(
    private val tokenStore: TokenStore,
    private val authRepository: AuthRepository,
    private val users: UserRepository,
    private val groups: GroupsRepository,
    private val calendars: CalendarsRepository,
    private val notifications: NotificationsRepository,
    private val prefs: AppPreferences,
    private val push: PushRegistrar,
    private val liveEvents: LiveEvents,
    private val syncScheduler: CalendarSyncScheduler,
    private val calendarObserver: CalendarChangeObserver,
    private val cookieJar: PasskeyCookieJar,
    authInterceptor: AuthInterceptor,
    @ApplicationScope private val scope: CoroutineScope,
) {
    val authState: StateFlow<AuthState> = tokenStore.tokenFlow
        .map { if (it == null) AuthState.SignedOut else AuthState.SignedIn }
        .stateIn(scope, SharingStarted.Eagerly, if (tokenStore.token() == null) AuthState.SignedOut else AuthState.SignedIn)

    init {
        scope.launch { authInterceptor.unauthorized.collect { clearLocalSession() } }
        // Runs on launch for a returning user and again whenever a sign-in completes.
        scope.launch { authState.collect { if (it == AuthState.SignedIn) onSignedIn() } }
    }

    /** Post sign-in work: refresh /me, register push, start live updates and calendar sync. */
    private fun onSignedIn() {
        if (tokenStore.token() == null) return
        scope.launch {
            users.restore()
            if (users.refresh() is ApiResult.Success) {
                push.register()
            }
        }
        liveEvents.start()
        calendarObserver.ensureRegistered()
        syncScheduler.schedulePeriodic()
        syncScheduler.syncSoon()
    }

    suspend fun signOut() {
        push.unregister()
        authRepository.signOutRemote()
        clearLocalSession()
    }

    private suspend fun clearLocalSession() {
        liveEvents.stop()
        syncScheduler.cancelAll()
        calendarObserver.unregister()
        tokenStore.clear()
        cookieJar.clear()
        users.clear()
        groups.clear()
        calendars.clear()
        notifications.clear()
        prefs.clearSession()
    }
}
