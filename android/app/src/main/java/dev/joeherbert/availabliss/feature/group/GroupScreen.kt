package dev.joeherbert.availabliss.feature.group

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.joeherbert.availabliss.designsystem.component.GlassIconButton
import dev.joeherbert.availabliss.designsystem.component.glass
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import dev.joeherbert.availabliss.designsystem.icon.AvIcons
import dev.joeherbert.availabliss.feature.availability.GroupCalendarTab
import dev.joeherbert.availabliss.feature.chat.GroupChatTab
import dev.joeherbert.availabliss.feature.proposal.GroupProposalsTab
import dev.joeherbert.availabliss.feature.proposal.ProposalPrefill
import dev.joeherbert.availabliss.feature.timing.GroupTimingTab
import dev.joeherbert.availabliss.navigation.GroupTab

/** Height of the group's pinned top bar below the status bar; each section leaves this much room. */
val GroupTopBarHeight = 118.dp

/**
 * A group: its header and a Calendar · Chat · Proposals · Timing strip pinned at the top, over
 * the section's content. The app's tab bar stays at the bottom throughout.
 */
@Composable
fun GroupScreen(
    initialTab: GroupTab,
    onBack: () -> Unit,
    onOpenMembers: () -> Unit,
    onOpenProposal: (String) -> Unit,
    onCreateProposal: (ProposalPrefill) -> Unit,
    onOpenEvents: () -> Unit,
    onOpenEvent: (eventId: String) -> Unit,
    onAddEvent: (start: String?, end: String?) -> Unit,
    viewModel: GroupViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    LaunchedEffect(tab) { viewModel.onTabChanged(tab) }
    BackHandler(enabled = tab != GroupTab.Calendar) { tab = GroupTab.Calendar }

    val toCalendar = { tab = GroupTab.Calendar }
    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = tab,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "group-tab",
        ) { current ->
            when (current) {
                GroupTab.Calendar -> GroupCalendarTab(
                    onBack = onBack,
                    onOpenMembers = onOpenMembers,
                    onOpenTiming = { tab = GroupTab.Timing },
                    onCreateProposal = onCreateProposal,
                )
                GroupTab.Proposals -> GroupProposalsTab(onOpenProposal = onOpenProposal, onOpenEvents = onOpenEvents)
                GroupTab.Chat -> GroupChatTab(
                    onBack = toCalendar,
                    onOpenProposal = onOpenProposal,
                    onOpenEvent = onOpenEvent,
                    onProposeTime = toCalendar,
                    onAddEvent = { onAddEvent(null, null) },
                )
                GroupTab.Timing -> GroupTimingTab(onBack = toCalendar, onSaved = toCalendar)
            }
        }
        GroupTopBar(state, tab, onSelect = { tab = it }, onBack = onBack, onOpenMembers = onOpenMembers, modifier = Modifier.align(Alignment.TopCenter))
    }
}

@Composable
private fun GroupTopBar(
    state: GroupShellUiState,
    tab: GroupTab,
    onSelect: (GroupTab) -> Unit,
    onBack: () -> Unit,
    onOpenMembers: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AvailablissTheme.colors
    val type = AvailablissTheme.type
    Column(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(0.8f to colors.bg.copy(alpha = 0.92f), 1f to colors.bg.copy(alpha = 0f)))
            .statusBarsPadding()
            .height(GroupTopBarHeight)
            .padding(start = 20.dp, end = 20.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassIconButton(AvIcons.Back, "Back to groups", onBack)
            Column(Modifier.weight(1f)) {
                Text(state.groupName, style = type.headlineSmall, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(state.membersLine, style = type.caption.copy(fontWeight = FontWeight.Normal), color = colors.muted, maxLines = 1)
            }
            // Only owners and admins can invite, so only they see the "add person" icon.
            if (state.isAdmin) GlassIconButton(AvIcons.AddPerson, "Members and invites", onOpenMembers)
            else GlassIconButton(AvIcons.People, "Members", onOpenMembers)
        }
        Row(Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp), shadow = false).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(
                Triple(GroupTab.Calendar, "Calendar", AvIcons.Calendar),
                Triple(GroupTab.Chat, "Chat", AvIcons.Chat),
                Triple(GroupTab.Proposals, "Proposals", AvIcons.ProposalCheck),
                Triple(GroupTab.Timing, "Timing", AvIcons.Sliders),
            ).forEach { (value, title, icon) ->
                val on = value == tab
                Row(
                    Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .then(if (on) Modifier.background(colors.solid) else Modifier)
                        .clickable(role = Role.Tab, onClickLabel = title) { onSelect(value) }
                        .semantics { selected = on },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(icon, null, tint = if (on) colors.onSolid else colors.ink, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(title, style = type.captionStrong, color = if (on) colors.onSolid else colors.ink, maxLines = 1)
                    if (value == GroupTab.Chat && state.unreadCount > 0) {
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (state.unreadCount > 99) "99+" else state.unreadCount.toString(),
                            style = type.nano.copy(fontWeight = FontWeight.Bold),
                            color = androidx.compose.ui.graphics.Color(0xFF1A1B3A),
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(colors.warn).padding(horizontal = 5.dp),
                        )
                    }
                }
            }
        }
    }
}
