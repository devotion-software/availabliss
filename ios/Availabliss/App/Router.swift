import Observation
import SwiftUI

enum AppTab: Hashable, CaseIterable {
    case groups, myTime, activity, you
}

/// A group's screens pushed over its calendar (the group's home).
enum GroupSection: Hashable, CaseIterable {
    case chat, plans, timing
}

/// Every pushable screen.
enum AppRoute: Hashable {
    case group(id: String)
    case groupSection(id: String, section: GroupSection)
    case proposal(id: String)
    case members(groupId: String)
    case appearance
    case calendars
    case region
}

/// Navigation state for the whole app: selected tab, one path per tab, and modal links.
@MainActor
@Observable
final class Router {
    var tab: AppTab = .groups
    var groupsPath: [AppRoute] = []
    var myTimePath: [AppRoute] = []
    var activityPath: [AppRoute] = []
    var youPath: [AppRoute] = []

    /// Invite token waiting to be shown (survives sign-in).
    var pendingInvite: InviteToken?

    /// The group whose chat is on screen, so its message pushes don't also show a banner.
    /// Not observed: nothing on screen depends on it.
    @ObservationIgnored var visibleChatGroupId: String?

    struct InviteToken: Identifiable, Hashable {
        let token: String
        var id: String { token }
    }

    var currentPath: [AppRoute] {
        switch tab {
        case .groups: groupsPath
        case .myTime: myTimePath
        case .activity: activityPath
        case .you: youPath
        }
    }

    func push(_ route: AppRoute) {
        switch tab {
        case .groups: groupsPath.append(route)
        case .myTime: myTimePath.append(route)
        case .activity: activityPath.append(route)
        case .you: youPath.append(route)
        }
    }

    func pop() {
        switch tab {
        case .groups: _ = groupsPath.popLast()
        case .myTime: _ = myTimePath.popLast()
        case .activity: _ = activityPath.popLast()
        case .you: _ = youPath.popLast()
        }
    }

    func open(_ link: DeepLink) {
        switch link {
        case .invite(let token):
            pendingInvite = InviteToken(token: token)
        case .group(let id):
            tab = .groups
            groupsPath = [.group(id: id)]
        case .groupChat(let id):
            tab = .groups
            groupsPath = [.group(id: id), .groupSection(id: id, section: .chat)]
        case .proposal(let id):
            tab = .groups
            groupsPath = [.proposal(id: id)]
        case .calendars:
            tab = .you
            youPath = [.calendars]
        }
    }

    func reset() {
        visibleChatGroupId = nil
        tab = .groups
        groupsPath = []
        myTimePath = []
        activityPath = []
        youPath = []
    }
}
