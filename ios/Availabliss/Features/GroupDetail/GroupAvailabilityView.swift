import SwiftUI

/// Group availability: sync banner, timing chip, and Month / Week / Day views.
struct GroupAvailabilityView: View {
    let detail: GroupDetailModel
    let group: Group
    let onOpenTiming: () -> Void
    @Environment(AppModel.self) private var app
    @Environment(\.theme) private var theme
    @State private var model: GroupAvailabilityViewModel
    @State private var proposalDraft: ProposalDraft?
    /// The optional AI planner: offered only when the server has it set up; Pro to use.
    @State private var aiPlanner: Plan.AIPlanner?
    @State private var showsPlanner = false
    @State private var showsUpgrade = false
    /// Picked in the planner; opened as a proposal once the planner sheet has gone.
    @State private var plannedDraft: ProposalDraft?

    init(detail: GroupDetailModel, group: Group, onOpenTiming: @escaping () -> Void) {
        self.detail = detail
        self.group = group
        self.onOpenTiming = onOpenTiming
        _model = State(initialValue: GroupAvailabilityViewModel(groupId: group.id, api: detail.api))
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                GroupHeader(group: group)
                if !group.pendingMembers.isEmpty {
                    SyncBanner(group: group, isAdmin: group.role.isAdmin, isNudging: model.isNudging) {
                        Task { await model.nudge() }
                    }
                }
                TimingChip(summary: group.requirementSummary, action: onOpenTiming)
                headingRow
                content
            }
            .padding(.horizontal, 20)
            .padding(.top, 20)
            .padding(.bottom, 16)
        }
        .screenBackground(OrbLayout.groupCalendar)
        .task { await model.load() }
        .task { aiPlanner = (try? await detail.api.send(API.Me.plan()))?.aiPlanner }
        .refreshable { await model.invalidate() }
        .onChange(of: model.mode) { _, _ in Task { await model.load() } }
        .onChange(of: group.requirement) { _, _ in Task { await model.invalidate() } }
        .task(id: group.id) {
            for await event in app.live.events(forGroup: group.id) {
                switch event.kind {
                case .availabilityChanged, .groupUpdated, .memberChanged: await model.invalidate()
                default: break
                }
            }
        }
        .sheet(item: $proposalDraft) { draft in
            NewProposalSheet(group: group, me: app.session.user?.id, draft: draft, api: detail.api) { proposal in
                app.router.push(.proposal(id: proposal.id))
            }
        }
        .sheet(isPresented: $showsPlanner, onDismiss: {
            if let draft = plannedDraft {
                plannedDraft = nil
                proposalDraft = draft
            }
        }) {
            PlannerSheet { notes in
                try await detail.api.send(API.Groups.planner(groupId: group.id, notes: notes))
            } propose: { slot, title, note in
                var draft = ProposalDraft(slot: slot, requirement: group.requirement)
                draft.title = title
                draft.note = note
                plannedDraft = draft
            }
        }
        .sheet(isPresented: $showsUpgrade, onDismiss: { Task { aiPlanner = (try? await detail.api.send(API.Me.plan()))?.aiPlanner } }) {
            UpgradeSheet(reason: "AI planning is part of Pro: it picks the best time, suggests things to do and drafts the plan.") { _ in
                model.toast = "Welcome to Pro"
            }
        }
        .errorAlert($model.error)
        .toast($model.toast)
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

    private var headingRow: some View {
        HStack(spacing: 6) {
            Button { Task { await model.step(by: -1) } } label: { chevron("chevron.left") }
                .accessibilityLabel("Previous")
            Text(model.heading)
                .displayFont(19, .semibold, relativeTo: .headline)
                .lineLimit(1)
                .minimumScaleFactor(0.8)
                .accessibilityAddTraits(.isHeader)
            Button { Task { await model.step(by: 1) } } label: { chevron("chevron.right") }
                .accessibilityLabel("Next")
            Spacer(minLength: 4)
            SegmentedControl(
                selection: $model.mode,
                options: [
                    SegmentOption(value: .month, label: "Month"),
                    SegmentOption(value: .week, label: "Week"),
                    SegmentOption(value: .day, label: "Day"),
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
            switch model.mode {
            case .month:
                MonthGrid(model: model, group: group, me: app.session.user?.id)
                DayDetailCard(model: model, group: group, me: app.session.user?.id) { draft in
                    proposalDraft = draft
                }
                planWithAI
            case .week:
                WeekGrid(model: model, group: group)
            case .day:
                DayTimeline(detail: model.dayDetails[model.selectedDay], date: model.selectedDay, group: group, me: app.session.user?.id) {
                    proposalDraft = $0
                }
            }
        }
    }
}

/// "2 people still to sync" with a progress ring and Nudge for admins.
private struct SyncBanner: View {
    let group: Group
    let isAdmin: Bool
    let isNudging: Bool
    let nudge: () -> Void
    @Environment(\.theme) private var theme

    var body: some View {
        let pending = group.pendingMembers
        HStack(spacing: 10) {
            ZStack {
                ProgressRing(progress: Double(group.syncedCount) / Double(max(group.memberCount, 1)), lineWidth: 4, color: theme.warn)
                Text("\(group.syncedCount)/\(group.memberCount)").bodyFont(11, .bold, relativeTo: .caption2)
            }
            .frame(width: 38, height: 38)
            VStack(alignment: .leading, spacing: 0) {
                Text("\(Formatting.plural(pending.count, "person", "people")) still to sync").bodyFont(14, .semibold)
                Text("\(Formatting.list(pending.map(\.firstName))) \(pending.count == 1 ? "hasn't" : "haven't") added busy times")
                    .bodyFont(12, relativeTo: .caption)
                    .foregroundStyle(theme.muted)
                    .lineLimit(2)
            }
            Spacer(minLength: 0)
            if isAdmin {
                Button(action: nudge) {
                    LoadingLabel(title: "Nudge", isLoading: isNudging)
                }
                .buttonStyle(.filled(.warning, height: 36, cornerRadius: 12, fontSize: 13, fullWidth: false))
                .disabled(isNudging)
                .frame(minHeight: 44)
            }
        }
        .padding(.leading, 12)
        .padding(.trailing, 10)
        .padding(.vertical, 10)
        .glass(cornerRadius: 20)
        .accessibilityElement(children: .contain)
    }
}

/// "A weekend · Fri from 18:00 → Sun · Nov – Jan" — opens the timing editor.
private struct TimingChip: View {
    let summary: String
    let action: () -> Void
    @Environment(\.theme) private var theme

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                Image(systemName: "clock").font(.system(size: 14, weight: .medium))
                (Text(lead).fontWeight(.semibold) + Text(rest))
                    .bodyFont(13, relativeTo: .subheadline)
                    .lineLimit(1)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Image(systemName: "pencil").font(.system(size: 13, weight: .medium)).foregroundStyle(theme.muted)
            }
            .foregroundStyle(theme.ink)
            .padding(.horizontal, 12)
            .frame(minHeight: 44)
            .glass(cornerRadius: 14, shadow: false)
        }
        .buttonStyle(.pressable)
        .accessibilityLabel("Timing: \(summary)")
        .accessibilityHint("Opens the timing settings")
    }

    private var parts: [Substring] { summary.split(separator: "·", maxSplits: 1) }
    private var lead: String { parts.first.map { $0.trimmingCharacters(in: .whitespaces) } ?? summary }
    private var rest: String { parts.count > 1 ? " ·\(parts[1])" : "" }
}

// MARK: - Month

private struct MonthGrid: View {
    let model: GroupAvailabilityViewModel
    let group: Group
    let me: String?
    @Environment(\.theme) private var theme

    var body: some View {
        let columns = Array(repeating: GridItem(.flexible(), spacing: 4), count: model.columns.count)
        VStack(alignment: .leading, spacing: 6) {
            LazyVGrid(columns: columns, spacing: 4) {
                ForEach(model.columns, id: \.self) { column in
                    Text(Weekday.short(column))
                        .bodyFont(11, .semibold, relativeTo: .caption2)
                        .foregroundStyle(theme.muted)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.leading, model.columns.count > 4 ? 4 : 10)
                }
                ForEach(Array(model.monthRows.enumerated()), id: \.offset) { _, row in
                    ForEach(Array(row.enumerated()), id: \.offset) { _, date in
                        if let date {
                            DayCell(
                                date: date,
                                info: model.day(date),
                                note: note(for: date),
                                compact: model.columns.count > 4,
                                isSelected: date == model.selectedDay
                            ) {
                                withAnimation(.spring(response: 0.3)) { model.pick(date) }
                            }
                        } else {
                            Color.clear.frame(height: 54).accessibilityHidden(true)
                        }
                    }
                }
            }
            legend
        }
    }

    private var legend: some View {
        HStack(spacing: 14) {
            LegendItem(label: "Works for everyone") { theme.accent }
            LegendItem(label: "Someone's busy") {
                theme.busyBg.opacity(0.5).overlay(RoundedRectangle(cornerRadius: 3).strokeBorder(theme.busyLine, lineWidth: 1))
            }
            if model.columns.count < 7, let first = model.columns.first, let last = model.columns.last {
                Text("\(Weekday.short(first))–\(Weekday.short(last)) only")
            }
        }
        .bodyFont(11, relativeTo: .caption2)
        .foregroundStyle(theme.muted)
        .padding(.top, 4)
        .padding(.horizontal, 2)
    }

    private func note(for date: CalendarDay) -> String {
        guard let info = model.day(date) else { return "" }
        switch info.status {
        case .match:
            if let slot = model.slot(for: date), info.slotPosition == .start || info.slotPosition == .single {
                let startsMidday = !Calendar.current.isDate(slot.start, equalTo: date.startDate(), toGranularity: .minute)
                return startsMidday ? "from \(Formatting.time(slot.start))" : "all free"
            }
            return "all free"
        case .busy:
            let names = info.busyMemberIds.map { group.member($0)?.firstName ?? "Someone" }
            if names.count == 1 { return info.busyMemberIds.first == me ? "You're busy" : "\(names[0]) busy" }
            return "\(names.count) busy"
        case .free, .hidden:
            if date == .today() { return "Today" }
            return info.holiday ?? ""
        }
    }
}

private struct DayCell: View {
    let date: CalendarDay
    let info: AvailabilityDay?
    let note: String
    let compact: Bool
    let isSelected: Bool
    let action: () -> Void
    @Environment(\.theme) private var theme

    var body: some View {
        let status = info?.status ?? .hidden
        Button(action: action) {
            VStack(alignment: .leading, spacing: 4) {
                Text("\(date.day)").font(AppFont.body(compact ? 15 : 17, .semibold, relativeTo: .body))
                if !compact || status == .match {
                    Text(note).bodyFont(compact ? 9 : 11, .semibold, relativeTo: .caption2).lineLimit(1)
                }
            }
            .foregroundStyle(status == .match ? theme.onAccent : theme.ink)
            .padding(.horizontal, compact ? 6 : 10)
            .frame(maxWidth: .infinity, minHeight: 54, alignment: .leading)
            .background(background(status))
            .opacity(status == .busy ? 0.5 : (status == .hidden ? 0.35 : 1))
            .overlay {
                if isSelected {
                    shape.stroke(theme.ink, lineWidth: 2).padding(-4)
                }
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(PressableButtonStyle(scale: 0.97))
        .accessibilityLabel(accessibilityText(status))
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }

    private var shape: UnevenRoundedRectangle {
        let big: CGFloat = 16, small: CGFloat = 5
        switch info?.slotPosition {
        case .start: return UnevenRoundedRectangle(topLeadingRadius: big, bottomLeadingRadius: big, bottomTrailingRadius: small, topTrailingRadius: small, style: .continuous)
        case .middle: return UnevenRoundedRectangle(topLeadingRadius: small, bottomLeadingRadius: small, bottomTrailingRadius: small, topTrailingRadius: small, style: .continuous)
        case .end: return UnevenRoundedRectangle(topLeadingRadius: small, bottomLeadingRadius: small, bottomTrailingRadius: big, topTrailingRadius: big, style: .continuous)
        default: return UnevenRoundedRectangle(topLeadingRadius: big, bottomLeadingRadius: big, bottomTrailingRadius: big, topTrailingRadius: big, style: .continuous)
        }
    }

    @ViewBuilder
    private func background(_ status: AvailabilityDay.Status) -> some View {
        switch status {
        case .match:
            shape.fill(theme.accent).shadow(color: theme.accent.opacity(0.55), radius: 10, y: 10)
        case .busy:
            shape.fill(theme.busyBg).overlay(shape.strokeBorder(theme.glassBorder, lineWidth: 1))
        case .free, .hidden:
            shape.fill(theme.surface).overlay(shape.strokeBorder(theme.glassBorder, lineWidth: 1))
        }
    }

    private func accessibilityText(_ status: AvailabilityDay.Status) -> String {
        let base = Formatting.longDay(date.startDate())
        switch status {
        case .match: return "\(base), works for everyone"
        case .busy: return "\(base), \(note)"
        case .free: return note.isEmpty ? base : "\(base), \(note)"
        case .hidden: return "\(base), outside the group's timing"
        }
    }
}

/// The card under the month: what the selected day means, with Propose / See who's free.
private struct DayDetailCard: View {
    let model: GroupAvailabilityViewModel
    let group: Group
    let me: String?
    let propose: (ProposalDraft) -> Void
    @Environment(\.theme) private var theme

    var body: some View {
        let slot = model.slot(for: model.selectedDay)
        let works = slot != nil && model.day(model.selectedDay)?.status == .match
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .center, spacing: 8) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(works ? (slot?.label ?? "") : Formatting.shortDay(model.selectedDay)).displayFont(16, .semibold, relativeTo: .headline)
                    Text(subtitle(works: works, slot: slot)).bodyFont(12, relativeTo: .caption).foregroundStyle(theme.muted)
                }
                Spacer()
                StatusPill(text: works ? "Works" : "Doesn\u{2019}t fit", tone: works ? .mint : .neutral)
            }
            HStack(spacing: 8) {
                if works, let slot {
                    Button("Propose this \(group.requirement.slotNoun.rawValue)") {
                        propose(ProposalDraft(slot: slot, requirement: group.requirement))
                    }
                    .buttonStyle(.filled(.accent, height: 44, cornerRadius: 14, fontSize: 14))
                } else if let status = model.day(model.selectedDay)?.status, status == .busy || status == .free {
                    // Doesn't work for everyone: the proposal sheet warns who's busy.
                    Button("Propose anyway") {
                        let day = model.selectedDay
                        propose(ProposalDraft(start: WallTime(hour: 18, minute: 0).date(on: day), end: WallTime(hour: 21, minute: 0).date(on: day), allDay: false))
                    }
                    .buttonStyle(.filled(.secondary, height: 44, cornerRadius: 14, fontSize: 14))
                }
                Button("See who's free") { Task { await model.showDay(model.selectedDay) } }
                    .buttonStyle(.filled(.secondary, height: 44, cornerRadius: 14, fontSize: 14))
            }
        }
        .padding(14)
        .glass(cornerRadius: 22)
        .animation(.easeInOut(duration: 0.2), value: model.selectedDay)
    }

    private func subtitle(works: Bool, slot: Slot?) -> String {
        guard let info = model.day(model.selectedDay) else { return "Outside the search range" }
        if works, let slot { return "Everyone synced is free from \(Formatting.shortDay(slot.start)) \(Formatting.time(slot.start))" }
        switch info.status {
        case .busy:
            let names = info.busyMemberIds.map { $0 == me ? "You" : (group.member($0)?.firstName ?? "Someone") }
            return "\(Formatting.list(names)) \(names == ["You"] || names.count > 1 ? "are" : "is") busy"
        case .hidden: return info.holiday.map { "\($0) · outside the group's timing" } ?? "Outside the group's timing"
        default: return info.holiday ?? "Free, but no full slot fits here"
        }
    }
}
