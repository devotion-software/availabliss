package dev.joeherbert.availabliss.feature.myplans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.joeherbert.availabliss.designsystem.component.AvSnackbarHost
import dev.joeherbert.availabliss.designsystem.component.AvailablissBackground
import dev.joeherbert.availabliss.designsystem.component.ButtonStyle
import dev.joeherbert.availabliss.designsystem.component.EmptyState
import dev.joeherbert.availabliss.designsystem.component.ErrorBanner
import dev.joeherbert.availabliss.designsystem.component.GlassPanel
import dev.joeherbert.availabliss.designsystem.component.LoadingIndicator
import dev.joeherbert.availabliss.designsystem.component.OrbLayout
import dev.joeherbert.availabliss.designsystem.component.PillTone
import dev.joeherbert.availabliss.designsystem.component.PrimaryButton
import dev.joeherbert.availabliss.designsystem.component.ScreenHeader
import dev.joeherbert.availabliss.designsystem.component.SectionLabel
import dev.joeherbert.availabliss.designsystem.component.StatusPill
import dev.joeherbert.availabliss.designsystem.component.UserMessageEffect
import dev.joeherbert.availabliss.designsystem.component.chat.DateTile
import dev.joeherbert.availabliss.designsystem.component.rememberSnackbarHostState
import dev.joeherbert.availabliss.designsystem.icon.AvIcons
import dev.joeherbert.availabliss.designsystem.theme.AvailablissPreviewTheme
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import dev.joeherbert.availabliss.designsystem.theme.Shapes
import dev.joeherbert.availabliss.designsystem.theme.Spacing
import dev.joeherbert.availabliss.feature.calendars.CalendarFeedCard
import dev.joeherbert.availabliss.feature.calendars.CalendarFeedViewModel
import dev.joeherbert.availabliss.feature.common.AddToCalendarEffect
import dev.joeherbert.availabliss.feature.common.LocalShowsAddToCalendar

/**
 * My plans (from Activity): Coming up, Can't make it (quieter, only when there are any) and Past,
 * across every group. A row opens its proposal, or its group when it was added straight to the calendar.
 */
@Composable
fun MyPlansScreen(
    onBack: () -> Unit,
    onOpenProposal: (String) -> Unit,
    onOpenGroup: (String) -> Unit,
    viewModel: MyPlansViewModel = hiltViewModel(),
    feedViewModel: CalendarFeedViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val feedState by feedViewModel.state.collectAsStateWithLifecycle()
    val snackbar = rememberSnackbarHostState()
    UserMessageEffect(state.message, snackbar, viewModel::messageShown)
    UserMessageEffect(feedState.message, snackbar) { feedViewModel.onMessageShown() }
    AddToCalendarEffect(state.calendar, viewModel::onIntentHandled, viewModel::chooseCalendar, viewModel::cancelCalendarChoice)
    MyPlansContent(
        state = state,
        snackbar = snackbar,
        onBack = onBack,
        onOpen = { row -> row.proposalId?.let(onOpenProposal) ?: onOpenGroup(row.groupId) },
        onAddAll = viewModel::addAll,
        onRetry = viewModel::refresh,
        feedCard = {
            CalendarFeedCard(
                state = feedState,
                onLoad = feedViewModel::load,
                onCopied = feedViewModel::onCopied,
                onAskReset = feedViewModel::askReset,
                onCancelReset = feedViewModel::cancelReset,
                onReset = feedViewModel::reset,
            )
        },
    )
}

@Composable
fun MyPlansContent(
    state: MyPlansUiState,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onOpen: (MyPlanRowUi) -> Unit,
    onAddAll: () -> Unit,
    onRetry: () -> Unit,
    /** "Subscribe to your plans", at the end. */
    feedCard: @Composable () -> Unit = {},
) {
    val sections = state.sections
    val showsAddToCalendar = LocalShowsAddToCalendar.current
    AvailablissBackground(OrbLayout.Proposal) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            ScreenHeader(
                title = "My plans",
                subtitle = "Across all your groups",
                onBack = onBack,
                backDescription = "Back to Activity",
                modifier = Modifier.padding(start = Spacing.screen, end = Spacing.screen, top = Spacing.screen, bottom = Spacing.s),
            )
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = Spacing.s, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                state.error?.let { item(key = "error") { ErrorBanner(it, onRetry = onRetry) } }
                if (state.loading && sections.isEmpty) item(key = "loading") { LoadingIndicator() }
                if (!state.loading && state.error == null && sections.isEmpty) {
                    item(key = "empty") {
                        EmptyState(
                            title = "No plans yet",
                            body = "When a time's confirmed in one of your groups, it shows up here.",
                            icon = AvIcons.Calendar,
                        )
                    }
                }
                if (sections.goingTo.isNotEmpty()) {
                    item(key = "going-label") { SectionLabel("Coming up", Modifier.padding(top = 4.dp)) }
                    if (state.toAddCount > 0 && showsAddToCalendar) {
                        item(key = "add-all") {
                            PrimaryButton(
                                text = "Add all to calendar",
                                onClick = onAddAll,
                                icon = AvIcons.CalendarAdd,
                                height = 46.dp,
                                shape = Shapes.controlSmall,
                                textStyle = AvailablissTheme.type.bodySmallStrong,
                                loading = state.adding,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    items(sections.goingTo, key = { "going-" + it.id }) { row -> PlanRow(row, onClick = { onOpen(row) }) }
                }
                if (sections.notGoing.isNotEmpty()) {
                    item(key = "not-going-label") { SectionLabel("Can't make it", Modifier.padding(top = 8.dp)) }
                    items(sections.notGoing, key = { "not-going-" + it.id }) { row -> PlanRow(row, quiet = true, onClick = { onOpen(row) }) }
                }
                if (sections.past.isNotEmpty()) {
                    item(key = "past-label") { SectionLabel("Past", Modifier.padding(top = 8.dp)) }
                    items(sections.past, key = { "past-" + it.id }) { row -> PlanRow(row, muted = true, onClick = { onOpen(row) }) }
                }
                if (!state.loading || !sections.isEmpty) item(key = "feed") { Box(Modifier.padding(top = 8.dp)) { feedCard() } }
            }
        }
        AvSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
}

/**
 * A plan: date tile, title, time and group, with a Maybe / Not replied pill and a mark when it's in
 * your calendar. [quiet] (can't make it) fades it; [muted] (past) greys the tile.
 */
@Composable
private fun PlanRow(row: MyPlanRowUi, onClick: () -> Unit, quiet: Boolean = false, muted: Boolean = false) {
    val colors = AvailablissTheme.colors
    val type = AvailablissTheme.type
    GlassPanel(
        Modifier
            .fillMaxWidth()
            .alpha(if (quiet) 0.65f else 1f)
            .semantics(mergeDescendants = true) {
                contentDescription = listOfNotNull(
                    row.title,
                    "${row.day} ${row.month}",
                    row.time,
                    row.groupName,
                    row.pill,
                    if (row.inCalendar) "In your calendar" else null,
                ).joinToString(", ")
            },
        shape = Shapes.row,
        contentPadding = PaddingValues(12.dp),
        onClick = onClick,
        onClickLabel = if (row.proposalId != null) "Open plan" else "Open ${row.groupName}",
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DateTile(row.month, row.day, muted = muted || quiet)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(row.title, style = type.bodyMedium, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(row.time, style = type.caption, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(row.groupName, style = type.caption.copy(fontWeight = FontWeight.Normal), color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                row.pill?.let { StatusPill(it, if (it == "Maybe") PillTone.Apricot else PillTone.Neutral) }
                if (row.inCalendar) {
                    Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                        Icon(AvIcons.CalendarSync, null, tint = colors.okInk, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun MyPlansPreview() {
    AvailablissPreviewTheme {
        MyPlansContent(
            state = MyPlansUiState(
                loading = false,
                toAddCount = 1,
                sections = MyPlanSections(
                    goingTo = listOf(
                        MyPlanRowUi("1", "g", "p", "NOV", "13", "Lake District weekend", "Fri 13 – Sun 15 Nov", "Lake District crew", inCalendar = true),
                        MyPlanRowUi("2", "g2", "p2", "NOV", "18", "Book club", "18:30 – 21:00", "Book club", pill = "Maybe"),
                    ),
                    notGoing = listOf(MyPlanRowUi("3", "g3", "p3", "NOV", "20", "Five-a-side", "19:00 – 20:00", "Football")),
                    past = listOf(MyPlanRowUi("4", "g2", null, "OCT", "21", "Book club", "All day", "Book club")),
                ),
            ),
            snackbar = SnackbarHostState(),
            onBack = {},
            onOpen = {},
            onAddAll = {},
            onRetry = {},
        )
    }
}
