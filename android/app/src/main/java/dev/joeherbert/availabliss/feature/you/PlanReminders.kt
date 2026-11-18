package dev.joeherbert.availabliss.feature.you

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import dev.joeherbert.availabliss.core.network.model.EventReminders
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

private val ReminderSegments = listOf(
    Segment(EventReminders.Both, "Both"),
    Segment(EventReminders.DayBefore, "Day before"),
    Segment(EventReminders.HourBefore, "Hour before"),
    Segment(EventReminders.Off, "Off"),
)

/** "Plan reminders", saved to the account (`eventReminders`). */
@HiltViewModel
class PlanRemindersViewModel @Inject constructor(private val users: UserRepository) : ViewModel() {
    /** Shown while a change is saving, so the control answers at once. */
    private val pending = MutableStateFlow<EventReminders?>(null)
    private val _message = MutableStateFlow<UserMessage?>(null)
    val message: StateFlow<UserMessage?> = _message.asStateFlow()

    val reminders: StateFlow<EventReminders> = combine(users.user, pending) { user, p -> p ?: user?.eventReminders ?: EventReminders.Both }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), users.user.value?.eventReminders ?: EventReminders.Both)

    fun select(value: EventReminders) {
        if (value == reminders.value) return
        pending.value = value
        viewModelScope.launch {
            val result = users.update { set("eventReminders", value.wire) }
            if (result is ApiResult.Failure) _message.value = UserMessage("Couldn't save plan reminders: ${result.error.message}")
            pending.value = null
        }
    }

    fun onMessageShown() {
        _message.value = null
    }
}

/** Which pushes arrive before confirmed plans you're going to: a day before, an hour before, both or none. */
@Composable
fun PlanRemindersCard(selected: EventReminders, onSelect: (EventReminders) -> Unit, modifier: Modifier = Modifier) {
    val colors = AvailablissTheme.colors
    val type = AvailablissTheme.type
    GlassPanel(
        modifier.fillMaxWidth(),
        shape = Shapes.panel,
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Plan reminders", style = type.label, color = colors.ink, modifier = Modifier.semantics { heading() })
            Text("Pushes before plans you're going to.", style = type.caption, color = colors.muted)
        }
        SegmentedControl(
            segments = ReminderSegments,
            selected = selected,
            onSelect = onSelect,
            style = SegmentedStyle.Inset,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
