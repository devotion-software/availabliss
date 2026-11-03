package dev.joeherbert.availabliss.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import dev.joeherbert.availabliss.feature.events.EventEditorScreen
import dev.joeherbert.availabliss.feature.events.EventsScreen
import dev.joeherbert.availabliss.feature.group.GroupScreen
import dev.joeherbert.availabliss.feature.home.HomeScreen
import dev.joeherbert.availabliss.feature.members.InviteScreen
import dev.joeherbert.availabliss.feature.members.MembersScreen
import dev.joeherbert.availabliss.feature.mytime.MyTimeScreen
import dev.joeherbert.availabliss.feature.newgroup.NewGroupScreen
import dev.joeherbert.availabliss.feature.onboarding.RegionScreen
import dev.joeherbert.availabliss.feature.proposal.CreateProposalScreen
import dev.joeherbert.availabliss.feature.proposal.ProposalScreen
import dev.joeherbert.availabliss.feature.you.YouScreen

private enum class MainTab { Groups, MyTime, Activity, You }

/** The signed-in, onboarded app: every destination plus the floating main tab bar. */
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

    Box(Modifier.fillMaxSize()) {
        NavHost(navController = nav, startDestination = HomeRoute, modifier = Modifier.fillMaxSize()) {
            composable<HomeRoute> {
                HomeScreen(
                    onOpenGroup = { nav.navigate(GroupRoute(it)) },
                    onNewGroup = { nav.navigate(NewGroupRoute) },
                    onOpenProfile = { nav.switchTab(YouRoute) },
                )
            }
            composable<MyTimeRoute> { MyTimeScreen(onOpenCalendars = { nav.navigate(CalendarsRoute) }) }
            composable<ActivityRoute> { ActivityScreen(onOpenLink = { nav.open(it) }) }
            composable<YouRoute> {
                YouScreen(
                    onOpenAppearance = { nav.navigate(AppearanceRoute) },
                    onOpenCalendars = { nav.navigate(CalendarsRoute) },
                    onOpenRegion = { nav.navigate(RegionRoute) },
                    onSignOut = root::signOut,
                )
            }
            composable<GroupRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<GroupRoute>()
                GroupScreen(
                    initialTab = runCatching { GroupTab.valueOf(route.tab) }.getOrDefault(GroupTab.Calendar),
                    onBack = { nav.popBackStack() },
                    onOpenMembers = { nav.navigate(MembersRoute(route.groupId)) },
                    onOpenProposal = { nav.navigate(ProposalRoute(it)) },
                    onCreateProposal = { p -> nav.navigate(CreateProposalRoute(route.groupId, p.start, p.end, p.allDay, p.title, p.note)) },
                    onOpenEvents = { nav.navigate(EventsRoute(route.groupId)) },
                    onOpenEvent = { nav.navigate(EventEditorRoute(route.groupId, eventId = it)) },
                    onAddEvent = { start, end -> nav.navigate(EventEditorRoute(route.groupId, start, end)) },
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
                )
            }
            composable<ProposalRoute> { ProposalScreen(onBack = { nav.popBackStack() }) }
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
            composable<CalendarsRoute> { CalendarsScreen(onBack = { nav.popBackStack() }) }
            composable<RegionRoute> { RegionScreen(onBack = { nav.popBackStack() }) }
        }

        if (currentTab != null) {
            TabBar(
                items = listOf(
                    TabItem(MainTab.Groups, "Groups", AvIcons.Groups),
                    TabItem(MainTab.MyTime, "My time", AvIcons.Calendar),
                    TabItem(MainTab.Activity, "Activity", AvIcons.Bell, dot = unread > 0),
                    TabItem(MainTab.You, "You", AvIcons.Person),
                ),
                selected = currentTab,
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
        is DeepLink.GroupChat -> navigateSingleTop(GroupRoute(link.groupId, GroupTab.Chat.name))
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
