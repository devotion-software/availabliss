package dev.joeherbert.availabliss.feature.proposal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.joeherbert.availabliss.designsystem.component.AvailablissBackground
import dev.joeherbert.availabliss.designsystem.component.EmptyState
import dev.joeherbert.availabliss.designsystem.component.ErrorBanner
import dev.joeherbert.availabliss.designsystem.component.GlassPanel
import dev.joeherbert.availabliss.designsystem.component.LoadingIndicator
import dev.joeherbert.availabliss.designsystem.component.OrbLayout
import dev.joeherbert.availabliss.designsystem.component.ScreenHeader
import dev.joeherbert.availabliss.designsystem.component.SectionLabel
import dev.joeherbert.availabliss.designsystem.component.chat.ProposalCard
import dev.joeherbert.availabliss.designsystem.component.chat.ProposalCardTone
import dev.joeherbert.availabliss.designsystem.component.chat.ProposalCardUi
import dev.joeherbert.availabliss.designsystem.icon.AvIcons
import dev.joeherbert.availabliss.designsystem.theme.AvailablissPreviewTheme
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import dev.joeherbert.availabliss.designsystem.theme.Shapes
import dev.joeherbert.availabliss.designsystem.theme.Spacing

/** The group's "Proposals" tab (shown above the group tab bar). */
@Composable
fun GroupProposalsTab(
    onOpenProposal: (String) -> Unit,
    onOpenEvents: () -> Unit,
    viewModel: GroupProposalsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    GroupProposalsContent(state, onOpenProposal, onOpenEvents, viewModel::refresh)
}

@Composable
private fun GroupProposalsContent(
    state: GroupProposalsUiState,
    onOpenProposal: (String) -> Unit,
    onOpenEvents: () -> Unit,
    onRetry: () -> Unit,
) {
    AvailablissBackground(OrbLayout.Proposal) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            ScreenHeader(
                title = "Proposals",
                subtitle = state.groupName.ifBlank { null },
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
            )
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = Spacing.tabBarClearance),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                state.error?.let { item { ErrorBanner(it, onRetry = onRetry) } }
                if (state.loading && state.open.isEmpty() && state.confirmed.isEmpty()) item { LoadingIndicator() }
                if (!state.loading && state.error == null && state.open.isEmpty() && state.confirmed.isEmpty()) {
                    item {
                        EmptyState(
                            title = "No proposals yet",
                            body = "Pick a time that works on the calendar and propose it to the group.",
                            icon = AvIcons.ProposalCheck,
                        )
                    }
                }
                if (state.open.isNotEmpty()) {
                    item { SectionLabel("Waiting for replies") }
                    items(state.open, key = { it.id }) { ProposalCard(it.card, onClick = { onOpenProposal(it.id) }) }
                }
                if (state.confirmed.isNotEmpty()) {
                    item { SectionLabel("Confirmed", Modifier.padding(top = 6.dp)) }
                    items(state.confirmed, key = { it.id }) { ProposalCard(it.card, onClick = { onOpenProposal(it.id) }, actionLabel = "View") }
                }
                item { EventsRow(onOpenEvents) }
            }
        }
    }
}

@Composable
private fun EventsRow(onClick: () -> Unit) {
    val colors = AvailablissTheme.colors
    GlassPanel(
        Modifier.fillMaxWidth().padding(top = 6.dp),
        shape = Shapes.row,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        onClick = onClick,
        onClickLabel = "Open events",
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(AvIcons.Calendar, null, tint = colors.accentInk, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f)) {
                Text("Events", style = AvailablissTheme.type.bodyMedium, color = colors.ink)
                Text("Plans added to this group", style = AvailablissTheme.type.caption.copy(fontWeight = FontWeight.Normal), color = colors.muted)
            }
            Icon(AvIcons.Forward, null, tint = colors.muted, modifier = Modifier.size(16.dp))
        }
    }
}

@Preview
@Composable
private fun GroupProposalsPreview() {
    AvailablissPreviewTheme {
        GroupProposalsContent(
            GroupProposalsUiState(
                groupName = "Lake District crew",
                loading = false,
                open = listOf(ProposalListItem("1", ProposalCardUi("NOV", "13", "Ana proposed a time", "Fri 13 – Sun 15 Nov", "4 of 6 accepted"))),
                confirmed = listOf(ProposalListItem("2", ProposalCardUi("DEC", "4", "Tom proposed a time", "Fri 4 – Sun 6 Dec", "Everyone's in", ProposalCardTone.Confirmed))),
            ),
            {}, {}, {},
        )
    }
}
