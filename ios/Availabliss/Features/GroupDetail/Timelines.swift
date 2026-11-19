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
}

// MARK: - Week

/// How far a week day that doesn't work for everyone fades.
enum WeekFade {
    static let opacity = 0.5
}

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
                    HStack(spacing: 2) {
                        Text(model.weekDays.count > 4 ? "\(Weekday.short(day.weekday()).prefix(1)) \(day.day)" : "\(Weekday.short(day.weekday())) \(day.day)")
                            .todayMark(day)
                        if model.day(day)?.isBankHoliday == true { BankHolidayFlag(size: 8) }
                    }
                    .frame(maxWidth: .infinity)
                    .opacity(model.works(day) || day == .today() ? 1 : WeekFade.opacity)
                }
            }
            .bodyFont(11, .semibold, relativeTo: .caption2)
            .foregroundStyle(theme.muted)

            HStack(spacing: 6) {
                hourLabels
                ForEach(model.weekDays, id: \.self) { day in
                    let selected = day == model.selection
                    WeekColumn(day: day, detail: model.dayDetails[day], axis: axis, group: group)
                        // Days that don't work for everyone step back, like the month's plain cells.
                        .opacity(model.works(day) ? 1 : WeekFade.opacity)
                        .overlay {
                            if selected { RoundedRectangle(cornerRadius: 12).strokeBorder(theme.ink, lineWidth: 2) }
                        }
                        .contentShape(Rectangle())
                        .onTapGesture { withAnimation(.spring(response: 0.3)) { model.pick(day) } }
                        .accessibilityAddTraits(selected ? [.isButton, .isSelected] : .isButton)
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
                    // Most of us free: lighter, under the busy runs and the time everyone's free.
                    ForEach(detail.mostFreeSpans, id: \.self) { span in
                        mostFreeBlock(start: span.start, end: span.end, height: proxy.size.height)
                    }
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

    /// A soft accent run with an accent outline, for when most of the group is free.
    private func mostFreeBlock(start: Date, end: Date, height: CGFloat) -> some View {
        let top = axis.fraction(start, on: day) * height
        let bottom = axis.fraction(end, on: day) * height
        return RoundedRectangle(cornerRadius: 10).fill(theme.accentSoft)
            .overlay(RoundedRectangle(cornerRadius: 10).strokeBorder(theme.accent, lineWidth: 1))
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
        if detail.everyoneFree.isEmpty {
            guard !detail.mostFreeSpans.isEmpty else { return "\(base), no time when everyone is free" }
            let spans = detail.mostFreeSpans.map { "\(Formatting.time($0.start)) to \(Formatting.time($0.end))" }
            return "\(base), most of the group free \(Formatting.list(spans))"
        }
        let spans = detail.everyoneFree.map { "\(Formatting.time($0.start)) to \(Formatting.time($0.end))" }
        return "\(base), everyone free \(Formatting.list(spans))"
    }
}

/// A small flag beside a date that's a bank holiday (its name is in the day's label and detail).
struct BankHolidayFlag: View {
    var size: CGFloat = 9

    var body: some View {
        Image(systemName: "flag.fill")
            .font(.system(size: size, weight: .bold))
            .opacity(0.8)
            .accessibilityLabel("Bank holiday")
    }
}

// MARK: - Week cards

/// Week view in portrait: a card per day saying when everyone's free, with a 24-hour strip
/// (everyone free in accent, someone busy in grey). Tap a card for the day's detail below.
struct WeekCards: View {
    let model: GroupAvailabilityViewModel
    let group: Group
    @Environment(\.theme) private var theme

    var body: some View {
        VStack(spacing: 8) {
            ForEach(model.weekDays, id: \.self) { day in
                WeekDayCard(
                    day: day, detail: model.dayDetails[day], status: model.day(day)?.status, works: model.works(day), group: group, isSelected: day == model.selection,
                    bankHoliday: model.day(day)?.isBankHoliday == true
                )
                .onTapGesture { withAnimation(.spring(response: 0.3)) { model.pick(day) } }
                .accessibilityAddTraits(day == model.selection ? [.isButton, .isSelected] : .isButton)
            }
        }
    }
}

private struct WeekDayCard: View {
    let day: CalendarDay
    let detail: DayAvailability?
    let status: AvailabilityDay.Status?
    /// Works for everyone (or not known yet): days that don't are faded.
    let works: Bool
    let group: Group
    let isSelected: Bool
    var bankHoliday = false
    private let axis = TimeAxis(startHour: 0, endHour: 24)
    @Environment(\.theme) private var theme

    private var isPast: Bool { day < .today() }

    var body: some View {
        HStack(spacing: 12) {
            VStack(spacing: 0) {
                HStack(spacing: 2) {
                    Text(Weekday.short(day.weekday())).bodyFont(11, .semibold, relativeTo: .caption2).foregroundStyle(theme.muted)
                    if bankHoliday { BankHolidayFlag(size: 8).foregroundStyle(theme.muted) }
                }
                Text("\(day.day)").displayFont(20, .bold).monospacedDigit()
                    .todayMark(day)
                    .padding(.top, day == .today() ? 3 : 0)
            }
            .frame(width: 40)
            // Today's date stays at full strength, so it's easy to find on a faded day.
            .opacity(works || day == .today() ? 1 : WeekFade.opacity)
            VStack(alignment: .leading, spacing: 6) {
                Text(summary)
                    .bodyFont(14, .semibold)
                    .foregroundStyle(hasFree ? theme.accentInk : theme.ink)
                    .lineLimit(1)
                if let busy = busyLine {
                    Text(busy).bodyFont(12, relativeTo: .caption).foregroundStyle(theme.muted).lineLimit(1)
                }
                strip
            }
            .opacity(works ? 1 : WeekFade.opacity)
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .glass(cornerRadius: 18, shadow: false)
        .overlay {
            if isSelected { RoundedRectangle(cornerRadius: 18, style: .continuous).stroke(theme.ink, lineWidth: 2) }
        }
        .contentShape(Rectangle())
        .accessibilityElement(children: .combine)
    }

    private var hasFree: Bool { (!(detail?.everyoneFree.isEmpty ?? true) || !(detail?.mostFreeSpans.isEmpty ?? true)) && !isPast }

    private var summary: String {
        if isPast { return "Past" }
        guard let detail else { return status == .hidden ? "Outside the timing" : "Loading\u{2026}" }
        guard let first = detail.everyoneFree.first else {
            if let most = detail.mostFreeSpans.first {
                let end = most.end >= day.adding(days: 1).startDate() ? "late" : Formatting.time(most.end)
                let more = detail.mostFreeSpans.count > 1 ? " +\(detail.mostFreeSpans.count - 1) more" : ""
                return "Most of us free \(Formatting.time(most.start)) – \(end)\(more)"
            }
            return status == .hidden ? "Outside the timing" : "No time when everyone\u{2019}s free"
        }
        let wholeDay = axis.fraction(first.start, on: day) == 0 && axis.fraction(first.end, on: day) == 1
        if wholeDay { return "Everyone free all day" }
        let end = first.end >= day.adding(days: 1).startDate() ? "late" : Formatting.time(first.end)
        let more = detail.everyoneFree.count > 1 ? " +\(detail.everyoneFree.count - 1) more" : ""
        return "Everyone free \(Formatting.time(first.start)) – \(end)\(more)"
    }

    private var busyLine: String? {
        guard let detail, !isPast else { return nil }
        let names = detail.members.filter { !$0.busy.isEmpty }.map { group.member($0.userId)?.firstName ?? "Someone" }
        guard !names.isEmpty else { return nil }
        return "\(Formatting.list(names)) busy some of the day"
    }

    /// The day's 24 hours: someone busy in grey, most of us free in soft accent, everyone free in accent.
    private var strip: some View {
        GeometryReader { proxy in
            let width = proxy.size.width
            ZStack(alignment: .leading) {
                Capsule().fill(theme.line)
                if let detail {
                    ForEach(detail.mostFreeSpans, id: \.self) { span in
                        bar(span.start, span.end, width: width, color: theme.accentSoft, outline: theme.accent)
                    }
                    ForEach(BusyMerge.segments(detail.members), id: \.self) { segment in
                        bar(segment.start, segment.end, width: width, color: theme.busyFill)
                    }
                    ForEach(detail.everyoneFree, id: \.self) { span in
                        bar(span.start, span.end, width: width, color: theme.accent)
                    }
                }
            }
        }
        .frame(height: 8)
        .accessibilityHidden(true)
    }

    private func bar(_ start: Date, _ end: Date, width: CGFloat, color: Color, outline: Color? = nil) -> some View {
        let left = axis.fraction(start, on: day) * width
        let right = axis.fraction(end, on: day) * width
        return Capsule().fill(color)
            .overlay { if let outline { Capsule().strokeBorder(outline, lineWidth: 1) } }
            .frame(width: max(3, right - left)).offset(x: left)
    }
}

// MARK: - Day

/// Who's free on one day: an "Everyone" row, then each member's busy blocks (dashed = not
/// synced yet). Busy is just busy: nothing shows whether it came from a calendar or was marked
/// by hand. Shown under the month / week grid for the selected day.
struct DayTimeline: View {
    let detail: DayAvailability
    let date: CalendarDay
    let group: Group
    let me: String?
    @Environment(\.theme) private var theme

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            // The whole day, so early and late busy time is never cut off.
            rows(detail, axis: TimeAxis(startHour: 0, endHour: 24))
            legend
        }
    }

    private var legend: some View {
        HStack(spacing: 14) {
            LegendItem(label: "Free") { theme.okBg.overlay(RoundedRectangle(cornerRadius: 3).strokeBorder(theme.ok.opacity(0.5), lineWidth: 1)) }
            LegendItem(label: "Busy") { theme.busyFill }
            LegendItem(label: "Everyone free") { theme.accent }
            if !detail.mostFreeSpans.isEmpty {
                LegendItem(label: "Most of us") {
                    theme.accentSoft.overlay(RoundedRectangle(cornerRadius: 3).strokeBorder(theme.accent, lineWidth: 1))
                }
            }
        }
        .bodyFont(11, relativeTo: .caption2)
        .foregroundStyle(theme.muted)
    }

    private func rows(_ detail: DayAvailability, axis: TimeAxis) -> some View {
        Grid(alignment: .leading, horizontalSpacing: 8, verticalSpacing: 8) {
            GridRow {
                Text("Everyone").bodyFont(12, .bold, relativeTo: .caption).foregroundStyle(theme.accentInk)
                Track(height: 30, radius: 9, background: AnyShapeStyle(theme.line)) { width in
                    // Most of us free: lighter, under the time everyone's free (labelled only where it stands alone).
                    ForEach(detail.mostFreeSpans, id: \.self) { span in
                        let left = axis.fraction(span.start, on: date) * width
                        let right = axis.fraction(span.end, on: date) * width
                        Text(detail.mostFreeOnly.contains(span) ? spanText(span) : "")
                            .bodyFont(11, .semibold, relativeTo: .caption2)
                            .foregroundStyle(theme.accentInk)
                            .lineLimit(1)
                            .padding(.leading, 8)
                            .frame(width: max(0, right - left), height: 30, alignment: .leading)
                            .background(RoundedRectangle(cornerRadius: 9).fill(theme.accentSoft))
                            .overlay(RoundedRectangle(cornerRadius: 9).strokeBorder(theme.accent, lineWidth: 1))
                            .offset(x: left)
                    }
                    ForEach(detail.everyoneFree, id: \.self) { span in
                        let left = axis.fraction(span.start, on: date) * width
                        let right = axis.fraction(span.end, on: date) * width
                        Text(
                            span.end >= date.adding(days: 1).startDate()
                                ? "\(Formatting.time(span.start)) – late" : "\(Formatting.time(span.start)) – \(Formatting.time(span.end))"
                        )
                        .bodyFont(11, .semibold, relativeTo: .caption2)
                        .foregroundStyle(theme.onAccent)
                        .lineLimit(1)
                        .padding(.leading, 8)
                        .frame(width: max(0, right - left), height: 30, alignment: .leading)
                        .background(RoundedRectangle(cornerRadius: 9).fill(theme.accent))
                        .offset(x: left)
                    }
                }
                .accessibilityLabel(everyoneAccessibilityText)
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

    private func spanText(_ span: TimeSpan) -> String {
        span.end >= date.adding(days: 1).startDate() ? "\(Formatting.time(span.start)) – late" : "\(Formatting.time(span.start)) – \(Formatting.time(span.end))"
    }

    private var everyoneAccessibilityText: String {
        let spans = { (list: [TimeSpan]) in Formatting.list(list.map { "\(Formatting.time($0.start)) to \(Formatting.time($0.end))" }) }
        var text = "Everyone free: " + spans(detail.everyoneFree)
        if !detail.mostFreeOnly.isEmpty { text += ". Most of the group free: " + spans(detail.mostFreeOnly) }
        return text
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
        Track(height: 22, radius: 7, background: member.pending ? AnyShapeStyle(Color.clear) : AnyShapeStyle(theme.okBg)) { width in
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
                RoundedRectangle(cornerRadius: 7).fill(theme.busyFill)
                    .frame(width: max(2, right - left), height: 22)
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
        return "Busy " + Formatting.list(member.busy.map { "\(Formatting.time($0.start)) to \(Formatting.time($0.end))" })
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

extension DayAvailability {
    /// "Most of us are free" stretches with a quorum or optional members; empty without one.
    /// They include `everyoneFree`, so draw them underneath it.
    var mostFreeSpans: [TimeSpan] { mostFree ?? [] }

    /// Most-free stretches that don't overlap any time everyone's free: the ones worth labelling.
    var mostFreeOnly: [TimeSpan] {
        mostFreeSpans.filter { span in !everyoneFree.contains { $0.start < span.end && $0.end > span.start } }
    }
}

extension Theme {
    /// Solid fill for someone's busy time in the day timeline: clearly darker than the mint free track.
    var busyFill: Color { ink.opacity(0.32) }
}
