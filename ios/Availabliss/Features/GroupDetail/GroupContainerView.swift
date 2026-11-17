import SwiftUI

/// A group's screens: its calendar (the group's home), and Chat / Plans / Timing pushed over it.
/// Each is its own route, and they all share one `GroupDetailModel` from `GroupDetailStore`,
/// so pushing a section doesn't load or subscribe to the group again.
struct GroupContainerView: View {
    let section: GroupSection?
    @Environment(AppModel.self) private var app
    @Environment(\.theme) private var theme
    @State private var detail: GroupDetailModel

    init(groupId: String, section: GroupSection?, app: AppModel) {
        self.section = section
        _detail = State(initialValue: app.groupDetails.model(for: groupId, api: app.api, live: app.live, me: { [session = app.session] in session.user?.id }))
    }

    var body: some View {
        content
            .safeAreaInset(edge: .top, spacing: 0) {
                if let group = detail.group {
                    GroupTopBar(group: group, section: section, unreadCount: detail.unreadCount)
                }
            }
            .foregroundStyle(theme.ink)
            .chromeless()
            .task { await detail.start() }
            .onAppear {
                guard section == .chat else { return }
                detail.isChatVisible = true
                detail.unreadCount = 0
            }
            .onDisappear { if section == .chat { detail.isChatVisible = false } }
    }

    @ViewBuilder
    private var content: some View {
        if let group = detail.group {
            switch section {
            case nil:
                GroupAvailabilityView(detail: detail, group: group, onOpenTiming: {
                    app.router.push(.groupSection(id: group.id, section: .timing))
                })
            case .chat:
                ChatView(detail: detail, group: group)
            case .plans:
                GroupPlansView(detail: detail, group: group)
            case .timing:
                TimingEditorView(detail: detail, group: group, onDone: { app.router.pop() })
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
}

/// Pinned at the top of a group's screens. On the calendar: back to groups, name, role, Chat and More
/// (Plans, Timing, Members). On a pushed section: back to the calendar, with the group's name.
private struct GroupTopBar: View {
    let group: Group
    let section: GroupSection?
    let unreadCount: Int
    @Environment(AppModel.self) private var app
    @Environment(\.theme) private var theme

    var body: some View {
        SwiftUI.Group {
            if let section, app.router.isOverGroup(group.id) {
                ScreenHeader(title: group.name, titleSize: 22, backLabel: "Back to calendar", onBack: { app.router.pop() })
            } else if section != nil {
                // Opened from elsewhere (Activity): back returns there; the name opens the group.
                ScreenHeader(title: group.name, titleSize: 22, onBack: { app.router.pop() }, onTitle: { app.router.pop(); app.open(.group(id: group.id)) })
            } else {
                GroupHeader(group: group, unreadCount: unreadCount)
            }
        }
        .padding(.horizontal, 20)
        .padding(.top, 8)
        .padding(.bottom, 10)
        .background {
            // The page colour fading out at the bottom (as on Android and web), so the calendar
            // scrolls away under the name; a plain material read as a flat grey band.
            LinearGradient(
                stops: [.init(color: theme.bg.opacity(0.92), location: 0.8), .init(color: theme.bg.opacity(0), location: 1)],
                startPoint: .top, endPoint: .bottom
            )
            .ignoresSafeArea(edges: .top)
        }
    }
}

/// The calendar's header: back, name + "6 members · you're an admin", Chat (with unread badge) and More.
private struct GroupHeader: View {
    let group: Group
    let unreadCount: Int
    @Environment(AppModel.self) private var app
    @Environment(\.theme) private var theme

    var body: some View {
        ScreenHeader(title: group.name, subtitle: subtitle, titleSize: 22, backLabel: "Back to groups", onBack: { app.router.pop() }) {
            HStack(spacing: 8) {
                chatButton
                moreMenu
            }
        }
    }

    private var chatButton: some View {
        IconButton(systemImage: "bubble.left", label: unreadCount > 0 ? "Chat, \(unreadCount) unread" : "Chat") {
            app.router.push(.groupSection(id: group.id, section: .chat))
        }
        .overlay(alignment: .topTrailing) {
            if unreadCount > 0 {
                Text(unreadCount > 99 ? "99+" : "\(unreadCount)")
                    .font(AppFont.body(10, .bold, relativeTo: .caption2))
                    .foregroundStyle(Color(hex: 0x1A1B3A))
                    .padding(.horizontal, 5)
                    .frame(minWidth: 18, minHeight: 18)
                    .background(Capsule().fill(theme.warn))
                    .offset(x: 3, y: -3)
                    .allowsHitTesting(false)
                    .accessibilityHidden(true)
            }
        }
    }

    private var moreMenu: some View {
        Menu {
            Button { app.router.push(.groupSection(id: group.id, section: .plans)) } label: {
                Label("Plans", systemImage: "checkmark.circle")
            }
            Button { app.router.push(.groupSection(id: group.id, section: .timing)) } label: {
                Label("Timing", systemImage: "slider.horizontal.3")
            }
            Button { app.router.push(.members(groupId: group.id)) } label: {
                Label(canInvite ? "Members and invites" : "Members", systemImage: canInvite ? "person.badge.plus" : "person.2")
            }
        } label: {
            Image(systemName: "ellipsis")
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(theme.ink)
                .frame(width: 44, height: 44)
                .glass(in: Circle(), shadow: false)
                .contentShape(Circle())
        }
        .accessibilityLabel("More")
    }

    /// Only owners and admins can invite.
    private var canInvite: Bool { group.role != .member }

    private var subtitle: String {
        let members = Formatting.plural(group.memberCount, "member")
        switch group.role {
        case .owner: return "\(members) · you're the owner"
        case .admin: return "\(members) · you're an admin"
        case .member: return members
        }
    }
}
