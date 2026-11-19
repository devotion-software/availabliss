package dev.joeherbert.availabliss.feature.you

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.joeherbert.availabliss.core.common.UserMessage
import dev.joeherbert.availabliss.core.data.UserRepository
import dev.joeherbert.availabliss.core.network.ApiResult
import dev.joeherbert.availabliss.designsystem.component.GlassPanel
import dev.joeherbert.availabliss.designsystem.component.ToggleRow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** "Auto-archive groups I create", saved to the account (`autoArchiveNewGroups`), so every device follows. */
@HiltViewModel
class AutoArchiveSettingViewModel @Inject constructor(private val users: UserRepository) : ViewModel() {
    /** Shown while a change is saving, so the switch answers at once. */
    private val pending = MutableStateFlow<Boolean?>(null)
    private val _message = MutableStateFlow<UserMessage?>(null)
    val message: StateFlow<UserMessage?> = _message.asStateFlow()

    val on: StateFlow<Boolean> = combine(users.user, pending) { user, p -> p ?: user?.autoArchiveNewGroups ?: false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), users.user.value?.autoArchiveNewGroups ?: false)

    fun set(value: Boolean) {
        if (value == on.value) return
        pending.value = value
        viewModelScope.launch {
            val result = users.update { set("autoArchiveNewGroups", value) }
            if (result is ApiResult.Failure) _message.value = UserMessage("Couldn't save: ${result.error.message}")
            pending.value = null
        }
    }

    fun onMessageShown() {
        _message.value = null
    }
}

/** New groups you make start with auto-archive on; each can still change it in its settings. Like web and iOS. */
@Composable
fun AutoArchiveSettingCard(on: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    GlassPanel(modifier, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
        ToggleRow(
            title = "Auto-archive groups I create",
            subtitle = "New groups you make archive themselves once their last plan has passed. You can change it for each group in its settings.",
            checked = on,
            onCheckedChange = onChange,
        )
    }
}
