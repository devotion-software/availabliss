import Observation
import SwiftUI

enum AppTab: Hashable, CaseIterable {
    case groups, myTime, activity, you
}

/// A group's screens pushed over its calendar (the group's home).
enum GroupSection: Hashable, CaseIterable {
    case chat, plans, timing, settings
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
    /// My plans: every plan across your groups (from Activity).
    case myPlans
}

/// Set by a screen with unsaved changes while it's on screen (the group's Timing). Back — the
/// header's back button or the swipe in from the screen's edge — asks it first; it returns false
/// to stay put, and asks whether to save or discard instead.
@MainActor
enum LeaveGuard {
    static var shouldLeave: (() -> Bool)?
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

    /// Back from the header's back button: asks `LeaveGuard` first, so unsaved changes aren't lost.
    func back() {
        if let shouldLeave = LeaveGuard.shouldLeave, !shouldLeave() { return }
        pop()
    }

    func pop() {
        switch tab {
        case .groups: _ = groupsPath.popLast()
        case .myTime: _ = myTimePath.popLast()
        case .activity: _ = activityPath.popLast()
        case .you: _ = youPath.popLast()
        }
    }

    /// Opens a link from the Activity tab: a chat or proposal stacks on Activity, so back returns there.
    func openFromActivity(_ link: DeepLink) {
        switch link {
        case .groupChat(let id):
            tab = .activity
            activityPath.append(.groupSection(id: id, section: .chat))
        case .proposal(let id):
            tab = .activity
            activityPath.append(.proposal(id: id))
        default:
            open(link)
        }
    }

    /// Whether the screen on top of the current tab sits over its group's calendar.
    func isOverGroup(_ id: String) -> Bool {
        currentPath.dropLast().last == .group(id: id)
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
        case .myPlans:
            tab = .activity
            activityPath = [.myPlans]
        case .activity:
            tab = .activity
            activityPath = []
        }
    }

    /// Takes every screen of a group off every tab (it was deleted, or you're no longer in it).
    func closeGroup(_ id: String) {
        func cut(_ path: inout [AppRoute]) {
            guard let index = path.firstIndex(where: { $0.groupId == id }) else { return }
            path.removeSubrange(index...)
        }
        cut(&groupsPath)
        cut(&myTimePath)
        cut(&activityPath)
        cut(&youPath)
        if visibleChatGroupId == id { visibleChatGroupId = nil }
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

extension AppRoute {
    /// The group a route belongs to, if any.
    var groupId: String? {
        switch self {
        case .group(let id), .groupSection(let id, _): id
        case .members(let groupId): groupId
        case .proposal, .appearance, .calendars, .region, .myPlans: nil
        }
    }
}
