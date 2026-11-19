package dev.joeherbert.availabliss.feature.group

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.joeherbert.availabliss.core.data.MuteDuration
import dev.joeherbert.availabliss.designsystem.component.AvSnackbarHost
import dev.joeherbert.availabliss.designsystem.component.ConfirmOption
import dev.joeherbert.availabliss.designsystem.component.ConfirmSheet
import dev.joeherbert.availabliss.designsystem.component.GlassIconButton
import dev.joeherbert.availabliss.designsystem.component.UserMessageEffect
import dev.joeherbert.availabliss.designsystem.component.rememberSnackbarHostState
import dev.joeherbert.availabliss.designsystem.icon.AvIcons
import dev.joeherbert.availabliss.designsystem.theme.AvailablissPreviewTheme
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import dev.joeherbert.availabliss.designsystem.theme.Spacing
import dev.joeherbert.availabliss.feature.availability.GroupCalendarTab
import dev.joeherbert.availabliss.feature.proposal.PlansCard
import dev.joeherbert.availabliss.feature.proposal.ProposalPrefill

/**
 * Height of the group's pinned header below the status bar (its row plus a short band); the calendar
 * leaves this much room. Its fade hangs a further [GroupTopBarFade] over the calendar, shown only once
 * the calendar is scrolled, so there's no big gap above the first card at the top.
 */
val GroupTopBarHeight = 68.dp

private val GroupTopBarFade = 48.dp

/**
 * A group: its calendar, under a pinned header (back, name, chat, "More"). Chat, Plans, Timing,
 * Members, Group settings and "New group with these people" are their own screens pushed on top;
 * "Mute notifications" (or "Unmute") acts here. The app's tab bar stays at the bottom.
 */
@Composable
fun GroupScreen(
    onBack: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenPlans: () -> Unit,
    onOpenTiming: () -> Unit,
    onOpenMembers: () -> Unit,
    onOpenSettings: () -> Unit,
    onNewGroupWithPeople: () -> Unit,
    onOpenProposal: (String) -> Unit,
    onCreateProposal: (ProposalPrefill) -> Unit,
    viewModel: GroupViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = rememberSnackbarHostState()
    var choosingMute by rememberSaveable { mutableStateOf(false) }
    UserMessageEffect(message, snackbar, viewModel::onMessageShown)
    val calendarScroll = rememberScrollState()
    Box(Modifier.fillMaxSize()) {
        GroupCalendarTab(
            onOpenTiming = onOpenTiming,
            onCreateProposal = onCreateProposal,
            plans = { PlansCard(onOpenProposal = onOpenProposal, onSeeAll = onOpenPlans) },
            onOpenChat = onOpenChat,
            scrollState = calendarScroll,
        )
        GroupTopBar(
            state,
            calendarScroll,
            GroupTopBarActions(
                onBack, onOpenChat, onOpenPlans, onOpenTiming, onOpenMembers, onOpenSettings, onNewGroupWithPeople,
                onMute = { choosingMute = true },
                onUnmute = viewModel::unmute,
            ),
            Modifier.align(Alignment.TopCenter),
        )
        AvSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = Spacing.tabBarClearance))
    }
    if (choosingMute) {
        ConfirmSheet(
            title = "Mute notifications",
            message = "No pushes from ${state.groupName.ifEmpty { "this group" }}. It's all still in Activity.",
            options = MuteDuration.entries.map { duration ->
                ConfirmOption(duration.label, {
                    choosingMute = false
                    viewModel.mute(duration)
                }, kind = ConfirmOption.Kind.Secondary)
            },
            onDismiss = { choosingMute = false },
        )
    }
}

private class GroupTopBarActions(
    val onBack: () -> Unit,
    val onOpenChat: () -> Unit,
    val onOpenPlans: () -> Unit,
    val onOpenTiming: () -> Unit,
    val onOpenMembers: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onNewGroupWithPeople: () -> Unit,
    val onMute: () -> Unit,
    val onUnmute: () -> Unit,
)

@Composable
private fun GroupTopBar(state: GroupShellUiState, scroll: ScrollState, actions: GroupTopBarActions, modifier: Modifier = Modifier) {
    val colors = AvailablissTheme.colors
    val fadeIn = with(LocalDensity.current) { 24.dp.toPx() }
    Column(modifier.fillMaxWidth()) {
        // Nearly opaque once scrolled: without a backdrop blur (no library just for that), anything
        // lighter lets the calendar's text show through under the group's name. At the top it's clear,
        // so the page's background runs on behind the header without an edge where the header stops.
        // Read in the draw phase so scrolling doesn't recompose the header.
        val wash = Brush.verticalGradient(0f to colors.bg.copy(alpha = 0.94f), 1f to colors.bg.copy(alpha = 0.88f))
        Row(
            Modifier
                .fillMaxWidth()
                .drawBehind {
                    val alpha = (scroll.value / fadeIn).coerceIn(0f, 1f)
                    if (alpha > 0f) drawRect(wash, alpha = alpha)
                }
                .statusBarsPadding()
                .height(GroupTopBarHeight)
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GroupTopBarRow(state, actions)
        }
        // The fade over the calendar: hidden at the top (it would dim the first card), easing in over
        // the first 24dp of scrolling. Read in the layer so scrolling doesn't recompose the header.
        Box(
            Modifier
                .fillMaxWidth()
                .height(GroupTopBarFade)
                .graphicsLayer { alpha = (scroll.value / fadeIn).coerceIn(0f, 1f) }
                .background(
                    Brush.verticalGradient(
                        0f to colors.bg.copy(alpha = 0.88f),
                        0.3f to colors.bg.copy(alpha = 0.62f),
                        0.6f to colors.bg.copy(alpha = 0.28f),
                        1f to colors.bg.copy(alpha = 0f),
                    ),
                ),
        )
    }
}

@Composable
private fun RowScope.GroupTopBarRow(state: GroupShellUiState, actions: GroupTopBarActions) {
    val colors = AvailablissTheme.colors
    val type = AvailablissTheme.type
    GlassIconButton(AvIcons.Back, "Back to groups", actions.onBack)
    Column(Modifier.weight(1f)) {
        Text(state.groupName, style = type.headlineSmall, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(state.membersLine, style = type.caption.copy(fontWeight = FontWeight.Normal), color = colors.muted, maxLines = 1)
    }
    ChatButton(state.unreadCount, actions.onOpenChat)
    MoreMenu(state.isAdmin, state.hasOthers, state.muted, actions)
}

@Composable
private fun ChatButton(unread: Int, onClick: () -> Unit) {
    val colors = AvailablissTheme.colors
    Box {
        GlassIconButton(AvIcons.Chat, if (unread > 0) "Chat, $unread unread" else "Chat", onClick)
        if (unread > 0) {
            Text(
                if (unread > 99) "99+" else unread.toString(),
                style = AvailablissTheme.type.nano.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFF1A1B3A),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-2).dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.warn)
                    .padding(horizontal = 5.dp)
                    .clearAndSetSemantics { },
            )
        }
    }
}

@Composable
private fun MoreMenu(isAdmin: Boolean, hasOthers: Boolean, muted: Boolean, actions: GroupTopBarActions) {
    var open by remember { mutableStateOf(false) }
    val colors = AvailablissTheme.colors
    Box {
        GlassIconButton(AvIcons.More, "More", { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = colors.bg) {
            listOfNotNull(
                Triple("Plans", AvIcons.ProposalCheck, actions.onOpenPlans),
                Triple("Timing", AvIcons.Sliders, actions.onOpenTiming),
                // Only owners and admins can invite, so only they see "invites".
                if (isAdmin) Triple("Members and invites", AvIcons.AddPerson, actions.onOpenMembers)
                else Triple("Members", AvIcons.People, actions.onOpenMembers),
                // Another group with the same people (anyone in it can start one).
                if (hasOthers) Triple("New group with these people", AvIcons.Plus, actions.onNewGroupWithPeople) else null,
                if (muted) Triple("Unmute", AvIcons.Bell, actions.onUnmute)
                else Triple("Mute notifications", AvIcons.BellOff, actions.onMute),
                Triple("Group settings", AvIcons.Settings, actions.onOpenSettings),
            ).forEach { (label, icon, onClick) ->
                MenuItem(label, icon) {
                    open = false
                    onClick()
                }
            }
        }
    }
}

@Composable
private fun MenuItem(label: String, icon: ImageVector, onClick: () -> Unit) {
    val colors = AvailablissTheme.colors
    DropdownMenuItem(
        text = { Text(label, style = AvailablissTheme.type.bodySmallStrong, color = colors.ink) },
        leadingIcon = { Icon(icon, null, tint = colors.ink, modifier = Modifier.size(18.dp)) },
        onClick = onClick,
        modifier = Modifier.semantics { role = Role.Button },
    )
}

@Preview(widthDp = 390)
@Composable
private fun GroupTopBarPreview() {
    AvailablissPreviewTheme {
        GroupTopBar(
            GroupShellUiState("Lake District crew", unreadCount = 3, isAdmin = true, membersLine = "6 members · you're an admin"),
            rememberScrollState(),
            GroupTopBarActions({}, {}, {}, {}, {}, {}, {}, {}, {}),
        )
    }
}
