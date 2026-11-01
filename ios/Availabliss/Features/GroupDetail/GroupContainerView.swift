import SwiftUI

/// A group with its own floating tab bar: Calendar · Chat · Proposals · Timing.
/// Chat is full-screen (its composer replaces the tab bar), as in the concept.
struct GroupContainerView: View {
    @Environment(AppModel.self) private var app
    @Environment(\.theme) private var theme
    @State private var detail: GroupDetailModel
    @State private var tab: GroupTab

    init(groupId: String, initialTab: GroupTab, api: APIClient) {
        _detail = State(initialValue: GroupDetailModel(groupId: groupId, api: api))
        _tab = State(initialValue: initialTab)
    }

    var body: some View {
        content
            .safeAreaInset(edge: .bottom, spacing: 0) {
                if tab != .chat {
                    TabBar(selection: $tab, items: tabItems)
                        .transition(.move(edge: .bottom).combined(with: .opacity))
                }
            }
            .animation(.spring(response: 0.35, dampingFraction: 0.85), value: tab)
            .foregroundStyle(theme.ink)
            .chromeless()
            .task { await detail.load() }
            .task(id: detail.groupId) {
                for await event in app.live.events(forGroup: detail.groupId) {
                    await detail.apply(event)
                    if event.kind == .messageCreated, tab != .chat { detail.unreadCount += 1 }
                }
            }
            .onChange(of: tab) { _, newTab in if newTab == .chat { detail.unreadCount = 0 } }
    }

    @ViewBuilder
    private var content: some View {
        if let group = detail.group {
            switch tab {
            case .calendar:
                GroupAvailabilityView(detail: detail, group: group, onOpenTiming: { tab = .timing })
            case .chat:
                ChatView(detail: detail, group: group, onBack: { tab = .calendar }, onShowCalendar: { tab = .calendar })
            case .proposals:
                GroupPlansView(detail: detail, group: group)
            case .timing:
                TimingEditorView(detail: detail, group: group, onDone: { tab = .calendar })
            }
        } else if let error = detail.loadError {
            VStack {
                ScreenHeader(title: "Group", onBack: { app.router.pop() })
                Spacer()
                StatusView(kind: .error(error) { Task { await detail.load() } })
                Spacer()
            }
            .padding(20)
            .screenBackground(OrbLayout.groupCalendar)
        } else {
            StatusView(kind: .loading)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .screenBackground(OrbLayout.groupCalendar)
        }
    }

    private var tabItems: [TabItem<GroupTab>] {
        [
            TabItem(value: .calendar, title: "Calendar", systemImage: "calendar"),
            TabItem(value: .chat, title: "Chat", systemImage: "bubble.left", badge: detail.unreadCount > 0 ? .count(detail.unreadCount) : .none),
            TabItem(value: .proposals, title: "Proposals", systemImage: "checkmark.circle"),
            TabItem(value: .timing, title: "Timing", systemImage: "slider.horizontal.3"),
        ]
    }
}

/// Header shared by the group's tabs: back, name + "6 members · you're an admin", invite.
struct GroupHeader: View {
    let group: Group
    @Environment(AppModel.self) private var app

    var body: some View {
        ScreenHeader(title: group.name, subtitle: subtitle, titleSize: 22, backLabel: "Back to groups", onBack: { app.router.pop() }) {
            IconButton(systemImage: "person.badge.plus", label: "Members and invites") {
                app.router.push(.members(groupId: group.id))
            }
        }
    }

    private var subtitle: String {
        let members = Formatting.plural(group.memberCount, "member")
        switch group.role {
        case .owner: return "\(members) · you're the owner"
        case .admin: return "\(members) · you're an admin"
        case .member: return members
        }
    }
}
