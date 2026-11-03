package dev.joeherbert.availabliss.core.auth

import dev.joeherbert.availabliss.core.billing.PlanRepository
import dev.joeherbert.availabliss.core.billing.StoreBilling
import dev.joeherbert.availabliss.core.calendar.CalendarChangeObserver
import dev.joeherbert.availabliss.core.calendar.CalendarSyncScheduler
import dev.joeherbert.availabliss.core.common.ApplicationScope
import dev.joeherbert.availabliss.core.data.AppPreferences
import dev.joeherbert.availabliss.core.data.CalendarsRepository
import dev.joeherbert.availabliss.core.data.GroupsRepository
import dev.joeherbert.availabliss.core.data.NotificationsRepository
import dev.joeherbert.availabliss.core.data.UserRepository
import dev.joeherbert.availabliss.core.live.LiveConnectionObserver
import dev.joeherbert.availabliss.core.live.LiveEvents
import dev.joeherbert.availabliss.core.network.ApiResult
import dev.joeherbert.availabliss.core.network.AuthInterceptor
import dev.joeherbert.availabliss.core.network.PasskeyCookieJar
import dev.joeherbert.availabliss.core.push.PushRegistrar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** [Loading] until [TokenStore] has read the stored token (off the main thread). */
enum class AuthState { Loading, SignedOut, SignedIn }

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
    private val appLifecycle: LiveConnectionObserver,
    private val syncScheduler: CalendarSyncScheduler,
    private val calendarObserver: CalendarChangeObserver,
    private val cookieJar: PasskeyCookieJar,
    private val plans: PlanRepository,
    private val billing: StoreBilling,
    authInterceptor: AuthInterceptor,
    @ApplicationScope private val scope: CoroutineScope,
) {
    val authState: StateFlow<AuthState> = tokenStore.state
        .map { state ->
            when (state) {
                TokenState.Loading -> AuthState.Loading
                is TokenState.Ready -> if (state.token == null) AuthState.SignedOut else AuthState.SignedIn
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, AuthState.Loading)

    private val clearMutex = Mutex()

    /** The token whose foreground work has already run in this process (see [onForegroundSession]). */
    private var foregroundWorkFor: String? = null

    init {
        scope.launch { authInterceptor.unauthorized.collect { token -> clearLocalSession(ifToken = token) } }
        // Runs on every process start for a returning user (including background starts
        // for push or WorkManager) and again whenever a sign-in completes.
        scope.launch { authState.collect { if (it == AuthState.SignedIn) onSignedIn() } }
        scope.launch {
            combine(tokenStore.tokenFlow, appLifecycle.foreground) { token, foreground -> token.takeIf { foreground } }
                .collect { token ->
                    if (token == null) return@collect
                    // Covers signing in while already foregrounded, when ON_START came before the token.
                    liveEvents.start()
                    if (token != foregroundWorkFor) {
                        foregroundWorkFor = token
                        onForegroundSession()
                    }
                }
        }
    }

    /** Local, cheap setup that is safe in a background process start. */
    private fun onSignedIn() {
        if (tokenStore.token() == null) return
        scope.launch { users.restore() }
        calendarObserver.ensureRegistered()
        syncScheduler.schedulePeriodic()
    }

    /**
     * Network work done once per sign-in, or once per process the first time it comes
     * to the foreground, rather than on every background wake (each of which would
     * otherwise refetch /me, re-register push and queue a second calendar sync).
     */
    private fun onForegroundSession() {
        scope.launch {
            users.restore()
            if (users.refresh() is ApiResult.Success) {
                push.register()
            }
        }
        syncScheduler.syncSoon()
    }

    suspend fun signOut() {
        val token = tokenStore.token()
        push.unregister()
        authRepository.signOutRemote()
        clearLocalSession(ifToken = token)
    }

    /**
     * Serialised and keyed by token so a 401 that arrives during [signOut] (e.g.
     * `DELETE /devices` after the account was deleted) doesn't clear a second time,
     * and can't clear a session that has since replaced the rejected one.
     * [ifToken] = null clears unconditionally.
     */
    private suspend fun clearLocalSession(ifToken: String?) = clearMutex.withLock {
        if (ifToken != null && tokenStore.token() != ifToken) return@withLock
        liveEvents.stop()
        syncScheduler.cancelAll()
        calendarObserver.unregister()
        tokenStore.clear()
        cookieJar.clear()
        users.clear()
        plans.clear()
        billing.logOut()
        groups.clear()
        calendars.clear()
        notifications.clear()
        prefs.clearSession()
    }
}
