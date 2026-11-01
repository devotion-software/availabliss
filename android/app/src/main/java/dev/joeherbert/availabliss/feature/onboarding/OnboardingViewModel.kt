package dev.joeherbert.availabliss.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.joeherbert.availabliss.core.common.UserMessage
import dev.joeherbert.availabliss.core.data.UserRepository
import dev.joeherbert.availabliss.core.deeplink.DeepLink
import dev.joeherbert.availabliss.core.deeplink.DeepLinkBus
import dev.joeherbert.availabliss.core.network.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class OnboardingStep { Region, Calendars }

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.Region,
    val finishing: Boolean = false,
    val message: UserMessage? = null,
)

/**
 * Two-step onboarding: region, then busy calendars. Finishing sets `onboarded`
 * via `PATCH /me`, which switches the app root to the main app.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val users: UserRepository,
    private val deepLinks: DeepLinkBus,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    fun goToCalendars() = _state.update { it.copy(step = OnboardingStep.Calendars) }

    fun goToRegion() = _state.update { it.copy(step = OnboardingStep.Region) }

    /** [markBusyByHand] = "No calendar? Mark busy times yourself": land on My time. */
    fun finish(markBusyByHand: Boolean) {
        if (_state.value.finishing) return
        viewModelScope.launch {
            _state.update { it.copy(finishing = true) }
            // Post first so the main app opens My time as soon as it appears.
            if (markBusyByHand) deepLinks.post(DeepLink.MyTime)
            when (val result = users.update { set("onboarded", true) }) {
                is ApiResult.Success -> _state.update { it.copy(finishing = false) }
                is ApiResult.Failure -> {
                    if (markBusyByHand) deepLinks.consume(DeepLink.MyTime)
                    _state.update { it.copy(finishing = false, message = UserMessage(result.error.message)) }
                }
            }
        }
    }

    fun onMessageShown(message: UserMessage) =
        _state.update { if (it.message?.id == message.id) it.copy(message = null) else it }
}
