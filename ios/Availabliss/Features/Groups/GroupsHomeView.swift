import SwiftUI

/// Groups home: greeting, the next confirmed plan, and your groups.
struct GroupsHomeView: View {
    @Environment(AppModel.self) private var app
    @Environment(\.theme) private var theme
    @State private var model: GroupsHomeViewModel
    @State private var showsNewGroup = false
    /// The Archived section starts collapsed.
    @State private var showsArchived = false

    init(api: APIClient) {
        _model = State(initialValue: GroupsHomeViewModel(api: api))
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                header
                if let event = model.upNext {
                    UpNextCard(event: event, goingCount: model.memberCount(forGroup: event.groupId))
                }
                groupsHeader
                groupsList
            }
            .padding(.horizontal, 20)
            .padding(.top, 28)
            .padding(.bottom, 24)
        }
        .screenBackground(OrbLayout.groups)
        .foregroundStyle(theme.ink)
        .chromeless()
        .task(id: app.accountVersion) { await model.load() }
        .refreshable { await model.load() }
        .task {
            // A burst of live events refetches once, a second after the last one.
            var reload: Task<Void, Never>?
            defer { reload?.cancel() }
            for await event in app.live.events() {
                switch event.kind {
                case .messageCreated:
                    // Only the unread badge changes, so patch it rather than refetching.
                    if let message = event.payload(as: Message.self), message.authorId != app.session.user?.id,
                       message.groupId != app.router.visibleChatGroupId {
                        model.addUnread(groupId: message.groupId)
                    }
                case .groupUpdated, .memberChanged, .proposalCreated, .proposalConfirmed, .eventChanged, .availabilityChanged, .reconnected:
                    reload?.cancel()
                    reload = Task {
                        try? await Task.sleep(for: .seconds(1))
                        guard !Task.isCancelled else { return }
                        await model.load()
                    }
                default: break
                }
            }
        }
        .sheet(isPresented: $showsNewGroup) {
            NewGroupSheet(api: model.api, timezone: app.session.user?.timezone ?? TimeZone.current.identifier) { group in
                Task { await model.load() }
                app.router.push(.group(id: group.id))
            }
        }
        .errorAlert($model.error)
    }

    private var header: some View {
        HStack(alignment: .bottom) {
            VStack(alignment: .leading, spacing: 2) {
                Text(Formatting.longDay(Date()))
                    .bodyFont(13, .medium, relativeTo: .subheadline)
                    .foregroundStyle(theme.muted)
                Text(app.session.user.map(\.firstName).flatMap { $0.isEmpty ? nil : "Hi, \($0)" } ?? "Hi")
                    .displayFont(32, .bold, relativeTo: .largeTitle)
                    .accessibilityAddTraits(.isHeader)
            }
            Spacer()
            Button {
                app.router.tab = .you
            } label: {
                Text(Avatar.initials(app.session.user?.name ?? "?"))
                    .bodyFont(15, .semibold)
                    .foregroundStyle(theme.ink)
                    .frame(width: 44, height: 44)
                    .glass(in: Circle(), shadow: false)
            }
            .buttonStyle(.pressable)
            .accessibilityLabel("Your profile")
        }
    }

    private var groupsHeader: some View {
        HStack {
            Text("Your groups").displayFont(20, .semibold).accessibilityAddTraits(.isHeader)
            Spacer()
            Button {
                showsNewGroup = true
            } label: {
                Label("New group", systemImage: "plus").labelStyle(.titleAndIcon)
            }
            .buttonStyle(.glassPill)
        }
    }

    @ViewBuilder
    private var groupsList: some View {
        if model.isLoading {
            StatusView(kind: .loading)
        } else if let error = model.loadError, model.groups.isEmpty {
            StatusView(kind: .error(error) { Task { await model.load() } })
        } else if model.groups.isEmpty {
            StatusView(kind: .empty(title: "No groups yet", message: "Start one and invite your people. We'll find the times that work.", systemImage: "circle.hexagongrid"))
        } else {
            let active = model.groups.filter { !$0.isArchived }
            let archived = model.groups.filter(\.isArchived)
            LazyVStack(spacing: 10) {
                ForEach(active) { group in groupButton(group) }
            }
            if !archived.isEmpty {
                VStack(alignment: .leading, spacing: 10) {
                    Button {
                        withAnimation(.snappy) { showsArchived.toggle() }
                    } label: {
                        HStack(spacing: 6) {
                            Text("Archived (\(archived.count))").bodyFont(15, .semibold)
                            Image(systemName: "chevron.down")
                                .font(.system(size: 12, weight: .bold))
                                .rotationEffect(.degrees(showsArchived ? 0 : -90))
                            Spacer()
                        }
                        .foregroundStyle(theme.muted)
                        .frame(minHeight: 44)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityAddTraits(.isHeader)
                    .accessibilityValue(showsArchived ? "Shown" : "Hidden")
                    if showsArchived {
                        LazyVStack(spacing: 10) {
                            ForEach(archived) { group in groupButton(group) }
                        }
                    }
                }
            }
        }
    }

    private func groupButton(_ group: GroupSummary) -> some View {
        Button {
            app.router.push(.group(id: group.id))
        } label: {
            GroupRow(group: group) {
                app.router.push(.group(id: group.id))
                app.router.push(.groupSection(id: group.id, section: .chat))
            }
        }
        .buttonStyle(.pressable)
    }
}

/// The "Up next" card: confirmed plan with one-tap Add to calendar.
private struct UpNextCard: View {
    let event: GroupEvent
    let goingCount: Int?
    @Environment(\.theme) private var theme

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            if let image = event.image { EventCover(imageURL: image, height: 150, cornerRadius: 16) }
            HStack {
                StatusPill(text: "Everyone's in", tone: .mint, systemImage: "checkmark", fontSize: 12)
                Spacer()
                if let groupName = event.groupName {
                    Text(groupName).bodyFont(12, .medium, relativeTo: .caption).foregroundStyle(theme.muted)
                }
            }
            VStack(alignment: .leading, spacing: 4) {
                Text(event.allDay ? Formatting.span(start: event.start, end: event.end, allDay: true) : Formatting.dayAndTime(event.start))
                    .displayFont(22, .semibold)
                Text(subtitle).bodyFont(14).foregroundStyle(theme.muted)
            }
            AddToCalendarButton(event: event, height: 48)
        }
        .padding(18)
        .glass(cornerRadius: 28)
        .accessibilityElement(children: .contain)
        .accessibilityLabel("Up next")
    }

    private var subtitle: String {
        var parts = [event.title]
        if let location = event.location, !location.isEmpty { parts = ["At \(location)"] }
        if let goingCount { parts.append("\(goingCount) going") }
        return parts.joined(separator: " · ")
    }
}

/// One group in the list.
struct GroupRow: View {
    let group: GroupSummary
    /// Opens the group straight on its chat (shown when there are unread messages).
    var onOpenChat: (() -> Void)?
    @Environment(\.theme) private var theme

    var body: some View {
        HStack(spacing: 12) {
            GroupTile(monogram: group.monogram, colorToken: group.color, imageURL: group.image)
                .overlay(alignment: .topTrailing) {
                    if group.unreadCount > 0 {
                        Circle().fill(theme.warn).frame(width: 10, height: 10)
                            .overlay(Circle().stroke(theme.bg, lineWidth: 1.5))
                            .offset(x: 3, y: -3)
                    }
                }
            VStack(alignment: .leading, spacing: 5) {
                HStack(spacing: 8) {
                    Text(group.name).bodyFont(16, .semibold).lineLimit(1)
                    Spacer(minLength: 0)
                    StatusPill(text: group.statusBadge.text, tone: group.statusBadge.tone)
                }
                Text(group.requirementSummary).bodyFont(13, relativeTo: .subheadline).foregroundStyle(theme.muted).lineLimit(1)
                HStack {
                    AvatarStack(people: group.memberPreview.map(\.avatarPerson))
                    Spacer()
                    SyncRing(synced: group.syncedCount, total: group.memberCount)
                }
                .frame(minHeight: 22)
                if group.unreadCount > 0, let onOpenChat {
                    UnreadChatButton(count: group.unreadCount, action: onOpenChat)
                        .padding(.top, 4)
                }
            }
        }
        .foregroundStyle(theme.ink)
        .padding(14)
        .glass(cornerRadius: 24)
        .contentShape(Rectangle())
        .accessibilityElement(children: .combine)
        .accessibilityHint(group.unreadCount > 0 ? "\(group.unreadCount) unread messages" : "")
        .accessibilityActions {
            if group.unreadCount > 0, let onOpenChat {
                Button("Open chat", action: onOpenChat)
            }
        }
    }
}

/// "3 new messages · Open chat" strip at the foot of a group card.
private struct UnreadChatButton: View {
    let count: Int
    let action: () -> Void
    @Environment(\.theme) private var theme

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                Image(systemName: "bubble.left.fill").font(.system(size: 13, weight: .semibold))
                Text(count == 1 ? "1 new message" : "\(count > 99 ? "99+" : String(count)) new messages")
                    .bodyFont(13, .semibold, relativeTo: .subheadline)
                Spacer(minLength: 4)
                Text("Open chat").bodyFont(13, .semibold, relativeTo: .subheadline)
                Image(systemName: "chevron.right").font(.system(size: 11, weight: .bold))
            }
            .foregroundStyle(theme.accentInk)
            .padding(.horizontal, 12)
            .frame(minHeight: 36)
            .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(theme.accentSoft))
            .contentShape(Rectangle())
        }
        .buttonStyle(.pressable)
    }
}
