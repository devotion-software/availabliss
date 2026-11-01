import Observation
import SwiftUI

@MainActor
@Observable
final class ActivityViewModel {
    private(set) var notifications: [AppNotification] = []
    private(set) var isLoading = false
    private(set) var loadError: String?

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
        } catch where !error.isCancellation {
            loadError = error.userMessage
        } catch {}
    }

    func prepend(_ notification: AppNotification) {
        guard !notifications.contains(where: { $0.id == notification.id }) else { return }
        notifications.insert(notification, at: 0)
    }
}

/// Activity: everything that's happened across your groups.
struct ActivityView: View {
    @Environment(AppModel.self) private var app
    @Environment(\.theme) private var theme
    @State private var model: ActivityViewModel

    init(api: APIClient) {
        _model = State(initialValue: ActivityViewModel(api: api))
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                ScreenHeader(title: "Activity", subtitle: "Proposals, plans and nudges")
                content
            }
            .padding(.horizontal, 20)
            .padding(.top, 28)
            .padding(.bottom, 24)
        }
        .screenBackground(OrbLayout.proposal)
        .foregroundStyle(theme.ink)
        .chromeless()
        .task {
            await model.load()
            await app.markNotificationsRead()
        }
        .refreshable { await model.load() }
        .task {
            for await event in app.live.events() where event.kind == .notificationCreated {
                if let notification = event.payload(as: AppNotification.self) { model.prepend(notification) }
                if app.router.tab == .activity { await app.markNotificationsRead() }
            }
        }
    }

    @ViewBuilder
    private var content: some View {
        if model.isLoading {
            StatusView(kind: .loading)
        } else if let error = model.loadError, model.notifications.isEmpty {
            StatusView(kind: .error(error) { Task { await model.load() } })
        } else if model.notifications.isEmpty {
            StatusView(kind: .empty(title: "All quiet", message: "When someone proposes a time or a plan is confirmed, it shows up here.", systemImage: "bell"))
                .glass(cornerRadius: 24)
        } else {
            LazyVStack(spacing: 10) {
                ForEach(model.notifications) { notification in
                    Button {
                        if let link = DeepLink(data: notification.data.merging(["kind": notification.kind.rawValue, "groupId": notification.groupId ?? ""]) { current, _ in current }) {
                            app.open(link)
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

    private var icon: String {
        switch notification.kind {
        case .proposalCreated: "checkmark.circle"
        case .proposalConfirmed: "party.popper"
        case .nudge: "hand.wave"
        case .inviteAccepted: "person.badge.plus"
        case .message: "bubble.left"
        case .eventCreated: "calendar.badge.plus"
        case .unknown: "bell"
        }
    }

    private var tint: (background: Color, foreground: Color) {
        switch notification.kind {
        case .proposalConfirmed: (theme.okBg, theme.okInk)
        case .nudge: (theme.warnBg, theme.warnInk)
        default: (theme.accentSoft, theme.accentInk)
        }
    }
}
