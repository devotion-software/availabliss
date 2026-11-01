package dev.joeherbert.availabliss.feature.signin

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.joeherbert.availabliss.core.auth.AuthRepository
import dev.joeherbert.availabliss.core.auth.SignInResult
import dev.joeherbert.availabliss.core.deeplink.DeepLink
import dev.joeherbert.availabliss.core.deeplink.DeepLinkBus
import dev.joeherbert.availabliss.core.network.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SignInStep { Email, Code }

/** Which action is in flight (drives per-button spinners). */
enum class SignInBusy { Email, Code, Apple, Google, Passkey }

data class SignInUiState(
    val step: SignInStep = SignInStep.Email,
    val email: String = "",
    val code: String = "",
    val busy: SignInBusy? = null,
    val error: String? = null,
    val googleAvailable: Boolean = true,
    /** Apple's authorize URL, to be opened in a Custom Tab by the UI. */
    val appleUrl: String? = null,
) {
    val canSendCode: Boolean get() = EMAIL_REGEX.matches(email.trim()) && busy == null

    private companion object {
        val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }
}

/**
 * Sign-in screen logic: email one-time code (two steps), Google via Credential
 * Manager, passkeys, and Sign in with Apple (web flow in a Custom Tab). A successful
 * sign-in stores the token, which flips the app root on its own.
 */
@HiltViewModel
class SignInViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val deepLinks: DeepLinkBus,
) : ViewModel() {

    private val _state = MutableStateFlow(SignInUiState(googleAvailable = auth.isGoogleAvailable))
    val state: StateFlow<SignInUiState> = _state.asStateFlow()

    init {
        // Return from the Apple web flow: availabliss://auth/callback?token=…
        viewModelScope.launch {
            deepLinks.pending.collect { link ->
                if (link is DeepLink.AuthCallback) {
                    deepLinks.consume(link)
                    run(SignInBusy.Apple) { auth.completeWebSignIn(link.token, link.error) }
                }
            }
        }
    }

    fun onEmailChange(value: String) = _state.update { it.copy(email = value, error = null) }

    fun sendCode() {
        val s = _state.value
        if (!s.canSendCode) {
            if (s.busy == null) _state.update { it.copy(error = "Enter a valid email address.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = SignInBusy.Email, error = null) }
            when (val result = auth.sendCode(s.email)) {
                is ApiResult.Success -> _state.update { it.copy(busy = null, step = SignInStep.Code, code = "") }
                is ApiResult.Failure -> _state.update { it.copy(busy = null, error = result.error.message) }
            }
        }
    }

    fun onCodeChange(value: String) {
        _state.update { it.copy(code = value, error = null) }
        if (value.length == CODE_LENGTH) verifyCode()
    }

    fun verifyCode() {
        val s = _state.value
        if (s.code.length != CODE_LENGTH || s.busy != null) return
        run(SignInBusy.Code, onFailure = { it.copy(code = "") }) { auth.verifyCode(s.email, s.code) }
    }

    fun useDifferentEmail() = _state.update { it.copy(step = SignInStep.Email, code = "", error = null) }

    /** [activity] anchors the Credential Manager sheet; it is not retained. */
    fun signInWithGoogle(activity: Context) = run(SignInBusy.Google) { auth.signInWithGoogle(activity) }

    fun signInWithPasskey(activity: Context) = run(SignInBusy.Passkey) { auth.signInWithPasskey(activity) }

    fun signInWithApple() {
        if (_state.value.busy != null) return
        viewModelScope.launch {
            _state.update { it.copy(busy = SignInBusy.Apple, error = null) }
            when (val result = auth.startAppleSignIn()) {
                is ApiResult.Success -> _state.update { it.copy(busy = null, appleUrl = result.value) }
                is ApiResult.Failure -> _state.update { it.copy(busy = null, error = result.error.message) }
            }
        }
    }

    fun onAppleUrlOpened() = _state.update { it.copy(appleUrl = null) }

    private fun run(
        busy: SignInBusy,
        onFailure: (SignInUiState) -> SignInUiState = { it },
        block: suspend () -> SignInResult,
    ) {
        if (_state.value.busy != null) return
        viewModelScope.launch {
            _state.update { it.copy(busy = busy, error = null) }
            when (val result = block()) {
                // The root switches screens once the session exists; just clear the spinner.
                is SignInResult.Success -> _state.update { it.copy(busy = null) }
                SignInResult.Cancelled -> _state.update { it.copy(busy = null) }
                is SignInResult.Failed -> _state.update { onFailure(it).copy(busy = null, error = result.message) }
            }
        }
    }

    companion object {
        const val CODE_LENGTH = 6
    }
}
