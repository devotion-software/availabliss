package dev.joeherbert.availabliss.feature.you

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.joeherbert.availabliss.core.common.DateTimes
import dev.joeherbert.availabliss.core.common.UserMessage
import dev.joeherbert.availabliss.core.data.NotificationSettingsRepository
import dev.joeherbert.availabliss.core.network.ApiResult
import dev.joeherbert.availabliss.core.network.model.NotificationCategory
import dev.joeherbert.availabliss.core.network.model.NotificationSettings
import dev.joeherbert.availabliss.core.network.model.QuietHours
import dev.joeherbert.availabliss.designsystem.component.AvSnackbarHost
import dev.joeherbert.availabliss.designsystem.component.AvTimePickerDialog
import dev.joeherbert.availabliss.designsystem.component.AvailablissBackground
import dev.joeherbert.availabliss.designsystem.component.ErrorBanner
import dev.joeherbert.availabliss.designsystem.component.GlassPanel
import dev.joeherbert.availabliss.designsystem.component.LoadingIndicator
import dev.joeherbert.availabliss.designsystem.component.OrbLayout
import dev.joeherbert.availabliss.designsystem.component.RowDivider
import dev.joeherbert.availabliss.designsystem.component.ScreenHeader
import dev.joeherbert.availabliss.designsystem.component.SectionLabel
import dev.joeherbert.availabliss.designsystem.component.ToggleRow
import dev.joeherbert.availabliss.designsystem.component.UserMessageEffect
import dev.joeherbert.availabliss.designsystem.component.rememberSnackbarHostState
import dev.joeherbert.availabliss.designsystem.theme.AvailablissPreviewTheme
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalTime
import javax.inject.Inject

/** The switches on the Notifications screen, in order. */
private val CategoryLabels = listOf(
    NotificationCategory.Chat to "Chat messages",
    NotificationCategory.Proposals to "New proposals and changes",
    NotificationCategory.Plans to "Confirmed plans",
    NotificationCategory.Reminders to "Reminders",
    NotificationCategory.Nudges to "Nudges to add busy times",
)

/** Quiet hours when they're first turned on. */
val DefaultQuietHours = QuietHours(from = "22:00", to = "07:00")

data class NotificationSettingsUiState(
    /** Null while loading. */
    val settings: NotificationSettings? = null,
    val loadFailed: Boolean = false,
    val message: UserMessage? = null,
)

/**
 * Which pushes you get: a switch per [NotificationCategory] and quiet hours. Each change shows at
 * once and saves straight away; if the save fails the screen goes back to the server's settings
 * and says so. Saves run one at a time, so they reach the server in the order they were made.
 */
@HiltViewModel
class NotificationSettingsViewModel @Inject constructor(
    private val repository: NotificationSettingsRepository,
) : ViewModel() {
    /** Shown while a change is saving. */
    private val pending = MutableStateFlow<NotificationSettings?>(null)
    private val loadFailed = MutableStateFlow(false)
    private val message = MutableStateFlow<UserMessage?>(null)
    private val saving = Mutex()

    /** What turning quiet hours back on restores (the last hours shown this visit). */
    private var lastQuietHours = repository.settings.value?.quietHours ?: DefaultQuietHours

    val state: StateFlow<NotificationSettingsUiState> =
        combine(repository.settings, pending, loadFailed, message) { saved, p, failed, m ->
            NotificationSettingsUiState(settings = p ?: saved, loadFailed = failed && saved == null, message = m)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotificationSettingsUiState(settings = repository.settings.value))

    init {
        refresh()
    }

    fun refresh() {
        loadFailed.value = false
        viewModelScope.launch {
            val result = repository.refresh()
            if (result is ApiResult.Failure) loadFailed.value = true
            else repository.settings.value?.quietHours?.let { lastQuietHours = it }
        }
    }

    fun setCategory(category: NotificationCategory, on: Boolean) {
        val current = state.value.settings ?: return
        val next = current.copy(off = current.offWith(category, on))
        save(next) { repository.setOff(next.off) }
    }

    fun setQuietHoursOn(on: Boolean) {
        val current = state.value.settings ?: return
        save(current.copy(quietHours = if (on) lastQuietHours else null)) {
            repository.setQuietHours(if (on) lastQuietHours else null)
        }
    }

    fun setQuietFrom(time: LocalTime) = setQuietHours { it.copy(from = DateTimes.toWireTime(time)) }

    fun setQuietTo(time: LocalTime) = setQuietHours { it.copy(to = DateTimes.toWireTime(time)) }

    fun setCatchUp(on: Boolean) = setQuietHours { it.copy(catchUp = on) }

    fun onMessageShown(shown: UserMessage) {
        if (message.value?.id == shown.id) message.value = null
    }

    private fun setQuietHours(change: (QuietHours) -> QuietHours) {
        val current = state.value.settings ?: return
        val hours = change(current.quietHours ?: lastQuietHours)
        lastQuietHours = hours
        save(current.copy(quietHours = hours)) { repository.setQuietHours(hours) }
    }

    private fun save(next: NotificationSettings, request: suspend () -> ApiResult<NotificationSettings>) {
        pending.value = next
        viewModelScope.launch {
            val result = saving.withLock { request() }
            when (result) {
                is ApiResult.Failure -> {
                    // Back to what the server has (a later change still saving shows again once it lands).
                    pending.value = null
                    message.value = UserMessage("Couldn't save notification settings: ${result.error.message}")
                }
                is ApiResult.Success -> if (pending.value === next) pending.value = null
            }
        }
    }
}

@Composable
fun NotificationSettingsScreen(onBack: () -> Unit, viewModel: NotificationSettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = rememberSnackbarHostState()
    UserMessageEffect(state.message, snackbar, viewModel::onMessageShown)
    AvailablissBackground(OrbLayout.Main) {
        NotificationSettingsContent(
            state = state,
            onBack = onBack,
            onRetry = viewModel::refresh,
            onCategoryChange = viewModel::setCategory,
            onQuietHoursChange = viewModel::setQuietHoursOn,
            onQuietFromChange = viewModel::setQuietFrom,
            onQuietToChange = viewModel::setQuietTo,
            onCatchUpChange = viewModel::setCatchUp,
        )
        AvSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
}

@Composable
fun NotificationSettingsContent(
    state: NotificationSettingsUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit = {},
    onCategoryChange: (NotificationCategory, Boolean) -> Unit = { _, _ -> },
    onQuietHoursChange: (Boolean) -> Unit = {},
    onQuietFromChange: (LocalTime) -> Unit = {},
    onQuietToChange: (LocalTime) -> Unit = {},
    onCatchUpChange: (Boolean) -> Unit = {},
) {
    val colors = AvailablissTheme.colors
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeader(title = "Notifications", onBack = onBack, subtitle = "Pushes to your devices")

        val settings = state.settings
        when {
            settings != null -> {
                CategoriesPanel(settings, onCategoryChange)
                QuietHoursPanel(settings.quietHours, onQuietHoursChange, onQuietFromChange, onQuietToChange, onCatchUpChange)
                Text(
                    "Invites and changes to your role always come through. To mute one group, use More in that group.",
                    style = AvailablissTheme.type.caption.copy(fontWeight = FontWeight.Normal),
                    color = colors.muted,
                )
            }
            state.loadFailed -> ErrorBanner("Couldn't load your notification settings.", onRetry = onRetry)
            else -> LoadingIndicator()
        }
    }
}

@Composable
private fun CategoriesPanel(settings: NotificationSettings, onChange: (NotificationCategory, Boolean) -> Unit) {
    GlassPanel(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        SectionLabel("Send me pushes for")
        CategoryLabels.forEachIndexed { index, (category, label) ->
            if (index > 0) RowDivider()
            ToggleRow(label, checked = !settings.isOff(category), onCheckedChange = { onChange(category, it) })
        }
    }
}

@Composable
private fun QuietHoursPanel(
    quietHours: QuietHours?,
    onToggle: (Boolean) -> Unit,
    onFromChange: (LocalTime) -> Unit,
    onToChange: (LocalTime) -> Unit,
    onCatchUpChange: (Boolean) -> Unit,
) {
    var picking by rememberSaveable { mutableStateOf<QuietEnd?>(null) }
    GlassPanel(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        ToggleRow(
            "Quiet hours",
            checked = quietHours != null,
            onCheckedChange = onToggle,
            subtitle = "No pushes during these hours. They're still in Activity.",
        )
        if (quietHours != null) {
            RowDivider()
            TimeRow("From", quietHours.from) { picking = QuietEnd.From }
            RowDivider()
            TimeRow("To", quietHours.to) { picking = QuietEnd.To }
            RowDivider()
            ToggleRow(
                "Catch me up afterwards",
                checked = quietHours.catchUp,
                onCheckedChange = onCatchUpChange,
                subtitle = "One push when quiet hours end, if anything came in that you haven't seen.",
            )
        }
    }
    val end = picking
    if (end != null && quietHours != null) {
        val (title, value, onPick) = when (end) {
            QuietEnd.From -> Triple("Quiet from", quietHours.from, onFromChange)
            QuietEnd.To -> Triple("Quiet until", quietHours.to, onToChange)
        }
        AvTimePickerDialog(
            title = title,
            initial = DateTimes.parseTime(value) ?: LocalTime.MIDNIGHT,
            onPick = onPick,
            onDismiss = { picking = null },
        )
    }
}

/** Which end of quiet hours is being picked. */
private enum class QuietEnd { From, To }

@Composable
private fun TimeRow(label: String, value: String, onClick: () -> Unit) {
    val colors = AvailablissTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 50.dp)
            .clickable(role = Role.Button, onClickLabel = "Change", onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = AvailablissTheme.type.bodyMediumRegular, color = colors.ink, modifier = Modifier.weight(1f))
        Text(value, style = AvailablissTheme.type.bodyMedium, color = colors.accentInk)
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun NotificationSettingsPreview() {
    AvailablissPreviewTheme {
        AvailablissBackground(OrbLayout.Main) {
            NotificationSettingsContent(
                state = NotificationSettingsUiState(NotificationSettings(off = listOf("nudges"), quietHours = DefaultQuietHours)),
                onBack = {},
            )
        }
    }
}
