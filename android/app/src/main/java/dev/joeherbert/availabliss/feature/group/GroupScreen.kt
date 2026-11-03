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
import dev.joeherbert.availabliss.designsystem.component.TabBar
import dev.joeherbert.availabliss.designsystem.component.TabItem
import dev.joeherbert.availabliss.designsystem.icon.AvIcons
import dev.joeherbert.availabliss.feature.availability.GroupCalendarTab
import dev.joeherbert.availabliss.feature.chat.GroupChatTab
import dev.joeherbert.availabliss.feature.proposal.GroupProposalsTab
import dev.joeherbert.availabliss.feature.proposal.ProposalPrefill
import dev.joeherbert.availabliss.feature.timing.GroupTimingTab
import dev.joeherbert.availabliss.navigation.GroupTab

/**
 * A group with its own tabs. Calendar and Proposals show the floating group tab bar;
 * Chat and Timing are full-screen (composer / save bar) and go back to Calendar.
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
        if (tab == GroupTab.Calendar || tab == GroupTab.Proposals) {
            TabBar(
                items = listOf(
                    TabItem(GroupTab.Calendar, "Calendar", AvIcons.Calendar),
                    TabItem(GroupTab.Chat, "Chat", AvIcons.Chat, badgeCount = state.unreadCount),
                    TabItem(GroupTab.Proposals, "Proposals", AvIcons.ProposalCheck),
                    TabItem(GroupTab.Timing, "Timing", AvIcons.Sliders),
                ),
                selected = tab,
                onSelect = { tab = it },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}
