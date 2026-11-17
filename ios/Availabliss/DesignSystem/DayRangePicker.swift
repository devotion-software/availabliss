import SwiftUI

/// One month calendar for picking a range of days: tap the first day, then the last, and the days
/// between are highlighted. A third tap starts a new range. One day on its own is a range too
/// (first and last the same), so `range` is set from the first tap.
struct DayRangePicker: View {
    @Binding var range: DateRange?
    /// Days before this can't be picked.
    var earliest: CalendarDay?

    @Environment(\.theme) private var theme
    /// The first day tapped, while waiting for the last.
    @State private var anchor: CalendarDay?
    @State private var month: CalendarDay

    init(range: Binding<DateRange?>, earliest: CalendarDay? = nil) {
        _range = range
        self.earliest = earliest
        _month = State(initialValue: (range.wrappedValue?.from ?? earliest.map { max($0, .today()) } ?? .today()).firstOfMonth)
    }

    private static let cellHeight: CGFloat = 40

    var body: some View {
        VStack(spacing: 8) {
            header
            HStack(spacing: 0) {
                ForEach(Weekday.all, id: \.self) { day in
                    Text(Weekday.initials[day])
                        .bodyFont(11, .bold, relativeTo: .caption)
                        .foregroundStyle(theme.muted)
                        .frame(maxWidth: .infinity)
                        .accessibilityHidden(true)
                }
            }
            VStack(spacing: 2) {
                ForEach(0..<6, id: \.self) { row in
                    HStack(spacing: 0) {
                        ForEach(0..<7, id: \.self) { column in
                            cell(weeks[row * 7 + column], column: column)
                        }
                    }
                }
            }
            Text(hint)
                .bodyFont(13, relativeTo: .subheadline)
                .foregroundStyle(theme.muted)
                .frame(maxWidth: .infinity, alignment: .leading)
                .accessibilityAddTraits(.updatesFrequently)
        }
        .animation(.easeOut(duration: 0.15), value: range)
    }

    private var header: some View {
        HStack {
            monthButton("chevron.left", label: "Previous month", enabled: canGoBack) { month = month.addingMonths(-1) }
            Spacer()
            Text(month.startDate().formatted(.dateTime.month(.wide).year()))
                .displayFont(17, .bold, relativeTo: .headline)
                .accessibilityAddTraits(.isHeader)
            Spacer()
            monthButton("chevron.right", label: "Next month", enabled: true) { month = month.addingMonths(1) }
        }
    }

    private func monthButton(_ icon: String, label: String, enabled: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: icon)
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(enabled ? theme.ink : theme.line3)
                .frame(width: 44, height: 44)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .accessibilityLabel(label)
    }

    @ViewBuilder
    private func cell(_ day: CalendarDay?, column: Int) -> some View {
        if let day {
            let isStart = range?.from == day
            let isEnd = range?.to == day
            let isEndpoint = isStart || isEnd
            let inRange = range?.contains(day) ?? false
            let isDisabled = earliest.map { day < $0 } ?? false
            Button { tap(day) } label: {
                Text("\(day.day)")
                    .bodyFont(15, .semibold)
                    .monospacedDigit()
                    .foregroundStyle(isEndpoint ? theme.onAccent : isDisabled ? theme.line3 : inRange ? theme.accentInk : theme.ink)
                    .frame(width: Self.cellHeight, height: Self.cellHeight)
                    .background {
                        if isEndpoint {
                            Circle().fill(theme.accent)
                        } else if day == .today() {
                            Circle().strokeBorder(theme.line3, lineWidth: 1.5)
                        }
                    }
                    .frame(maxWidth: .infinity)
                    .background {
                        if inRange, range?.from != range?.to {
                            band(isStart: isStart, isEnd: isEnd, column: column)
                        }
                    }
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .disabled(isDisabled)
            .accessibilityLabel(Formatting.longDay(day.startDate()))
            .accessibilityValue(isStart && isEnd ? "Selected" : isStart ? "First day" : isEnd ? "Last day" : inRange ? "In range" : "")
            .accessibilityAddTraits(inRange ? .isSelected : [])
        } else {
            Color.clear.frame(maxWidth: .infinity, minHeight: Self.cellHeight, maxHeight: Self.cellHeight)
        }
    }

    /// The soft band joining the days in the range; half-width under the first and last day, rounded at week edges.
    private func band(isStart: Bool, isEnd: Bool, column: Int) -> some View {
        let radius = Self.cellHeight / 2
        let roundLeading = isStart || column == 0
        let roundTrailing = isEnd || column == 6
        return GeometryReader { proxy in
            // The first and last day's half reaches back under their circle.
            UnevenRoundedRectangle(
                topLeadingRadius: roundLeading ? radius : 0,
                bottomLeadingRadius: roundLeading ? radius : 0,
                bottomTrailingRadius: roundTrailing ? radius : 0,
                topTrailingRadius: roundTrailing ? radius : 0,
                style: .continuous
            )
            .fill(theme.accentSoft)
            .frame(width: isStart || isEnd ? proxy.size.width / 2 + radius : proxy.size.width)
            .frame(maxWidth: .infinity, alignment: isStart ? .trailing : isEnd ? .leading : .center)
        }
        .frame(height: Self.cellHeight)
    }

    private var hint: String {
        if anchor != nil { return "Now tap the last day." }
        guard let range else { return "Tap the first day, then the last." }
        return range.from == range.to ? Formatting.longDay(range.from.startDate()) : "\(Formatting.shortDay(range.from)) \u{2013} \(Formatting.shortDay(range.to))"
    }

    private var canGoBack: Bool {
        guard let earliest else { return true }
        return month > earliest.firstOfMonth
    }

    /// The month's days in a Monday-first, six-week grid (nil = outside the month).
    private var weeks: [CalendarDay?] {
        let lead = month.weekday()
        var cells: [CalendarDay?] = Array(repeating: nil, count: lead)
        cells += month.through(month.lastOfMonth()).map { Optional($0) }
        cells += Array(repeating: nil, count: max(0, 42 - cells.count))
        return cells
    }

    private func tap(_ day: CalendarDay) {
        if let start = anchor {
            range = DateRange(from: min(start, day), to: max(start, day))
            anchor = nil
        } else {
            range = DateRange(from: day, to: day)
            anchor = day
        }
    }
}
