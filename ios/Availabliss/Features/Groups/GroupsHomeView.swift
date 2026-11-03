import SwiftUI

/// Groups home: greeting, the next confirmed plan, and your groups.
struct GroupsHomeView: View {
    @Environment(AppModel.self) private var app
    @Environment(\.theme) private var theme
    @State private var model: GroupsHomeViewModel
    @State private var showsNewGroup = false

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
                app.router.push(.group(id: group.id, tab: .calendar))
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
                Text("Hi, \(app.session.user?.firstName ?? "there")")
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
            LazyVStack(spacing: 10) {
                ForEach(model.groups) { group in
                    Button {
                        app.router.push(.group(id: group.id, tab: .calendar))
                    } label: {
                        GroupRow(group: group)
                    }
                    .buttonStyle(.pressable)
                }
            }
        }
    }
}

/// The "Up next" card: confirmed plan with one-tap Add to calendar.
private struct UpNextCard: View {
    let event: GroupEvent
    let goingCount: Int?
    @Environment(\.theme) private var theme

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
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
    @Environment(\.theme) private var theme

    var body: some View {
        HStack(spacing: 12) {
            GroupTile(monogram: group.monogram, colorToken: group.color)
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
            }
        }
        .foregroundStyle(theme.ink)
        .padding(14)
        .glass(cornerRadius: 24)
        .contentShape(Rectangle())
        .accessibilityElement(children: .combine)
        .accessibilityHint(group.unreadCount > 0 ? "\(group.unreadCount) unread messages" : "")
    }
}

/// Add-to-device-calendar button with the "Added" state; picks a calendar when there's a choice.
struct AddToCalendarButton: View {
    let event: GroupEvent
    var height: CGFloat = 54
    @Environment(AppModel.self) private var app
    @State private var isAdded = false
    @State private var error: String?

    var body: some View {
        Button {
            Task { await add() }
        } label: {
            Label(isAdded ? "Added to your calendar" : "Add to calendar", systemImage: isAdded ? "checkmark" : "calendar.badge.plus")
        }
        .buttonStyle(.filled(isAdded ? .success : .primary, height: height, cornerRadius: height > 50 ? 18 : 16))
        .disabled(isAdded)
        .onAppear { isAdded = app.deviceCalendar.isAdded(event.id) }
        .errorAlert($error)
        .sensoryFeedback(.success, trigger: isAdded)
    }

    private func add() async {
        do {
            try await app.deviceCalendar.add(event)
            withAnimation { isAdded = true }
        } catch {
            self.error = error.userMessage
        }
    }
}
