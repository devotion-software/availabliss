import Observation
import SwiftUI

@MainActor
@Observable
final class ActivityViewModel {
    private(set) var notifications: [AppNotification] = []
    private(set) var isLoading = false
    private(set) var loadError: String?
    /// The Chats tab: every group chat, latest message first.
    private(set) var chats: [ChatSummary] = []
    private(set) var chatsLoaded = false
    private(set) var chatsError: String?
    @ObservationIgnored private var chatsRefresh: Task<Void, Never>?

    /// Everything but chats (they have their own tab).
    var updates: [AppNotification] { notifications.filter { $0.kind != .message } }

    private let api: APIClient

    init(api: APIClient) {
        self.api = api
    }

    func load() async {
        isLoading = notifications.isEmpty
        defer { isLoading = false }
        do {
            notifications = try await api.send(API.Notifications.list()).notifications.sorted { $0.createdAt > $1.createdAt }
            loadError = nil
        } catch  where !error.isCancellation {
            loadError = error.userMessage
        } catch {}
    }

    func loadChats() async {
        do {
            chats = try await api.send(API.Chat.chats()).chats
            chatsError = nil
        } catch  where !error.isCancellation {
            if !chatsLoaded { chatsError = error.userMessage }
        } catch {}
        chatsLoaded = true
    }

    /// A message arrived, changed or was read: refetch the chats a moment later (one request for a burst).
    func chatsChanged() {
        chatsRefresh?.cancel()
        chatsRefresh = Task {
            try? await Task.sleep(for: .milliseconds(400))
            guard !Task.isCancelled else { return }
            await loadChats()
        }
    }

    /// A new item, or an update to one we have (a group's chat item gets a new message, or is
    /// read): replaced in place, or moved to the top when newer.
    func upsert(_ notification: AppNotification) {
        if let index = notifications.firstIndex(where: { $0.id == notification.id }) {
            if notification.createdAt > notifications[index].createdAt {
                notifications.remove(at: index)
                notifications.insert(notification, at: 0)
            } else {
                notifications[index] = notification
            }
        } else {
            notifications.insert(notification, at: 0)
        }
    }
}

/// Activity: your group chats (like a messaging app) and everything else that's happened across
/// your groups (Updates). Like web and Android.
struct ActivityView: View {
    @Environment(AppModel.self) private var app
    @Environment(\.theme) private var theme
    @State private var model: ActivityViewModel
    @AppStorage("activity.tab") private var tab = Tab.chats

    enum Tab: String {
        case chats, updates
    }

    init(api: APIClient) {
        _model = State(initialValue: ActivityViewModel(api: api))
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                ScreenHeader(title: "Activity", subtitle: "Your chats, proposals, plans and nudges") {
                    IconButton(systemImage: "calendar", label: "My plans") { app.router.push(.myPlans) }
                }
                Picker("Show", selection: $tab) {
                    Text("Chats").tag(Tab.chats)
                    Text("Updates").tag(Tab.updates)
                }
                .pickerStyle(.segmented)
                if tab == .chats { chatsContent } else { content }
            }
            .padding(.horizontal, 20)
            .padding(.top, 28)
            .padding(.bottom, 24)
        }
        .tabBarScrollInset()
        .screenBackground(OrbLayout.proposal)
        .foregroundStyle(theme.ink)
        .chromeless()
        .task {
            async let chats: Void = model.loadChats()
            await model.load()
            await app.markNotificationsRead()
            await chats
        }
        .refreshable {
            async let chats: Void = model.loadChats()
            await model.load()
            await chats
        }
        // An invite opened from here was joined or declined (a declined one's item goes): catch up.
        .onChange(of: app.router.pendingInvite == nil) { _, closed in
            if closed { Task { await model.load() } }
        }
        .task {
            for await event in app.live.events() {
                switch event.kind {
                case .reconnected:
                    await model.load()
                    await model.loadChats()
                case .notificationCreated:
                    if let notification = event.payload(as: AppNotification.self) { model.upsert(notification) }
                    model.chatsChanged()
                case .messageCreated, .messageUpdated:
                    model.chatsChanged()
                default:
                    break
                }
            }
        }
    }

    @ViewBuilder
    private var content: some View {
        if model.isLoading {
            StatusView(kind: .loading)
        } else if let error = model.loadError, model.notifications.isEmpty {
            StatusView(kind: .error(error) { Task { await model.load() } })
        } else if model.updates.isEmpty {
            StatusView(kind: .empty(title: "All quiet", message: "When someone proposes a time or a plan is confirmed, it shows up here.", systemImage: "bell"))
                .glass(cornerRadius: 24)
        } else {
            LazyVStack(spacing: 10) {
                ForEach(model.updates) { notification in
                    Button {
                        if let link = DeepLink(
                            data: notification.data.merging(["kind": notification.kind.rawValue, "groupId": notification.groupId ?? ""]) { current, _ in current })
                        {
                            app.openFromActivity(link)
                        }
                    } label: {
                        NotificationRow(notification: notification)
                    }
                    .buttonStyle(.pressable)
                }
            }
        }
    }
}

extension ActivityView {
    @ViewBuilder
    fileprivate var chatsContent: some View {
        if !model.chatsLoaded {
            StatusView(kind: .loading)
        } else if let error = model.chatsError, model.chats.isEmpty {
            StatusView(kind: .error(error) { Task { await model.loadChats() } })
        } else if model.chats.isEmpty {
            StatusView(
                kind: .empty(
                    title: "No chats yet", message: "Each group you're in has a chat. Make a group, or join one from an invite.",
                    systemImage: "bubble.left.and.bubble.right")
            )
            .glass(cornerRadius: 24)
        } else {
            LazyVStack(spacing: 0) {
                ForEach(model.chats) { chat in
                    Button {
                        app.openFromActivity(.groupChat(id: chat.group.id))
                    } label: {
                        ChatRow(chat: chat, me: app.session.user?.id)
                    }
                    .buttonStyle(.pressable)
                    .overlay(alignment: .top) {
                        if chat.id != model.chats.first?.id { Rectangle().fill(theme.line).frame(height: 1).padding(.leading, 70) }
                    }
                }
            }
            .padding(.horizontal, 12)
            .glass(cornerRadius: 24)
        }
    }
}

/// A chat in Activity: the group's tile, its name, the latest message and an unread count.
private struct ChatRow: View {
    let chat: ChatSummary
    let me: String?
    @Environment(\.theme) private var theme

    var body: some View {
        HStack(spacing: 12) {
            GroupTile(monogram: chat.group.monogram, colorToken: chat.group.color, imageURL: chat.group.image, size: 46, cornerRadius: 15)
            VStack(alignment: .leading, spacing: 2) {
                HStack {
                    Text(chat.group.name).bodyFont(15, .semibold).lineLimit(1)
                    Spacer(minLength: 8)
                    if let last = chat.lastMessage {
                        Text(Formatting.relative(last.createdAt))
                            .bodyFont(11, chat.unreadCount > 0 ? .semibold : .regular, relativeTo: .caption2)
                            .foregroundStyle(chat.unreadCount > 0 ? theme.accentInk : theme.muted)
                    }
                }
                HStack(alignment: .top) {
                    Text(chat.lastMessage?.preview(me: me) ?? "No messages yet. Say hello!")
                        .bodyFont(13, relativeTo: .subheadline)
                        .foregroundStyle(theme.muted)
                        .lineLimit(2)
                    Spacer(minLength: 8)
                    if chat.unreadCount > 0 {
                        Text(chat.unreadCount > 99 ? "99+" : "\(chat.unreadCount)")
                            .bodyFont(11, .bold, relativeTo: .caption2)
                            .foregroundStyle(theme.onAccent)
                            .padding(.horizontal, 6)
                            .frame(minWidth: 20, minHeight: 20)
                            .background(Capsule().fill(theme.accent))
                            .accessibilityLabel("\(chat.unreadCount) unread")
                    }
                }
            }
        }
        .foregroundStyle(theme.ink)
        .padding(.vertical, 12)
        .contentShape(Rectangle())
        .accessibilityElement(children: .combine)
    }
}

private struct NotificationRow: View {
    let notification: AppNotification
    @Environment(\.theme) private var theme

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: icon)
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(tint.foreground)
                .frame(width: 36, height: 36)
                .background(RoundedRectangle(cornerRadius: 12).fill(tint.background))
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 2) {
                HStack {
                    Text(notification.title).bodyFont(14, .semibold).lineLimit(2)
                    if let newCount { CountBadge(count: newCount) }
                    Spacer()
                    Text(Formatting.relative(notification.createdAt)).bodyFont(11, relativeTo: .caption2).foregroundStyle(theme.muted)
                }
                Text(notification.body).bodyFont(13, relativeTo: .subheadline).foregroundStyle(theme.muted).lineLimit(3)
            }
            if notification.readAt == nil {
                Circle().fill(theme.warn).frame(width: 8, height: 8).padding(.top, 6).accessibilityLabel("Unread")
            }
        }
        .foregroundStyle(theme.ink)
        .padding(14)
        .glass(cornerRadius: 22)
        .accessibilityElement(children: .combine)
    }

    /// "3 new" on a group's unread chat item with more than one message.
    private var newCount: Int? {
        guard notification.kind == .message, notification.readAt == nil, let count = Int(notification.data["count"] ?? ""), count > 1 else { return nil }
        return count
    }

    private var icon: String {
        switch notification.kind {
        case .proposalCreated: "checkmark.circle"
        case .proposalConfirmed: "party.popper"
        case .proposalChanged: "exclamationmark.circle"
        case .nudge: "hand.wave"
        case .inviteAccepted, .inviteReceived: "person.badge.plus"
        case .message: "bubble.left"
        case .eventCreated: "calendar.badge.plus"
        case .roleChanged: "checkmark.shield"
        case .reminder: "alarm"
        case .unknown: "bell"
        }
    }

    /// On the group's own colour, so groups are easy to tell apart; the kind's colour without a group.
    private var tint: (background: Color, foreground: Color) {
        if let color = notification.groupColor { return (theme.tile(color), theme.ink) }
        return switch notification.kind {
        case .proposalConfirmed: (theme.okBg, theme.okInk)
        case .nudge, .proposalChanged: (theme.warnBg, theme.warnInk)
        default: (theme.accentSoft, theme.accentInk)
        }
    }
}

/// "3 new", next to a chat's title in Activity.
private struct CountBadge: View {
    let count: Int
    @Environment(\.theme) private var theme

    var body: some View {
        Text("\(count) new")
            .bodyFont(11, .bold, relativeTo: .caption2)
            .foregroundStyle(theme.onAccent)
            .padding(.horizontal, 7)
            .padding(.vertical, 1)
            .background(Capsule().fill(theme.accent))
    }
}
