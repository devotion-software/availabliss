package dev.joeherbert.availabliss.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.joeherbert.availabliss.designsystem.theme.Spacing
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dev.joeherbert.availabliss.core.deeplink.DeepLink
import dev.joeherbert.availabliss.designsystem.component.TabBar
import dev.joeherbert.availabliss.designsystem.component.TabItem
import dev.joeherbert.availabliss.designsystem.icon.AvIcons
import dev.joeherbert.availabliss.feature.activity.ActivityScreen
import dev.joeherbert.availabliss.feature.appearance.AppearanceScreen
import dev.joeherbert.availabliss.feature.calendars.CalendarsScreen
import dev.joeherbert.availabliss.feature.chat.GroupChatTab
import dev.joeherbert.availabliss.feature.events.EventEditorScreen
import dev.joeherbert.availabliss.feature.events.EventsScreen
import dev.joeherbert.availabliss.feature.group.GroupScreen
import dev.joeherbert.availabliss.feature.group.GroupSettingsScreen
import dev.joeherbert.availabliss.feature.group.GroupViewModel
import dev.joeherbert.availabliss.feature.home.HomeScreen
import dev.joeherbert.availabliss.feature.members.InviteScreen
import dev.joeherbert.availabliss.feature.members.MembersScreen
import dev.joeherbert.availabliss.feature.mytime.MyTimeScreen
import dev.joeherbert.availabliss.feature.newgroup.NewGroupScreen
import dev.joeherbert.availabliss.feature.onboarding.RegionScreen
import dev.joeherbert.availabliss.feature.proposal.CreateProposalScreen
import dev.joeherbert.availabliss.feature.proposal.GroupProposalsTab
import dev.joeherbert.availabliss.feature.proposal.GroupProposalsViewModel
import dev.joeherbert.availabliss.feature.proposal.ProposalScreen
import dev.joeherbert.availabliss.feature.timing.GroupTimingTab
import dev.joeherbert.availabliss.feature.you.NotificationSettingsScreen
import dev.joeherbert.availabliss.feature.you.YouScreen

private enum class MainTab { Groups, MyTime, Activity, You }

/**
 * The signed-in, onboarded app: every destination plus the floating main tab bar, which stays
 * on screen everywhere except full-screen editors and while the keyboard is up.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainNavHost(root: RootViewModel) {
    val nav = rememberNavController()
    val pending by root.pendingDeepLink.collectAsStateWithLifecycle()
    val unread by root.unreadNotifications.collectAsStateWithLifecycle()

    LaunchedEffect(pending) {
        val link = pending ?: return@LaunchedEffect
        nav.open(link)
        // The calendars screen consumes its own link so it can show "connected" / the error.
        if (link !is DeepLink.Calendars) root.consume(link)
    }

    // A group you're no longer in (deleted, or you were removed): close its screens.
    LaunchedEffect(root) { root.groupsLeft.collect { nav.closeGroup(it) } }

    val entry by nav.currentBackStackEntryAsState()
    val destination = entry?.destination
    val currentTab = when {
        destination == null -> MainTab.Groups
        destination.hasRoute<HomeRoute>() -> MainTab.Groups
        destination.hasRoute<MyTimeRoute>() -> MainTab.MyTime
        destination.hasRoute<ActivityRoute>() -> MainTab.Activity
        destination.hasRoute<YouRoute>() -> MainTab.You
        else -> null
    }
    // Inside a tab (a group, a proposal, settings…) the bar shows the tab you came from.
    var lastTab by remember { mutableStateOf(MainTab.Groups) }
    if (currentTab != null) lastTab = currentTab
    val isEditor = destination != null && (
        destination.hasRoute<NewGroupRoute>() || destination.hasRoute<CreateProposalRoute>() ||
            destination.hasRoute<EventEditorRoute>() || destination.hasRoute<InviteRoute>()
        )
    val showBar = destination != null && !isEditor && !WindowInsets.isImeVisible
    // Tab roots leave room for the floating bar themselves; other screens are laid out above it.
    val innerPadding = if (showBar && currentTab == null) Spacing.tabBarClearance else 0.dp

    Box(Modifier.fillMaxSize().background(AvailablissTheme.colors.bg)) {
        NavHost(
            navController = nav,
            startDestination = HomeRoute,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding)
                .then(if (innerPadding > 0.dp) Modifier.consumeWindowInsets(WindowInsets.navigationBars) else Modifier),
        ) {
            composable<HomeRoute> {
                HomeScreen(
                    onOpenGroup = { nav.navigate(GroupRoute(it)) },
                    onOpenGroupChat = { nav.openGroupSection(it, GroupChatRoute(it)) },
                    onOpenProposal = { nav.navigate(ProposalRoute(it)) },
                    onOpenGroupPlans = { nav.openGroupSection(it, GroupPlansRoute(it)) },
                    onNewGroup = { nav.navigate(NewGroupRoute()) },
                    onOpenProfile = { nav.switchTab(YouRoute) },
                )
            }
            composable<MyTimeRoute> { MyTimeScreen(onOpenCalendars = { nav.navigate(CalendarsRoute) }) }
            composable<ActivityRoute> {
                ActivityScreen(
                    onOpenLink = { link ->
                        // A chat opens straight over Activity, so back returns here.
                        if (link is DeepLink.GroupChat) nav.navigateSingleTop(GroupChatRoute(link.groupId)) else nav.open(link)
                    },
                )
            }
            composable<YouRoute> {
                YouScreen(
                    onOpenAppearance = { nav.navigate(AppearanceRoute) },
                    onOpenCalendars = { nav.navigate(CalendarsRoute) },
                    onOpenRegion = { nav.navigate(RegionRoute) },
                    onOpenNotifications = { nav.navigate(NotificationSettingsRoute) },
                    onSignOut = root::signOut,
                )
            }
            composable<GroupRoute> { backStackEntry ->
                val groupId = backStackEntry.toRoute<GroupRoute>().groupId
                GroupScreen(
                    onBack = { nav.popBackStack() },
                    onOpenChat = { nav.navigate(GroupChatRoute(groupId)) },
                    onOpenPlans = { nav.navigate(GroupPlansRoute(groupId)) },
                    onOpenTiming = { nav.navigate(GroupTimingRoute(groupId)) },
                    onOpenMembers = { nav.navigate(MembersRoute(groupId)) },
                    onOpenSettings = { nav.navigate(GroupSettingsRoute(groupId)) },
                    onNewGroupWithPeople = { nav.navigate(NewGroupRoute(groupId)) },
                    onOpenProposal = { nav.navigate(ProposalRoute(it)) },
                    onCreateProposal = { p -> nav.navigate(CreateProposalRoute(groupId, p.start, p.end, p.allDay, p.title, p.note)) },
                )
            }
            // A group's sections. Each reuses state held by the group's calendar entry below it
            // (see groupEntry) rather than loading the group twice.
            composable<GroupChatRoute> { backStackEntry ->
                val groupId = backStackEntry.toRoute<GroupChatRoute>().groupId
                // While the chat shows, the calendar header's unread badge stays clear.
                val groupEntry = remember(backStackEntry) { nav.groupEntry(groupId) }
                if (groupEntry != null) {
                    val shell: GroupViewModel = hiltViewModel(groupEntry)
                    DisposableEffect(shell) {
                        shell.onChatVisible(true)
                        onDispose { shell.onChatVisible(false) }
                    }
                }
                // Opened from Activity rather than over the calendar: back returns there, and the
                // group's name (or proposing a time) opens the calendar in its place.
                val overGroup = remember(backStackEntry) {
                    nav.previousBackStackEntry?.let { it.destination.hasRoute<GroupRoute>() && it.toRoute<GroupRoute>().groupId == groupId } == true
                }
                val openGroup = {
                    nav.popBackStack()
                    nav.open(DeepLink.GroupCalendar(groupId))
                }
                GroupChatTab(
                    onBack = { nav.popBackStack() },
                    onOpenProposal = { nav.navigate(ProposalRoute(it)) },
                    onOpenEvent = { nav.navigate(EventEditorRoute(groupId, eventId = it)) },
                    // Proposing starts from the calendar.
                    onProposeTime = { if (overGroup) nav.popBackStack() else openGroup() },
                    onAddEvent = { nav.navigate(EventEditorRoute(groupId)) },
                    onOpenGroup = if (overGroup) null else openGroup,
                )
            }
            composable<GroupPlansRoute> { backStackEntry ->
                val groupId = backStackEntry.toRoute<GroupPlansRoute>().groupId
                // The calendar's Plans card already loads (and live-updates) these proposals.
                val owner = remember(backStackEntry) { nav.groupEntry(groupId) } ?: backStackEntry
                GroupProposalsTab(
                    onBack = { nav.popBackStack() },
                    onOpenProposal = { nav.navigate(ProposalRoute(it)) },
                    onOpenEvent = { nav.navigate(EventEditorRoute(groupId, eventId = it)) },
                    onAddEvent = { nav.navigate(EventEditorRoute(groupId)) },
                    viewModel = hiltViewModel<GroupProposalsViewModel>(owner),
                )
            }
            composable<GroupTimingRoute> { GroupTimingTab(onBack = { nav.popBackStack() }, onSaved = { nav.popBackStack() }) }
            composable<GroupSettingsRoute> { backStackEntry ->
                val groupId = backStackEntry.toRoute<GroupSettingsRoute>().groupId
                GroupSettingsScreen(
                    onBack = { nav.popBackStack() },
                    onNewGroupWithPeople = { nav.navigate(NewGroupRoute(groupId)) },
                    onDeleted = { nav.popBackStack(HomeRoute, inclusive = false) },
                )
            }
            composable<NewGroupRoute> {
                NewGroupScreen(
                    onBack = { nav.popBackStack() },
                    onCreated = { id ->
                        nav.navigate(GroupRoute(id)) { popUpTo<NewGroupRoute> { inclusive = true } }
                    },
                )
            }
            composable<MembersRoute> {
                MembersScreen(
                    onBack = { nav.popBackStack() },
                    onLeftGroup = { nav.popBackStack(HomeRoute, inclusive = false) },
                )
            }
            composable<InviteRoute> {
                InviteScreen(
                    onBack = { nav.popBackStack() },
                    onAccepted = { id -> nav.navigate(GroupRoute(id)) { popUpTo<InviteRoute> { inclusive = true } } },
                    onSignOut = root::signOut,
                )
            }
            composable<ProposalRoute> {
                ProposalScreen(
                    onBack = { nav.popBackStack() },
                    // Opened from that chat, it just goes back to it.
                    onOpenChat = { groupId ->
                        val previous = nav.previousBackStackEntry
                        if (previous?.destination?.hasRoute<GroupChatRoute>() == true && previous.toRoute<GroupChatRoute>().groupId == groupId) {
                            nav.popBackStack()
                        } else {
                            nav.navigate(GroupChatRoute(groupId))
                        }
                    },
                    // A cancelled plan: back to the group's calendar to pick another date.
                    onOpenGroupCalendar = { groupId ->
                        nav.popBackStack()
                        val current = nav.currentBackStackEntry
                        if (current?.destination?.hasRoute<GroupRoute>() != true || current.toRoute<GroupRoute>().groupId != groupId) {
                            nav.navigate(GroupRoute(groupId))
                        }
                    },
                )
            }
            composable<CreateProposalRoute> {
                CreateProposalScreen(
                    onBack = { nav.popBackStack() },
                    onCreated = { id -> nav.navigate(ProposalRoute(id)) { popUpTo<CreateProposalRoute> { inclusive = true } } },
                )
            }
            composable<EventsRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<EventsRoute>()
                EventsScreen(
                    onBack = { nav.popBackStack() },
                    onAddEvent = { nav.navigate(EventEditorRoute(route.groupId)) },
                    onOpenEvent = { nav.navigate(EventEditorRoute(route.groupId, eventId = it)) },
                )
            }
            composable<EventEditorRoute> { EventEditorScreen(onBack = { nav.popBackStack() }, onSaved = { nav.popBackStack() }) }
            composable<AppearanceRoute> { AppearanceScreen(onBack = { nav.popBackStack() }) }
            composable<NotificationSettingsRoute> { NotificationSettingsScreen(onBack = { nav.popBackStack() }) }
            composable<CalendarsRoute> { CalendarsScreen(onBack = { nav.popBackStack() }) }
            composable<RegionRoute> { RegionScreen(onBack = { nav.popBackStack() }) }
        }

        if (showBar) {
            TabBar(
                items = listOf(
                    TabItem(MainTab.Groups, "Groups", AvIcons.Groups),
                    TabItem(MainTab.MyTime, "My time", AvIcons.Calendar),
                    TabItem(MainTab.Activity, "Activity", AvIcons.Bell, dot = unread > 0),
                    TabItem(MainTab.You, "You", AvIcons.Person),
                ),
                selected = currentTab ?: lastTab,
                onSelect = { tab ->
                    nav.switchTab(
                        when (tab) {
                            MainTab.Groups -> HomeRoute
                            MainTab.MyTime -> MyTimeRoute
                            MainTab.Activity -> ActivityRoute
                            MainTab.You -> YouRoute
                        },
                    )
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/** Top-level tab switch that keeps one copy of each tab and restores its state. */
private fun NavHostController.switchTab(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Routes a [DeepLink] to its destination. */
internal fun NavHostController.open(link: DeepLink) {
    when (link) {
        is DeepLink.Invite -> navigateSingleTop(InviteRoute(link.token))
        is DeepLink.GroupCalendar -> navigateSingleTop(GroupRoute(link.groupId))
        is DeepLink.GroupChat -> openGroupSection(link.groupId, GroupChatRoute(link.groupId))
        is DeepLink.GroupEvents -> navigateSingleTop(EventsRoute(link.groupId))
        is DeepLink.Proposal -> navigateSingleTop(ProposalRoute(link.proposalId))
        is DeepLink.Calendars -> navigate(CalendarsRoute) { launchSingleTop = true }
        DeepLink.MyTime -> switchTab(MyTimeRoute)
        DeepLink.Activity -> switchTab(ActivityRoute)
        is DeepLink.AuthCallback -> Unit // Already signed in.
    }
}

/**
 * Navigates to [route] unless it's already the top screen, so tapping a second
 * notification for the same thing doesn't stack a duplicate. Only for an identical
 * route: `launchSingleTop` onto the same destination with other arguments would keep
 * the old screen's ViewModel, which reads its id once.
 */
private inline fun <reified T : Any> NavHostController.navigateSingleTop(route: T) {
    val top = currentBackStackEntry
    navigate(route) { launchSingleTop = top?.destination?.hasRoute<T>() == true && top.toRoute<T>() == route }
}

/**
 * Opens one of a group's sections ([GroupChatRoute], [GroupPlansRoute], [GroupTimingRoute]) over
 * that group's calendar, adding the calendar underneath first unless it's already the top screen,
 * so back always lands on it. Does nothing if [section] is already showing.
 */
internal inline fun <reified T : Any> NavHostController.openGroupSection(groupId: String, section: T) {
    val top = currentBackStackEntry
    if (top?.destination?.hasRoute<T>() == true && top.toRoute<T>() == section) return
    if (top?.destination?.hasRoute<GroupRoute>() != true || top.toRoute<GroupRoute>().groupId != groupId) navigate(GroupRoute(groupId))
    navigate(section)
}

/**
 * Pops the screens of [groupId] (its calendar, sections, members, settings, events…) off the top
 * of the back stack, so a group that's gone doesn't stay on screen. Every group screen's route
 * carries a `groupId`.
 */
private fun NavHostController.closeGroup(groupId: String) {
    while (currentBackStackEntry?.arguments?.getString("groupId") == groupId) {
        if (!popBackStack()) break
    }
}

/** The back stack entry of [groupId]'s calendar, if it's open under the current screen. */
private fun NavHostController.groupEntry(groupId: String): NavBackStackEntry? =
    runCatching { getBackStackEntry<GroupRoute>() }.getOrNull()?.takeIf { it.toRoute<GroupRoute>().groupId == groupId }
