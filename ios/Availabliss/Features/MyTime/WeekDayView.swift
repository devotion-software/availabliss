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

/// Calendar busy time the user tapped.
struct CalendarBusyItem: Identifiable {
    let id = UUID()
    let interval: BusyInterval
}

/// My time opened on a day: the week as a strip of days on top, and the chosen day's tall timeline
/// under it. Swipe the strip for other weeks and the timeline for other days; tap free time to
/// block that hour, tap busy time to open it.
struct WeekDayView: View {
    let model: MyTimeViewModel
    @State var day: CalendarDay
    /// "Month" or "Agenda": where back goes.
    let backTitle: String
    let onBack: () -> Void
    /// Leaves My time for calendar settings.
    let openCalendars: () -> Void

    @Environment(\.theme) private var theme
    @State private var draft: BlockDraft?
    @State private var calendarBusy: CalendarBusyItem?
    @State private var toast: String?
    /// Which side the next day / week slides in from (trailing = later).
    @State private var pageEdge: Edge = .trailing
    /// When a sideways swipe last moved the page. It follows the finger, so lifting it would
    /// otherwise also tap whatever it started on.
    @State private var lastSwipeAt = Date.distantPast
    private var justSwiped: Bool { Date.now.timeIntervalSince(lastSwipeAt) < 0.4 }

    /// The first time the timeline shows.
    private static let startHour = 8
    private static var lastDay: CalendarDay { CalendarDay.today().adding(days: MyTimeViewModel.maxDays - 1) }

    private var weekStart: CalendarDay { day.startOfWeek() }

    var body: some View {
        let summary = model.summary(for: day)
        let allDay = model.layout.allDay[day] ?? []
        // Busy all day: no hours to show, just what makes it so.
        let busyAllDay = allDay.contains { !$0.isIgnored }
        VStack(spacing: 0) {
            VStack(alignment: .leading, spacing: 12) {
                topBar
                WeekStrip(model: model, weekStart: weekStart, selected: day) { picked in if !justSwiped { select(picked) } }
                    .id(weekStart)
                    .transition(slide)
                    .modifier(SwipeToStep(onDrag: { lastSwipeAt = .now }) { delta in move(byDays: 7 * delta) })
                summaryLine(summary)
                if !allDay.isEmpty && !busyAllDay {
                    VStack(spacing: 6) {
                        ForEach(Array(allDay.enumerated()), id: \.offset) { _, interval in
                            AllDayChip(interval: interval) { open(interval) }
                        }
                    }
                }
            }
            .padding(.horizontal, 20)
            .padding(.top, 12)
            .padding(.bottom, 10)
            theme.line.frame(height: 1)
            SwiftUI.Group {
                if busyAllDay {
                    ScrollView(.vertical) {
                        BusyAllDayCard(day: day, items: allDay) { interval in
                            if !justSwiped { open(interval) }
                        }
                        .padding(.horizontal, 20)
                        .padding(.vertical, 20)
                    }
                    .scrollIndicators(.hidden)
                    .id(day)
                    .transition(slide)
                } else {
                    ScrollViewReader { proxy in
                        ScrollView(.vertical) {
                            MyDayTimeline(day: day, pieces: model.layout.timed[day] ?? []) { from, to in
                                if !justSwiped { draft = BlockDraft(day: day, range: (from, to)) }
                            } onOpen: { interval in
                                if !justSwiped { open(interval) }
                            }
                            .padding(.leading, 8)
                            .padding(.trailing, 20)
                            .padding(.bottom, 24)
                        }
                        .scrollIndicators(.hidden)
                        .task(id: day) { proxy.scrollTo(Self.startHour, anchor: .top) }
                    }
                    // A new page per day, sliding in from the side it's coming from.
                    .id(day)
                    .transition(slide)
                }
            }
            .frame(maxHeight: .infinity, alignment: .top)
            .task(id: day) { await model.ensureLoaded(through: weekStart.adding(days: 6)) }
            .modifier(SwipeToStep(onDrag: { lastSwipeAt = .now }) { delta in move(byDays: delta) })
            .clipped()
        }
        .foregroundStyle(theme.ink)
        .sheet(item: $draft) { $0.sheet(model: model) }
        .sheet(item: $calendarBusy) { busy in
            CalendarBusySheet(interval: busy.interval) {
                calendarBusy = nil
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

    private var slide: AnyTransition {
        .asymmetric(
            insertion: .move(edge: pageEdge).combined(with: .opacity),
            removal: .move(edge: pageEdge == .trailing ? .leading : .trailing).combined(with: .opacity)
        )
    }

    private var topBar: some View {
        HStack(spacing: 8) {
            Button(action: onBack) {
                HStack(spacing: 4) {
                    Image(systemName: "chevron.left").font(.system(size: 14, weight: .bold))
                    Text(backTitle).bodyFont(15, .semibold)
                }
                .foregroundStyle(theme.accentInk)
                .frame(minHeight: 44)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Back to \(backTitle.lowercased())")
            Spacer(minLength: 4)
            Text(Formatting.dayRange(weekStart, weekStart.adding(days: 6)))
                .displayFont(18, .semibold, relativeTo: .headline)
                .lineLimit(1)
                .accessibilityAddTraits(.isHeader)
                .contentTransition(.numericText())
            Spacer(minLength: 4)
            Button {
                draft = BlockDraft(day: day)
            } label: {
                Label("Block", systemImage: "plus")
                    .bodyFont(13, .semibold, relativeTo: .subheadline)
                    .foregroundStyle(theme.onSolid)
                    .padding(.horizontal, 12)
                    .frame(height: 32)
                    .background(Capsule().fill(theme.solid))
                    .frame(minHeight: 44)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Block time on \(Formatting.longDay(day.startDate()))")
        }
    }

    private func summaryLine(_ summary: DaySummary) -> some View {
        HStack(spacing: 0) {
            Text(day == .today() ? "Today · \(summary.text)" : "\(Formatting.longDay(day.startDate())) · \(summary.text)")
                .bodyFont(14, .semibold)
                .layoutPriority(1)
            if !summary.names.isEmpty {
                Text(" · \(summary.names.joined(separator: ", "))")
                    .bodyFont(14)
                    .foregroundStyle(theme.muted)
            }
        }
        .lineLimit(1)
        .minimumScaleFactor(0.85)
        .contentTransition(.numericText())
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(summary.accessibilityText)
    }

    private func select(_ picked: CalendarDay) {
        guard picked != day, picked >= .today(), picked <= Self.lastDay else { return }
        pageEdge = picked > day ? .trailing : .leading
        withAnimation(.spring(response: 0.32, dampingFraction: 0.9)) { day = picked }
    }

    /// The previous / next day (or week), no earlier than today.
    private func move(byDays delta: Int) {
        let target = min(max(day.adding(days: delta), .today()), Self.lastDay)
        select(target)
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

/// In place of the timeline on a day something blocks entirely: "Busy all day" and what it is,
/// each tappable (to edit your block, or to mark a calendar event not busy).
private struct BusyAllDayCard: View {
    let day: CalendarDay
    let items: [BusyInterval]
    let onOpen: (BusyInterval) -> Void
    @Environment(\.theme) private var theme

    var body: some View {
        VStack(spacing: 14) {
            Image(systemName: "calendar")
                .font(.system(size: 20, weight: .semibold))
                .foregroundStyle(theme.accentInk)
                .frame(width: 48, height: 48)
                .background(Circle().fill(theme.accentSoft))
                .accessibilityHidden(true)
            VStack(spacing: 4) {
                Text("Busy all day")
                    .displayFont(19, .semibold, relativeTo: .headline)
                    .accessibilityAddTraits(.isHeader)
                Text("Groups see you as busy for the whole day.")
                    .bodyFont(14, relativeTo: .subheadline)
                    .foregroundStyle(theme.muted)
                    .multilineTextAlignment(.center)
            }
            VStack(spacing: 6) {
                ForEach(Array(items.enumerated()), id: \.offset) { _, interval in
                    AllDayChip(interval: interval) { onOpen(interval) }
                }
            }
        }
        .frame(maxWidth: .infinity)
        .padding(18)
        .glass(cornerRadius: 24)
    }
}

/// Mon … Sun of one week: weekday, date and dots for what's on. The selected day is filled.
private struct WeekStrip: View {
    let model: MyTimeViewModel
    let weekStart: CalendarDay
    let selected: CalendarDay
    let onSelect: (CalendarDay) -> Void
    @Environment(\.theme) private var theme

    var body: some View {
        HStack(spacing: 2) {
            ForEach(0..<7, id: \.self) { offset in
                let day = weekStart.adding(days: offset)
                cell(day)
            }
        }
        .padding(4)
        .glass(cornerRadius: 20)
    }

    private func cell(_ day: CalendarDay) -> some View {
        let isSelected = day == selected
        let isToday = day == .today()
        let isPast = day < .today()
        let summary = model.summaries[day]
        let calendarBusy = summary?.segments.contains { !$0.manual } ?? false
        return Button {
            onSelect(day)
        } label: {
            VStack(spacing: 3) {
                Text(Weekday.short(day.weekday()))
                    .bodyFont(11, .semibold, relativeTo: .caption2)
                    .foregroundStyle(isSelected ? theme.onSolid.opacity(0.8) : theme.muted)
                Text("\(day.day)")
                    .displayFont(17, .bold, relativeTo: .headline)
                    .monospacedDigit()
                    .foregroundStyle(isSelected ? theme.onSolid : isToday ? theme.accentInk : theme.ink)
                HStack(spacing: 3) {
                    if summary?.hasOwnBlock == true { Circle().fill(isSelected ? theme.onSolid : theme.accent).frame(width: 5, height: 5) }
                    if calendarBusy { Circle().fill(isSelected ? theme.onSolid.opacity(0.6) : theme.muted.opacity(0.6)).frame(width: 5, height: 5) }
                }
                .frame(height: 5)
            }
            .frame(maxWidth: .infinity, minHeight: 58)
            .background { if isSelected { RoundedRectangle(cornerRadius: 16, style: .continuous).fill(theme.solid) } }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(isPast)
        .opacity(isPast ? 0.35 : 1)
        .accessibilityLabel((isToday ? "Today, " : "") + (summary?.accessibilityText ?? Formatting.longDay(day.startDate())))
        .accessibilityAddTraits(isSelected ? [.isButton, .isSelected] : .isButton)
    }
}

/// Swipe sideways to step: the content follows the finger, and a long enough drag or a flick
/// calls `onStep` with 1 (later) or -1 (earlier).
struct SwipeToStep: ViewModifier {
    /// Called while a sideways drag moves the content.
    var onDrag: () -> Void = {}
    let onStep: (Int) -> Void
    @State private var dragX: CGFloat = 0

    func body(content: Content) -> some View {
        content
            .offset(x: dragX)
            .simultaneousGesture(
                DragGesture(minimumDistance: 20)
                    .onChanged { drag in
                        let dx = drag.translation.width, dy = drag.translation.height
                        guard abs(dx) > abs(dy) * 1.5 else { return }
                        dragX = dx
                        onDrag()
                    }
                    .onEnded { drag in
                        let dx = drag.translation.width, dy = drag.translation.height
                        let flick = abs(drag.predictedEndTranslation.width) > 160
                        if abs(dx) > abs(dy) * 1.5, abs(dx) > 60 || flick {
                            onStep(dx < 0 ? 1 : -1)
                        }
                        withAnimation(.spring(response: 0.3, dampingFraction: 0.85)) { dragX = 0 }
                    }
            )
    }
}
