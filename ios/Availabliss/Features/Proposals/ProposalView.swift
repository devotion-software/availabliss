import EventKit
import SwiftUI

/// A proposal: acceptance ring, member replies, Accept / Can't make it, admin Override,
/// and the "Everyone's in." confirmation with one-tap Add to calendar.
struct ProposalView: View {
    @Environment(AppModel.self) private var app
    @Environment(\.theme) private var theme
    @State private var model: ProposalViewModel

    init(proposalId: String, api: APIClient) {
        _model = State(initialValue: ProposalViewModel(proposalId: proposalId, api: api))
    }

    var body: some View {
        content
            .screenBackground(OrbLayout.proposal)
            .foregroundStyle(theme.ink)
            .chromeless()
            .task { await model.load() }
            .task {
                for await event in app.live.events() { await model.apply(event) }
            }
            .animation(.spring(response: 0.5, dampingFraction: 0.85), value: model.isConfirmed)
            .errorAlert($model.error)
    }

    @ViewBuilder
    private var content: some View {
        if let proposal = model.proposal, let group = model.group {
            if proposal.status == .confirmed {
                ConfirmedView(model: model, group: group)
                    .transition(.opacity.combined(with: .scale(scale: 0.98)))
            } else {
                PendingProposalView(model: model, proposal: proposal, group: group)
                    .transition(.opacity)
            }
        } else if let error = model.loadError {
            VStack {
                ScreenHeader(title: "Proposal", onBack: { app.router.pop() })
                Spacer()
                StatusView(kind: .error(error) { Task { await model.load() } })
                Spacer()
            }
            .padding(20)
        } else {
            StatusView(kind: .loading).frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }
}

private struct PendingProposalView: View {
    let model: ProposalViewModel
    let proposal: Proposal
    let group: Group
    @Environment(AppModel.self) private var app
    @Environment(\.theme) private var theme
    @State private var overrideTarget: Member?
    @State private var confirmsCancel = false

    private var me: String? { app.session.user?.id }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                ScreenHeader(title: "Proposal", subtitle: group.name, titleSize: 22, backLabel: "Back", onBack: { app.router.pop() }) {
                    if proposal.createdBy == me || group.role.isAdmin {
                        Menu {
                            Button("Cancel proposal", role: .destructive) { confirmsCancel = true }
                        } label: {
                            Image(systemName: "ellipsis").font(.system(size: 17, weight: .semibold)).foregroundStyle(theme.ink)
                                .frame(width: 44, height: 44).glass(in: Circle(), shadow: false)
                        }
                        .accessibilityLabel("More")
                    }
                }
                summaryCard
                if let clash = model.clash(me: me) {
                    ClashPanel(headline: clash, detail: clashDetail, cornerRadius: 18)
                }
                membersList
                if group.role.isAdmin {
                    Label("As an admin you can set anyone's reply.", systemImage: "shield")
                        .bodyFont(12, relativeTo: .caption)
                        .foregroundStyle(theme.muted)
                }
                if proposal.status == .cancelled {
                    StatusPill(text: "This proposal was cancelled", tone: .neutral, fontSize: 13)
                }
            }
            .padding(.horizontal, 20)
            .padding(.top, 20)
            .padding(.bottom, 16)
        }
        .safeAreaInset(edge: .bottom) {
            if proposal.status == .open { replyBar }
        }
        .confirmationDialog("Set a reply", isPresented: Binding(get: { overrideTarget != nil }, set: { if !$0 { overrideTarget = nil } }), presenting: overrideTarget) { member in
            Button("\(member.firstName) can make it") { Task { await model.override(userId: member.userId, response: .accepted) } }
            Button("\(member.firstName) can't make it") { Task { await model.override(userId: member.userId, response: .declined) } }
            Button("Clear reply") { Task { await model.override(userId: member.userId, response: .pending) } }
        }
        .confirmationDialog("Cancel this proposal?", isPresented: $confirmsCancel, titleVisibility: .visible) {
            Button("Cancel proposal", role: .destructive) {
                Task { if await model.cancel() { app.router.pop() } }
            }
        }
    }

    private var clashDetail: String {
        proposal.openClashIds == [me]
            ? "Your calendar or blocked time overlaps. You can still accept if you can move things."
            : "Their calendars or blocked time overlap. They can still accept if they can move things."
    }

    private var summaryCard: some View {
        HStack(spacing: 14) {
            VStack(alignment: .leading, spacing: 6) {
                Text(model.proposerLine(me: me)).bodyFont(12, .semibold, relativeTo: .caption).foregroundStyle(theme.muted)
                Text(model.title).displayFont(26, .bold).fixedSize(horizontal: false, vertical: true)
                if !model.subtitle.isEmpty {
                    Text(model.subtitle).bodyFont(13, relativeTo: .subheadline).foregroundStyle(theme.muted)
                }
                if let note = proposal.note, !note.isEmpty {
                    Text(note).bodyFont(13, relativeTo: .subheadline)
                }
            }
            Spacer(minLength: 0)
            AcceptanceRing(accepted: model.acceptedCount, total: model.memberCount)
        }
        .padding(18)
        .glass(cornerRadius: 28)
        .accessibilityElement(children: .combine)
    }

    private var membersList: some View {
        GlassList(data: model.rows, id: \.userId, rowMinHeight: 56) { member in
            HStack(spacing: 12) {
                Avatar(name: member.name, imageURL: member.image, color: theme.tile(forUser: member.userId))
                VStack(alignment: .leading, spacing: 0) {
                    Text(member.userId == me ? "\(member.firstName) (you)" : member.name).bodyFont(15, .semibold)
                    Text(model.note(for: member, me: me)).bodyFont(12, relativeTo: .caption).foregroundStyle(theme.muted)
                }
                Spacer(minLength: 4)
                let response = proposal.response(for: member.userId)?.response ?? .pending
                if group.role.isAdmin && member.userId != me && response != .accepted && proposal.status == .open {
                    Button("Override") { overrideTarget = member }
                        .buttonStyle(.filled(.secondary, height: 34, cornerRadius: 10, fontSize: 12, fullWidth: false))
                        .frame(minHeight: 44)
                }
                ResponsePill(response: response)
            }
            .accessibilityElement(children: .combine)
        }
    }

    private var replyBar: some View {
        let mine = model.myResponse(me)
        return VStack(alignment: .leading, spacing: 10) {
            Text(mine == .accepted ? "You're in. Changed your mind?" : mine == .declined ? "You said you can't make it." : model.isBusy(me) ? "You're busy then. Can you still make it?" : "Can you make it?")
                .bodyFont(13, .semibold, relativeTo: .subheadline)
                .padding(.leading, 4)
            HStack(spacing: 8) {
                Button("Can't make it") { Task { await model.respond(.declined) } }
                    .buttonStyle(.secondary)
                    .disabled(mine == .declined || model.isResponding)
                Button {
                    Task { await model.respond(.accepted) }
                } label: {
                    LoadingLabel(title: mine == .accepted ? "Accepted" : "Accept", systemImage: mine == .accepted ? "checkmark" : nil, isLoading: model.isResponding)
                }
                .buttonStyle(.primary)
                .disabled(mine == .accepted || model.isResponding)
            }
        }
        .padding(12)
        .glass(cornerRadius: 26)
        .padding(.horizontal, 12)
        .padding(.bottom, 8)
        .sensoryFeedback(.success, trigger: mine)
    }
}

/// Big ok-coloured ring: "4/6 accepted".
struct AcceptanceRing: View {
    let accepted: Int
    let total: Int
    @Environment(\.theme) private var theme

    var body: some View {
        ZStack {
            ProgressRing(progress: total == 0 ? 0 : Double(accepted) / Double(total), lineWidth: 7, color: theme.ok)
            VStack(spacing: 0) {
                Text("\(accepted)/\(total)").displayFont(20, .bold, relativeTo: .headline)
                Text("accepted").bodyFont(10, relativeTo: .caption2).foregroundStyle(theme.muted)
            }
        }
        .frame(width: 76, height: 76)
        .accessibilityElement()
        .accessibilityLabel("\(accepted) of \(total) accepted")
    }
}

struct ResponsePill: View {
    let response: ResponseKind
    var body: some View {
        switch response {
        case .accepted: StatusPill(text: "Accepted", tone: .mint)
        case .declined: StatusPill(text: "Can\u{2019}t make it", tone: .neutral)
        case .pending: StatusPill(text: "Waiting", tone: .apricot)
        }
    }
}

/// "Everyone's in." — the confirmed state.
private struct ConfirmedView: View {
    let model: ProposalViewModel
    let group: Group
    @Environment(AppModel.self) private var app
    @Environment(\.theme) private var theme
    @State private var bloom = false
    @State private var calendar: EKCalendar?

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                IconButton(systemImage: "chevron.left", label: "Back") { app.router.pop() }
                Spacer()
            }
            .padding(.horizontal, 6)
            Spacer()
            VStack(spacing: 18) {
                VennLogo(size: 132)
                    .scaleEffect(bloom ? 1 : 0.6)
                    .opacity(bloom ? 1 : 0)
                Text("Everyone's in.")
                    .displayFont(40, .heavy, relativeTo: .largeTitle)
                    .accessibilityAddTraits(.isHeader)
                Text(message)
                    .bodyFont(16)
                    .foregroundStyle(theme.muted)
                    .multilineTextAlignment(.center)
                    .lineSpacing(3)
            }
            .padding(.horizontal, 10)
            Spacer()
            actions
        }
        .padding(.horizontal, 14)
        .padding(.top, 16)
        .padding(.bottom, 24)
        .onAppear {
            withAnimation(.spring(response: 0.7, dampingFraction: 0.6).delay(0.1)) { bloom = true }
            calendar = app.deviceCalendar.store.defaultCalendarForNewEvents
        }
        .sensoryFeedback(.success, trigger: bloom)
    }

    private var message: String {
        guard let proposal = model.proposal else { return "" }
        let when = Formatting.span(start: proposal.start, end: proposal.end, allDay: proposal.allDay)
        let place = proposal.location.map { " in \($0)" } ?? ""
        return "All \(model.memberCount) of you can make\n\(when)\(place)."
    }

    @ViewBuilder
    private var actions: some View {
        VStack(spacing: 10) {
            if app.deviceCalendar.hasFullAccess, !app.deviceCalendar.writableCalendars.isEmpty {
                CalendarPickerRow(selection: $calendar, calendars: app.deviceCalendar.writableCalendars)
            }
            if let event = model.event {
                ConfirmedAddButton(event: event, calendar: calendar)
            } else {
                ProgressView().frame(height: 54)
            }
        }
        .padding(14)
        .glass(cornerRadius: 28)
    }
}

private struct CalendarPickerRow: View {
    @Binding var selection: EKCalendar?
    let calendars: [EKCalendar]
    @Environment(\.theme) private var theme

    var body: some View {
        Menu {
            ForEach(calendars, id: \.calendarIdentifier) { calendar in
                Button("\(calendar.title) · \(calendar.source.title)") { selection = calendar }
            }
        } label: {
            HStack(spacing: 10) {
                RoundedRectangle(cornerRadius: 4).fill(selection.map { Color(cgColor: $0.cgColor) } ?? theme.warn).frame(width: 12, height: 12)
                (Text(selection?.title ?? "Calendar").fontWeight(.semibold) + Text(" · \(selection?.source.title ?? "")").foregroundColor(theme.muted))
                    .bodyFont(14)
                    .foregroundStyle(theme.ink)
                Spacer()
                Image(systemName: "chevron.down").font(.system(size: 13, weight: .semibold)).foregroundStyle(theme.muted)
            }
            .padding(.horizontal, 14)
            .frame(minHeight: 52)
            .surfaceBackground(cornerRadius: 16)
        }
        .accessibilityLabel("Calendar: \(selection?.title ?? "default")")
    }
}

private struct ConfirmedAddButton: View {
    let event: GroupEvent
    let calendar: EKCalendar?
    @Environment(AppModel.self) private var app
    @State private var isAdded = false
    @State private var error: String?

    var body: some View {
        Button {
            Task {
                do {
                    try await app.deviceCalendar.add(event, to: calendar)
                    withAnimation { isAdded = true }
                } catch {
                    self.error = error.userMessage
                }
            }
        } label: {
            Label(isAdded ? "Added to your calendar" : "Add to calendar", systemImage: isAdded ? "checkmark" : "plus")
        }
        .buttonStyle(.filled(isAdded ? .success : .primary, height: 54, cornerRadius: 18))
        .disabled(isAdded)
        .onAppear { isAdded = app.deviceCalendar.isAdded(event.id) }
        .sensoryFeedback(.success, trigger: isAdded)
        .errorAlert($error)
    }
}
