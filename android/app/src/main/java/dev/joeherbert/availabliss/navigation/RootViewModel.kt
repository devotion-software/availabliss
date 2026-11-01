package dev.joeherbert.availabliss.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.joeherbert.availabliss.core.auth.AuthState
import dev.joeherbert.availabliss.core.auth.SessionManager
import dev.joeherbert.availabliss.core.data.AppPreferences
import dev.joeherbert.availabliss.core.data.Appearance
import dev.joeherbert.availabliss.core.data.NotificationsRepository
import dev.joeherbert.availabliss.core.data.UserRepository
import dev.joeherbert.availabliss.core.deeplink.DeepLink
import dev.joeherbert.availabliss.core.deeplink.DeepLinkBus
import dev.joeherbert.availabliss.core.network.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the root shows. */
sealed interface RootState {
    data object SignedOut : RootState
    /** Signed in, waiting for the first `/me`. [failed] = couldn't load and nothing cached. */
    data class Loading(val failed: Boolean = false) : RootState
    data object Onboarding : RootState
    data object Main : RootState
}

@HiltViewModel
class RootViewModel @Inject constructor(
    private val session: SessionManager,
    private val users: UserRepository,
    prefs: AppPreferences,
    private val deepLinkBus: DeepLinkBus,
    notifications: NotificationsRepository,
) : ViewModel() {

    private val loadFailed = MutableStateFlow(false)

    val appearance: StateFlow<Appearance> = prefs.appearance
        .stateIn(viewModelScope, SharingStarted.Eagerly, Appearance())

    val state: StateFlow<RootState> = combine(session.authState, users.user, loadFailed) { auth, user, failed ->
        when {
            auth == AuthState.SignedOut -> RootState.SignedOut
            user == null -> RootState.Loading(failed)
            !user.onboarded -> RootState.Onboarding
            else -> RootState.Main
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, RootState.Loading())

    val pendingDeepLink: StateFlow<DeepLink?> = deepLinkBus.pending
    val unreadNotifications: StateFlow<Int> = notifications.unread

    init {
        viewModelScope.launch {
            users.restore()
            session.authState.collect { if (it == AuthState.SignedIn && users.user.value == null) retryLoad() }
        }
    }

    fun retryLoad() {
        viewModelScope.launch {
            loadFailed.value = false
            loadFailed.value = users.refresh() !is ApiResult.Success && users.user.value == null
        }
    }

    fun consume(link: DeepLink) = deepLinkBus.consume(link)

    fun signOut() {
        viewModelScope.launch { session.signOut() }
    }
}
