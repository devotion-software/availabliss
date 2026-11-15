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
            .safeAreaInset(edge: .top, spacing: 0) {
                if let group = detail.group {
                    GroupTopBar(group: group, tab: $tab, unreadCount: detail.unreadCount)
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
            // Each tab fades in (the container animates `tab` changes).
            SwiftUI.Group {
                switch tab {
                case .calendar:
                    GroupAvailabilityView(detail: detail, group: group, onOpenTiming: { tab = .timing })
                case .chat:
                    ChatView(detail: detail, group: group)
                case .proposals:
                    GroupPlansView(detail: detail, group: group)
                case .timing:
                    TimingEditorView(detail: detail, group: group, onDone: { tab = .calendar })
                }
            }
            .id(tab)
            .transition(.opacity)
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

/// Header shared by the group's tabs: back, name + "6 members · you're an admin", invite.
struct GroupHeader: View {
    let group: Group
    @Environment(AppModel.self) private var app

    var body: some View {
        ScreenHeader(title: group.name, subtitle: subtitle, titleSize: 22, backLabel: "Back to groups", onBack: { app.router.pop() }) {
            IconButton(systemImage: canInvite ? "person.badge.plus" : "person.2", label: canInvite ? "Members and invites" : "Members") {
                app.router.push(.members(groupId: group.id))
            }
        }
    }

    /// Only owners and admins can invite, so only they see the "add person" icon.
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

/// Pinned at the top of a group: back, name, members, and the section strip.
private struct GroupTopBar: View {
    let group: Group
    @Binding var tab: GroupTab
    let unreadCount: Int
    @Environment(\.theme) private var theme

    var body: some View {
        VStack(spacing: 10) {
            GroupHeader(group: group)
            HStack(spacing: 4) {
                item(.calendar, "Calendar", "calendar")
                item(.chat, "Chat", "bubble.left")
                item(.proposals, "Proposals", "checkmark.circle")
                item(.timing, "Timing", "slider.horizontal.3")
            }
            .padding(4)
            .glass(cornerRadius: 16, shadow: false)
        }
        .padding(.horizontal, 20)
        .padding(.top, 8)
        .padding(.bottom, 10)
        .background {
            Rectangle().fill(.ultraThinMaterial).mask {
                LinearGradient(stops: [.init(color: .black, location: 0.75), .init(color: .clear, location: 1)], startPoint: .top, endPoint: .bottom)
            }
            .ignoresSafeArea(edges: .top)
        }
    }

    private func item(_ value: GroupTab, _ title: String, _ systemImage: String) -> some View {
        let isOn = tab == value
        return Button {
            tab = value
        } label: {
            HStack(spacing: 5) {
                Image(systemName: systemImage).font(.system(size: 13, weight: .semibold))
                Text(title).bodyFont(13, isOn ? .semibold : .medium, relativeTo: .caption).lineLimit(1).minimumScaleFactor(0.8)
                if value == .chat, unreadCount > 0 {
                    Text(unreadCount > 99 ? "99+" : "\(unreadCount)")
                        .font(AppFont.body(10, .bold, relativeTo: .caption2))
                        .foregroundStyle(Color(hex: 0x1A1B3A))
                        .padding(.horizontal, 5)
                        .frame(minWidth: 16, minHeight: 16)
                        .background(Capsule().fill(theme.warn))
                }
            }
            .foregroundStyle(isOn ? theme.onSolid : theme.ink)
            .frame(maxWidth: .infinity, minHeight: 36)
            .background { if isOn { RoundedRectangle(cornerRadius: 12, style: .continuous).fill(theme.solid) } }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(unreadCount > 0 && value == .chat ? "\(title), \(unreadCount) unread" : title)
        .accessibilityAddTraits(isOn ? .isSelected : [])
    }
}
