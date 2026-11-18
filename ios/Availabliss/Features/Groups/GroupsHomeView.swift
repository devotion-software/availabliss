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
                if !model.upNext.isEmpty {
                    UpNextPager(events: model.upNext, goingCount: model.memberCount(forGroup:), group: model.group, open: openPlan)
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
                        message.groupId != app.router.visibleChatGroupId
                    {
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
            StatusView(
                kind: .empty(title: "No groups yet", message: "Start one and invite your people. We'll find the times that work.", systemImage: "circle.hexagongrid"))
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

    /// Opens a confirmed plan: its proposal ("Everyone's in", with the details and Add to calendar),
    /// or the group's Plans for an event added without one.
    private func openPlan(_ event: GroupEvent) {
        if let proposalId = event.proposalId {
            app.router.push(.proposal(id: proposalId))
        } else {
            app.router.push(.group(id: event.groupId))
            app.router.push(.groupSection(id: event.groupId, section: .plans))
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

/// The next confirmed plans, one card each, swiped left and right with page dots under them
/// (no dots when there's only one). Cards are the same height so the dots don't jump.
private struct UpNextPager: View {
    let events: [GroupEvent]
    let goingCount: (String) -> Int?
    let group: (String) -> GroupSummary?
    let open: (GroupEvent) -> Void
    @State private var page: String?

    var body: some View {
        if events.count == 1, let event = events.first {
            UpNextCard(event: event, group: group(event.groupId), goingCount: goingCount(event.groupId)) { open(event) }
        } else {
            VStack(spacing: 12) {
                ScrollView(.horizontal) {
                    HStack(alignment: .top, spacing: 20) {
                        ForEach(events) { event in
                            UpNextCard(event: event, group: group(event.groupId), goingCount: goingCount(event.groupId)) { open(event) }
                                .frame(maxHeight: .infinity)
                                .containerRelativeFrame(.horizontal)
                                .id(event.id)
                        }
                    }
                    .fixedSize(horizontal: false, vertical: true)
                    .scrollTargetLayout()
                }
                .scrollTargetBehavior(.viewAligned(limitBehavior: .always))
                .scrollPosition(id: $page)
                .scrollIndicators(.hidden)
                // Lets the cards' shadows show, and the next card slide in from the screen's edge.
                .scrollClipDisabled()
                PageDots(count: events.count, current: currentIndex) { index in
                    withAnimation(.snappy) { page = events[index].id }
                }
            }
        }
    }

    private var currentIndex: Int {
        page.flatMap { id in events.firstIndex { $0.id == id } } ?? 0
    }
}

/// A row of small dots, the current one wider and darker; tapping one goes to it.
private struct PageDots: View {
    let count: Int
    let current: Int
    let select: (Int) -> Void
    @Environment(\.theme) private var theme

    var body: some View {
        HStack(spacing: 6) {
            ForEach(0..<count, id: \.self) { index in
                Capsule()
                    .fill(index == current ? theme.ink : theme.muted.opacity(0.35))
                    .frame(width: index == current ? 18 : 7, height: 7)
                    .padding(.vertical, 8)
                    .contentShape(Rectangle())
                    .onTapGesture { select(index) }
            }
        }
        .animation(.snappy, value: current)
        .frame(maxWidth: .infinity)
        .accessibilityElement()
        .accessibilityLabel("Plan")
        .accessibilityValue("\(current + 1) of \(count)")
        .accessibilityAdjustableAction { direction in
            switch direction {
            case .increment: if current < count - 1 { select(current + 1) }
            case .decrement: if current > 0 { select(current - 1) }
            @unknown default: break
            }
        }
    }
}

/// The "Up next" card: confirmed plan with one-tap Add to calendar. Tapping anywhere else on the
/// card opens the plan.
private struct UpNextCard: View {
    let event: GroupEvent
    /// Its group from your list, for the small photo or coloured tile beside the name.
    let group: GroupSummary?
    let goingCount: Int?
    let open: () -> Void
    @Environment(\.theme) private var theme

    var body: some View {
        Button(action: open) { card }
            .buttonStyle(.pressable)
            .accessibilityElement(children: .contain)
            .accessibilityLabel("Up next")
            .accessibilityHint("Opens the plan")
            .accessibilityActions {
                Button("Open plan", action: open)
            }
    }

    private var card: some View {
        VStack(alignment: .leading, spacing: 14) {
            if let image = event.image { EventCover(imageURL: image, height: 150, cornerRadius: 16) }
            HStack {
                StatusPill(text: "It\u{2019}s on", tone: .mint, systemImage: "checkmark", fontSize: 12)
                Spacer()
                if let groupName = event.groupName ?? group?.name {
                    HStack(spacing: 6) {
                        if let group {
                            GroupTile(monogram: group.monogram, colorToken: group.color, imageURL: group.image, size: 20, cornerRadius: 7)
                        }
                        Text(groupName).bodyFont(12, .medium, relativeTo: .caption).foregroundStyle(theme.muted).lineLimit(1)
                    }
                }
            }
            VStack(alignment: .leading, spacing: 4) {
                Text(event.allDay ? Formatting.span(start: event.start, end: event.end, allDay: true) : Formatting.dayAndTime(event.start))
                    .displayFont(22, .semibold)
                Text(subtitle).bodyFont(14).foregroundStyle(theme.muted)
            }
            // In the pager a shorter card stretches to match the tallest, keeping its button at the foot.
            Spacer(minLength: 0)
            AddToCalendarButton(event: event, height: 48)
        }
        .foregroundStyle(theme.ink)
        .padding(18)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .glass(cornerRadius: 28)
        .contentShape(RoundedRectangle(cornerRadius: 28, style: .continuous))
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
