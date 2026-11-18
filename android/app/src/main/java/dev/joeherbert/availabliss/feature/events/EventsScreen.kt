package dev.joeherbert.availabliss.feature.events

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.joeherbert.availabliss.designsystem.component.ConfirmOption
import dev.joeherbert.availabliss.designsystem.component.ConfirmSheet
import dev.joeherbert.availabliss.designsystem.component.AvSnackbarHost
import dev.joeherbert.availabliss.designsystem.component.AvailablissBackground
import dev.joeherbert.availabliss.designsystem.component.CoverPhoto
import dev.joeherbert.availabliss.designsystem.component.EmptyState
import dev.joeherbert.availabliss.designsystem.component.ErrorBanner
import dev.joeherbert.availabliss.designsystem.component.GlassIconButton
import dev.joeherbert.availabliss.designsystem.component.GlassPanel
import dev.joeherbert.availabliss.designsystem.component.LoadingIndicator
import dev.joeherbert.availabliss.designsystem.component.OrbLayout
import dev.joeherbert.availabliss.designsystem.component.PlainIconButton
import dev.joeherbert.availabliss.designsystem.component.ScreenHeader
import dev.joeherbert.availabliss.designsystem.component.SectionLabel
import dev.joeherbert.availabliss.designsystem.component.UserMessageEffect
import dev.joeherbert.availabliss.designsystem.component.chat.DateTile
import dev.joeherbert.availabliss.designsystem.component.rememberSnackbarHostState
import dev.joeherbert.availabliss.designsystem.icon.AvIcons
import dev.joeherbert.availabliss.designsystem.theme.AvailablissPreviewTheme
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import dev.joeherbert.availabliss.designsystem.theme.Shapes
import dev.joeherbert.availabliss.feature.common.AddToCalendarButton
import dev.joeherbert.availabliss.feature.common.AddToCalendarEffect
import dev.joeherbert.availabliss.feature.common.CalendarStatus
import dev.joeherbert.availabliss.feature.common.AddToCalendarState

@Composable
fun EventsScreen(onBack: () -> Unit, onAddEvent: () -> Unit, onOpenEvent: (String) -> Unit, viewModel: EventsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = rememberSnackbarHostState()
    UserMessageEffect(state.message, snackbar, viewModel::onMessageShown)
    AddToCalendarEffect(state.calendar, viewModel::onIntentHandled, viewModel::chooseCalendar, viewModel::cancelCalendarChoice)
    EventsContent(
        state = state,
        onBack = onBack,
        onAddEvent = onAddEvent,
        onOpenEvent = onOpenEvent,
        onRetry = viewModel::refresh,
        onAddToCalendar = viewModel::onAddToCalendar,
        onDelete = viewModel::onDelete,
    )
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) { AvSnackbarHost(snackbar, Modifier.navigationBarsPadding()) }
}

@Composable
private fun EventsContent(
    state: EventsUiState,
    onBack: () -> Unit,
    onAddEvent: () -> Unit,
    onOpenEvent: (String) -> Unit,
    onRetry: () -> Unit,
    onAddToCalendar: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var confirmDelete by remember { mutableStateOf<EventRowUi?>(null) }
    AvailablissBackground(OrbLayout.Proposal) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            ScreenHeader(
                title = "Events",
                subtitle = state.groupName.ifBlank { null },
                onBack = onBack,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
                actions = { GlassIconButton(AvIcons.Plus, "Add an event", onAddEvent) },
            )
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                state.error?.let { item { ErrorBanner(it, onRetry = onRetry) } }
                if (state.loading && state.upcoming.isEmpty() && state.past.isEmpty()) item { LoadingIndicator() }
                if (!state.loading && state.error == null && state.upcoming.isEmpty() && state.past.isEmpty()) {
                    item {
                        EmptyState(
                            title = "No events yet",
                            body = "Confirmed proposals land here. You can also add an event straight to the group.",
                            icon = AvIcons.Calendar,
                            actionLabel = "Add an event",
                            onAction = onAddEvent,
                        )
                    }
                }
                if (state.upcoming.isNotEmpty()) {
                    item { SectionLabel("Coming up", Modifier.padding(top = 4.dp)) }
                    items(state.upcoming, key = { it.id }) { row ->
                        EventRow(row, past = false, onOpen = { onOpenEvent(row.id) }, onAddToCalendar = { onAddToCalendar(row.id) }, onDelete = { confirmDelete = row })
                    }
                }
                if (state.past.isNotEmpty()) {
                    item { SectionLabel("Past", Modifier.padding(top = 8.dp)) }
                    items(state.past, key = { "past-" + it.id }) { row ->
                        EventRow(row, past = true, onOpen = { onOpenEvent(row.id) }, onAddToCalendar = {}, onDelete = { confirmDelete = row })
                    }
                }
            }
        }
    }
    confirmDelete?.let { row ->
        DeleteEventDialog(row.title, onConfirm = { onDelete(row.id); confirmDelete = null }, onDismiss = { confirmDelete = null })
    }
}

/** Confirms deleting a group event (shared by the events list and the event screen). */
@Composable
fun DeleteEventDialog(title: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ConfirmSheet(
        title = "Delete $title?",
        message = "It will be removed for everyone in the group.",
        options = listOf(ConfirmOption("Delete event", onConfirm, AvIcons.Trash, ConfirmOption.Kind.Destructive)),
        onDismiss = onDismiss,
        cancelText = "Keep",
    )
}

/** A group event: date tile, title, time, place, and Add to calendar while it's coming up. */
@Composable
internal fun EventRow(row: EventRowUi, past: Boolean, onOpen: () -> Unit, onAddToCalendar: () -> Unit, onDelete: () -> Unit) {
    val colors = AvailablissTheme.colors
    GlassPanel(Modifier.fillMaxWidth(), shape = Shapes.row, contentPadding = PaddingValues(12.dp), onClick = onOpen, onClickLabel = "Open ${row.title}") {
        row.image?.let { CoverPhoto(it, aspectRatio = 2.4f) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DateTile(row.month, row.day, muted = past)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(row.title, style = AvailablissTheme.type.bodyMedium, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(row.timeLabel, style = AvailablissTheme.type.caption, color = colors.muted, maxLines = 1)
                row.location?.let { Text(it, style = AvailablissTheme.type.caption, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            if (row.canDelete) PlainIconButton(AvIcons.Trash, "Delete ${row.title}", onDelete, size = 40.dp, iconSize = 18.dp, tint = colors.muted)
        }
        if (!past) AddToCalendarButton(row.calendar, onClick = onAddToCalendar, height = 44.dp)
    }
}

@Preview
@Composable
private fun EventsPreview() {
    AvailablissPreviewTheme {
        EventsContent(
            state = EventsUiState(
                groupName = "Book club",
                loading = false,
                upcoming = listOf(EventRowUi("1", "Book club", "Wed 11 Nov · 18:30 – 21:00", "At Hana's", "NOV", "11", canDelete = true, calendar = CalendarStatus())),
                calendar = AddToCalendarState(),
            ),
            onBack = {}, onAddEvent = {}, onOpenEvent = {}, onRetry = {}, onAddToCalendar = {}, onDelete = {},
        )
    }
}
