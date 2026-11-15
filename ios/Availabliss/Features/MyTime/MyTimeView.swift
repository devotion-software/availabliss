import SwiftUI

/// My time: a week calendar of your busy time at exact times. Calendar busy is faded,
/// blocks you made are striped; tap empty time to block it, tap busy time to open it.
struct MyTimeView: View {
    @Environment(AppModel.self) private var app
    @Environment(\.theme) private var theme
    @State private var model: MyTimeViewModel
    @State private var draft: BlockDraft?
    @State private var calendarBusy: CalendarBusy?
    @State private var toast: String?

    /// What the block sheet opens on.
    private struct BlockDraft: Identifiable {
        let id = UUID()
        var day: CalendarDay
        var range: (from: Int, to: Int)?
        var editing: ManualBlock?
    }

    /// Calendar busy time the user tapped.
    private struct CalendarBusy: Identifiable {
        let id = UUID()
        let interval: BusyInterval
    }

    init(api: APIClient) {
        _model = State(initialValue: MyTimeViewModel(api: api))
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                ScreenHeader(title: "My time", subtitle: "Tap to block time, tap a block to edit it") {
                    IconButton(icon: .asset("CalendarSync"), label: "Connected calendars") { app.router.push(.calendars) }
                }
                SegmentedControl(
                    selection: Binding(get: { model.mode }, set: { mode in Task { await model.setMode(mode) } }),
                    options: [SegmentOption(value: .week, label: "Week"), SegmentOption(value: .month, label: "Month")],
                    accessibilityLabel: "My time view"
                )
                weekSwitcher
                SwiftUI.Group {
                    switch model.mode {
                    case .week:
                        WeekPanel(model: model) { day, from, to in
                            draft = BlockDraft(day: day, range: (from, to))
                        } onOpen: { interval in
                            open(interval)
                        }
                    case .month:
                        MonthPanel(model: model) { day in Task { await model.showWeek(of: day) } }
                    }
                }
                // Swipe sideways for the previous / next week or month.
                .simultaneousGesture(
                    DragGesture(minimumDistance: 30).onEnded { drag in
                        let dx = drag.translation.width, dy = drag.translation.height
                        guard abs(dx) > 60, abs(dx) > abs(dy) * 1.5 else { return }
                        Task { await model.move(by: dx < 0 ? 1 : -1) }
                    }
                )
                ForEach(model.recurringBlocks) { block in
                    RecurringBlockRow(block: block) {
                        draft = BlockDraft(day: CalendarDay(block.start), editing: block)
                    } onRemove: {
                        model.pendingRemoval = block
                    }
                }
                Button {
                    draft = BlockDraft(day: max(CalendarDay.today(), model.weekStart))
                } label: {
                    LoadingLabel(title: "Block a specific time", systemImage: "plus")
                }
                .buttonStyle(.filled(.primary, height: 54, cornerRadius: 18))
                .padding(.top, 4)
            }
            .padding(.horizontal, 20)
            .padding(.top, 20)
            .padding(.bottom, 24)
        }
        .screenBackground(OrbLayout.myTime)
        .foregroundStyle(theme.ink)
        .chromeless()
        .task { await model.load() }
        .refreshable { await model.load() }
        .task {
            for await event in app.live.events() {
                switch event.kind {
                case .availabilityChanged:
                    if event.payload(as: [String: String].self)?["userId"] == app.session.user?.id { await model.load() }
                case .reconnected:
                    await model.load()
                default:
                    break
                }
            }
        }
        .sheet(item: $draft) { draft in
            BlockTimeSheet(initialDay: draft.day, range: draft.range, editing: draft.editing) { block in
                if let editing = draft.editing { await model.update(editing.id, block) } else { await model.create(block) }
            }
        }
        .sheet(item: $calendarBusy) { busy in
            CalendarBusySheet(interval: busy.interval) {
                app.router.push(.calendars)
            } onIgnore: { series in
                let ok = await model.ignore(busy.interval, series: series)
                if ok { toast = series ? "Not busy every time it repeats" : "Marked not busy" }
                return ok
            } onUnignore: {
                let ok = await model.unignore(busy.interval)
                if ok { toast = "Counts as busy again" }
                return ok
            }
        }
        .toast($toast)
        .confirmationDialog(
            "Remove this block?",
            isPresented: Binding(get: { model.pendingRemoval != nil }, set: { if !$0 { model.pendingRemoval = nil } }),
            titleVisibility: .visible,
            presenting: model.pendingRemoval
        ) { block in
            Button(block.repeat == .none ? "Remove block" : "Remove every occurrence", role: .destructive) {
                Task { await model.remove(block) }
            }
        } message: { block in
            Text("\(block.note ?? "Busy") · \(block.scheduleDescription)")
        }
        .errorAlert($model.error)
    }

    /// A tap on busy time: edit your own block (the whole series if it repeats), or explain calendar busy.
    private func open(_ interval: BusyInterval) {
        switch interval.source {
        case .calendar:
            calendarBusy = CalendarBusy(interval: interval)
        case .manual:
            if let block = model.blocks.first(where: { $0.id == interval.blockId }) {
                draft = BlockDraft(day: CalendarDay(block.start), editing: block)
            } else {
                toast = "That block was just removed."
            }
        }
    }

    private var weekSwitcher: some View {
        HStack(spacing: 8) {
            WeekArrow(systemImage: "chevron.left", label: model.mode == .week ? "Previous week" : "Previous month") { Task { await model.move(by: -1) } }
            Text(model.title)
                .displayFont(17, .semibold, relativeTo: .headline)
                .frame(maxWidth: .infinity)
                .contentTransition(.numericText())
            WeekArrow(systemImage: "chevron.right", label: model.mode == .week ? "Next week" : "Next month") { Task { await model.move(by: 1) } }
        }
    }
}

private struct WeekArrow: View {
    let systemImage: String
    let label: String
    let action: () -> Void
    @Environment(\.theme) private var theme

    var body: some View {
        Button(action: action) {
            Image(systemName: systemImage)
                .font(.system(size: 14, weight: .bold))
                .foregroundStyle(theme.ink)
                .frame(width: 40, height: 40)
                .glass(cornerRadius: 12, shadow: false)
                .frame(width: 44, height: 44)
                .contentShape(Rectangle())
        }
        .buttonStyle(.pressable)
        .accessibilityLabel(label)
    }
}

/// The glass panel: the week grid and its legend.
private struct WeekPanel: View {
    let model: MyTimeViewModel
    let onSelect: (CalendarDay, Int, Int) -> Void
    let onOpen: (BusyInterval) -> Void
    @Environment(\.theme) private var theme

    var body: some View {
        VStack(spacing: 0) {
            // Until the first week lands, a loader the grid's size: the tab opens straight away
            // instead of waiting to build the grid.
            if model.loadedWeek == nil {
                ProgressView()
                    .frame(maxWidth: .infinity)
                    .frame(height: WeekTimeGrid.visibleHeight + 48)
                    .accessibilityLabel("Loading your week")
            } else {
                WeekTimeGrid(layout: model.layout, scrollKey: model.loadedWeek, onSelect: onSelect, onOpen: onOpen)
            }
            legend
        }
        .padding(12)
        .glass(cornerRadius: 24)
        .accessibilityElement(children: .contain)
        .accessibilityLabel("Busy times this week")
    }

    private var legend: some View {
        HStack(spacing: 12) {
            LegendItem(label: "Blocked by you") {
                ZStack { theme.accentSoft; Stripes(color: theme.accent, lineWidth: 2, spacing: 3) }
            }
            LegendItem(label: "From a calendar") {
                theme.busyBg.opacity(0.5).overlay(RoundedRectangle(cornerRadius: 3).strokeBorder(theme.busyLine, lineWidth: 1))
            }
            Spacer(minLength: 0)
            Text("\(model.blockedCount) blocked this week")
        }
        .bodyFont(11, relativeTo: .caption2)
        .foregroundStyle(theme.muted)
        .padding(.top, 10)
    }
}

/// "Choir · Every Tuesday · 19:00 – 21:30 · Private"
private struct RecurringBlockRow: View {
    let block: ManualBlock
    let onEdit: () -> Void
    let onRemove: () -> Void
    @Environment(\.theme) private var theme

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "repeat")
                .font(.system(size: 15, weight: .bold))
                .foregroundStyle(theme.accentInk)
                .frame(width: 34, height: 34)
                .background(RoundedRectangle(cornerRadius: 11).fill(theme.accentSoft))
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 0) {
                Text(block.note ?? "Busy").bodyFont(14, .semibold)
                Text(block.scheduleDescription).bodyFont(12, relativeTo: .caption).foregroundStyle(theme.muted)
            }
            Spacer()
            StatusPill(text: "Private")
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .glass(cornerRadius: 20)
        .contentShape(RoundedRectangle(cornerRadius: 20))
        .onTapGesture(perform: onEdit)
        .contextMenu {
            Button(action: onEdit) { Label("Edit", systemImage: "pencil") }
            Button(role: .destructive, action: onRemove) { Label("Remove", systemImage: "trash") }
        }
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isButton)
        .accessibilityHint("Double-tap to edit")
        .accessibilityAction(named: "Remove", onRemove)
    }
}

/// The month: each day with a strip of its busy time (blocked by you striped, calendars grey).
/// Tap a day to open its week.
private struct MonthPanel: View {
    let model: MyTimeViewModel
    let onPick: (CalendarDay) -> Void
    @Environment(\.theme) private var theme

    var body: some View {
        VStack(spacing: 6) {
            HStack(spacing: 4) {
                ForEach(0..<7, id: \.self) { column in
                    Text(Weekday.short((model.monthWeeks.first?.first ?? .today()).adding(days: column).weekday()))
                        .bodyFont(11, .semibold, relativeTo: .caption2)
                        .foregroundStyle(theme.muted)
                        .frame(maxWidth: .infinity)
                }
            }
            ForEach(model.monthWeeks, id: \.self) { week in
                HStack(spacing: 4) {
                    ForEach(week, id: \.self) { day in
                        MonthDayCell(
                            day: day,
                            inMonth: day.month == model.monthStart.month,
                            intervals: model.intervals(on: day)
                        ) { onPick(day) }
                    }
                }
            }
            HStack(spacing: 12) {
                LegendItem(label: "Blocked by you") { theme.accent }
                LegendItem(label: "From a calendar") { theme.ink.opacity(0.3) }
                Spacer(minLength: 0)
                Text("Tap a day for its week")
            }
            .bodyFont(11, relativeTo: .caption2)
            .foregroundStyle(theme.muted)
            .padding(.top, 6)
        }
        .padding(12)
        .glass(cornerRadius: 24)
        .accessibilityElement(children: .contain)
        .accessibilityLabel("Busy times this month")
    }
}

private struct MonthDayCell: View {
    let day: CalendarDay
    let inMonth: Bool
    let intervals: [BusyInterval]
    let action: () -> Void
    @Environment(\.theme) private var theme

    var body: some View {
        let isToday = day == .today()
        Button(action: action) {
            VStack(spacing: 5) {
                Text("\(day.day)")
                    .bodyFont(14, .semibold)
                    .monospacedDigit()
                    .foregroundStyle(isToday ? theme.onAccent : theme.ink)
                    .frame(width: 26, height: 26)
                    .background { if isToday { Circle().fill(theme.accent) } }
                strip
            }
            .frame(maxWidth: .infinity, minHeight: 56)
            .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(theme.surface.opacity(inMonth ? 1 : 0.4)))
            .opacity(inMonth ? 1 : 0.5)
            .contentShape(Rectangle())
        }
        .buttonStyle(.pressable)
        .accessibilityLabel(accessibilityText)
    }

    /// The day's 24 hours as a thin bar, busy time drawn where it falls.
    private var strip: some View {
        GeometryReader { proxy in
            let start = day.startDate(), length = day.adding(days: 1).startDate().timeIntervalSince(start)
            ZStack(alignment: .leading) {
                Capsule().fill(theme.line)
                ForEach(Array(intervals.enumerated()), id: \.offset) { _, interval in
                    let from = max(0, interval.start.timeIntervalSince(start)) / length
                    let to = min(length, interval.end.timeIntervalSince(start)) / length
                    Capsule()
                        .fill(interval.source == .manual ? theme.accent : theme.ink.opacity(0.3))
                        .frame(width: max(3, (to - from) * proxy.size.width))
                        .offset(x: from * proxy.size.width)
                }
            }
        }
        .frame(height: 6)
        .padding(.horizontal, 6)
        .accessibilityHidden(true)
    }

    private var accessibilityText: String {
        let base = Formatting.longDay(day.startDate())
        return intervals.isEmpty ? "\(base), free" : "\(base), \(Formatting.plural(intervals.count, "busy time")). Opens the week"
    }
}
