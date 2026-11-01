import SwiftUI

/// The group's "Proposals" tab: open proposals, confirmed / added events, and Add event.
struct GroupPlansView: View {
    let detail: GroupDetailModel
    let group: Group
    @Environment(AppModel.self) private var app
    @Environment(\.theme) private var theme
    @State private var proposals: [Proposal] = []
    @State private var events: [GroupEvent] = []
    @State private var isLoading = true
    @State private var editingEvent: EventEditorSheet.Target?
    @State private var error: String?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                GroupHeader(group: group)
                HStack {
                    Text("Proposals").displayFont(19, .semibold).accessibilityAddTraits(.isHeader)
                    Spacer()
                    Button { editingEvent = .new } label: { Label("Add event", systemImage: "plus") }
                        .buttonStyle(.glassPill)
                }
                if isLoading {
                    StatusView(kind: .loading)
                } else {
                    proposalsSection
                    eventsSection
                }
            }
            .padding(.horizontal, 20)
            .padding(.top, 20)
            .padding(.bottom, 16)
        }
        .screenBackground(OrbLayout.proposal)
        .task { await load() }
        .refreshable { await load() }
        .task(id: group.id) {
            for await event in app.live.events(forGroup: group.id) {
                switch event.kind {
                case .proposalCreated, .proposalUpdated, .proposalConfirmed, .eventChanged: await load()
                default: break
                }
            }
        }
        .sheet(item: $editingEvent) { target in
            EventEditorSheet(groupId: group.id, target: target, api: detail.api) { _ in Task { await load() } }
        }
        .errorAlert($error)
    }

    @ViewBuilder
    private var proposalsSection: some View {
        let open = proposals.filter { $0.status == .open }
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

    @ViewBuilder
    private var eventsSection: some View {
        if !events.isEmpty {
            Text("Coming up").displayFont(19, .semibold).padding(.top, 6).accessibilityAddTraits(.isHeader)
            ForEach(events) { event in
                EventRow(event: event, canEdit: event.createdBy == app.session.user?.id || group.role.isAdmin) {
                    editingEvent = .edit(event)
                }
            }
        }
    }

    private func load() async {
        defer { isLoading = false }
        do {
            async let proposalList = detail.api.send(API.Proposals.list(groupId: group.id))
            async let eventList = detail.api.send(API.Events.list(groupId: group.id, from: Calendar.current.startOfDay(for: Date())))
            proposals = try await proposalList.proposals.sorted { $0.start < $1.start }
            events = try await eventList.events.sorted { $0.start < $1.start }
        } catch where !error.isCancellation {
            self.error = error.userMessage
        } catch {}
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
                Text(status).bodyFont(12, .semibold, relativeTo: .caption).foregroundStyle(proposal.status == .cancelled ? theme.muted : theme.okInk)
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
