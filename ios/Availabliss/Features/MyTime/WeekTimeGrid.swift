import SwiftUI

/// Week view of your busy time: days across the top, hours down the side, every
/// interval at its exact times. Tap empty time to block that hour; tap busy time to open it.
struct WeekTimeGrid: View {
    let layout: WeekLayout
    /// Bumped when a new week's intervals land, to re-run the initial scroll.
    let scrollKey: CalendarDay?
    /// A tap on empty time: the day and the hour it fell in (minutes since midnight).
    let onSelect: (CalendarDay, Int, Int) -> Void
    /// A tap on busy time (a box or an all-day chip): your own block, or an interval from a calendar.
    let onOpen: (BusyInterval) -> Void

    static let hourHeight: CGFloat = 44
    static let gutterWidth: CGFloat = 38
    static let visibleHeight: CGFloat = 440
    private static let bodyPadding: CGFloat = 8
    private var bodyHeight: CGFloat { Self.hourHeight * 24 }

    @Environment(\.theme) private var theme

    var body: some View {
        VStack(spacing: 0) {
            header
                .padding(.bottom, 8)
            theme.line.frame(height: 1)
            if layout.hasAllDay {
                allDayRow
                    .padding(.vertical, 6)
                theme.line.frame(height: 1)
            }
            ScrollViewReader { proxy in
                ScrollView(.vertical) {
                    timeBody
                        .padding(.vertical, Self.bodyPadding)
                        .background(alignment: .top) { scrollAnchors }
                }
                .scrollIndicators(.hidden)
                .frame(height: Self.visibleHeight)
                .task(id: scrollKey) {
                    guard scrollKey != nil else { return }
                    proxy.scrollTo(layout.initialScrollMinutes / 60, anchor: .top)
                }
            }
        }
    }

    // MARK: Header

    private var header: some View {
        HStack(spacing: 0) {
            Color.clear.frame(width: Self.gutterWidth, height: 1)
            ForEach(layout.days, id: \.self) { day in
                let isToday = day == .today()
                VStack(spacing: 2) {
                    Text(Weekday.short(day.weekday()))
                        .bodyFont(11, .semibold, relativeTo: .caption2)
                        .foregroundStyle(isToday ? theme.accentInk : theme.muted)
                    Text("\(day.day)")
                        .displayFont(17, .semibold, relativeTo: .headline)
                        .monospacedDigit()
                        .foregroundStyle(isToday ? theme.onAccent : theme.ink)
                        .frame(width: 30, height: 30)
                        .background { if isToday { Circle().fill(theme.accent) } }
                }
                .frame(maxWidth: .infinity)
                .accessibilityElement(children: .combine)
                .accessibilityLabel("\(Weekday.full(day.weekday())) \(day.day)\(isToday ? ", today" : "")")
            }
        }
    }

    private var allDayRow: some View {
        HStack(alignment: .top, spacing: 0) {
            Text("All day")
                .bodyFont(10, .semibold, relativeTo: .caption2)
                .foregroundStyle(theme.muted)
                .frame(width: Self.gutterWidth, alignment: .leading)
                .frame(maxHeight: .infinity)
            ForEach(layout.days, id: \.self) { day in
                VStack(spacing: 3) {
                    ForEach(Array((layout.allDay[day] ?? []).enumerated()), id: \.offset) { _, interval in
                        AllDayChip(interval: interval) { onOpen(interval) }
                    }
                }
                .padding(.horizontal, 2)
                .frame(maxWidth: .infinity, alignment: .top)
            }
        }
    }

    // MARK: Body

    private var timeBody: some View {
        HStack(spacing: 0) {
            hourGutter
            ForEach(layout.days, id: \.self) { day in
                DayColumn(
                    day: day,
                    pieces: layout.timed[day] ?? [],
                    height: bodyHeight,
                    onTapEmpty: { minutes in
                        let range = WeekLayout.tapRange(at: minutes)
                        onSelect(day, range.from, range.to)
                    },
                    onOpen: onOpen
                )
            }
        }
        .frame(height: bodyHeight)
    }

    /// One scroll target per hour, so scrolling to an hour leaves its line just below the top padding.
    private var scrollAnchors: some View {
        VStack(spacing: 0) {
            ForEach(0..<24, id: \.self) { hour in
                Color.clear.frame(height: Self.hourHeight).id(hour)
            }
        }
        .accessibilityHidden(true)
    }

    private var hourGutter: some View {
        ZStack(alignment: .topTrailing) {
            ForEach(1..<24, id: \.self) { hour in
                Text(String(format: "%02d:00", hour))
                    .bodyFont(10, relativeTo: .caption2)
                    .monospacedDigit()
                    .foregroundStyle(theme.muted)
                    .padding(.trailing, 6)
                    .frame(height: 14)
                    .offset(y: CGFloat(hour) * Self.hourHeight - 7)
            }
        }
        .frame(width: Self.gutterWidth, height: bodyHeight, alignment: .topTrailing)
        .accessibilityHidden(true)
    }
}

/// One day's column: hour lines, busy boxes, and (today) the current-time line.
private struct DayColumn: View {
    let day: CalendarDay
    let pieces: [WeekLayout.Placed]
    let height: CGFloat
    let onTapEmpty: (Double) -> Void
    let onOpen: (BusyInterval) -> Void
    @Environment(\.theme) private var theme

    private var isToday: Bool { day == .today() }
    private func y(_ minutes: Double) -> CGFloat { CGFloat(minutes / Double(WeekLayout.dayMinutes)) * height }

    var body: some View {
        GeometryReader { proxy in
            let width = proxy.size.width
            ZStack(alignment: .topLeading) {
                gridLines
                    .contentShape(Rectangle())
                    .onTapGesture(coordinateSpace: .local) { point in
                        onTapEmpty(Double(point.y / height) * Double(WeekLayout.dayMinutes))
                    }
                ForEach(pieces) { piece in
                    let laneWidth = width / CGFloat(piece.lanes)
                    BusyBox(piece: piece, height: y(piece.to) - y(piece.from))
                        .frame(width: max(0, laneWidth - 4))
                        .contentShape(Rectangle())
                        // Boxes take their own taps so they never fall through to "block this hour".
                        .onTapGesture { onOpen(piece.interval) }
                        .offset(x: CGFloat(piece.lane) * laneWidth + 2, y: y(piece.from))
                }
                if isToday { nowLine }
            }
        }
        .frame(height: height)
        .frame(maxWidth: .infinity)
        .accessibilityElement(children: .contain)
        .accessibilityLabel(Formatting.longDay(day.startDate()))
    }

    private var gridLines: some View {
        Canvas { context, size in
            let hour = size.height / 24
            var hours = Path()
            var halves = Path()
            for h in 0..<24 {
                let top = CGFloat(h) * hour
                hours.addRect(CGRect(x: 0, y: top, width: size.width, height: 1))
                halves.addRect(CGRect(x: 0, y: top + hour / 2, width: size.width, height: 1))
            }
            context.fill(hours, with: .color(theme.line))
            context.fill(halves, with: .color(theme.line.opacity(0.5)))
            context.fill(Path(CGRect(x: 0, y: 0, width: 1, height: size.height)), with: .color(theme.line))
        }
        .background(isToday ? theme.accentSoft.opacity(0.35) : .clear)
    }

    private var nowLine: some View {
        TimelineView(.everyMinute) { context in
            let minutes = WeekLayout.minutesIntoDay(context.date, on: day)
            if minutes >= 0, minutes <= Double(WeekLayout.dayMinutes) {
                ZStack(alignment: .leading) {
                    Rectangle().fill(theme.warn).frame(height: 2)
                    Circle().fill(theme.warn).frame(width: 8, height: 8).offset(x: -4)
                }
                .frame(height: 8)
                .offset(y: y(minutes) - 4)
                .allowsHitTesting(false)
                .accessibilityHidden(true)
            }
        }
    }
}

/// A busy interval at its exact times: striped for blocks you made, faded for calendar busy.
private struct BusyBox: View {
    let piece: WeekLayout.Placed
    let height: CGFloat
    @Environment(\.theme) private var theme

    private var isManual: Bool { piece.interval.source == .manual }

    var body: some View {
        let shape = UnevenRoundedRectangle(
            topLeadingRadius: piece.continuesBefore ? 2 : 8,
            bottomLeadingRadius: piece.continuesAfter ? 2 : 8,
            bottomTrailingRadius: piece.continuesAfter ? 2 : 8,
            topTrailingRadius: piece.continuesBefore ? 2 : 8,
            style: .continuous
        )
        VStack(alignment: .leading, spacing: 1) {
            // Narrow lanes clip the label rather than collapsing it to "…".
            Text(piece.interval.label ?? "Busy")
                .bodyFont(11, .semibold, relativeTo: .caption2)
                .fixedSize()
            if piece.minutes >= 45 {
                let from = WeekLayout.clock(piece.from), to = WeekLayout.clock(piece.to)
                ViewThatFits(in: .horizontal) {
                    Text("\(from) – \(to)").fixedSize()
                    if piece.minutes >= 75 {
                        Text("\(from) –\n\(to)").fixedSize()
                    }
                    Text(from).fixedSize()
                }
                .bodyFont(10, relativeTo: .caption2)
                .monospacedDigit()
                .opacity(0.8)
            }
        }
        .lineLimit(nil)
        .foregroundStyle(isManual ? theme.accentInk : theme.ink)
        .padding(.vertical, 3)
        .padding(.leading, isManual ? 5 : 6)
        .padding(.trailing, 4)
        // minWidth 0: take the lane's width even when the (fixed-size) text is wider.
        .frame(minWidth: 0, maxWidth: .infinity, alignment: .topLeading)
        .frame(height: max(14, height), alignment: .topLeading)
        .clipped()
        .background { BusyFill(isManual: isManual, shape: shape) }
        .clipShape(shape)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(accessibilityText)
        .accessibilityAddTraits(.isButton)
    }

    private var accessibilityText: String {
        let label = piece.interval.label
        let what = isManual ? "Blocked by you\(label.map { ": \($0)" } ?? "")" : "Busy from \(label ?? "a calendar")"
        let edit = isManual ? "Edit: " : ""
        return "\(edit)\(what), \(Formatting.longDay(piece.day.startDate())) \(WeekLayout.clock(piece.from)) to \(WeekLayout.clock(piece.to))"
    }
}

/// An all-day interval in the row under the header.
private struct AllDayChip: View {
    let interval: BusyInterval
    let onTap: () -> Void
    @Environment(\.theme) private var theme

    private var isManual: Bool { interval.source == .manual }

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 6, style: .continuous)
        Text(interval.label ?? "Busy")
            .bodyFont(10, .semibold, relativeTo: .caption2)
            .lineLimit(1)
            .foregroundStyle(isManual ? theme.accentInk : theme.ink)
            .padding(.vertical, 3)
            .padding(.leading, isManual ? 5 : 6)
            .padding(.trailing, 4)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background { BusyFill(isManual: isManual, shape: shape) }
            .clipShape(shape)
            .contentShape(shape)
            .onTapGesture(perform: onTap)
            .accessibilityLabel(isManual ? "Edit: blocked by you all day, \(interval.label ?? "Busy")" : "Busy all day from \(interval.label ?? "a calendar")")
            .accessibilityAddTraits(.isButton)
    }
}

/// Manual: accent-soft stripes over surface with an accent border.
/// Calendar: busy-bg with a busy-line border and a thicker leading edge.
private struct BusyFill<S: InsettableShape>: View {
    let isManual: Bool
    let shape: S
    @Environment(\.theme) private var theme

    var body: some View {
        if isManual {
            ZStack {
                shape.fill(theme.surface)
                Stripes(color: theme.accentSoft, lineWidth: 6, spacing: 6).clipShape(shape)
                shape.strokeBorder(theme.accent, lineWidth: 1.5)
            }
        } else {
            ZStack(alignment: .leading) {
                shape.fill(theme.busyBg)
                shape.strokeBorder(theme.busyLine, lineWidth: 1)
                Rectangle().fill(theme.busyLine).frame(width: 3)
            }
            .clipShape(shape)
        }
    }
}
