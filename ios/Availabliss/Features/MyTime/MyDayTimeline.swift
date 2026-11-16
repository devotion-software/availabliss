import SwiftUI

/// One day of your busy time, hours down the side and every interval at its exact times.
/// Tap empty time to block that hour; tap busy time to open it.
struct MyDayTimeline: View {
    let day: CalendarDay
    let pieces: [DayLayout.Placed]
    /// A tap on empty time: the hour it fell in (minutes since midnight).
    let onSelect: (Int, Int) -> Void
    /// A tap on busy time: your own block, or an interval from a calendar.
    let onOpen: (BusyInterval) -> Void

    static let hourHeight: CGFloat = 40
    static let gutterWidth: CGFloat = 48
    /// Space above 00:00 and below 24:00 so their labels aren't cut off.
    static let verticalPadding: CGFloat = 10
    private var bodyHeight: CGFloat { Self.hourHeight * 24 }

    @Environment(\.theme) private var theme

    var body: some View {
        HStack(spacing: 0) {
            hourGutter
            DayColumn(
                day: day,
                pieces: pieces,
                height: bodyHeight,
                onTapEmpty: { minutes in
                    let range = DayLayout.tapRange(at: minutes)
                    onSelect(range.from, range.to)
                },
                onOpen: onOpen
            )
        }
        .frame(height: bodyHeight)
        .padding(.vertical, Self.verticalPadding)
        .background(alignment: .top) { scrollAnchors }
    }

    /// One scroll target per hour (ids 0–23), so scrolling to an hour leaves its line just below the top padding.
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
            ForEach(0...24, id: \.self) { hour in
                Text(String(format: "%02d:00", hour))
                    .bodyFont(11, relativeTo: .caption2)
                    .monospacedDigit()
                    .foregroundStyle(theme.muted)
                    .padding(.trailing, 8)
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
    let pieces: [DayLayout.Placed]
    let height: CGFloat
    let onTapEmpty: (Double) -> Void
    let onOpen: (BusyInterval) -> Void
    @Environment(\.theme) private var theme

    private var isToday: Bool { day == .today() }
    private func y(_ minutes: Double) -> CGFloat { CGFloat(minutes / Double(DayLayout.dayMinutes)) * height }

    var body: some View {
        GeometryReader { proxy in
            let width = proxy.size.width
            ZStack(alignment: .topLeading) {
                gridLines
                    .contentShape(Rectangle())
                    .onTapGesture(coordinateSpace: .local) { point in
                        onTapEmpty(Double(point.y / height) * Double(DayLayout.dayMinutes))
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
            let minutes = DayLayout.minutesIntoDay(context.date, on: day)
            if minutes >= 0, minutes <= Double(DayLayout.dayMinutes) {
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
    let piece: DayLayout.Placed
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
            Text(piece.interval.name)
                .bodyFont(13, .semibold, relativeTo: .footnote)
                .fixedSize()
            if piece.minutes >= 60 {
                let from = DayLayout.clock(piece.from), to = DayLayout.clock(piece.to)
                ViewThatFits(in: .horizontal) {
                    Text("\(from) – \(to)").fixedSize()
                    if piece.minutes >= 90 {
                        Text("\(from) –\n\(to)").fixedSize()
                    }
                    Text(from).fixedSize()
                }
                .bodyFont(12, relativeTo: .caption)
                .monospacedDigit()
                .opacity(0.8)
            }
        }
        .lineLimit(nil)
        .foregroundStyle(isManual ? theme.accentInk : theme.ink)
        .padding(.vertical, 4)
        .padding(.leading, isManual ? 8 : 9)
        .padding(.trailing, 6)
        // minWidth 0: take the lane's width even when the (fixed-size) text is wider.
        .frame(minWidth: 0, maxWidth: .infinity, alignment: .topLeading)
        .frame(height: max(18, height), alignment: .topLeading)
        .clipped()
        .background { BusyFill(isManual: isManual, isIgnored: piece.interval.isIgnored, shape: shape) }
        .clipShape(shape)
        .opacity(piece.interval.isIgnored ? 0.55 : 1)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(accessibilityText)
        .accessibilityAddTraits(.isButton)
    }

    private var accessibilityText: String {
        let label = piece.interval.label
        let event = piece.interval.title.map { "\($0), " } ?? ""
        let what = isManual ? "Blocked by you\(label.map { ": \($0)" } ?? "")"
            : piece.interval.isIgnored ? "Marked not busy, \(event)from \(label ?? "a calendar")" : "\(event)busy from \(label ?? "a calendar")"
        let edit = isManual ? "Edit: " : ""
        return "\(edit)\(what), \(Formatting.longDay(piece.day.startDate())) \(DayLayout.clock(piece.from)) to \(DayLayout.clock(piece.to))"
    }
}

/// An all-day interval, as a chip above the timeline.
struct AllDayChip: View {
    let interval: BusyInterval
    let onTap: () -> Void
    @Environment(\.theme) private var theme

    private var isManual: Bool { interval.source == .manual }

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 12, style: .continuous)
        Text(interval.name)
            .bodyFont(14, .semibold, relativeTo: .subheadline)
            .lineLimit(1)
            .foregroundStyle(isManual ? theme.accentInk : theme.ink)
            .padding(.vertical, 10)
            .padding(.leading, isManual ? 10 : 12)
            .padding(.trailing, 10)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background { BusyFill(isManual: isManual, isIgnored: interval.isIgnored, shape: shape) }
            .clipShape(shape)
            .opacity(interval.isIgnored ? 0.55 : 1)
            .contentShape(shape)
            .onTapGesture(perform: onTap)
            .accessibilityLabel(isManual ? "Edit: blocked by you all day, \(interval.label ?? "Busy")" : "\(interval.title.map { "\($0), " } ?? "")busy all day from \(interval.label ?? "a calendar")")
            .accessibilityAddTraits(.isButton)
    }
}

/// Manual: accent-soft stripes over surface with an accent border.
/// Calendar: busy-bg with a busy-line border and a thicker leading edge.
/// Marked not busy: just a dashed outline.
private struct BusyFill<S: InsettableShape>: View {
    let isManual: Bool
    var isIgnored = false
    let shape: S
    @Environment(\.theme) private var theme

    var body: some View {
        if isIgnored {
            shape.strokeBorder(theme.busyLine, style: StrokeStyle(lineWidth: 1, dash: [4, 3]))
        } else if isManual {
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
