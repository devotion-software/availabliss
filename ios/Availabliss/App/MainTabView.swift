import SwiftUI

/// The four main tabs with the floating glass tab bar.
struct MainTabView: View {
    @Environment(AppModel.self) private var app

    var body: some View {
        @Bindable var router = app.router
        TabView(selection: $router.tab) {
            tabStack(path: $router.groupsPath) { GroupsHomeView(api: app.api) }
                .tag(AppTab.groups)
            tabStack(path: $router.myTimePath) { MyTimeView(api: app.api) }
                .tag(AppTab.myTime)
            tabStack(path: $router.activityPath) { ActivityView(api: app.api) }
                .tag(AppTab.activity)
            tabStack(path: $router.youPath) { ProfileView() }
                .tag(AppTab.you)
        }
        .safeAreaInset(edge: .bottom, spacing: 0) {
            if router.showsTabBar {
                TabBar(selection: $router.tab, items: tabItems)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .animation(.spring(response: 0.35, dampingFraction: 0.85), value: router.showsTabBar)
    }

    private var tabItems: [TabItem<AppTab>] {
        [
            TabItem(value: .groups, title: "Groups", systemImage: "circle.hexagongrid"),
            TabItem(value: .myTime, title: "My time", systemImage: "calendar"),
            TabItem(value: .activity, title: "Activity", systemImage: "bell", badge: app.unreadNotifications > 0 ? .dot : .none),
            TabItem(value: .you, title: "You", systemImage: "person"),
        ]
    }

    private func tabStack<Root: View>(path: Binding<[AppRoute]>, @ViewBuilder root: () -> Root) -> some View {
        NavigationStack(path: path) {
            root()
                .navigationDestination(for: AppRoute.self) { route in
                    RouteDestination(route: route)
                }
        }
        .toolbar(.hidden, for: .tabBar)
    }
}

/// Builds the screen for a route.
struct RouteDestination: View {
    let route: AppRoute
    @Environment(AppModel.self) private var app

    var body: some View {
        switch route {
        case .group(let id, let tab):
            GroupContainerView(groupId: id, initialTab: tab, api: app.api)
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
        }
    }
}

extension View {
    /// Hides the system navigation bar — screens draw their own glass headers.
    func chromeless() -> some View {
        toolbar(.hidden, for: .navigationBar)
    }
}

/// Keeps the interactive swipe-back gesture working when the navigation bar is hidden.
extension UINavigationController: @retroactive UIGestureRecognizerDelegate {
    override open func viewDidLoad() {
        super.viewDidLoad()
        interactivePopGestureRecognizer?.delegate = self
    }

    public func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
        viewControllers.count > 1
    }
}
