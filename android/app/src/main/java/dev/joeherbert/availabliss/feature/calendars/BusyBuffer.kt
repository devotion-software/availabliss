package dev.joeherbert.availabliss.feature.calendars

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.joeherbert.availabliss.core.common.UserMessage
import dev.joeherbert.availabliss.core.data.UserRepository
import dev.joeherbert.availabliss.core.network.ApiResult
import dev.joeherbert.availabliss.designsystem.component.GlassPanel
import dev.joeherbert.availabliss.designsystem.component.Segment
import dev.joeherbert.availabliss.designsystem.component.SegmentedControl
import dev.joeherbert.availabliss.designsystem.component.SegmentedStyle
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import dev.joeherbert.availabliss.designsystem.theme.Shapes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The buffers on offer, as the API accepts them. */
private val BufferSegments = listOf(
    Segment(0, "None"),
    Segment(15, "15 min"),
    Segment(30, "30 min"),
    Segment(60, "1 hr"),
    Segment(120, "2 hr"),
)

/** "Time around busy times", saved to the account (`busyBufferMinutes`). */
@HiltViewModel
class BusyBufferViewModel @Inject constructor(private val users: UserRepository) : ViewModel() {
    /** Shown while a change is saving, so the control answers at once. */
    private val pending = MutableStateFlow<Int?>(null)
    private val _message = MutableStateFlow<UserMessage?>(null)
    val message: StateFlow<UserMessage?> = _message.asStateFlow()

    val minutes: StateFlow<Int> = combine(users.user, pending) { user, p -> p ?: user?.busyBufferMinutes ?: 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), users.user.value?.busyBufferMinutes ?: 0)

    fun select(value: Int) {
        if (value == minutes.value) return
        pending.value = value
        viewModelScope.launch {
            val result = users.update { set("busyBufferMinutes", value) }
            if (result is ApiResult.Failure) _message.value = UserMessage(result.error.message)
            pending.value = null
        }
    }

    fun onMessageShown() {
        _message.value = null
    }
}

/**
 * Counts you as busy for a little longer before and after each event, so nobody plans something
 * the minute you're free. Only changes which times look free to your groups.
 */
@Composable
fun BusyBufferCard(minutes: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = AvailablissTheme.colors
    val type = AvailablissTheme.type
    GlassPanel(
        modifier.fillMaxWidth(),
        shape = Shapes.panel,
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Time around busy times", style = type.label, color = colors.ink, modifier = Modifier.semantics { heading() })
            Text(
                "Counts you as busy for a little longer before and after each event, so nobody plans something the minute you're free.",
                style = type.caption,
                color = colors.muted,
            )
        }
        SegmentedControl(
            segments = BufferSegments,
            selected = minutes,
            onSelect = onSelect,
            style = SegmentedStyle.Inset,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
