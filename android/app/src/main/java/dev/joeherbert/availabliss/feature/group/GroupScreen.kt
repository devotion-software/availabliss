package dev.joeherbert.availabliss.feature.group

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import dev.joeherbert.availabliss.designsystem.component.GlassIconButton
import dev.joeherbert.availabliss.designsystem.icon.AvIcons
import dev.joeherbert.availabliss.designsystem.theme.AvailablissPreviewTheme
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import dev.joeherbert.availabliss.feature.availability.GroupCalendarTab
import dev.joeherbert.availabliss.feature.proposal.PlansCard
import dev.joeherbert.availabliss.feature.proposal.ProposalPrefill

/** Height of the group's pinned header below the status bar; the calendar leaves this much room. */
val GroupTopBarHeight = 64.dp

/**
 * A group: its calendar, under a pinned header (back, name, chat, "More"). Chat, Plans, Timing,
 * Members, Group settings and "New group with these people" are their own screens pushed on top. The app's tab bar stays at the bottom.
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
    Box(Modifier.fillMaxSize()) {
        GroupCalendarTab(
            onOpenTiming = onOpenTiming,
            onCreateProposal = onCreateProposal,
            plans = { PlansCard(onOpenProposal = onOpenProposal, onSeeAll = onOpenPlans) },
        )
        GroupTopBar(
            state,
            GroupTopBarActions(onBack, onOpenChat, onOpenPlans, onOpenTiming, onOpenMembers, onOpenSettings, onNewGroupWithPeople),
            Modifier.align(Alignment.TopCenter),
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
)

@Composable
private fun GroupTopBar(state: GroupShellUiState, actions: GroupTopBarActions, modifier: Modifier = Modifier) {
    val colors = AvailablissTheme.colors
    val type = AvailablissTheme.type
    Row(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(0.8f to colors.bg.copy(alpha = 0.92f), 1f to colors.bg.copy(alpha = 0f)))
            .statusBarsPadding()
            .height(GroupTopBarHeight)
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        GlassIconButton(AvIcons.Back, "Back to groups", actions.onBack)
        Column(Modifier.weight(1f)) {
            Text(state.groupName, style = type.headlineSmall, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(state.membersLine, style = type.caption.copy(fontWeight = FontWeight.Normal), color = colors.muted, maxLines = 1)
        }
        ChatButton(state.unreadCount, actions.onOpenChat)
        MoreMenu(state.isAdmin, state.hasOthers, actions)
    }
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
private fun MoreMenu(isAdmin: Boolean, hasOthers: Boolean, actions: GroupTopBarActions) {
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
                Triple("Group settings", AvIcons.Settings, actions.onOpenSettings),
            ).forEach { (label, icon, onClick) -> MenuItem(label, icon) { open = false; onClick() } }
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
            GroupTopBarActions({}, {}, {}, {}, {}, {}, {}),
        )
    }
}
