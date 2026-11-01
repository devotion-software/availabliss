package dev.joeherbert.availabliss.feature.appearance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.joeherbert.availabliss.core.common.UserMessage
import dev.joeherbert.availabliss.core.data.AppPreferences
import dev.joeherbert.availabliss.core.data.UserRepository
import dev.joeherbert.availabliss.core.network.ApiResult
import dev.joeherbert.availabliss.core.network.model.ColorTheme
import dev.joeherbert.availabliss.core.network.model.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppearanceUiState(
    val mode: ThemeMode = ThemeMode.System,
    val theme: ColorTheme = ColorTheme.Dawn,
    val saving: Boolean = false,
    val message: UserMessage? = null,
)

/**
 * Light / Dark / System and the five colour themes. Changes apply instantly on this
 * device (DataStore drives the app theme) and are then saved to the account.
 */
@HiltViewModel
class AppearanceViewModel @Inject constructor(
    private val users: UserRepository,
    prefs: AppPreferences,
) : ViewModel() {

    private val _state = MutableStateFlow(AppearanceUiState())
    val state: StateFlow<AppearanceUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            prefs.appearance.collect { a -> _state.update { it.copy(mode = a.mode, theme = a.theme) } }
        }
    }

    fun selectMode(mode: ThemeMode) = save(mode, _state.value.theme)

    fun selectTheme(theme: ColorTheme) = save(_state.value.mode, theme)

    fun onMessageShown(message: UserMessage) =
        _state.update { if (it.message?.id == message.id) it.copy(message = null) else it }

    private fun save(mode: ThemeMode, theme: ColorTheme) {
        _state.update { it.copy(mode = mode, theme = theme, saving = true) }
        viewModelScope.launch {
            val result = users.setAppearance(mode, theme)
            _state.update {
                it.copy(
                    saving = false,
                    message = (result as? ApiResult.Failure)?.let { f ->
                        UserMessage("Applied on this phone, but couldn't save to your account: ${f.error.message}")
                    } ?: it.message,
                )
            }
        }
    }
}
