package dev.joeherbert.availabliss.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.joeherbert.availabliss.core.network.model.TileColor
import dev.joeherbert.availabliss.designsystem.component.AvSnackbarHost
import dev.joeherbert.availabliss.designsystem.component.Avatar
import dev.joeherbert.availabliss.designsystem.component.AvatarPerson
import dev.joeherbert.availabliss.designsystem.component.AvatarStack
import dev.joeherbert.availabliss.designsystem.component.AvailablissBackground
import dev.joeherbert.availabliss.designsystem.component.EmptyState
import dev.joeherbert.availabliss.designsystem.component.ErrorBanner
import dev.joeherbert.availabliss.designsystem.component.GlassPillButton
import dev.joeherbert.availabliss.designsystem.component.GroupTile
import dev.joeherbert.availabliss.designsystem.component.LoadingIndicator
import dev.joeherbert.availabliss.designsystem.component.OrbLayout
import dev.joeherbert.availabliss.designsystem.component.PillTone
import dev.joeherbert.availabliss.designsystem.component.SectionTitle
import dev.joeherbert.availabliss.designsystem.component.StatusPill
import dev.joeherbert.availabliss.designsystem.component.SyncRing
import dev.joeherbert.availabliss.designsystem.component.UserMessageEffect
import dev.joeherbert.availabliss.designsystem.component.glass
import dev.joeherbert.availabliss.designsystem.component.rememberSnackbarHostState
import dev.joeherbert.availabliss.designsystem.icon.AvIcons
import dev.joeherbert.availabliss.designsystem.theme.AvailablissPreviewTheme
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import dev.joeherbert.availabliss.designsystem.theme.Shapes
import dev.joeherbert.availabliss.designsystem.theme.Spacing
import dev.joeherbert.availabliss.feature.common.AddToCalendarButton
import dev.joeherbert.availabliss.feature.common.AddToCalendarEffect
import dev.joeherbert.availabliss.feature.common.CalendarPickerRow

/** Groups home (Main.dc.html). */
@Composable
fun HomeScreen(
    onOpenGroup: (String) -> Unit,
    onNewGroup: () -> Unit,
    onOpenProfile: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = rememberSnackbarHostState()
    UserMessageEffect(state.message, snackbar, viewModel::messageShown)
    AddToCalendarEffect(state.calendar, viewModel::calendarIntentHandled)
    HomeContent(
        state = state,
        onOpenGroup = onOpenGroup,
        onNewGroup = onNewGroup,
        onOpenProfile = onOpenProfile,
        onAddToCalendar = viewModel::addUpNextToCalendar,
        onSelectCalendar = viewModel::selectCalendar,
        onRetry = { viewModel.refresh() },
        snackbar = { AvSnackbarHost(snackbar) },
    )
}

@Composable
fun HomeContent(
    state: HomeUiState,
    onOpenGroup: (String) -> Unit,
    onNewGroup: () -> Unit,
    onOpenProfile: () -> Unit,
    onAddToCalendar: () -> Unit,
    onSelectCalendar: (Long) -> Unit,
    onRetry: () -> Unit,
    snackbar: @Composable () -> Unit = {},
) {
    AvailablissBackground(OrbLayout.Main) {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = 28.dp, bottom = Spacing.tabBarClearance),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxl),
        ) {
            item(key = "header") { Header(state, onOpenProfile) }
            state.upNext?.let { upNext ->
                item(key = "upnext") { UpNextCard(upNext, state, onAddToCalendar, onSelectCalendar) }
            }
            state.error?.let { error -> item(key = "error") { ErrorBanner(error, onRetry = onRetry) } }
            item(key = "groups-title") {
                SectionTitle("Your groups") { GlassPillButton("New group", onNewGroup, icon = AvIcons.Plus) }
            }
            when {
                state.loading -> item(key = "loading") { LoadingIndicator() }
                state.groups.isEmpty() -> item(key = "empty") {
                    EmptyState(
                        title = "No groups yet",
                        body = "Start a group, invite your people, and we'll find the times that work for everyone.",
                        icon = AvIcons.Groups,
                        actionLabel = "New group",
                        onAction = onNewGroup,
                    )
                }
                // The concept spaces tiles 10dp apart inside an 18dp-gap column.
                else -> item(key = "groups") {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                        state.groups.forEach { tile -> GroupRow(tile, onClick = { onOpenGroup(tile.id) }) }
                    }
                }
            }
        }
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = Spacing.tabBarClearance)) { snackbar() }
    }
}

@Composable
private fun Header(state: HomeUiState, onOpenProfile: () -> Unit) {
    val colors = AvailablissTheme.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            Text(state.dateLabel, style = AvailablissTheme.type.labelRegular, color = colors.muted)
            Text(state.greeting, style = AvailablissTheme.type.displayMedium, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(
            Modifier
                .size(44.dp)
                .glass(CircleShape)
                .clickable(role = Role.Button, onClick = onOpenProfile)
                .semantics { contentDescription = "Your profile" },
            contentAlignment = Alignment.Center,
        ) {
            if (state.profileImage != null) {
                Avatar(AvatarPerson("me", state.profileInitial, state.profileImage), size = 42.dp, background = colors.glass)
            } else {
                Text(state.profileInitial, style = AvailablissTheme.type.bodyMedium, color = colors.ink)
            }
        }
    }
}

@Composable
private fun UpNextCard(upNext: UpNextUi, state: HomeUiState, onAddToCalendar: () -> Unit, onSelectCalendar: (Long) -> Unit) {
    val colors = AvailablissTheme.colors
    Column(
        Modifier.fillMaxWidth().glass(Shapes.cardLarge).padding(18.dp).semantics(mergeDescendants = false) { contentDescription = "Up next" },
        verticalArrangement = Arrangement.spacedBy(Spacing.xl),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            StatusPill(
                "Everyone's in",
                PillTone.Mint,
                icon = AvIcons.Check,
                style = AvailablissTheme.type.captionStrong,
                padding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
            )
            Text(upNext.groupName, style = AvailablissTheme.type.caption, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(upNext.whenLabel, style = AvailablissTheme.type.titleLarge, color = colors.ink)
            val detail = listOf(upNext.title, upNext.detail).filter { it.isNotBlank() }.joinToString(" · ")
            if (detail.isNotBlank()) Text(detail, style = AvailablissTheme.type.body, color = colors.muted)
        }
        if (!upNext.added && state.calendar.calendars.size > 1) {
            CalendarPickerRow(state.calendar, onSelectCalendar)
        }
        AddToCalendarButton(added = upNext.added, onClick = onAddToCalendar, height = 48.dp)
    }
}

@Composable
private fun GroupRow(tile: GroupTileUi, onClick: () -> Unit) {
    val colors = AvailablissTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .glass(Shapes.card)
            .clickable(role = Role.Button, onClickLabel = "Open ${tile.name}", onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        GroupTile(tile.monogram, colors.tile(tile.color))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tile.name, style = AvailablissTheme.type.bodyStrong, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (tile.unread > 0) UnreadDot(tile.unread)
                StatusPill(tile.badge, tile.tone)
            }
            Text(tile.timing, style = AvailablissTheme.type.labelRegular.copy(fontWeight = FontWeight.Normal), color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                if (tile.people.isNotEmpty()) {
                    AvatarStack(tile.people, modifier = Modifier.padding(end = 8.dp))
                } else {
                    Box(Modifier.height(22.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SyncRing(tile.syncProgress)
                    Text(tile.syncLabel, style = AvailablissTheme.type.caption, color = colors.muted)
                }
            }
        }
    }
}

@Composable
private fun UnreadDot(count: Int) {
    val colors = AvailablissTheme.colors
    Box(
        Modifier
            .defaultMinSize(minWidth = 18.dp)
            .height(18.dp)
            .clip(Shapes.pill)
            .background(colors.accent)
            .padding(horizontal = 5.dp)
            .semantics { contentDescription = "$count unread" },
        contentAlignment = Alignment.Center,
    ) {
        Text(if (count > 99) "99+" else count.toString(), style = AvailablissTheme.type.nano.copy(fontWeight = FontWeight.Bold), color = colors.onAccent)
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun HomePreview() {
    val people = listOf("Sam", "Priya", "Tom", "Ana", "Leo").map { AvatarPerson(it, it) }
    AvailablissPreviewTheme {
        HomeContent(
            state = HomeUiState(
                loading = false,
                dateLabel = "Sunday 1 November",
                greeting = "Hi, Sam",
                profileInitial = "S",
                upNext = UpNextUi("e1", "Book club", "Book club", "Wed 11 Nov · 18:30", "At Hana's · 8 going", added = false),
                groups = listOf(
                    GroupTileUi("1", "Lake District crew", "LD", TileColor.T1, "2 weekends work", PillTone.Indigo, "A weekend · Fri 6pm → Sun · Nov–Jan", people, 4 / 6f, "2 of 6 to sync", 3),
                    GroupTileUi("2", "Mum's 60th", "60", TileColor.T4, "Needs your reply", PillTone.Apricot, "One day · school holidays only", people, 1f, "All synced", 0),
                ),
            ),
            onOpenGroup = {}, onNewGroup = {}, onOpenProfile = {}, onAddToCalendar = {}, onSelectCalendar = {}, onRetry = {},
        )
    }
}
