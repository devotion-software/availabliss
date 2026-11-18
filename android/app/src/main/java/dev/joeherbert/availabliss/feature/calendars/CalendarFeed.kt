package dev.joeherbert.availabliss.feature.calendars

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.joeherbert.availabliss.core.auth.CustomTabs
import dev.joeherbert.availabliss.core.common.UserMessage
import dev.joeherbert.availabliss.core.data.CalendarsRepository
import dev.joeherbert.availabliss.core.network.ApiResult
import dev.joeherbert.availabliss.core.network.model.CalendarFeed
import dev.joeherbert.availabliss.designsystem.component.ConfirmOption
import dev.joeherbert.availabliss.designsystem.component.ConfirmSheet
import dev.joeherbert.availabliss.designsystem.component.GlassPanel
import dev.joeherbert.availabliss.designsystem.component.PrimaryButton
import dev.joeherbert.availabliss.designsystem.component.SecondaryButton
import dev.joeherbert.availabliss.designsystem.component.TextAction
import dev.joeherbert.availabliss.designsystem.icon.AvIcons
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import dev.joeherbert.availabliss.designsystem.theme.Shapes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the "Subscribe to your plans" card shows. */
data class CalendarFeedState(
    val feed: CalendarFeed? = null,
    val loading: Boolean = false,
    val confirmingReset: Boolean = false,
    val resetting: Boolean = false,
    val message: UserMessage? = null,
)

/**
 * Your secret webcal feed of plans (`/me/calendar-feed`). Fetched lazily, the first time the
 * card is shown, since asking for it makes the link on the server.
 */
@HiltViewModel
class CalendarFeedViewModel @Inject constructor(private val calendars: CalendarsRepository) : ViewModel() {
    private val _state = MutableStateFlow(CalendarFeedState())
    val state: StateFlow<CalendarFeedState> = _state.asStateFlow()

    /** Fetches the link unless it's already here or on its way. */
    fun load() {
        val current = _state.value
        if (current.feed != null || current.loading) return
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            when (val result = calendars.calendarFeed()) {
                is ApiResult.Success -> _state.update { it.copy(feed = result.value, loading = false) }
                is ApiResult.Failure -> _state.update { it.copy(loading = false, message = UserMessage(result.error.message)) }
            }
        }
    }

    fun onCopied() {
        _state.update { it.copy(message = UserMessage("Link copied")) }
    }

    fun askReset() {
        _state.update { it.copy(confirmingReset = true) }
    }

    fun cancelReset() {
        _state.update { if (it.resetting) it else it.copy(confirmingReset = false) }
    }

    /** Swaps in a new link; calendars subscribed with the old one stop updating. */
    fun reset() {
        if (_state.value.resetting) return
        _state.update { it.copy(resetting = true) }
        viewModelScope.launch {
            when (val result = calendars.resetCalendarFeed()) {
                is ApiResult.Success -> _state.update {
                    it.copy(feed = result.value, resetting = false, confirmingReset = false, message = UserMessage("Calendar link reset"))
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(resetting = false, confirmingReset = false, message = UserMessage(result.error.message))
                }
            }
        }
    }

    fun onMessageShown() {
        _state.update { it.copy(message = null) }
    }
}

/** Google Calendar's "add by URL" page for [webcalUrl]. */
internal fun googleCalendarSubscribeUrl(webcalUrl: String): String =
    "https://calendar.google.com/calendar/r?cid=" + Uri.encode(webcalUrl)

/**
 * "Subscribe to your plans": adds the secret feed to Google Calendar (in a Custom Tab), copies
 * the link for any other calendar app, and offers to reset it (behind [ConfirmSheet]). Fetches
 * the link the first time it's shown; the buttons stay disabled until it arrives.
 */
@Composable
fun CalendarFeedCard(
    state: CalendarFeedState,
    onLoad: () -> Unit,
    onCopied: () -> Unit,
    onAskReset: () -> Unit,
    onCancelReset: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AvailablissTheme.colors
    val type = AvailablissTheme.type
    val context = LocalContext.current
    val feed = state.feed
    LaunchedEffect(Unit) { onLoad() }

    GlassPanel(
        modifier.fillMaxWidth(),
        shape = Shapes.panel,
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Subscribe to your plans", style = type.label, color = colors.ink, modifier = Modifier.semantics { heading() })
            Text(
                "Your plans from every group in Google Calendar or another app. Changes and cancellations follow automatically.",
                style = type.caption,
                color = colors.muted,
            )
        }
        PrimaryButton(
            text = "Add to Google Calendar",
            onClick = { feed?.let { CustomTabs.open(context, googleCalendarSubscribeUrl(it.webcalUrl)) } },
            icon = AvIcons.CalendarAdd,
            height = 48.dp,
            shape = Shapes.control,
            enabled = feed != null,
            loading = state.loading && feed == null,
            modifier = Modifier.fillMaxWidth(),
        )
        SecondaryButton(
            text = "Copy link",
            onClick = {
                feed?.let {
                    copyToClipboard(context, "Availabliss plans", it.url)
                    onCopied()
                }
            },
            icon = AvIcons.Link,
            height = 48.dp,
            enabled = feed != null,
            modifier = Modifier.fillMaxWidth(),
        )
        TextAction(
            "Reset link",
            onAskReset,
            enabled = feed != null && !state.resetting,
            color = colors.muted,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }

    if (state.confirmingReset) {
        ConfirmSheet(
            title = "Reset your calendar link?",
            message = "Calendars subscribed with the old link stop updating. You'll need to subscribe again.",
            options = listOf(
                ConfirmOption("Reset link", onReset, AvIcons.Refresh, ConfirmOption.Kind.Destructive, loading = state.resetting),
            ),
            onDismiss = onCancelReset,
        )
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}
