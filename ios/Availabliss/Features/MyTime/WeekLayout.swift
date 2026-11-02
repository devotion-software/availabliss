import Foundation

/// The "My time" week, laid out: days across the top, minutes down the side, and every
/// busy interval at its exact start and end. Overlapping intervals share the column side
/// by side; intervals covering whole days go in the all-day row.
///
/// Pure value logic (no UI) so it can be unit tested. Mirrors `web/src/lib/components/mytime/parts.ts`.
struct WeekLayout: Sendable {
    static let dayMinutes = 24 * 60
    /// Tapping an empty slot suggests an hour.
    static let defaultBlockMinutes = 60

    /// One interval's piece on one day.
    struct Placed: Identifiable, Hashable, Sendable {
        let interval: BusyInterval
        let day: CalendarDay
        /// Minutes from the start of `day` on the 24h axis, clamped to the day.
        let from: Double
        let to: Double
        /// Side-by-side position among overlapping intervals.
        var lane = 0
        var lanes = 1
        /// The interval carries on from the previous day / into the next.
        let continuesBefore: Bool
        let continuesAfter: Bool

        var id: String { "\(day)-\(interval.start.timeIntervalSince1970)-\(interval.end.timeIntervalSince1970)-\(interval.blockId ?? interval.calendarId ?? "")-\(interval.label ?? "")" }
        var minutes: Double { to - from }
    }

    let days: [CalendarDay]
    /// Intervals that cover whole days, per day.
    let allDay: [CalendarDay: [BusyInterval]]
    /// Timed intervals per day, with lanes.
    let timed: [CalendarDay: [Placed]]

    var hasAllDay: Bool { allDay.values.contains { !$0.isEmpty } }

    init(weekStart: CalendarDay, intervals: [BusyInterval], calendar: Calendar = .current) {
        let days = (0..<7).map { weekStart.adding(days: $0, calendar: calendar) }
        var allDay: [CalendarDay: [BusyInterval]] = [:]
        var timed: [CalendarDay: [Placed]] = [:]
        let ordered = intervals.sorted { ($0.start, $0.end) < ($1.start, $1.end) }

        for day in days {
            let midnight = day.startDate(calendar: calendar)
            let length = Self.dayLength(day, calendar: calendar)
            // Scale DST days onto the 24-hour axis so 23:00 still lines up with 23:00.
            let scale = Double(Self.dayMinutes) / length
            var whole: [BusyInterval] = []
            var pieces: [Placed] = []
            for interval in ordered {
                let start = interval.start.timeIntervalSince(midnight) / 60
                let end = interval.end.timeIntervalSince(midnight) / 60
                if end <= 0 || start >= length { continue }
                if start <= 0 && end >= length {
                    whole.append(interval)
                    continue
                }
                pieces.append(Placed(
                    interval: interval,
                    day: day,
                    from: max(0, start) * scale,
                    to: min(length, end) * scale,
                    continuesBefore: start < 0,
                    continuesAfter: end > length
                ))
            }
            allDay[day] = whole
            timed[day] = Self.assignLanes(pieces)
        }
        self.days = days
        self.allDay = allDay
        self.timed = timed
    }

    /// Length of `day` in minutes (1380 / 1500 on DST changes).
    static func dayLength(_ day: CalendarDay, calendar: Calendar = .current) -> Double {
        day.adding(days: 1, calendar: calendar).startDate(calendar: calendar)
            .timeIntervalSince(day.startDate(calendar: calendar)) / 60
    }

    /// Minutes from `day`'s midnight to `date`, on the 24h axis (DST days scaled).
    static func minutesIntoDay(_ date: Date, on day: CalendarDay, calendar: Calendar = .current) -> Double {
        let raw = date.timeIntervalSince(day.startDate(calendar: calendar)) / 60
        return raw * Double(dayMinutes) / dayLength(day, calendar: calendar)
    }

    /// Assigns lanes so overlapping intervals sit side by side; each overlap cluster shares a lane count.
    static func assignLanes(_ items: [Placed]) -> [Placed] {
        let sorted = items.enumerated().sorted { a, b in
            if a.element.from != b.element.from { return a.element.from < b.element.from }
            if a.element.to != b.element.to { return a.element.to > b.element.to }
            return a.offset < b.offset
        }.map(\.element)

        var out: [Placed] = []
        var cluster: [Placed] = []
        var laneEnds: [Double] = []
        var clusterEnd = -Double.infinity

        func flush() {
            for var item in cluster {
                item.lanes = laneEnds.count
                out.append(item)
            }
            cluster = []
            laneEnds = []
        }

        for var item in sorted {
            if item.from >= clusterEnd { flush() }
            if let lane = laneEnds.firstIndex(where: { $0 <= item.from }) {
                laneEnds[lane] = item.to
                item.lane = lane
            } else {
                laneEnds.append(item.to)
                item.lane = laneEnds.count - 1
            }
            cluster.append(item)
            clusterEnd = max(clusterEnd, item.to)
        }
        flush()
        return out
    }

    /// Where to scroll to first: an hour before the earliest timed interval, but no later than 06:00.
    var initialScrollMinutes: Int {
        var earliest = Double(7 * 60)
        for pieces in timed.values {
            for piece in pieces { earliest = min(earliest, piece.from) }
        }
        return max(0, Int(earliest / 60) * 60 - 60)
    }

    /// The range a tap on an empty slot suggests: the hour it falls in (23:xx → 23:00–24:00).
    static func tapRange(at minutes: Double) -> (from: Int, to: Int) {
        let hour = Int(max(0, minutes) / 60) * 60
        let from = min(hour, dayMinutes - defaultBlockMinutes)
        return (from, from + defaultBlockMinutes)
    }

    /// "09:15" for minutes since midnight; 1440 is "24:00".
    static func clock(_ minutes: Double) -> String {
        let m = Int(minutes.rounded())
        return String(format: "%02d:%02d", m / 60, m % 60)
    }
}
