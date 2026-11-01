package dev.joeherbert.availabliss.feature.you

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.joeherbert.availabliss.core.auth.AuthRepository
import dev.joeherbert.availabliss.core.common.UserMessage
import dev.joeherbert.availabliss.core.data.AppPreferences
import dev.joeherbert.availabliss.core.data.Appearance
import dev.joeherbert.availabliss.core.data.UserRepository
import dev.joeherbert.availabliss.core.network.ApiResult
import dev.joeherbert.availabliss.core.network.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

data class YouUiState(
    val user: User? = null,
    val appearance: Appearance = Appearance(),
    /** Non-null while the edit-name sheet is open. */
    val nameDraft: String? = null,
    val savingName: Boolean = false,
    val addingPasskey: Boolean = false,
    val confirmingDelete: Boolean = false,
    val deleting: Boolean = false,
    /** Set after the account is deleted; the UI then runs sign-out cleanup. */
    val deleted: Boolean = false,
    val message: UserMessage? = null,
) {
    /** "United Kingdom · GB-ENG" */
    val regionSummary: String
        get() {
            val country = user?.country ?: return "Not set"
            val name = Locale("", country).displayCountry.ifBlank { country }
            return listOfNotNull(name, user?.subdivision?.substringAfter('-')).joinToString(" · ")
        }

    /** "System · Dawn" */
    val appearanceSummary: String get() = "${appearance.mode.name} · ${appearance.theme.name}"
}

@HiltViewModel
class YouViewModel @Inject constructor(
    private val users: UserRepository,
    private val auth: AuthRepository,
    prefs: AppPreferences,
) : ViewModel() {

    private val _state = MutableStateFlow(YouUiState())
    val state: StateFlow<YouUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(users.user, prefs.appearance) { user, appearance -> user to appearance }
                .collect { (user, appearance) -> _state.update { it.copy(user = user, appearance = appearance) } }
        }
    }

    fun startEditingName() = _state.update { it.copy(nameDraft = it.user?.name.orEmpty()) }

    fun onNameChange(value: String) = _state.update { it.copy(nameDraft = value.take(80)) }

    fun cancelEditingName() = _state.update { it.copy(nameDraft = null) }

    fun saveName() {
        val draft = _state.value.nameDraft?.trim().orEmpty()
        if (draft.isEmpty() || _state.value.savingName) return
        viewModelScope.launch {
            _state.update { it.copy(savingName = true) }
            val result = users.update { set("name", draft) }
            _state.update {
                when (result) {
                    is ApiResult.Success -> it.copy(savingName = false, nameDraft = null)
                    is ApiResult.Failure -> it.copy(savingName = false, message = UserMessage(result.error.message))
                }
            }
        }
    }

    /** [activity] anchors the Credential Manager sheet; it is not retained. */
    fun addPasskey(activity: Context) {
        if (_state.value.addingPasskey) return
        viewModelScope.launch {
            _state.update { it.copy(addingPasskey = true) }
            val error = auth.registerPasskey(activity, name = android.os.Build.MODEL)
            _state.update {
                it.copy(
                    addingPasskey = false,
                    message = when {
                        error == null -> UserMessage("Passkey added. You can use it to sign in next time.")
                        error.isEmpty() -> it.message // cancelled
                        else -> UserMessage(error)
                    },
                )
            }
        }
    }

    fun requestDelete() = _state.update { it.copy(confirmingDelete = true) }

    fun cancelDelete() = _state.update { it.copy(confirmingDelete = false) }

    fun confirmDelete() {
        if (_state.value.deleting) return
        viewModelScope.launch {
            _state.update { it.copy(deleting = true) }
            when (val result = users.deleteAccount()) {
                is ApiResult.Success -> _state.update { it.copy(deleting = false, confirmingDelete = false, deleted = true) }
                is ApiResult.Failure -> _state.update {
                    it.copy(deleting = false, confirmingDelete = false, message = UserMessage(result.error.message))
                }
            }
        }
    }

    fun onMessageShown(message: UserMessage) =
        _state.update { if (it.message?.id == message.id) it.copy(message = null) else it }
}
