import SwiftUI

/// My time: a week of morning / afternoon / evening slots. Calendar busy is faded,
/// blocks you made are striped; tap a free slot to block it.
struct MyTimeView: View {
    @Environment(AppModel.self) private var app
    @Environment(\.theme) private var theme
    @State private var model: MyTimeViewModel
    @State private var showsBlockSheet = false

    init(api: APIClient) {
        _model = State(initialValue: MyTimeViewModel(api: api))
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                ScreenHeader(title: "My time", subtitle: "Tap to block time you're busy") {
                    IconButton(systemImage: "link", label: "Connected calendars") { app.router.push(.calendars) }
                }
                weekSwitcher
                WeekSlotGrid(model: model)
                ForEach(model.recurringBlocks) { block in
                    RecurringBlockRow(block: block) { model.pendingRemoval = block }
                }
                Button {
                    showsBlockSheet = true
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
            for await event in app.live.events() where event.kind == .availabilityChanged {
                if event.payload(as: [String: String].self)?["userId"] == app.session.user?.id { await model.load() }
            }
        }
        .sheet(isPresented: $showsBlockSheet) {
            BlockTimeSheet(initialDay: max(CalendarDay.today(), model.weekStart)) { block in
                await model.create(block)
            }
        }
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

    private var weekSwitcher: some View {
        HStack(spacing: 8) {
            WeekArrow(systemImage: "chevron.left", label: "Previous week") { Task { await model.moveWeek(by: -1) } }
            Text(model.title)
                .displayFont(17, .semibold, relativeTo: .headline)
                .frame(maxWidth: .infinity)
                .contentTransition(.numericText())
            WeekArrow(systemImage: "chevron.right", label: "Next week") { Task { await model.moveWeek(by: 1) } }
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

/// The glass week grid.
private struct WeekSlotGrid: View {
    let model: MyTimeViewModel
    @Environment(\.theme) private var theme

    var body: some View {
        VStack(spacing: 6) {
            Grid(horizontalSpacing: 6, verticalSpacing: 6) {
                GridRow {
                    Color.clear.frame(width: 44, height: 1)
                    ForEach(DayPart.allCases) { part in
                        Text(part.label).frame(maxWidth: .infinity)
                    }
                }
                .bodyFont(11, .semibold, relativeTo: .caption)
                .foregroundStyle(theme.muted)

                ForEach(model.days, id: \.self) { day in
                    GridRow {
                        DayLabel(day: day)
                        ForEach(DayPart.allCases) { part in
                            SlotCell(
                                day: day,
                                part: part,
                                state: model.state(day, part),
                                isWorking: model.togglingKey == model.key(day, part)
                            ) {
                                Task { await model.toggle(day, part) }
                            }
                        }
                    }
                }
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
        .padding(.top, 6)
    }
}

private struct DayLabel: View {
    let day: CalendarDay
    @Environment(\.theme) private var theme

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(Weekday.short(day.weekday())).bodyFont(12, .semibold, relativeTo: .caption)
            Text("\(day.day)").bodyFont(11, relativeTo: .caption2).foregroundStyle(theme.muted)
        }
        .frame(width: 44, alignment: .leading)
        .foregroundStyle(day == .today() ? theme.accentInk : theme.ink)
    }
}

private struct SlotCell: View {
    let day: CalendarDay
    let part: DayPart
    let state: SlotState
    let isWorking: Bool
    let action: () -> Void
    @Environment(\.theme) private var theme

    var body: some View {
        Button(action: action) {
            HStack(spacing: 4) {
                if case .calendar = state {
                    Circle().fill(theme.accent).frame(width: 6, height: 6)
                }
                if isWorking {
                    ProgressView().controlSize(.mini)
                } else {
                    Text(label).lineLimit(1).truncationMode(.tail)
                }
            }
            .bodyFont(11, .semibold, relativeTo: .caption2)
            .foregroundStyle(foreground)
            .padding(.horizontal, 6)
            .frame(maxWidth: .infinity, minHeight: 46)
            .background(background)
            .contentShape(Rectangle())
        }
        .buttonStyle(PressableButtonStyle(scale: 0.96))
        .disabled(isCalendar)
        .accessibilityLabel(accessibilityText)
        .accessibilityAddTraits(isBlocked ? .isSelected : [])
        .accessibilityHint(isCalendar ? "" : (isBlocked ? "Double-tap to unblock" : "Double-tap to block"))
    }

    private var isCalendar: Bool { if case .calendar = state { true } else { false } }
    private var isBlocked: Bool { if case .manual = state { true } else { false } }

    private var label: String {
        switch state {
        case .free: ""
        case .calendar(let label), .manual(let label, _): label
        }
    }

    private var foreground: Color {
        switch state {
        case .free: theme.muted
        case .calendar: theme.ink
        case .manual: theme.accentInk
        }
    }

    @ViewBuilder
    private var background: some View {
        let shape = RoundedRectangle(cornerRadius: 12, style: .continuous)
        switch state {
        case .free:
            shape.fill(theme.surface).overlay(shape.strokeBorder(theme.glassBorder, lineWidth: 1))
        case .calendar:
            shape.fill(theme.busyBg).overlay(shape.strokeBorder(theme.busyLine, lineWidth: 1)).opacity(0.5)
        case .manual:
            ZStack {
                shape.fill(theme.surface)
                Stripes(color: theme.accentSoft, lineWidth: 6, spacing: 6).clipShape(shape)
                shape.strokeBorder(theme.accent, lineWidth: 1.5)
            }
        }
    }

    private var accessibilityText: String {
        let base = "\(Weekday.full(day.weekday())) \(day.day) \(part.label.lowercased())"
        switch state {
        case .free: return "\(base), free"
        case .calendar(let label): return "\(base), busy from calendar: \(label)"
        case .manual(let label, _): return "\(base), blocked by you: \(label)"
        }
    }
}

/// "Choir · Every Tuesday · 19:00 – 21:30 · Private"
private struct RecurringBlockRow: View {
    let block: ManualBlock
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
        .contextMenu {
            Button(role: .destructive, action: onRemove) { Label("Remove", systemImage: "trash") }
        }
        .accessibilityElement(children: .combine)
        .accessibilityAction(named: "Remove", onRemove)
    }
}
