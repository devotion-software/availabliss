import SwiftUI

/// A proposal: acceptance ring, member replies, Accept / Maybe / Can't make it, admin Override,
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
    @State private var confirmsAnyway = false

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
                if model.canConfirmAnyway(me: me) {
                    Button { confirmsAnyway = true } label: {
                        LoadingLabel(title: "Confirm anyway", systemImage: "checkmark.circle", isLoading: model.isResponding && model.sending == nil)
                    }
                    .buttonStyle(.secondary)
                    .disabled(model.isResponding)
                    Text("Not everyone can make it? Go ahead with who can.")
                        .bodyFont(12, relativeTo: .caption)
                        .foregroundStyle(theme.muted)
                        .frame(maxWidth: .infinity)
                }
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
        .confirmSheet(item: $overrideTarget) { member in
            "Set \(member.firstName)\u{2019}s reply"
        } message: { _ in
            "As an admin you can set anyone\u{2019}s reply."
        } options: { member in
            [
                ConfirmOption("\(member.firstName) can make it", systemImage: "checkmark", kind: .secondary) {
                    Task { await model.override(userId: member.userId, response: .accepted) }
                },
                ConfirmOption("\(member.firstName) might make it", systemImage: "questionmark", kind: .secondary) {
                    Task { await model.override(userId: member.userId, response: .maybe) }
                },
                ConfirmOption("\(member.firstName) can\u{2019}t make it", systemImage: "xmark", kind: .secondary) {
                    Task { await model.override(userId: member.userId, response: .declined) }
                },
                ConfirmOption("Clear reply", kind: .secondary) {
                    Task { await model.override(userId: member.userId, response: .pending) }
                },
            ]
        }
        .confirmSheet("Confirm anyway?", isPresented: $confirmsAnyway, message: model.confirmAnywayMessage(me: me)) {
            [ConfirmOption("Confirm the plan", systemImage: "checkmark") {
                Task { await model.confirmAnyway() }
            }]
        }
        .confirmSheet("Cancel this proposal?", isPresented: $confirmsCancel, message: "It'll show as cancelled for everyone in the group.", cancelTitle: "Keep proposal") {
            [ConfirmOption("Cancel proposal", kind: .destructive) {
                Task { if await model.cancel() { app.router.pop() } }
            }]
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
            AcceptanceRing(accepted: model.acceptedCount, maybe: model.maybeCount, total: model.memberCount)
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
            Text(replyPrompt(mine))
                .bodyFont(13, .semibold, relativeTo: .subheadline)
                .padding(.leading, 4)
            HStack(spacing: 8) {
                Button { Task { await model.respond(.declined) } } label: {
                    LoadingLabel(title: "Can't make it", isLoading: model.sending == .declined)
                }
                    .buttonStyle(.secondary)
                    .disabled(mine == .declined || model.isResponding)
                Button { Task { await model.respond(.maybe) } } label: {
                    LoadingLabel(title: "Maybe", systemImage: mine == .maybe ? "checkmark" : nil, isLoading: model.sending == .maybe)
                }
                    .buttonStyle(.secondary)
                    .disabled(mine == .maybe || model.isResponding)
                Button {
                    Task { await model.respond(.accepted) }
                } label: {
                    LoadingLabel(title: mine == .accepted ? "Accepted" : "Accept", systemImage: mine == .accepted ? "checkmark" : nil, isLoading: model.sending == .accepted)
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

    private func replyPrompt(_ mine: ResponseKind?) -> String {
        switch mine {
        case .accepted: "You\u{2019}re in. Changed your mind?"
        case .maybe: "You said maybe. Know for sure?"
        case .declined: "You said you can\u{2019}t make it."
        case .pending, nil: model.isBusy(me) ? "You\u{2019}re busy then. Can you still make it?" : "Can you make it?"
        }
    }
}

/// Big ok-coloured ring: "4/6 accepted", with maybes as a fainter stretch after the yeses and "+1 maybe".
struct AcceptanceRing: View {
    let accepted: Int
    var maybe: Int = 0
    let total: Int
    @Environment(\.theme) private var theme

    var body: some View {
        ZStack {
            ProgressRing(progress: fraction(accepted), lineWidth: 7, color: theme.ok)
            if maybe > 0 {
                // The maybes carry on from where the yeses end, fainter (ProgressRing's geometry).
                Circle()
                    .trim(from: min(1, fraction(accepted)), to: min(1, fraction(accepted + maybe)))
                    .stroke(theme.ok.opacity(0.35), style: StrokeStyle(lineWidth: 7, lineCap: .butt))
                    .rotationEffect(.degrees(-90))
                    .padding(3.5)
                    .accessibilityHidden(true)
            }
            VStack(spacing: 0) {
                Text("\(accepted)/\(total)").displayFont(20, .bold, relativeTo: .headline)
                Text("accepted").bodyFont(10, relativeTo: .caption2).foregroundStyle(theme.muted)
                if maybe > 0 {
                    Text("+\(maybe) maybe").bodyFont(10, .semibold, relativeTo: .caption2).foregroundStyle(theme.okInk)
                }
            }
        }
        .frame(width: 76, height: 76)
        .accessibilityElement()
        .accessibilityLabel(maybe > 0 ? "\(accepted) of \(total) accepted, \(maybe) maybe" : "\(accepted) of \(total) accepted")
    }

    private func fraction(_ count: Int) -> Double { total == 0 ? 0 : Double(count) / Double(total) }
}

struct ResponsePill: View {
    let response: ResponseKind
    var body: some View {
        switch response {
        case .accepted: StatusPill(text: "Accepted", tone: .mint)
        case .maybe: StatusPill(text: "Maybe", tone: .indigo)
        case .declined: StatusPill(text: "Can\u{2019}t make it", tone: .neutral)
        case .pending: StatusPill(text: "Waiting", tone: .apricot)
        }
    }
}

/// "Everyone's in." (or "It's on." when confirmed before everyone accepted) — the confirmed state:
/// when, Add to calendar, who's coming, who can't or never replied; Go to chat in the top corner.
private struct ConfirmedView: View {
    let model: ProposalViewModel
    let group: Group
    @Environment(AppModel.self) private var app
    @Environment(\.theme) private var theme
    @State private var bloom = false
    @State private var changingReply = false
    @State private var reviewing = false
    /// The review sheet opens by itself once per visit; after that, the panel's button opens it.
    @State private var promptedReview = false

    private var me: String? { app.session.user?.id }

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                HStack {
                    IconButton(systemImage: "chevron.left", label: "Back") { app.router.pop() }
                    Spacer()
                    IconButton(systemImage: "bubble.left", label: "Go to chat", action: openChat)
                }
                .padding(.horizontal, 6)
                if model.needsReview(me: me) {
                    reviewPanel
                        .padding(.horizontal, 6)
                        .padding(.top, 8)
                }
                VStack(spacing: 18) {
                    VennLogo(size: 132)
                        .scaleEffect(bloom ? 1 : 0.6)
                        .opacity(bloom ? 1 : 0)
                    Text(model.everyoneIn ? "Everyone\u{2019}s in." : "It\u{2019}s on.")
                        .displayFont(40, .heavy, relativeTo: .largeTitle)
                        .accessibilityAddTraits(.isHeader)
                    when
                        .padding(.horizontal, 10)
                    calendarCard
                        .padding(.top, 6)
                    people
                }
                .padding(.top, 12)
            }
            .padding(.horizontal, 14)
            .padding(.top, 16)
            .padding(.bottom, 20)
        }
        .scrollBounceBehavior(.basedOnSize)
        .onAppear {
            withAnimation(.spring(response: 0.7, dampingFraction: 0.6).delay(0.1)) { bloom = true }
        }
        .sensoryFeedback(.success, trigger: bloom)
        .task(id: model.needsReview(me: me)) {
            guard model.needsReview(me: me), !promptedReview else { return }
            promptedReview = true
            try? await Task.sleep(for: .milliseconds(600))
            reviewing = true
        }
        .confirmSheet(model.droppedOutHeadline(me: me), isPresented: $reviewing, message: "The plan\u{2019}s still on. Keep it with who can make it, or cancel it and find another date.", cancelTitle: "Decide later") {
            [
                ConfirmOption("Keep the plan", systemImage: "checkmark") {
                    Task { await model.keepPlan() }
                },
                ConfirmOption("Cancel and find another date", systemImage: "calendar", kind: .destructive) {
                    Task { await cancelAndFindAnotherDate() }
                },
            ]
        }
        .confirmSheet(changeReplyTitle, isPresented: $changingReply, message: changeReplyMessage) {
            changeReplyOptions
        }
    }

    // MARK: Changing your reply

    private var myReply: ResponseKind? { model.myResponse(me) }

    /// Only people asked at the time can reply (members who joined since can't).
    private var changeReplyLabel: String? {
        switch myReply {
        case .accepted: "Can\u{2019}t make it any more?"
        case .maybe: "Know whether you can make it?"
        case .declined: "Can make it after all?"
        case .pending: "Can you make it?"
        case nil: nil
        }
    }

    private var changeReplyTitle: String {
        myReply == .accepted ? "Can\u{2019}t make it any more?" : "Can you make it?"
    }

    private var changeReplyMessage: String {
        if myReply == .accepted {
            let reviewer = model.proposal?.createdBy == me ? "admins" : "whoever proposed it and the admins"
            return "The plan stays on. If you can\u{2019}t make it, \(reviewer) will be told, so they can keep it or find another date."
        }
        return "Let everyone know whether you\u{2019}re coming."
    }

    /// The replies other than the one you've given: I can make it, I might make it, I can't make it.
    private var changeReplyOptions: [ConfirmOption] {
        var options: [ConfirmOption] = []
        if myReply != .accepted {
            options.append(ConfirmOption("I can make it", systemImage: "checkmark") {
                Task { await model.respond(.accepted) }
            })
        }
        if myReply != .maybe {
            options.append(ConfirmOption("I might make it", systemImage: "questionmark", kind: .secondary) {
                Task { await model.respond(.maybe) }
            })
        }
        if myReply != .declined {
            // Going from a yes to a no is dropping out (the reviewers are told), so it's destructive.
            options.append(ConfirmOption("I can\u{2019}t make it", systemImage: "xmark", kind: myReply == .accepted ? .destructive : .secondary) {
                Task { await model.respond(.declined) }
            })
        }
        return options
    }

    // MARK: Review

    /// For the proposer or an admin after someone dropped out: stays until they keep or cancel.
    private var reviewPanel: some View {
        VStack(alignment: .leading, spacing: 10) {
            ClashPanel(headline: model.droppedOutHeadline(me: me), detail: "Keep the plan with who can make it, or cancel it and find another date.", cornerRadius: 18)
            Button { reviewing = true } label: {
                Label("Keep or cancel\u{2026}", systemImage: "arrow.triangle.branch")
            }
            .buttonStyle(.filled(.secondary, height: 44, cornerRadius: 14, fontSize: 15))
            .disabled(model.isResponding)
        }
    }

    /// Cancels the plan (its event goes too), then opens the group's calendar to pick another date.
    private func cancelAndFindAnotherDate() async {
        guard await model.cancel() else { return }
        app.router.pop()
        if app.router.currentPath.last != .group(id: group.id) {
            app.router.push(.group(id: group.id))
        }
    }

    private var comingCount: Int {
        let count = model.coming.count
        return count > 0 ? count : model.acceptedCount
    }

    /// "All 4 of you can make" (or "3 of 4 can make"), then the date and time large on their own
    /// lines, then the place.
    @ViewBuilder
    private var when: some View {
        if let proposal = model.proposal {
            let parts = Formatting.spanParts(start: proposal.start, end: proposal.end, allDay: proposal.allDay)
            VStack(spacing: 6) {
                Text(model.everyoneIn ? "All \(comingCount) of you can make" : "\(model.coming.count) of \(model.invitedCount) can make")
                    .bodyFont(16)
                    .foregroundStyle(theme.muted)
                    .padding(.bottom, 2)
                Text(parts.date)
                    .displayFont(30, .bold, relativeTo: .title)
                    .foregroundStyle(theme.ink)
                if let time = parts.time {
                    Text(time)
                        .displayFont(22, .semibold, relativeTo: .title2)
                        .foregroundStyle(theme.ink)
                        .monospacedDigit()
                }
                if let location = proposal.location {
                    Text("in \(location)")
                        .bodyFont(16)
                        .foregroundStyle(theme.muted)
                        .padding(.top, 2)
                }
            }
            .multilineTextAlignment(.center)
            .accessibilityElement(children: .combine)
        }
    }

    /// Who's coming (everyone who accepted), who said maybe, then, quieter, who can't make it, who never replied
    /// and anyone who joined the group since.
    @ViewBuilder
    private var people: some View {
        VStack(spacing: 12) {
            if !model.coming.isEmpty {
                GlassList(data: model.coming, id: \.userId, header: "Who\u{2019}s coming", trailingHeader: "\(model.coming.count)", rowMinHeight: 52) { member in
                    personRow(member, note: member.userId == model.proposal?.createdBy ? "Proposed this" : nil)
                }
            }
            if !model.maybe.isEmpty {
                GlassList(data: model.maybe, id: \.userId, header: "Maybe", trailingHeader: "\(model.maybe.count)", rowMinHeight: 52) { member in
                    personRow(member, note: "Might make it")
                }
            }
            if !model.cantMakeIt.isEmpty {
                GlassList(data: model.cantMakeIt, id: \.userId, header: "Can\u{2019}t make it", trailingHeader: "\(model.cantMakeIt.count)", rowMinHeight: 48) { member in
                    personRow(member, note: model.droppedOut(member) ? "Can\u{2019}t make it any more" : nil, size: 28)
                        .opacity(0.65)
                }
            }
            if !model.notReplied.isEmpty {
                GlassList(data: model.notReplied, id: \.userId, header: "Not replied", trailingHeader: "\(model.notReplied.count)", rowMinHeight: 48) { member in
                    personRow(member, note: nil, size: 28)
                        .opacity(0.65)
                }
            }
            if !model.joinedLater.isEmpty {
                GlassList(data: model.joinedLater, id: \.userId, header: "Joined since", rowMinHeight: 48) { member in
                    personRow(member, note: "Joined after this was confirmed", size: 28)
                        .opacity(0.65)
                }
            }
        }
    }

    private func personRow(_ member: Member, note: String?, size: CGFloat = 34) -> some View {
        HStack(spacing: 12) {
            Avatar(name: member.name, imageURL: member.image, color: theme.tile(forUser: member.userId), size: size)
            VStack(alignment: .leading, spacing: 0) {
                Text(member.userId == me ? "\(member.name) (you)" : member.name)
                    .bodyFont(15, .semibold)
                    .lineLimit(1)
                if let note {
                    Text(note).bodyFont(12, relativeTo: .caption).foregroundStyle(theme.muted)
                }
            }
            Spacer(minLength: 0)
        }
        .accessibilityElement(children: .combine)
    }

    /// The first card, above Who's coming: Add to calendar (or which calendar it's in), and changing your reply.
    @ViewBuilder
    private var calendarCard: some View {
        VStack(spacing: 10) {
            if let event = model.event {
                AddToCalendarButton(event: event)
            } else {
                ProgressView().frame(height: 54)
            }
            if let changeReplyLabel {
                Button(changeReplyLabel) { changingReply = true }
                    .bodyFont(14, .semibold, relativeTo: .subheadline)
                    .foregroundStyle(theme.muted)
                    .frame(minHeight: 36)
                    .disabled(model.isResponding)
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity)
        .glass(cornerRadius: 22)
    }

    /// Opens the group's chat; back from it returns here. Opened from that chat, it just goes back.
    private func openChat() {
        let chat = AppRoute.groupSection(id: group.id, section: .chat)
        if app.router.currentPath.dropLast().last == chat {
            app.router.pop()
        } else {
            app.router.push(chat)
        }
    }
}
