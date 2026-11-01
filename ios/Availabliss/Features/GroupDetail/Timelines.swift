import SwiftUI

/// Maps times within one day onto a 0…1 axis between two hours.
struct TimeAxis {
    var startHour: Int = 8
    var endHour: Int = 24

    var hours: Double { Double(endHour - startHour) }
    var labels: [Int] { stride(from: startHour, through: endHour, by: 4).map { $0 } }

    func fraction(_ date: Date, on day: CalendarDay) -> Double {
        let seconds = date.timeIntervalSince(day.startDate())
        return min(1, max(0, (seconds / 3600 - Double(startHour)) / hours))
    }

    /// Widens the default 08–24 axis to include the requirement's window.
    static func fitting(_ window: TimeSpan?, on day: CalendarDay) -> TimeAxis {
        guard let window else { return TimeAxis() }
        let startHour = Int(window.start.timeIntervalSince(day.startDate()) / 3600)
        return TimeAxis(startHour: max(0, min(8, startHour - startHour % 4)), endHour: 24)
    }
}

// MARK: - Week

/// Week view: a time grid per visible day with "everyone free" in accent and busy runs faded.
struct WeekGrid: View {
    let model: GroupAvailabilityViewModel
    let group: Group
    private let axis = TimeAxis()
    @Environment(\.theme) private var theme

    var body: some View {
        VStack(spacing: 8) {
            HStack(spacing: 6) {
                Color.clear.frame(width: 28, height: 1)
                ForEach(model.weekDays, id: \.self) { day in
                    Text(model.weekDays.count > 4 ? "\(Weekday.short(day.weekday()).prefix(1)) \(day.day)" : "\(Weekday.short(day.weekday())) \(day.day)")
                        .frame(maxWidth: .infinity)
                }
            }
            .bodyFont(11, .semibold, relativeTo: .caption2)
            .foregroundStyle(theme.muted)

            HStack(spacing: 6) {
                hourLabels
                ForEach(model.weekDays, id: \.self) { day in
                    WeekColumn(day: day, detail: model.dayDetails[day], axis: axis, group: group)
                        .onTapGesture { Task { await model.showDay(day) } }
                        .accessibilityAddTraits(.isButton)
                }
            }
            .frame(height: 320)
        }
        .padding(12)
        .glass(cornerRadius: 22)
    }

    private var hourLabels: some View {
        GeometryReader { proxy in
            ForEach(axis.labels, id: \.self) { hour in
                Text(String(format: "%02d", hour))
                    .bodyFont(10, relativeTo: .caption2)
                    .foregroundStyle(theme.muted)
                    .position(x: 10, y: proxy.size.height * Double(hour - axis.startHour) / axis.hours)
            }
        }
        .frame(width: 28)
        .accessibilityHidden(true)
    }
}

private struct WeekColumn: View {
    let day: CalendarDay
    let detail: DayAvailability?
    let axis: TimeAxis
    let group: Group
    @Environment(\.theme) private var theme

    var body: some View {
        GeometryReader { proxy in
            ZStack(alignment: .top) {
                RoundedRectangle(cornerRadius: 12).fill(theme.line)
                if let detail {
                    ForEach(detail.everyoneFree, id: \.self) { span in
                        block(start: span.start, end: span.end, height: proxy.size.height, label: freeLabel(span), free: true)
                    }
                    ForEach(BusyMerge.segments(detail.members), id: \.self) { segment in
                        block(start: segment.start, end: segment.end, height: proxy.size.height, label: names(segment.userIds), free: false)
                    }
                } else {
                    ProgressView().frame(maxHeight: .infinity)
                }
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(accessibilityText)
    }

    private func block(start: Date, end: Date, height: CGFloat, label: String, free: Bool) -> some View {
        let top = axis.fraction(start, on: day) * height
        let bottom = axis.fraction(end, on: day) * height
        return Text(bottom - top > 24 ? label : "")
            .bodyFont(10, .semibold, relativeTo: .caption2)
            .lineSpacing(1)
            .foregroundStyle(free ? theme.onAccent : theme.ink)
            .padding(6)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .background {
                if free {
                    RoundedRectangle(cornerRadius: 10).fill(theme.accent)
                } else {
                    RoundedRectangle(cornerRadius: 10).fill(theme.busyBg)
                        .overlay(RoundedRectangle(cornerRadius: 10).strokeBorder(theme.busyLine, lineWidth: 1))
                        .opacity(0.5)
                }
            }
            .frame(height: max(0, bottom - top))
            .padding(.horizontal, 3)
            .offset(y: top)
    }

    private func freeLabel(_ span: TimeSpan) -> String {
        let fullDay = axis.fraction(span.start, on: day) == 0 && axis.fraction(span.end, on: day) == 1
        return fullDay ? "Everyone free all day" : "Free from \(Formatting.time(span.start))"
    }

    private func names(_ ids: [String]) -> String {
        ids.map { group.member($0)?.firstName ?? "Someone" }.joined(separator: ", ")
    }

    private var accessibilityText: String {
        let base = Formatting.longDay(day.startDate())
        guard let detail else { return "\(base), loading" }
        if detail.everyoneFree.isEmpty { return "\(base), no time when everyone is free" }
        let spans = detail.everyoneFree.map { "\(Formatting.time($0.start)) to \(Formatting.time($0.end))" }
        return "\(base), everyone free \(Formatting.list(spans))"
    }
}

// MARK: - Day

/// Day view: an "Everyone" row, then each member's busy blocks (striped = blocked by hand,
/// dashed = not synced yet).
struct DayTimeline: View {
    let detail: DayAvailability?
    let date: CalendarDay
    let group: Group
    let me: String?
    let propose: (ProposalDraft) -> Void
    @Environment(\.theme) private var theme

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .firstTextBaseline) {
                Text(Formatting.shortDay(date)).bodyFont(14, .semibold)
                Spacer()
                if let window = detail?.window {
                    Text("Timing starts \(Formatting.time(window.start))").bodyFont(12, relativeTo: .caption).foregroundStyle(theme.muted)
                }
            }
            if let detail {
                rows(detail, axis: TimeAxis.fitting(detail.window, on: date))
                if let first = detail.everyoneFree.first {
                    Button("Propose \(Formatting.time(first.start)) – \(Formatting.time(first.end))") {
                        propose(ProposalDraft(start: first.start, end: first.end, allDay: false))
                    }
                    .buttonStyle(.filled(.accent, height: 44, cornerRadius: 14, fontSize: 14))
                }
            } else {
                ProgressView().frame(maxWidth: .infinity, minHeight: 120)
            }
            HStack(spacing: 6) {
                ZStack { theme.busyBg; Stripes(color: theme.line3, lineWidth: 2, spacing: 3) }
                    .frame(width: 14, height: 10)
                    .clipShape(RoundedRectangle(cornerRadius: 3))
                Text("Striped = blocked by hand")
            }
            .bodyFont(11, relativeTo: .caption2)
            .foregroundStyle(theme.muted)
        }
        .padding(14)
        .glass(cornerRadius: 22)
    }

    private func rows(_ detail: DayAvailability, axis: TimeAxis) -> some View {
        Grid(alignment: .leading, horizontalSpacing: 8, verticalSpacing: 8) {
            GridRow {
                Text("Everyone").bodyFont(12, .bold, relativeTo: .caption).foregroundStyle(theme.accentInk)
                Track(height: 30, radius: 9, background: AnyShapeStyle(theme.line)) { width in
                    ForEach(detail.everyoneFree, id: \.self) { span in
                        let left = axis.fraction(span.start, on: date) * width
                        let right = axis.fraction(span.end, on: date) * width
                        Text(span.end >= date.adding(days: 1).startDate() ? "\(Formatting.time(span.start)) – late" : "\(Formatting.time(span.start)) – \(Formatting.time(span.end))")
                            .bodyFont(11, .semibold, relativeTo: .caption2)
                            .foregroundStyle(theme.onAccent)
                            .lineLimit(1)
                            .padding(.leading, 8)
                            .frame(width: max(0, right - left), height: 30, alignment: .leading)
                            .background(RoundedRectangle(cornerRadius: 9).fill(theme.accent))
                            .offset(x: left)
                    }
                }
                .accessibilityLabel("Everyone free: " + Formatting.list(detail.everyoneFree.map { "\(Formatting.time($0.start)) to \(Formatting.time($0.end))" }))
            }
            ForEach(detail.members) { member in
                GridRow {
                    Text(name(member.userId)).bodyFont(12, .medium, relativeTo: .caption).lineLimit(1).frame(width: 64, alignment: .leading)
                    MemberTrack(member: member, axis: axis, date: date)
                }
            }
            GridRow {
                Color.clear.frame(width: 64, height: 1)
                HStack {
                    ForEach(axis.labels, id: \.self) { hour in
                        Text(String(format: "%02d", hour))
                        if hour != axis.labels.last { Spacer() }
                    }
                }
                .bodyFont(10, relativeTo: .caption2)
                .foregroundStyle(theme.muted)
                .accessibilityHidden(true)
            }
        }
    }

    private func name(_ userId: String) -> String {
        let first = group.member(userId)?.firstName ?? "Someone"
        return userId == me ? "\(first) (you)" : first
    }
}

private struct MemberTrack: View {
    let member: DayAvailability.MemberDay
    let axis: TimeAxis
    let date: CalendarDay
    @Environment(\.theme) private var theme

    var body: some View {
        Track(height: 22, radius: 7, background: member.pending ? AnyShapeStyle(Color.clear) : AnyShapeStyle(theme.accentSoft)) { width in
            if member.pending {
                Text("Not synced yet")
                    .bodyFont(10, relativeTo: .caption2)
                    .foregroundStyle(theme.muted)
                    .padding(.leading, 8)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
            }
            ForEach(member.busy, id: \.self) { busy in
                let left = axis.fraction(busy.start, on: date) * width
                let right = axis.fraction(busy.end, on: date) * width
                ZStack {
                    RoundedRectangle(cornerRadius: 7).fill(theme.busyBg)
                    if busy.manual { Stripes(color: theme.line3, lineWidth: 2, spacing: 3).clipShape(RoundedRectangle(cornerRadius: 7)) }
                    RoundedRectangle(cornerRadius: 7).strokeBorder(theme.busyLine, lineWidth: 1)
                }
                .opacity(0.5)
                .frame(width: max(0, right - left), height: 22)
                .offset(x: left)
            }
        }
        .overlay {
            if member.pending {
                RoundedRectangle(cornerRadius: 7).strokeBorder(theme.line3, style: StrokeStyle(lineWidth: 1, dash: [4, 3]))
            }
        }
        .accessibilityLabel(accessibilityText)
    }

    private var accessibilityText: String {
        if member.pending { return "Not synced yet" }
        if member.busy.isEmpty { return "Free all day" }
        return "Busy " + Formatting.list(member.busy.map { "\(Formatting.time($0.start)) to \(Formatting.time($0.end))\($0.manual ? ", blocked by hand" : "")" })
    }
}

/// A horizontal track whose content is laid out by x offset in points.
private struct Track<Content: View>: View {
    let height: CGFloat
    let radius: CGFloat
    let background: AnyShapeStyle
    @ViewBuilder let content: (CGFloat) -> Content

    var body: some View {
        GeometryReader { proxy in
            ZStack(alignment: .leading) {
                RoundedRectangle(cornerRadius: radius).fill(background)
                content(proxy.size.width)
            }
        }
        .frame(height: height)
        .accessibilityElement(children: .ignore)
    }
}
