import SwiftUI

/// Group availability: sync banner, timing chip, and Month / Week / Day views.
struct GroupAvailabilityView: View {
    let detail: GroupDetailModel
    let group: Group
    let onOpenTiming: () -> Void
    @Environment(AppModel.self) private var app
    @Environment(\.theme) private var theme
    @Environment(\.verticalSizeClass) private var verticalSizeClass
    @State private var model: GroupAvailabilityViewModel
    @State private var proposalDraft: ProposalDraft?
    /// Which side the next calendar page slides in from (trailing = later).
    @State private var pageEdge: Edge = .trailing
    /// The optional AI planner: offered only when the server has it set up; Pro to use.
    @State private var aiPlanner: Plan.AIPlanner?
    /// Pro viewers get "Best options" ranked by the server's best-date score.
    @State private var isPro = false
    @State private var showsPlanner = false
    @State private var showsUpgrade = false
    /// Picked in the planner; opened as a proposal once the planner sheet has gone.
    @State private var plannedDraft: ProposalDraft?
    /// Times shortlisted from Best options, in the order picked, to ask the group about in a date poll.
    @State private var shortlist: [Slot] = []
    @State private var showsAskGroup = false
    /// With a rhythm: the free time nearest the group's next meet-up.
    @State private var next: NextMeetResponse.Suggestion?

    init(detail: GroupDetailModel, group: Group, onOpenTiming: @escaping () -> Void) {
        self.detail = detail
        self.group = group
        self.onOpenTiming = onOpenTiming
        let model = GroupAvailabilityViewModel(groupId: group.id, api: detail.api)
        model.searchRange = group.requirement.range
        _model = State(initialValue: model)
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if !group.pendingMembers.isEmpty {
                    SyncBanner(group: group, isAdmin: group.role.isAdmin, isNudging: model.isNudging) {
                        Task { await model.nudge() }
                    }
                }
                TimingChip(summary: group.requirementSummary, action: onOpenTiming)
                GroupPlansCard(detail: detail, group: group)
                if group.rhythm != nil, let next {
                    NextMeetCard(suggestion: next, group: group, me: app.session.user?.id) { draft in
                        proposalDraft = draft
                    }
                }
                headingRow
                freeJumps
                if let gaps = model.availability?.checkedUntil, !gaps.isEmpty {
                    CoverageNote(gaps: gaps, group: group, me: app.session.user?.id)
                }
                content
            }
            .padding(.horizontal, 20)
            .padding(.top, 20)
            .padding(.bottom, 16)
        }
        .tracksScrollUnderTopBar()
        .screenBackground(OrbLayout.groupCalendar)
        .task { await model.load() }
        .task { await loadPlan() }
        .task(id: group.rhythm) { await loadNext() }
        .refreshable { await model.invalidate() }
        .onChange(of: model.mode) { _, _ in Task { await model.load() } }
        .onChange(of: model.selection) { _, _ in Task { await model.loadSelectedDay() } }
        .onChange(of: group.requirement) { _, requirement in
            model.searchRange = requirement.range
            Task { await model.invalidate() }
        }
        .task(id: group.id) {
            for await event in app.live.events(forGroup: group.id) {
                switch event.kind {
                case .availabilityChanged, .groupUpdated, .memberChanged, .reconnected:
                    await model.invalidate()
                    await loadNext()
                default: break
                }
            }
        }
        .sheet(item: $proposalDraft) { draft in
            NewProposalSheet(group: group, me: app.session.user?.id, draft: draft, api: detail.api) { proposal in
                app.router.push(.proposal(id: proposal.id))
            }
        }
        .sheet(
            isPresented: $showsPlanner,
            onDismiss: {
                if let draft = plannedDraft {
                    plannedDraft = nil
                    proposalDraft = draft
                }
            }
        ) {
            PlannerSheet { notes in
                try await detail.api.send(API.Groups.planner(groupId: group.id, notes: notes))
            } propose: { slot, title, note in
                var draft = ProposalDraft(slot: slot, requirement: group.requirement)
                draft.title = title
                draft.note = note
                plannedDraft = draft
            }
        }
        .sheet(isPresented: $showsAskGroup) {
            AskGroupSheet(slots: shortlist, requirement: group.requirement) { question, slots, multi in
                await askGroup(question: question, slots: slots, multi: multi)
            }
        }
        .sheet(isPresented: $showsUpgrade, onDismiss: { Task { await loadPlan() } }) {
            UpgradeSheet(reason: "AI planning is part of Pro: it picks the best time, suggests things to do and drafts the plan.") { _ in
                model.toast = "Welcome to Pro"
            }
        }
        .errorAlert($model.error)
        .toast($model.toast)
    }

    /// The "Next one" card's suggestion; cleared when the group has no rhythm.
    private func loadNext() async {
        guard group.rhythm != nil else {
            next = nil
            return
        }
        guard let response = try? await detail.api.send(API.Groups.next(groupId: group.id)) else { return }
        next = response.suggestion
    }

    /// Posts the shortlist as a date poll in the chat, then opens the chat.
    private func askGroup(question: String, slots: [PollSlot], multi: Bool) async -> Bool {
        do {
            let poll = SendMessageBody.NewPoll(question: question, slots: slots, multi: multi)
            _ = try await detail.api.send(API.Chat.send(groupId: group.id, SendMessageBody(poll: poll)))
            shortlist = []
            app.router.push(.groupSection(id: group.id, section: .chat))
            return true
        } catch  where !error.isCancellation {
            model.error = error.userMessage
            return false
        } catch {
            return false
        }
    }

    private func loadPlan() async {
        guard let plan = try? await detail.api.send(API.Me.plan()) else { return }
        aiPlanner = plan.aiPlanner
        isPro = plan.isPro
    }

    /// "Plan with AI", when the planner is set up and some times already work for everyone.
    @ViewBuilder
    private var planWithAI: some View {
        if aiPlanner?.available == true, !(model.availability?.slots.isEmpty ?? true) {
            Button {
                if aiPlanner?.included == true { showsPlanner = true } else { showsUpgrade = true }
            } label: {
                HStack(spacing: 10) {
                    Image(systemName: "sparkles")
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(theme.accentInk)
                        .frame(width: 34, height: 34)
                        .background(RoundedRectangle(cornerRadius: 11, style: .continuous).fill(theme.accentSoft))
                    VStack(alignment: .leading, spacing: 1) {
                        Text("Plan with AI").bodyFont(15, .semibold)
                        Text("The best of the times that work, with ideas and next steps")
                            .bodyFont(12, relativeTo: .caption)
                            .foregroundStyle(theme.muted)
                            .lineLimit(2)
                    }
                    Spacer(minLength: 4)
                    if aiPlanner?.included != true { StatusPill(text: "Pro", tone: .indigo) }
                    Image(systemName: "chevron.right").font(.system(size: 12, weight: .semibold)).foregroundStyle(theme.muted)
                }
                .foregroundStyle(theme.ink)
                .padding(12)
                .glass(cornerRadius: 22)
                .contentShape(RoundedRectangle(cornerRadius: 22))
            }
            .buttonStyle(.pressable)
        }
    }

    private struct PageID: Hashable {
        let mode: GroupAvailabilityViewModel.Mode
        let key: CalendarDay
    }

    private var pageTransition: AnyTransition {
        let out: Edge = pageEdge == .trailing ? .leading : .trailing
        return .asymmetric(
            insertion: .move(edge: pageEdge).combined(with: .opacity),
            removal: .move(edge: out).combined(with: .opacity)
        )
    }

    /// The previous / next month or week, sliding the grid across, then loading it.
    private func page(by delta: Int) {
        pageEdge = delta > 0 ? .trailing : .leading
        withAnimation(.spring(response: 0.32, dampingFraction: 0.9)) {
            model.advance(by: delta)
        }
        Task { await model.load() }
    }

    /// "Today": back to today's month or week, sliding in from the side it's on.
    private func jumpToToday() {
        let today = CalendarDay.today()
        let later = model.mode == .month ? today.firstOfMonth > model.month : today > model.selectedDay
        pageEdge = later ? .trailing : .leading
        withAnimation(.spring(response: 0.32, dampingFraction: 0.9)) { model.jumpToToday() }
        Task { await model.load() }
    }

    /// To the month or week holding the nearest free date before / after this one, with it selected.
    private func jump(to day: CalendarDay) {
        pageEdge = day > model.selectedDay ? .trailing : .leading
        withAnimation(.spring(response: 0.32, dampingFraction: 0.9)) { model.jump(to: day) }
        Task { await model.load() }
    }

    /// "‹ Fri 25 Dec · Free dates · Fri 5 Feb ›": skips the months or weeks where nothing works.
    /// A side is greyed out when there's none that way in the group's search range.
    private var freeJumps: some View {
        HStack(spacing: 8) {
            FreeJumpButton(direction: -1, day: model.previousFree, isFinding: model.isFindingFree) { jump(to: $0) }
                .frame(maxWidth: .infinity, alignment: .leading)
            Text("Free dates")
                .bodyFont(11, .semibold, relativeTo: .caption2)
                .foregroundStyle(theme.muted)
                .fixedSize()
                .accessibilityHidden(true)
            FreeJumpButton(direction: 1, day: model.nextFree, isFinding: model.isFindingFree) { jump(to: $0) }
                .frame(maxWidth: .infinity, alignment: .trailing)
        }
        .padding(.top, -6)
    }

    private var headingRow: some View {
        HStack(spacing: 6) {
            Button {
                page(by: -1)
            } label: {
                chevron("chevron.left")
            }
            .accessibilityLabel("Previous")
            Text(model.heading)
                .displayFont(19, .semibold, relativeTo: .headline)
                .lineLimit(1)
                .minimumScaleFactor(0.8)
                .accessibilityAddTraits(.isHeader)
            Button {
                page(by: 1)
            } label: {
                chevron("chevron.right")
            }
            .accessibilityLabel("Next")
            if model.isAwayFromToday {
                Button("Today", action: jumpToToday)
                    .bodyFont(12, .semibold, relativeTo: .caption)
                    .foregroundStyle(theme.accentInk)
                    .padding(.horizontal, 10)
                    .frame(height: 28)
                    .background(Capsule().fill(theme.accentSoft))
                    .frame(minHeight: 44)
                    .contentShape(Rectangle())
                    .buttonStyle(.plain)
                    .transition(.opacity.combined(with: .scale(scale: 0.9)))
                    .accessibilityHint("Goes back to this \(model.mode == .month ? "month" : "week")")
            }
            Spacer(minLength: 4)
            SegmentedControl(
                selection: $model.mode,
                options: [
                    SegmentOption(value: .month, label: "Month"),
                    SegmentOption(value: .week, label: "Week"),
                ],
                style: .solid,
                height: 32,
                accessibilityLabel: "Calendar view"
            )
            .fixedSize()
        }
    }

    private func chevron(_ name: String) -> some View {
        Image(systemName: name)
            .font(.system(size: 12, weight: .bold))
            .foregroundStyle(theme.muted)
            .frame(width: 28, height: 44)
            .contentShape(Rectangle())
    }

    @ViewBuilder
    private var content: some View {
        if model.isLoading {
            StatusView(kind: .loading)
        } else {
            SwipePager(onSwipe: model.noteSwipe, onPage: page(by:)) {
                SwiftUI.Group {
                    switch model.mode {
                    case .month: MonthGrid(model: model, group: group, me: app.session.user?.id)
                    // The time grid needs width: cards in portrait, the grid in landscape.
                    case .week:
                        if verticalSizeClass == .compact { WeekGrid(model: model, group: group) } else { WeekCards(model: model, group: group) }
                    }
                }
                // A new page per month / week, sliding in from the side it's coming from.
                .id(PageID(mode: model.mode, key: model.pageKey))
                .transition(pageTransition)
            }
            // Only once a day's been tapped: nothing is selected to begin with.
            if let day = model.selection {
                DayDetailCard(model: model, day: day, group: group, me: app.session.user?.id) { draft in
                    proposalDraft = draft
                }
                .transition(.opacity.combined(with: .move(edge: .top)))
            }
            if model.mode == .month {
                BestOptionsCard(
                    model: model, group: group, me: app.session.user?.id, isPro: isPro, shortlist: $shortlist,
                    onAsk: { showsAskGroup = true }
                ) { draft in
                    proposalDraft = draft
                }
                planWithAI
            }
        }
    }
}
