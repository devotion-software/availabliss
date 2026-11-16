import SwiftUI

/// What the block sheet opens on.
struct BlockDraft: Identifiable {
    let id = UUID()
    var day: CalendarDay
    var range: (from: Int, to: Int)?
    var when: BlockTimeSheet.When = .times
    var pickDate = false
    var editing: ManualBlock?

    /// The block sheet for this draft, saving through `model`.
    @MainActor func sheet(model: MyTimeViewModel) -> some View {
        BlockTimeSheet(initialDay: day, range: range, when: when, pickDate: pickDate, editing: editing) { block in
            if let editing { await model.update(editing.id, block) } else { await model.create(block) }
        }
    }
}

/// One day of My time, full height: the date with previous / next arrows, all-day items as chips,
/// and a tall timeline. Tap free time to block that hour; tap busy time to open it.
struct DaySheet: View {
    let model: MyTimeViewModel
    @State var day: CalendarDay
    /// Leaves the sheet for calendar settings.
    let openCalendars: () -> Void

    @Environment(\.dismiss) private var dismiss
    @Environment(\.theme) private var theme
    @State private var draft: BlockDraft?
    @State private var calendarBusy: CalendarBusyItem?
    @State private var toast: String?
    /// Which side the next day slides in from (trailing = later).
    @State private var pageEdge: Edge = .trailing
    /// How far a sideways swipe has dragged the day.
    @State private var dragX: CGFloat = 0

    /// The first time the timeline shows.
    private static let startHour = 8

    var body: some View {
        let summary = model.summary(for: day)
        let allDay = model.layout.allDay[day] ?? []
        VStack(spacing: 0) {
            VStack(alignment: .leading, spacing: 12) {
                header(summary)
                if !allDay.isEmpty {
                    VStack(spacing: 6) {
                        ForEach(Array(allDay.enumerated()), id: \.offset) { _, interval in
                            AllDayChip(interval: interval) { open(interval) }
                        }
                    }
                }
            }
            .padding(.horizontal, 18)
            .padding(.top, 18)
            .padding(.bottom, 12)
            theme.line.frame(height: 1)
            ScrollViewReader { proxy in
                ScrollView(.vertical) {
                    MyDayTimeline(day: day, pieces: model.layout.timed[day] ?? []) { from, to in
                        draft = BlockDraft(day: day, range: (from, to))
                    } onOpen: { interval in
                        open(interval)
                    }
                    .padding(.leading, 6)
                    .padding(.trailing, 18)
                    .padding(.bottom, 24)
                }
                .scrollIndicators(.hidden)
                // A new page per day, sliding in from the side it's coming from.
                .id(day)
                .transition(.asymmetric(
                    insertion: .move(edge: pageEdge).combined(with: .opacity),
                    removal: .move(edge: pageEdge == .trailing ? .leading : .trailing).combined(with: .opacity)
                ))
                .task(id: day) {
                    proxy.scrollTo(Self.startHour, anchor: .top)
                    await model.ensureLoaded(through: day)
                }
            }
            .offset(x: dragX)
            .clipped()
            // Swipe sideways for the previous / next day; the day follows your finger.
            .simultaneousGesture(
                DragGesture(minimumDistance: 30)
                    .onChanged { drag in
                        let dx = drag.translation.width, dy = drag.translation.height
                        guard abs(dx) > abs(dy) * 1.5 else { return }
                        // No further back than today.
                        dragX = dx > 0 && day <= .today() ? dx * 0.1 : dx * 0.35
                    }
                    .onEnded { drag in
                        let dx = drag.translation.width, dy = drag.translation.height
                        if abs(dx) > 60, abs(dx) > abs(dy) * 1.5 {
                            move(by: dx < 0 ? 1 : -1)
                        }
                        withAnimation(.spring(response: 0.3, dampingFraction: 0.85)) { dragX = 0 }
                    }
            )
        }
        .foregroundStyle(theme.ink)
        .presentationDetents([.large])
        .presentationCornerRadius(32)
        .presentationBackground(theme.bg)
        .presentationDragIndicator(.visible)
        .sheet(item: $draft) { $0.sheet(model: model) }
        .sheet(item: $calendarBusy) { busy in
            CalendarBusySheet(interval: busy.interval) {
                dismiss()
                openCalendars()
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
    }

    private func header(_ summary: DaySummary) -> some View {
        HStack(spacing: 6) {
            IconButton(systemImage: "chevron.left", label: "Previous day") { move(by: -1) }
                .disabled(day <= .today())
                .opacity(day <= .today() ? 0.35 : 1)
            VStack(spacing: 1) {
                Text(Formatting.longDay(day.startDate()))
                    .displayFont(20, .bold)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
                    .accessibilityAddTraits(.isHeader)
                    .contentTransition(.numericText())
                Text(day == .today() ? "Today · \(summary.text)" : summary.text)
                    .bodyFont(13, relativeTo: .subheadline)
                    .foregroundStyle(theme.muted)
            }
            .frame(maxWidth: .infinity)
            IconButton(systemImage: "chevron.right", label: "Next day") { move(by: 1) }
                .disabled(day >= CalendarDay.today().adding(days: MyTimeViewModel.maxDays - 1))
            CloseButton { dismiss() }
        }
    }

    private func move(by days: Int) {
        let next = day.adding(days: days)
        guard next >= .today(), next < CalendarDay.today().adding(days: MyTimeViewModel.maxDays) else { return }
        pageEdge = days > 0 ? .trailing : .leading
        withAnimation(.spring(response: 0.38, dampingFraction: 0.9)) {
            day = next
            dragX = 0
        }
    }

    /// A tap on busy time: edit your own block (the whole series if it repeats), or explain calendar busy.
    private func open(_ interval: BusyInterval) {
        switch interval.source {
        case .calendar:
            calendarBusy = CalendarBusyItem(interval: interval)
        case .manual:
            if let block = model.block(for: interval) {
                draft = BlockDraft(day: CalendarDay(block.start), editing: block)
            } else {
                toast = "That block was just removed."
            }
        }
    }
}

/// Calendar busy time the user tapped.
struct CalendarBusyItem: Identifiable {
    let id = UUID()
    let interval: BusyInterval
}
