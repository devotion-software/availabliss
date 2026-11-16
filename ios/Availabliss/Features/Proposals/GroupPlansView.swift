import SwiftUI

/// The group's Plans screen (titled "Proposals"): open proposals, confirmed / added events, and Add event.
/// The lists live on the shared `GroupDetailModel`, which keeps them in sync with live updates.
struct GroupPlansView: View {
    let detail: GroupDetailModel
    let group: Group
    @Environment(AppModel.self) private var app
    @Environment(\.theme) private var theme
    @State private var editingEvent: EventEditorSheet.Target?
    @State private var error: String?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                HStack {
                    Text("Proposals").displayFont(19, .semibold).accessibilityAddTraits(.isHeader)
                    Spacer()
                    Button { editingEvent = .new } label: { Label("Add event", systemImage: "plus") }
                        .buttonStyle(.glassPill)
                }
                if !detail.plansLoaded {
                    StatusView(kind: .loading)
                } else if detail.proposals.contains(where: { $0.status == .open }) || detail.events.isEmpty {
                    proposalsSection
                    eventsSection
                } else {
                    // Everything's settled: what's coming up first, then a quiet note.
                    eventsSection
                    nothingWaiting
                }
            }
            .padding(.horizontal, 20)
            .padding(.top, 20)
            .padding(.bottom, 16)
        }
        .screenBackground(OrbLayout.proposal)
        .task { if !detail.plansLoaded { await load() } }
        .refreshable { await load() }
        .sheet(item: $editingEvent) { target in
            EventEditorSheet(groupId: group.id, target: target, api: detail.api) { _ in Task { await load() } }
        }
        .errorAlert($error)
    }

    @ViewBuilder
    private var proposalsSection: some View {
        let open = detail.proposals.filter { $0.status == .open }
        if open.isEmpty {
            StatusView(kind: .empty(title: "Nothing proposed yet", message: "Pick a day that works on the calendar and propose it.", systemImage: "checkmark.circle"))
                .glass(cornerRadius: 24)
        } else {
            ForEach(open) { proposal in
                Button { app.router.push(.proposal(id: proposal.id)) } label: {
                    ProposalCard(proposal: proposal, group: group, me: app.session.user?.id)
                }
                .buttonStyle(.pressable)
            }
        }
    }

    private var nothingWaiting: some View {
        HStack(spacing: 10) {
            Image(systemName: "checkmark.circle")
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(theme.muted)
                .accessibilityHidden(true)
            Text("No dates waiting for confirmation")
                .bodyFont(14, relativeTo: .subheadline)
                .foregroundStyle(theme.muted)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 16)
        .frame(minHeight: 52)
        .glass(cornerRadius: 18)
        .padding(.top, 6)
    }

    @ViewBuilder
    private var eventsSection: some View {
        if !detail.events.isEmpty {
            Text("Coming up").displayFont(19, .semibold).padding(.top, 6).accessibilityAddTraits(.isHeader)
            ForEach(detail.events) { event in
                EventRow(event: event, canEdit: event.createdBy == app.session.user?.id || group.role.isAdmin) {
                    editingEvent = .edit(event)
                }
            }
        }
    }

    private func load() async {
        do {
            try await detail.loadPlans()
        } catch where !error.isCancellation {
            self.error = error.userMessage
        } catch {}
    }
}

/// "Plans" on the group's calendar: open proposals first, then the next event (two at most), and See all.
/// Shows nothing when there's nothing planned.
struct GroupPlansCard: View {
    let detail: GroupDetailModel
    let group: Group
    @Environment(AppModel.self) private var app
    @Environment(\.theme) private var theme

    private enum Item: Identifiable {
        case proposal(Proposal), event(GroupEvent)
        var id: String {
            switch self {
            case .proposal(let proposal): "proposal-\(proposal.id)"
            case .event(let event): "event-\(event.id)"
            }
        }
    }

    private var items: [Item] {
        let open = detail.proposals.filter { $0.status == .open }.map(Item.proposal)
        let next = detail.events.prefix(1).map(Item.event)
        return Array((open + next).prefix(2))
    }

    var body: some View {
        let items = items
        if !items.isEmpty {
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Text("Plans").displayFont(17, .semibold, relativeTo: .headline).accessibilityAddTraits(.isHeader)
                    Spacer()
                    Button("See all", action: openPlans)
                        .bodyFont(13, .semibold, relativeTo: .subheadline)
                        .foregroundStyle(theme.accentInk)
                        .frame(minHeight: 32)
                        .accessibilityLabel("See all plans")
                }
                ForEach(items) { item in
                    switch item {
                    case .proposal(let proposal):
                        row(date: proposal.start, title: proposal.title ?? Formatting.span(start: proposal.start, end: proposal.end, allDay: proposal.allDay), detail: status(of: proposal), action: needsReply(proposal) ? "Reply" : nil) {
                            app.router.push(.proposal(id: proposal.id))
                        }
                    case .event(let event):
                        row(date: event.start, title: event.title, detail: Formatting.span(start: event.start, end: event.end, allDay: event.allDay), action: nil, onTap: openPlans)
                    }
                }
            }
            .padding(12)
            .glass(cornerRadius: 22)
        }
    }

    private func row(date: Date, title: String, detail: String, action: String?, onTap: @escaping () -> Void) -> some View {
        Button(action: onTap) {
            HStack(spacing: 12) {
                DateBlock(date: date)
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).bodyFont(15, .semibold).lineLimit(1)
                    Text(detail).bodyFont(12, relativeTo: .caption).foregroundStyle(theme.muted).lineLimit(1)
                }
                Spacer(minLength: 4)
                if let action {
                    Text(action).bodyFont(13, .semibold, relativeTo: .subheadline).foregroundStyle(theme.accentInk)
                } else {
                    Image(systemName: "chevron.right").font(.system(size: 12, weight: .semibold)).foregroundStyle(theme.muted)
                }
            }
            .foregroundStyle(theme.ink)
            .contentShape(Rectangle())
        }
        .buttonStyle(.pressable)
        .accessibilityElement(children: .combine)
    }

    /// "3 of 5 in · you're in".
    private func status(of proposal: Proposal) -> String {
        let count = "\(proposal.acceptedCount) of \(group.members.count) in"
        guard let me = app.session.user?.id, let mine = proposal.response(for: me)?.response else { return count }
        switch mine {
        case .accepted: return "\(count) · you're in"
        case .declined: return "\(count) · you can't make it"
        case .pending: return count
        }
    }

    private func needsReply(_ proposal: Proposal) -> Bool {
        guard let me = app.session.user?.id else { return false }
        return proposal.response(for: me)?.response == .pending
    }

    private func openPlans() {
        app.router.push(.groupSection(id: group.id, section: .plans))
    }
}

/// Date block + "Ana proposed a time" + "4 of 6 accepted" — used in Plans and in chat.
struct ProposalCard: View {
    let proposal: Proposal
    let group: Group
    let me: String?
    @Environment(\.theme) private var theme

    var body: some View {
        HStack(spacing: 12) {
            DateBlock(date: proposal.start)
            VStack(alignment: .leading, spacing: 2) {
                Text(proposer).bodyFont(12, relativeTo: .caption).foregroundStyle(theme.muted)
                Text(proposal.title ?? Formatting.span(start: proposal.start, end: proposal.end, allDay: proposal.allDay))
                    .bodyFont(15, .semibold)
                    .lineLimit(2)
                statusText.bodyFont(12, .semibold, relativeTo: .caption)
            }
            Spacer(minLength: 4)
            Text(action).bodyFont(13, .semibold, relativeTo: .subheadline).foregroundStyle(theme.accentInk)
        }
        .foregroundStyle(theme.ink)
        .padding(12)
        .glass(cornerRadius: 20)
        .accessibilityElement(children: .combine)
    }

    private var proposer: String {
        let who = proposal.createdBy == me ? "You" : (group.member(proposal.createdBy)?.firstName ?? "Someone")
        return "\(who) proposed a time"
    }

    /// "2 of 4 accepted · Priya busy then" (the clash in warn-ink).
    private var statusText: Text {
        let base = Text(status).foregroundColor(proposal.status == .cancelled ? theme.muted : theme.okInk)
        guard let clash = Clash.short(proposal.openClashIds, group: group) else { return base }
        return base + Text(" \u{00B7} \(clash)").foregroundColor(theme.warnInk)
    }

    private var status: String {
        switch proposal.status {
        case .confirmed: "Everyone's in"
        case .cancelled: "Cancelled"
        case .open: "\(proposal.acceptedCount) of \(group.members.count) accepted"
        }
    }

    private var action: String {
        guard proposal.status == .open, let me else { return "View" }
        return proposal.response(for: me)?.response == .pending ? "Reply" : "View"
    }
}

/// Accent "NOV / 13" date tile.
struct DateBlock: View {
    let date: Date
    @Environment(\.theme) private var theme

    var body: some View {
        VStack(spacing: 0) {
            Text(date.formatted(.dateTime.month(.abbreviated)).uppercased()).bodyFont(10, .semibold, relativeTo: .caption2)
            Text(date.formatted(.dateTime.day())).displayFont(19, .bold, relativeTo: .headline)
        }
        .foregroundStyle(theme.onAccent)
        .frame(width: 46, height: 50)
        .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(theme.accent))
        .accessibilityHidden(true)
    }
}

/// An event with Add to calendar.
struct EventRow: View {
    let event: GroupEvent
    let canEdit: Bool
    let onEdit: () -> Void
    @Environment(\.theme) private var theme

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            if let image = event.image { EventCover(imageURL: image) }
            HStack(spacing: 12) {
                DateBlock(date: event.start)
                VStack(alignment: .leading, spacing: 2) {
                    Text(event.title).bodyFont(15, .semibold)
                    Text(Formatting.span(start: event.start, end: event.end, allDay: event.allDay)).bodyFont(12, relativeTo: .caption).foregroundStyle(theme.muted)
                    if let location = event.location, !location.isEmpty {
                        Text(location).bodyFont(12, relativeTo: .caption).foregroundStyle(theme.muted)
                    }
                }
                Spacer()
                if canEdit {
                    IconButton(systemImage: "pencil", label: "Edit \(event.title)", size: 36, glass: false, action: onEdit)
                }
            }
            AddToCalendarButton(event: event, height: 44)
        }
        .padding(12)
        .glass(cornerRadius: 22)
    }
}
