package dev.joeherbert.availabliss.feature.home

import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.joeherbert.availabliss.core.network.model.TileColor
import dev.joeherbert.availabliss.designsystem.component.AvSnackbarHost
import dev.joeherbert.availabliss.designsystem.component.AvailablissBackground
import dev.joeherbert.availabliss.designsystem.component.Avatar
import dev.joeherbert.availabliss.designsystem.component.AvatarPerson
import dev.joeherbert.availabliss.designsystem.component.AvatarStack
import dev.joeherbert.availabliss.designsystem.component.CoverPhoto
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
import dev.joeherbert.availabliss.feature.common.CalendarStatus
import kotlinx.coroutines.launch

/** Groups home (Main.dc.html). */
@Composable
fun HomeScreen(
    onOpenGroup: (String) -> Unit,
    onOpenGroupChat: (String) -> Unit,
    onOpenProposal: (String) -> Unit,
    onOpenGroupPlans: (String) -> Unit,
    onNewGroup: () -> Unit,
    onOpenProfile: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = rememberSnackbarHostState()
    UserMessageEffect(state.message, snackbar, viewModel::messageShown)
    AddToCalendarEffect(state.calendar, viewModel::calendarIntentHandled, viewModel::chooseCalendar, viewModel::cancelCalendarChoice)
    HomeContent(
        state = state,
        onOpenGroup = onOpenGroup,
        onOpenGroupChat = onOpenGroupChat,
        // A confirmed plan opens its proposal; an event added directly opens the group's plans.
        onOpenUpNext = { upNext -> upNext.proposalId?.let(onOpenProposal) ?: onOpenGroupPlans(upNext.groupId) },
        onNewGroup = onNewGroup,
        onOpenProfile = onOpenProfile,
        onAddToCalendar = viewModel::addToCalendar,
        onRetry = { viewModel.refresh() },
        snackbar = { AvSnackbarHost(snackbar) },
    )
}

@Composable
fun HomeContent(
    state: HomeUiState,
    onOpenGroup: (String) -> Unit,
    onOpenGroupChat: (String) -> Unit,
    onOpenUpNext: (UpNextUi) -> Unit,
    onNewGroup: () -> Unit,
    onOpenProfile: () -> Unit,
    onAddToCalendar: (eventId: String) -> Unit,
    onRetry: () -> Unit,
    snackbar: @Composable () -> Unit = {},
) {
    // The Archived section starts collapsed.
    var showsArchived by rememberSaveable { mutableStateOf(false) }
    val active = state.groups.filterNot { it.archived }
    val archived = state.groups.filter { it.archived }
    AvailablissBackground(OrbLayout.Main) {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = 28.dp, bottom = Spacing.tabBarClearance),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxl),
        ) {
            item(key = "header") { Header(state, onOpenProfile) }
            if (state.upNext.isNotEmpty()) {
                item(key = "upnext") { UpNextPager(state.upNext, onOpen = onOpenUpNext, onAddToCalendar = onAddToCalendar) }
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
                else -> {
                    if (active.isNotEmpty()) {
                        item(key = "groups") {
                            Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                                active.forEach { tile -> GroupRow(tile, onClick = { onOpenGroup(tile.id) }, onOpenChat = { onOpenGroupChat(tile.id) }) }
                            }
                        }
                    }
                    if (archived.isNotEmpty()) {
                        item(key = "archived") {
                            Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                                ArchivedToggle(archived.size, showsArchived) { showsArchived = !showsArchived }
                                if (showsArchived) {
                                    archived.forEach { tile -> GroupRow(tile, onClick = { onOpenGroup(tile.id) }, onOpenChat = { onOpenGroupChat(tile.id) }) }
                                }
                            }
                        }
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

/**
 * The next confirmed plans, one card each, swiped left and right with page dots under them (no
 * dots when there's only one). Each card hugs its content and the pager takes the height of the
 * card you're on, so a short card doesn't leave a gap.
 */
@Composable
private fun UpNextPager(plans: List<UpNextUi>, onOpen: (UpNextUi) -> Unit, onAddToCalendar: (String) -> Unit) {
    if (plans.size == 1) {
        val plan = plans.first()
        UpNextCard(plan, onOpen = { onOpen(plan) }, onAddToCalendar = { onAddToCalendar(plan.key) })
        return
    }
    val pager = rememberPagerState { plans.size }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    // Each card's natural height, by plan.
    val heights = remember(plans.map { it.key }) { mutableStateMapOf<String, Int>() }
    val current = heights[plans.getOrNull(pager.currentPage)?.key] ?: heights.values.maxOrNull()
    val shadow = 14.dp
    val height by animateDpAsState(with(density) { (current ?: 0).toDp() } + shadow, label = "upNextHeight")
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        HorizontalPager(
            state = pager,
            // Spans the screen's full width, so the cards' shadows aren't cut off at the sides and the
            // next card slides in from the screen's edge; the padding puts each card back in line.
            modifier = Modifier
                .then(if (current != null) Modifier.height(height) else Modifier)
                .layout { measurable, constraints ->
                    val extra = (Spacing.screen * 2).roundToPx()
                    val width = constraints.maxWidth + extra
                    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
                    layout(constraints.maxWidth, placeable.height) { placeable.place(-extra / 2, 0) }
                },
            contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = shadow),
            pageSpacing = Spacing.screen,
            beyondViewportPageCount = plans.size,
            verticalAlignment = Alignment.Top,
            key = { plans[it].key },
        ) { page ->
            val plan = plans[page]
            UpNextCard(
                plan,
                onOpen = { onOpen(plan) },
                onAddToCalendar = { onAddToCalendar(plan.key) },
                modifier = Modifier
                    .wrapContentHeight(Alignment.Top, unbounded = true)
                    .onSizeChanged { heights[plan.key] = it.height },
            )
        }
        PageDots(count = plans.size, current = pager.currentPage) { scope.launch { pager.animateScrollToPage(it) } }
    }
}

/** A row of small dots, the current one wider and darker; tapping one goes to it. */
@Composable
private fun PageDots(count: Int, current: Int, onSelect: (Int) -> Unit) {
    val colors = AvailablissTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = "Plan ${current + 1} of $count" },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val selected = index == current
            val width by animateDpAsState(if (selected) 18.dp else 7.dp, label = "dot")
            Box(
                Modifier
                    .clip(CircleShape)
                    .clickable(onClickLabel = "Show plan ${index + 1}") { onSelect(index) }
                    .padding(horizontal = 3.dp, vertical = 8.dp)
                    .size(width = width, height = 7.dp)
                    .clip(CircleShape)
                    .background(if (selected) colors.ink else colors.muted.copy(alpha = 0.35f)),
            )
        }
    }
}

@Composable
private fun UpNextCard(upNext: UpNextUi, onOpen: () -> Unit, onAddToCalendar: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AvailablissTheme.colors
    // The whole card opens the plan; Add to calendar and Change inside it keep their own taps.
    Column(
        modifier
            .fillMaxWidth()
            .glass(Shapes.cardLarge)
            .clickable(role = Role.Button, onClickLabel = "Open plan", onClick = onOpen)
            .padding(18.dp)
            .semantics(mergeDescendants = false) { contentDescription = "Up next" },
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xl)) {
            upNext.image?.let { CoverPhoto(it) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                StatusPill(
                    "It's on",
                    PillTone.Mint,
                    icon = AvIcons.Check,
                    style = AvailablissTheme.type.captionStrong,
                    padding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
                )
                Row(
                    Modifier.weight(1f, fill = false).padding(start = Spacing.m),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (upNext.groupMonogram.isNotEmpty()) {
                        GroupTile(upNext.groupMonogram, colors.tile(upNext.groupColor), image = upNext.groupImage, size = 20.dp, radius = 7.dp)
                    }
                    Text(upNext.groupName, style = AvailablissTheme.type.caption, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(upNext.whenLabel, style = AvailablissTheme.type.titleLarge, color = colors.ink)
                val detail = listOf(upNext.title, upNext.detail).filter { it.isNotBlank() }.joinToString(" · ")
                if (detail.isNotBlank()) Text(detail, style = AvailablissTheme.type.body, color = colors.muted)
            }
        }
        AddToCalendarButton(upNext.calendar, onClick = onAddToCalendar, height = 48.dp, modifier = Modifier.padding(top = Spacing.xl), showAddedChip = false)
    }
}

@Composable
private fun GroupRow(tile: GroupTileUi, onClick: () -> Unit, onOpenChat: () -> Unit) {
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
        GroupTile(tile.monogram, colors.tile(tile.color), image = tile.image)
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
                    if (tile.syncProgress >= 1f) SyncedTick() else SyncRing(tile.syncProgress)
                    Text(tile.syncLabel, style = AvailablissTheme.type.caption, color = colors.muted)
                }
            }
            if (tile.unread > 0) UnreadChatButton(tile.unread, onOpenChat, Modifier.padding(top = 4.dp))
        }
    }
}

/** "3 new messages · Open chat" strip at the foot of a group card. */
@Composable
private fun UnreadChatButton(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AvailablissTheme.colors
    val label = if (count == 1) "1 new message" else "${if (count > 99) "99+" else count} new messages"
    Row(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 36.dp)
            .clip(Shapes.row)
            .background(colors.accentSoft)
            .clickable(role = Role.Button, onClickLabel = "Open chat", onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(AvIcons.Chat, contentDescription = null, tint = colors.accentInk, modifier = Modifier.size(15.dp))
        Text(label, style = AvailablissTheme.type.captionStrong, color = colors.accentInk, modifier = Modifier.weight(1f))
        Text("Open chat", style = AvailablissTheme.type.captionStrong, color = colors.accentInk)
        Icon(AvIcons.Forward, contentDescription = null, tint = colors.accentInk, modifier = Modifier.size(13.dp))
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
                upNext = listOf(
                    UpNextUi("e1", "g1", "p1", "Book club", "Book club", "Wed 11 Nov · 18:30", "At Hana's · 8 going", calendar = CalendarStatus(), groupMonogram = "BC", groupColor = TileColor.T3),
                    UpNextUi("e2", "g2", null, "Lake District crew", "Pub quiz", "Tue 24 Nov · 19:30", "At The Bell · 5 going", calendar = CalendarStatus(), groupMonogram = "LD", groupColor = TileColor.T1),
                ),
                groups = listOf(
                    GroupTileUi("1", "Lake District crew", "LD", TileColor.T1, "2 weekends work", PillTone.Indigo, "A weekend · Fri 6pm → Sun · Nov–Jan", people, 4 / 6f, "2 of 6 to sync", 3),
                    GroupTileUi("2", "Mum's 60th", "60", TileColor.T4, "Needs your reply", PillTone.Apricot, "One day · school holidays only", people, 1f, "All synced", 0),
                ),
            ),
            onOpenGroup = {},
            onOpenGroupChat = {},
            onOpenUpNext = {},
            onNewGroup = {},
            onOpenProfile = {},
            onAddToCalendar = {},
            onRetry = {},
        )
    }
}

/** Green tick in place of the sync ring once everyone has synced. */
@Composable
private fun SyncedTick() {
    val colors = AvailablissTheme.colors
    Box(Modifier.size(16.dp).clip(CircleShape).background(colors.ok), contentAlignment = Alignment.Center) {
        Icon(AvIcons.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(11.dp))
    }
}

/** "Archived (2)" with a chevron: shows or hides the archived groups under it. */
@Composable
private fun ArchivedToggle(count: Int, expanded: Boolean, onToggle: () -> Unit) {
    val colors = AvailablissTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 44.dp)
            .clip(Shapes.inner)
            .clickable(role = Role.Button, onClick = onToggle)
            .semantics {
                heading()
                stateDescription = if (expanded) "Shown" else "Hidden"
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Archived ($count)", style = AvailablissTheme.type.bodySmallStrong, color = colors.muted)
        Icon(AvIcons.ChevronDown, null, tint = colors.muted, modifier = Modifier.size(14.dp).rotate(if (expanded) 0f else -90f))
    }
}
