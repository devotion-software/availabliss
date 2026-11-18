import SwiftUI

/// The four main tabs on the system tab bar, which stays on screen everywhere (inside groups
/// and pushed screens too) and, on iOS 26, shrinks while you scroll down.
struct MainTabView: View {
    @Environment(AppModel.self) private var app
    @Environment(\.theme) private var theme

    var body: some View {
        SwiftUI.Group {
            if #available(iOS 18.0, *) { modernTabs } else { legacyTabs }
        }
        .tint(theme.accentInk)
        .modifier(MinimizeTabBarOnScroll())
    }

    /// iOS 18+: `Tab`s, which the iOS 26 tab bar can shrink on scroll.
    @available(iOS 18.0, *)
    private var modernTabs: some View {
        @Bindable var router = app.router
        return TabView(selection: $router.tab) {
            Tab("Groups", systemImage: "circle.hexagongrid", value: AppTab.groups) {
                tabStack(path: $router.groupsPath) { GroupsHomeView(api: app.api) }
            }
            Tab("My time", systemImage: "calendar", value: AppTab.myTime) {
                tabStack(path: $router.myTimePath) { MyTimeView(api: app.api) }
            }
            Tab("Activity", systemImage: "bell", value: AppTab.activity) {
                tabStack(path: $router.activityPath) { ActivityView(api: app.api) }
            }
            .badge(app.unreadNotifications)
            Tab("You", systemImage: "person", value: AppTab.you) {
                tabStack(path: $router.youPath) { ProfileView() }
            }
        }
    }

    private var legacyTabs: some View {
        @Bindable var router = app.router
        return TabView(selection: $router.tab) {
            tabStack(path: $router.groupsPath) { GroupsHomeView(api: app.api) }
                .tabItem { Label("Groups", systemImage: "circle.hexagongrid") }
                .tag(AppTab.groups)
            tabStack(path: $router.myTimePath) { MyTimeView(api: app.api) }
                .tabItem { Label("My time", systemImage: "calendar") }
                .tag(AppTab.myTime)
            tabStack(path: $router.activityPath) { ActivityView(api: app.api) }
                .tabItem { Label("Activity", systemImage: "bell") }
                .badge(app.unreadNotifications)
                .tag(AppTab.activity)
            tabStack(path: $router.youPath) { ProfileView() }
                .tabItem { Label("You", systemImage: "person") }
                .tag(AppTab.you)
        }
    }

    private func tabStack<Root: View>(path: Binding<[AppRoute]>, @ViewBuilder root: () -> Root) -> some View {
        NavigationStack(path: path) {
            root()
                .modifier(FadeInOnAppear())
                .navigationDestination(for: AppRoute.self) { route in
                    RouteDestination(route: route)
                }
        }
    }
}

/// iOS 26+: the tab bar shrinks to a small pill while scrolling down, and comes back on scroll up.
private struct MinimizeTabBarOnScroll: ViewModifier {
    func body(content: Content) -> some View {
        if #available(iOS 26.0, *) {
            content.tabBarMinimizeBehavior(.onScrollDown)
        } else {
            content
        }
    }
}

/// Fades a tab's screen in each time it's shown, so switching tabs isn't an abrupt cut.
private struct FadeInOnAppear: ViewModifier {
    @State private var isShown = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    func body(content: Content) -> some View {
        content
            .opacity(isShown || reduceMotion ? 1 : 0)
            .offset(y: isShown || reduceMotion ? 0 : 6)
            .onAppear { withAnimation(.easeOut(duration: 0.22)) { isShown = true } }
            .onDisappear { isShown = false }
    }
}

/// Builds the screen for a route.
struct RouteDestination: View {
    let route: AppRoute
    @Environment(AppModel.self) private var app

    var body: some View {
        switch route {
        case .group(let id):
            GroupContainerView(groupId: id, section: nil, app: app)
        case .groupSection(let id, let section):
            GroupContainerView(groupId: id, section: section, app: app)
        case .proposal(let id):
            ProposalView(proposalId: id, api: app.api)
        case .members(let groupId):
            MembersView(groupId: groupId, api: app.api)
        case .appearance:
            AppearanceView()
        case .calendars:
            CalendarsView(api: app.api, mode: .settings)
        case .region:
            RegionView(api: app.api, mode: .settings)
        case .myPlans:
            MyPlansView(api: app.api)
        }
    }
}

extension View {
    /// For a tab's main scroll view: ends the content just above the tab bar's top edge rather than
    /// reserving the bar's whole height, so no empty band is left when the bar shrinks on scroll.
    func tabBarScrollInset() -> some View {
        modifier(TabBarScrollInset())
    }

    /// Hides the system navigation bar — screens draw their own glass headers.
    func chromeless() -> some View {
        toolbar(.hidden, for: .navigationBar)
    }
}

private struct TabBarScrollInset: ViewModifier {
    /// From the bottom of the screen: just clear of the full tab bar, and close to the shrunk one.
    static let bottom: CGFloat = 60

    func body(content: Content) -> some View {
        if #available(iOS 26.0, *) {
            content
                .ignoresSafeArea(.container, edges: .bottom)
                .contentMargins(.bottom, Self.bottom, for: .scrollContent)
        } else {
            content
        }
    }
}

/// Keeps the interactive swipe-back gesture working when the navigation bar is hidden.
extension UINavigationController: @retroactive UIGestureRecognizerDelegate {
    override open func viewDidLoad() {
        super.viewDidLoad()
        interactivePopGestureRecognizer?.delegate = self
    }

    public func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
        guard viewControllers.count > 1 else { return false }
        // A screen with unsaved changes asks before it's swiped away.
        if gestureRecognizer === interactivePopGestureRecognizer, let shouldLeave = LeaveGuard.shouldLeave {
            return shouldLeave()
        }
        return true
    }
}
