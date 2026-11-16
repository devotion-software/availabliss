import SwiftUI

/// My time's month: each day shaded by how free it is (darker = more free time). Tap a day to
/// open its week. Swipe sideways, or use the arrows, for other months.
struct MyTimeMonth: View {
    let model: MyTimeViewModel
    @Binding var month: CalendarDay
    let onOpen: (CalendarDay) -> Void
    @Environment(\.theme) private var theme
    /// Which side the next month slides in from (trailing = later).
    @State private var pageEdge: Edge = .trailing
    @State private var lastSwipeAt = Date.distantPast

    private static var firstMonth: CalendarDay { CalendarDay.today().firstOfMonth }
    private static var lastMonth: CalendarDay { CalendarDay.today().adding(days: MyTimeViewModel.maxDays - 1).firstOfMonth }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            heading
            VStack(spacing: 6) {
                HStack(spacing: 4) {
                    ForEach(Weekday.all, id: \.self) { column in
                        Text(Weekday.short(column))
                            .bodyFont(11, .semibold, relativeTo: .caption2)
                            .foregroundStyle(theme.muted)
                            .frame(maxWidth: .infinity)
                    }
                }
                .accessibilityHidden(true)
                ZStack {
                    grid
                        .id(month)
                        .transition(.asymmetric(
                            insertion: .move(edge: pageEdge).combined(with: .opacity),
                            removal: .move(edge: pageEdge == .trailing ? .leading : .trailing).combined(with: .opacity)
                        ))
                }
                .modifier(SwipeToStep(onDrag: { lastSwipeAt = .now }) { delta in page(by: delta) })
                .clipped()
                legend
            }
            .padding(12)
            .glass(cornerRadius: 24)
        }
        .task(id: month) { await model.ensureLoaded(through: month.lastOfMonth()) }
    }

    private var heading: some View {
        HStack(spacing: 6) {
            Button { page(by: -1) } label: { chevron("chevron.left") }
                .disabled(month <= Self.firstMonth)
                .opacity(month <= Self.firstMonth ? 0.35 : 1)
                .accessibilityLabel("Previous month")
            Text(monthTitle)
                .displayFont(19, .semibold, relativeTo: .headline)
                .lineLimit(1)
                .accessibilityAddTraits(.isHeader)
                .contentTransition(.numericText())
            Button { page(by: 1) } label: { chevron("chevron.right") }
                .disabled(month >= Self.lastMonth)
                .opacity(month >= Self.lastMonth ? 0.35 : 1)
                .accessibilityLabel("Next month")
            if month != Self.firstMonth {
                Button("Today") { go(to: Self.firstMonth) }
                    .bodyFont(12, .semibold, relativeTo: .caption)
                    .foregroundStyle(theme.accentInk)
                    .padding(.horizontal, 10)
                    .frame(height: 28)
                    .background(Capsule().fill(theme.accentSoft))
                    .frame(minHeight: 44)
                    .contentShape(Rectangle())
                    .buttonStyle(.plain)
                    .transition(.opacity.combined(with: .scale(scale: 0.9)))
                    .accessibilityHint("Goes back to this month")
            }
            Spacer(minLength: 0)
        }
    }

    private var monthTitle: String {
        month.startDate().formatted(.dateTime.month(.wide).year())
    }

    private func chevron(_ name: String) -> some View {
        Image(systemName: name)
            .font(.system(size: 13, weight: .bold))
            .foregroundStyle(theme.muted)
            .frame(width: 30, height: 44)
            .contentShape(Rectangle())
    }

    private var grid: some View {
        let first = month.startOfWeek()
        let last = month.lastOfMonth()
        let weeks = (first.through(last).count + 6) / 7
        return VStack(spacing: 4) {
            ForEach(0..<weeks, id: \.self) { week in
                HStack(spacing: 4) {
                    ForEach(0..<7, id: \.self) { column in
                        let day = first.adding(days: week * 7 + column)
                        if day.month == month.month && day.year == month.year {
                            HeatCell(day: day, summary: model.summaries[day]) {
                                if Date.now.timeIntervalSince(lastSwipeAt) > 0.4 { onOpen(day) }
                            }
                        } else {
                            Color.clear.frame(maxWidth: .infinity, minHeight: 46, maxHeight: 46).accessibilityHidden(true)
                        }
                    }
                }
            }
        }
    }

    private var legend: some View {
        HStack(spacing: 6) {
            Text("Less free")
            ForEach(0..<4, id: \.self) { level in
                HeatFill(level: level, isPast: false).frame(width: 14, height: 14)
            }
            Text("More free")
            Spacer(minLength: 0)
        }
        .bodyFont(11, relativeTo: .caption2)
        .foregroundStyle(theme.muted)
        .padding(.top, 4)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Darker days have more free time")
    }

    private func page(by delta: Int) {
        go(to: month.addingMonths(delta))
    }

    private func go(to target: CalendarDay) {
        let target = min(max(target, Self.firstMonth), Self.lastMonth)
        guard target != month else { return }
        pageEdge = target > month ? .trailing : .leading
        withAnimation(.spring(response: 0.32, dampingFraction: 0.9)) { month = target }
    }
}

/// One day of the month, filled by how free it is, with today's date on a pill and a dot when
/// you've blocked time yourself.
private struct HeatCell: View {
    let day: CalendarDay
    let summary: DaySummary?
    let action: () -> Void
    @Environment(\.theme) private var theme

    private var isPast: Bool { day < .today() }
    private var level: Int { isPast ? 0 : summary?.freeLevel ?? 0 }

    var body: some View {
        let isToday = day == .today()
        let onAccent = level >= 2
        Button(action: action) {
            // The number alone is centred; the dot hangs just below it.
            ZStack {
                Text("\(day.day)")
                    .font(AppFont.body(15, .semibold, relativeTo: .body))
                    .monospacedDigit()
                    // Today's number sits on a pill: accent text on it when the cell is accent, else on-accent.
                    .foregroundStyle(isToday ? (onAccent ? theme.accent : theme.onAccent) : onAccent ? theme.onAccent : level == 0 ? theme.muted : theme.ink)
                    .todayMark(day, onAccent: onAccent)
                Circle()
                    .fill(onAccent ? theme.onAccent : theme.accent)
                    .frame(width: 4, height: 4)
                    .offset(y: 14)
                    .opacity(!isPast && summary?.hasOwnBlock == true ? 1 : 0)
            }
            .frame(maxWidth: .infinity, minHeight: 46, maxHeight: 46)
            .background(HeatFill(level: level, isPast: isPast))
            .opacity(isPast ? 0.4 : 1)
            .contentShape(Rectangle())
        }
        .buttonStyle(PressableButtonStyle(scale: 0.95))
        .frame(maxWidth: .infinity)
        .disabled(isPast)
        .accessibilityLabel(label(isToday: isToday))
        .accessibilityHint(isPast ? "" : "Opens the week")
    }

    private func label(isToday: Bool) -> String {
        let base = (isToday ? "Today, " : "") + Formatting.longDay(day.startDate())
        if isPast { return "\(base), in the past" }
        guard let summary else { return base }
        return "\(base), \(summary.text.lowercased())" + (summary.names.isEmpty ? "" : ", \(Formatting.list(summary.names))")
    }
}

/// The heat map's fill for a level: 0 plain, 1 a hint of accent, 2 half, 3 solid accent.
private struct HeatFill: View {
    let level: Int
    let isPast: Bool
    @Environment(\.theme) private var theme

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 11, style: .continuous)
        switch level {
        case 3: shape.fill(theme.accent)
        case 2: shape.fill(theme.accent.opacity(0.55))
        case 1: shape.fill(theme.accent.opacity(0.2))
        default: shape.fill(theme.surface).overlay(shape.strokeBorder(theme.glassBorder, lineWidth: 1))
        }
    }
}
