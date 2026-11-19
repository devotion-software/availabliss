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

/** "Add to calendar buttons", saved to the account (`showAddToCalendar`), so every device follows. */
@HiltViewModel
class AddToCalendarSettingViewModel @Inject constructor(private val users: UserRepository) : ViewModel() {
    /** Shown while a change is saving, so the switch answers at once. */
    private val pending = MutableStateFlow<Boolean?>(null)
    private val _message = MutableStateFlow<UserMessage?>(null)
    val message: StateFlow<UserMessage?> = _message.asStateFlow()

    val show: StateFlow<Boolean> = combine(users.user, pending) { user, p -> p ?: user?.showAddToCalendar ?: true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), users.user.value?.showAddToCalendar ?: true)

    fun set(value: Boolean) {
        if (value == show.value) return
        pending.value = value
        viewModelScope.launch {
            val result = users.update { set("showAddToCalendar", value) }
            if (result is ApiResult.Failure) _message.value = UserMessage("Couldn't save: ${result.error.message}")
            pending.value = null
        }
    }

    fun onMessageShown() {
        _message.value = null
    }
}

/** On plans, in chats, on Home and My plans. Off suits people who subscribe to their plans instead. */
@Composable
fun AddToCalendarSettingCard(show: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    GlassPanel(modifier, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
        ToggleRow(
            title = "Add to calendar buttons",
            subtitle = "On plans, in chats and on My plans. Turn them off if you subscribe to your plans instead.",
            checked = show,
            onCheckedChange = onChange,
        )
    }
}
