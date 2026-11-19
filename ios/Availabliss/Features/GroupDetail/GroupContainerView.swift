import SwiftUI

/// A group's screens: its calendar (the group's home), and Chat / Plans / Timing / Settings pushed over it.
/// Each is its own route, and they all share one `GroupDetailModel` from `GroupDetailStore`,
/// so pushing a section doesn't load or subscribe to the group again.
struct GroupContainerView: View {
    let section: GroupSection?
    @Environment(AppModel.self) private var app
    @Environment(\.theme) private var theme
    @State private var detail: GroupDetailModel
    /// "New group with these people", from the More menu or Group settings.
    @State private var showsNewGroup = false
    /// From muting or unmuting in the More menu.
    @State private var toast: String?
    @State private var error: String?
    /// How far the section under the header is scrolled, so the header's fade shows only once it is.
    @State private var topBarScroll = TopBarScrollState()

    init(groupId: String, section: GroupSection?, app: AppModel) {
        self.section = section
        _detail = State(initialValue: app.groupDetails.model(for: groupId, api: app.api, live: app.live, me: { [session = app.session] in session.user?.id }))
    }

    var body: some View {
        content
            .environment(topBarScroll)
            .safeAreaInset(edge: .top, spacing: 0) {
                if let group = detail.group {
                    GroupTopBar(
                        detail: detail, group: group, section: section, unreadCount: detail.unreadCount,
                        onNewGroup: { showsNewGroup = true }, toast: $toast, error: $error, scroll: topBarScroll)
                }
            }
            .sheet(isPresented: $showsNewGroup) {
                if let group = detail.group {
                    NewGroupSheet(
                        api: app.api, timezone: app.session.user?.timezone ?? TimeZone.current.identifier,
                        basedOn: group, me: app.session.user?.id
                    ) { created in
                        app.router.push(.group(id: created.id))
                    }
                }
            }
            .toast($toast)
            .errorAlert($error)
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
                GroupAvailabilityView(
                    detail: detail, group: group,
                    onOpenTiming: {
                        app.router.push(.groupSection(id: group.id, section: .timing))
                    })
            case .chat:
                ChatView(detail: detail, group: group)
            case .plans:
                GroupPlansView(detail: detail, group: group)
            case .timing:
                TimingEditorView(detail: detail, group: group, onDone: { app.router.pop() })
            case .settings:
                GroupSettingsView(detail: detail, group: group, onNewGroup: { showsNewGroup = true })
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
/// (Plans, Timing, Members, New group with these people, Mute notifications, Group settings). On a pushed section: back to the calendar, with the group's
/// name (Settings is titled "Group settings", with the name under it).
private struct GroupTopBar: View {
    let detail: GroupDetailModel
    let group: Group
    let section: GroupSection?
    let unreadCount: Int
    let onNewGroup: () -> Void
    @Binding var toast: String?
    @Binding var error: String?
    let scroll: TopBarScrollState
    @Environment(AppModel.self) private var app
    @Environment(\.theme) private var theme

    private static let fadeHeight: CGFloat = 48

    /// The group's photo, or its monogram on the group's colour, beside its name in the chat's header (as on
    /// Android and web). The calendar's header leaves it out: with Chat and More beside it there's no room.
    private var tile: AnyView {
        AnyView(GroupTile(monogram: group.monogram, colorToken: group.color, imageURL: group.image, size: 40, cornerRadius: 14))
    }

    /// Photos and files, and Search, in the chat's header.
    @ViewBuilder private var chatActions: some View {
        if section == .chat {
            HStack(spacing: 8) {
                IconButton(systemImage: "photo.on.rectangle", label: "Photos and files") { detail.chatHeaderRequest = .media }
                IconButton(systemImage: "magnifyingglass", label: "Search chat") { detail.chatHeaderRequest = .search }
            }
        }
    }

    /// "Lake District crew Plans" on Plans; the group's name on its other sections.
    private var sectionTitle: String { section == .plans ? "\(group.name) Plans" : group.name }

    var body: some View {
        SwiftUI.Group {
            if section == .settings {
                ScreenHeader(
                    title: "Group settings", subtitle: group.name, titleSize: 22,
                    backLabel: app.router.isOverGroup(group.id) ? "Back to calendar" : "Back", onBack: { app.router.back() })
            } else if let section, app.router.isOverGroup(group.id) {
                ScreenHeader(
                    title: sectionTitle, titleSize: 22, backLabel: "Back to calendar", onBack: { app.router.back() },
                    leading: section == .chat ? tile : nil
                ) { chatActions }
            } else if section != nil {
                // Opened from elsewhere (Activity): back returns there; the name opens the group.
                ScreenHeader(
                    title: sectionTitle, titleSize: 22, onBack: { app.router.back() },
                    onTitle: {
                        app.router.pop(); app.open(.group(id: group.id))
                    },
                    leading: section == .chat ? tile : nil
                ) { chatActions }
            } else {
                GroupHeader(detail: detail, group: group, unreadCount: unreadCount, onNewGroup: onNewGroup, toast: $toast, error: $error)
            }
        }
        .padding(.horizontal, 20)
        .padding(.top, 8)
        .padding(.bottom, 16)
        .background {
            // Just a blur of the calendar behind, with no grey wash (the system materials add one,
            // which looked like a flat grey band), at full strength behind the name and buttons and a
            // short band under them. Below that it eases out over 48pt that hang over the content, so the
            // calendar scrolls away under it gradually rather than hitting a hard edge. That fade only
            // shows once the content is scrolled: at the top it would blur the first card, and keeping
            // it out of the header's own height means no big gap above the content. At the top there's
            // no blur at all: there's nothing under the header to hide, and blurring the background
            // behind it alone left a visible edge where the header stops.
            ClearBlur(style: .systemUltraThinMaterial)
                .mask {
                    VStack(spacing: 0) {
                        Color.black.opacity(Self.blurStrength(scroll))
                        LinearGradient(
                            stops: [
                                .init(color: .black, location: 0),
                                .init(color: .black.opacity(0.82), location: 0.25),
                                .init(color: .black.opacity(0.5), location: 0.5),
                                .init(color: .black.opacity(0.18), location: 0.78),
                                .init(color: .clear, location: 1),
                            ],
                            startPoint: .top, endPoint: .bottom
                        )
                        .frame(height: Self.fadeHeight)
                        .opacity(scroll.fadeProgress)
                    }
                }
                .padding(.bottom, -Self.fadeHeight)
                .allowsHitTesting(false)
                .ignoresSafeArea(edges: .top)
                .animation(.easeOut(duration: 0.15), value: scroll.fadeProgress)
        }
    }
}

extension GroupTopBar {
    /// How strong the header's blur is: none at the top, full once scrolled 24pt. iOS 17 can't
    /// report the scroll position, so there it's always full (the content scrolls under the name).
    static func blurStrength(_ scroll: TopBarScrollState) -> Double {
        if #available(iOS 18.0, *) { return scroll.fadeProgress }
        return 1
    }
}

/// The calendar's header: back, name + "6 members · you're an admin" (and a crossed-out bell when muted),
/// Chat (with unread badge) and More.
private struct GroupHeader: View {
    let detail: GroupDetailModel
    let group: Group
    let unreadCount: Int
    let onNewGroup: () -> Void
    @Binding var toast: String?
    @Binding var error: String?
    @Environment(AppModel.self) private var app
    @Environment(\.theme) private var theme

    var body: some View {
        ScreenHeader(
            title: group.name, subtitle: subtitle, subtitleSystemImage: detail.isMuted ? "bell.slash" : nil, titleSize: 22, backLabel: "Back to groups",
            onBack: { app.router.pop() }
        ) {
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
            Button {
                app.router.push(.groupSection(id: group.id, section: .plans))
            } label: {
                Label("Plans", systemImage: "checkmark.circle")
            }
            Button {
                app.router.push(.groupSection(id: group.id, section: .timing))
            } label: {
                Label("Timing", systemImage: "slider.horizontal.3")
            }
            Button {
                app.router.push(.members(groupId: group.id))
            } label: {
                Label(canInvite ? "Members and invites" : "Members", systemImage: canInvite ? "person.badge.plus" : "person.2")
            }
            if group.members.count > 1 {
                Button(action: onNewGroup) {
                    Label("New group with these people", systemImage: "plus.square.on.square")
                }
            }
            muteMenu
            Button {
                app.router.push(.groupSection(id: group.id, section: .settings))
            } label: {
                Label("Group settings", systemImage: "gearshape")
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

    /// "Unmute" when muted, otherwise "Mute notifications" for 8 hours, a week, or until turned back on.
    @ViewBuilder
    private var muteMenu: some View {
        if detail.isMuted {
            Button {
                setMute(nil)
            } label: {
                Label("Unmute", systemImage: "bell")
            }
        } else {
            Menu {
                ForEach(MuteChoice.allCases) { choice in
                    Button(choice.label) { setMute(choice) }
                }
            } label: {
                Label("Mute notifications", systemImage: "bell.slash")
            }
        }
    }

    /// Mutes for `choice`, or with nil unmutes.
    private func setMute(_ choice: MuteChoice?) {
        Task {
            do {
                if let choice {
                    try await detail.setMute(until: choice.until())
                    toast = choice.toast
                } else {
                    try await detail.unmute()
                    toast = "Notifications on"
                }
            } catch  where !error.isCancellation {
                self.error = error.userMessage
            } catch {}
        }
    }

    /// Only owners and admins can invite.
    private var canInvite: Bool { group.role != .member }

    private var subtitle: String {
        var members = Formatting.plural(group.memberCount, "member")
        if group.isArchived { members += " · archived" }
        switch group.role {
        case .owner: return "\(members) · you're the owner"
        case .admin: return "\(members) · you're an admin"
        case .member: return members
        }
    }
}

/// How long "Mute notifications" lasts.
private enum MuteChoice: CaseIterable, Identifiable {
    case eightHours, week, untilUnmuted

    var id: Self { self }

    var label: String {
        switch self {
        case .eightHours: "For 8 hours"
        case .week: "For a week"
        case .untilUnmuted: "Until I turn it back on"
        }
    }

    var toast: String {
        switch self {
        case .eightHours: "Muted for 8 hours"
        case .week: "Muted for a week"
        case .untilUnmuted: "Muted"
        }
    }

    /// When the mute ends (nil: when it's unmuted).
    func until(from now: Date = Date()) -> Date? {
        switch self {
        case .eightHours: now.addingTimeInterval(8 * 60 * 60)
        case .week: Calendar.current.date(byAdding: .day, value: 7, to: now)
        case .untilUnmuted: nil
        }
    }
}

/// How far a group screen's content is scrolled under its pinned header. Each section's scroll view
/// reports into it with `tracksScrollUnderTopBar()`; the header shows its bottom fade as it grows.
@Observable
final class TopBarScrollState {
    var offset: CGFloat = 0

    /// 0 at the top, 1 once scrolled 24pt.
    var fadeProgress: Double { Double(min(max(offset / 24, 0), 1)) }
}

extension View {
    /// Reports this scroll view's offset to the group header above it (if any). Needs iOS 18; on
    /// iOS 17 the header simply keeps its fade off.
    func tracksScrollUnderTopBar() -> some View { modifier(TracksScrollUnderTopBar()) }
}

private struct TracksScrollUnderTopBar: ViewModifier {
    @Environment(TopBarScrollState.self) private var state: TopBarScrollState?

    func body(content: Content) -> some View {
        if #available(iOS 18.0, *), let state {
            content.onScrollGeometryChange(for: CGFloat.self) { geometry in
                geometry.contentOffset.y + geometry.contentInsets.top
            } action: { _, offset in
                // Only the 0...24pt range matters; skip the rest to avoid needless header updates.
                let clamped = min(max(offset, 0), 24)
                if clamped != state.offset { state.offset = clamped }
            }
        } else {
            content
        }
    }
}
