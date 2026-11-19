package dev.joeherbert.availabliss.feature.proposal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.joeherbert.availabliss.designsystem.component.AvSnackbarHost
import dev.joeherbert.availabliss.designsystem.component.AvailablissBackground
import dev.joeherbert.availabliss.designsystem.component.EmptyState
import dev.joeherbert.availabliss.designsystem.component.ErrorBanner
import dev.joeherbert.availabliss.designsystem.component.GlassPanel
import dev.joeherbert.availabliss.designsystem.component.GlassPillButton
import dev.joeherbert.availabliss.designsystem.component.LoadingIndicator
import dev.joeherbert.availabliss.designsystem.component.OrbLayout
import dev.joeherbert.availabliss.designsystem.component.ScreenHeader
import dev.joeherbert.availabliss.designsystem.component.SectionLabel
import dev.joeherbert.availabliss.designsystem.component.UserMessageEffect
import dev.joeherbert.availabliss.designsystem.component.chat.ProposalCard
import dev.joeherbert.availabliss.designsystem.component.chat.ProposalCardTone
import dev.joeherbert.availabliss.designsystem.component.chat.ProposalCardUi
import dev.joeherbert.availabliss.designsystem.component.rememberSnackbarHostState
import dev.joeherbert.availabliss.designsystem.icon.AvIcons
import dev.joeherbert.availabliss.designsystem.theme.AvailablissPreviewTheme
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import dev.joeherbert.availabliss.designsystem.theme.Shapes
import dev.joeherbert.availabliss.designsystem.theme.Spacing
import dev.joeherbert.availabliss.feature.common.AddToCalendarEffect
import dev.joeherbert.availabliss.feature.common.CalendarStatus
import dev.joeherbert.availabliss.feature.events.DeleteEventDialog
import dev.joeherbert.availabliss.feature.events.EventRow
import dev.joeherbert.availabliss.feature.events.EventRowUi
import dev.joeherbert.availabliss.feature.events.EventsUiState
import dev.joeherbert.availabliss.feature.events.EventsViewModel

/**
 * The group's Plans screen, matching iOS: what's coming up first (events, with Add to calendar),
 * then the Proposals heading with Add event and the open proposals — or a quiet note when
 * everything's settled.
 */
@Composable
fun GroupProposalsTab(
    onBack: () -> Unit,
    onOpenProposal: (String) -> Unit,
    onOpenEvent: (String) -> Unit,
    onAddEvent: () -> Unit,
    viewModel: GroupProposalsViewModel = hiltViewModel(),
    eventsViewModel: EventsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val events by eventsViewModel.state.collectAsStateWithLifecycle()
    val snackbar = rememberSnackbarHostState()
    UserMessageEffect(events.message, snackbar, eventsViewModel::onMessageShown)
    AddToCalendarEffect(events.calendar, eventsViewModel::onIntentHandled, eventsViewModel::chooseCalendar, eventsViewModel::cancelCalendarChoice)
    GroupProposalsContent(
        state = state,
        events = events,
        onBack = onBack,
        onOpenProposal = onOpenProposal,
        onOpenEvent = onOpenEvent,
        onAddEvent = onAddEvent,
        onRetry = {
            viewModel.refresh()
            eventsViewModel.refresh()
        },
        onAddToCalendar = eventsViewModel::onAddToCalendar,
        onDeleteEvent = eventsViewModel::onDelete,
    )
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) { AvSnackbarHost(snackbar, Modifier.navigationBarsPadding()) }
}

@Composable
private fun GroupProposalsContent(
    state: GroupProposalsUiState,
    events: EventsUiState,
    onBack: () -> Unit,
    onOpenProposal: (String) -> Unit,
    onOpenEvent: (String) -> Unit,
    onAddEvent: () -> Unit,
    onRetry: () -> Unit,
    onAddToCalendar: (String) -> Unit,
    onDeleteEvent: (String) -> Unit,
) {
    var confirmDelete by remember { mutableStateOf<EventRowUi?>(null) }
    val loaded = !(state.loading && state.open.isEmpty() && state.confirmed.isEmpty()) &&
        !(events.loading && events.upcoming.isEmpty() && events.past.isEmpty())
    AvailablissBackground(OrbLayout.Proposal) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            ScreenHeader(
                title = state.groupName.ifBlank { events.groupName }.let { if (it.isBlank()) "Plans" else "$it Plans" },
                onBack = onBack,
                backDescription = "Back to calendar",
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
            )
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = Spacing.tabBarClearance),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                (state.error ?: events.error)?.let { item { ErrorBanner(it, onRetry = onRetry) } }
                if (!loaded) {
                    item { LoadingIndicator() }
                    return@LazyColumn
                }
                // Coming up.
                if (events.upcoming.isNotEmpty()) {
                    item { Heading("Coming up") }
                    items(events.upcoming, key = { "event-" + it.id }) { row ->
                        EventRow(row, past = false, onOpen = { onOpenEvent(row.id) }, onAddToCalendar = { onAddToCalendar(row.id) }, onDelete = { confirmDelete = row })
                    }
                }
                // Proposals, with Add event.
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(top = if (events.upcoming.isEmpty()) 0.dp else 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Heading("Proposals", Modifier.weight(1f))
                        GlassPillButton("Add event", onAddEvent, icon = AvIcons.Plus)
                    }
                }
                when {
                    state.open.isNotEmpty() ->
                        items(state.open, key = { it.id }) { ProposalCard(it.card, onClick = { onOpenProposal(it.id) }) }
                    events.upcoming.isEmpty() -> item {
                        EmptyState(
                            title = "Nothing proposed yet",
                            body = "Pick a day that works on the calendar and propose it.",
                            icon = AvIcons.ProposalCheck,
                        )
                    }
                    else -> item { NothingWaiting() }
                }
            }
        }
    }
    confirmDelete?.let { row ->
        DeleteEventDialog(row.title, onConfirm = {
            onDeleteEvent(row.id)
            confirmDelete = null
        }, onDismiss = { confirmDelete = null })
    }
}

@Composable
private fun Heading(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = AvailablissTheme.type.titleSmall,
        color = AvailablissTheme.colors.ink,
        modifier = modifier.semantics { heading() },
    )
}

/** Everything's settled: nothing waiting on replies. */
@Composable
private fun NothingWaiting() {
    val colors = AvailablissTheme.colors
    GlassPanel(
        Modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = Shapes.row,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(AvIcons.ProposalCheck, null, tint = colors.muted, modifier = Modifier.size(16.dp))
            Text("No dates waiting for confirmation", style = AvailablissTheme.type.body, color = colors.muted)
        }
    }
}

@Preview
@Composable
private fun GroupProposalsPreview() {
    AvailablissPreviewTheme {
        GroupProposalsContent(
            state = GroupProposalsUiState(
                groupName = "Lake District crew",
                loading = false,
                open = listOf(ProposalListItem("1", ProposalCardUi("NOV", "13", "Ana proposed a time", "Fri 13 – Sun 15 Nov", "4 of 6 accepted"))),
                confirmed = listOf(ProposalListItem("2", ProposalCardUi("DEC", "4", "Tom proposed a time", "Fri 4 – Sun 6 Dec", "Everyone's in", ProposalCardTone.Confirmed))),
            ),
            events = EventsUiState(
                loading = false,
                upcoming = listOf(EventRowUi("e1", "Book club", "Thu 26 Nov · from 18:00", "Hana's", "NOV", "26", canDelete = true, calendar = CalendarStatus())),
            ),
            onBack = {}, onOpenProposal = {}, onOpenEvent = {}, onAddEvent = {}, onRetry = {},
            onAddToCalendar = {}, onDeleteEvent = {},
        )
    }
}
