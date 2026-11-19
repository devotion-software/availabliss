import Foundation

/// One day of "My time" boiled down for its row in the list: the busy stretches to draw on the
/// 06:00–24:00 strip, a one-line summary ("Free from 18:00"), what's on, and a spoken version.
///
/// Pure value logic (no UI) so it can be unit tested. Time marked not busy doesn't count.
struct DaySummary: Equatable, Sendable {
    /// A busy stretch in minutes since midnight (24h axis); `manual` = blocked by you.
    struct Segment: Equatable, Sendable {
        let from: Double
        let to: Double
        let manual: Bool
    }

    /// The strip and the summary cover the waking day.
    static let windowStart = 6.0 * 60
    static let windowEnd = Double(DayLayout.dayMinutes)
    /// Free gaps shorter than this don't make a day "free".
    static let minimumFree = 30.0

    let day: CalendarDay
    /// Busy stretches as they are (not merged), clamped to the day; calendar busy first so blocks draw on top.
    let segments: [Segment]
    /// Busy across the whole day, merged and sorted.
    let busy: [ClosedRange<Double>]
    let isBusyAllDay: Bool
    /// "Free all day", "Busy all day", "Free from 18:00", "Free until 09:00", "Free 12:00 – 14:00".
    let text: String
    /// Notes on your own blocks that day, in time order, without repeats ("Choir").
    let names: [String]

    init(day: CalendarDay, allDay: [BusyInterval], timed: [DayLayout.Placed]) {
        self.day = day
        let allDay = allDay.filter { !$0.isIgnored }
        let timed = timed.filter { !$0.interval.isIgnored }

        var segments = timed.map { Segment(from: $0.from, to: $0.to, manual: $0.interval.source == .manual) }
        segments += allDay.map { Segment(from: 0, to: Self.windowEnd, manual: $0.source == .manual) }
        segments.sort { ($0.manual ? 1 : 0, $0.from) < ($1.manual ? 1 : 0, $1.from) }
        self.segments = segments

        let busy = Self.merge(segments.map { $0.from...$0.to })
        self.busy = busy

        var names: [String] = []
        // Your blocks' notes and your events' names (never "Busy").
        let manual =
            allDay.filter { !$0.isIgnored }.map { $0.name == "Busy" ? nil : $0.name }
            + timed.sorted { $0.from < $1.from }.filter { !$0.interval.isIgnored }.map { $0.interval.name == "Busy" ? nil : $0.interval.name }
        for case let name? in manual where !name.isEmpty && !names.contains(name) { names.append(name) }
        self.names = names

        let gap = Self.longestFree(busy)
        isBusyAllDay = gap == nil
        text = Self.describe(gap)
    }

    /// Merges overlapping or touching ranges.
    static func merge(_ ranges: [ClosedRange<Double>]) -> [ClosedRange<Double>] {
        var out: [ClosedRange<Double>] = []
        for range in ranges.sorted(by: { $0.lowerBound < $1.lowerBound }) {
            if let last = out.last, range.lowerBound <= last.upperBound {
                out[out.count - 1] = last.lowerBound...max(last.upperBound, range.upperBound)
            } else {
                out.append(range)
            }
        }
        return out
    }

    /// The longest free stretch between 06:00 and 24:00 (the later one on a tie: evenings matter
    /// most for plans), or nil when nothing free is long enough to count.
    static func longestFree(_ busy: [ClosedRange<Double>]) -> ClosedRange<Double>? {
        var best: ClosedRange<Double>?
        var cursor = windowStart
        func consider(_ to: Double) {
            guard to - cursor >= minimumFree else { return }
            if best.map({ to - cursor >= $0.upperBound - $0.lowerBound }) ?? true { best = cursor...to }
        }
        for range in busy where range.upperBound > windowStart {
            if range.lowerBound >= windowEnd { break }
            if range.lowerBound > cursor { consider(range.lowerBound) }
            cursor = max(cursor, range.upperBound)
        }
        if cursor < windowEnd { consider(windowEnd) }
        return best
    }

    /// Free minutes between 06:00 and 24:00.
    var freeMinutes: Double {
        let busyInWindow = busy.reduce(0.0) { total, range in
            total + max(0, min(range.upperBound, Self.windowEnd) - max(range.lowerBound, Self.windowStart))
        }
        return Self.windowEnd - Self.windowStart - busyInWindow
    }

    /// How free the day is, for the month heat map, by time busy between 06:00 and 24:00:
    /// 4 = nothing, 3 = under 3h, 2 = under 8h, 1 = at least half an hour free, 0 = busy all day.
    /// Graded by busy time so an hour's plan already shows against a free day.
    var freeLevel: Int {
        let free = freeMinutes
        let busy = Self.windowEnd - Self.windowStart - free
        if busy <= 0 { return 4 }
        if busy < 3 * 60 { return 3 }
        if busy < 8 * 60 { return 2 }
        if free >= Self.minimumFree { return 1 }
        return 0
    }

    /// You blocked some of this day yourself.
    var hasOwnBlock: Bool { segments.contains(where: \.manual) }

    /// The longest free stretch (minutes on the 24h axis), as `text` describes it; nil when busy all day.
    var longestFree: ClosedRange<Double>? { Self.longestFree(busy) }

    static func describe(_ free: ClosedRange<Double>?) -> String {
        guard let free else { return "Busy all day" }
        switch (free.lowerBound <= windowStart, free.upperBound >= windowEnd) {
        case (true, true): return "Free all day"
        case (false, true): return "Free from \(DayLayout.clock(free.lowerBound))"
        case (true, false): return "Free until \(DayLayout.clock(free.upperBound))"
        case (false, false): return "Free \(DayLayout.clock(free.lowerBound)) – \(DayLayout.clock(free.upperBound))"
        }
    }

    /// "Monday 17 November, busy 09:00 to 17:00 and 19:00 to 21:30, free from 21:30, Choir"
    var accessibilityText: String {
        var parts = [Formatting.longDay(day.startDate())]
        if day == .today() { parts.append("today") }
        if busy.contains(where: { $0.lowerBound <= 0 && $0.upperBound >= Self.windowEnd }) {
            parts.append("busy all day")
        } else {
            if !busy.isEmpty {
                let spans = busy.map { "\(DayLayout.clock($0.lowerBound)) to \(DayLayout.clock($0.upperBound))" }
                parts.append("busy " + Formatting.list(spans))
            }
            if !isBusyAllDay { parts.append(text.lowercased()) }
        }
        if !names.isEmpty { parts.append(Formatting.list(names)) }
        return parts.joined(separator: ", ")
    }
}
