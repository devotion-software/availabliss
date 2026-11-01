import Foundation

/// A calendar day as the API sends it: `YYYY-MM-DD`, with no time or zone.
struct CalendarDay: Hashable, Comparable, Sendable, Codable, CustomStringConvertible {
    let year: Int
    let month: Int
    let day: Int

    init(year: Int, month: Int, day: Int) {
        self.year = year
        self.month = month
        self.day = day
    }

    /// The day `date` falls on in `calendar`'s time zone.
    init(_ date: Date, calendar: Calendar = .current) {
        let parts = calendar.dateComponents([.year, .month, .day], from: date)
        self.init(year: parts.year ?? 1970, month: parts.month ?? 1, day: parts.day ?? 1)
    }

    init?(string: String) {
        let parts = string.split(separator: "-").compactMap { Int($0) }
        guard parts.count == 3 else { return nil }
        self.init(year: parts[0], month: parts[1], day: parts[2])
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        let raw = try container.decode(String.self)
        guard let value = CalendarDay(string: raw) else {
            throw DecodingError.dataCorruptedError(in: container, debugDescription: "Expected YYYY-MM-DD, got \(raw)")
        }
        self = value
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        try container.encode(description)
    }

    var description: String {
        String(format: "%04d-%02d-%02d", year, month, day)
    }

    static func < (lhs: CalendarDay, rhs: CalendarDay) -> Bool {
        (lhs.year, lhs.month, lhs.day) < (rhs.year, rhs.month, rhs.day)
    }

    static func today(calendar: Calendar = .current) -> CalendarDay { CalendarDay(Date(), calendar: calendar) }

    /// Midnight at the start of this day in `calendar`'s time zone.
    func startDate(calendar: Calendar = .current) -> Date {
        calendar.date(from: DateComponents(year: year, month: month, day: day)) ?? Date(timeIntervalSince1970: 0)
    }

    func adding(days: Int, calendar: Calendar = .current) -> CalendarDay {
        let date = calendar.date(byAdding: .day, value: days, to: startDate(calendar: calendar)) ?? startDate(calendar: calendar)
        return CalendarDay(date, calendar: calendar)
    }

    /// API weekday: 0 = Monday … 6 = Sunday.
    func weekday(calendar: Calendar = .current) -> Int {
        let gregorian = calendar.component(.weekday, from: startDate(calendar: calendar)) // 1 = Sunday
        return (gregorian + 5) % 7
    }

    /// Monday of the week containing this day.
    func startOfWeek(calendar: Calendar = .current) -> CalendarDay {
        adding(days: -weekday(calendar: calendar), calendar: calendar)
    }

    var firstOfMonth: CalendarDay { CalendarDay(year: year, month: month, day: 1) }

    func lastOfMonth(calendar: Calendar = .current) -> CalendarDay {
        let next = calendar.date(byAdding: DateComponents(month: 1, day: -1), to: firstOfMonth.startDate(calendar: calendar))
        return next.map { CalendarDay($0, calendar: calendar) } ?? self
    }

    func addingMonths(_ months: Int, calendar: Calendar = .current) -> CalendarDay {
        let date = calendar.date(byAdding: .month, value: months, to: firstOfMonth.startDate(calendar: calendar))
        return date.map { CalendarDay($0, calendar: calendar) } ?? self
    }

    /// All days from `self` through `end` inclusive.
    func through(_ end: CalendarDay, calendar: Calendar = .current) -> [CalendarDay] {
        var days: [CalendarDay] = []
        var cursor = self
        while cursor <= end {
            days.append(cursor)
            cursor = cursor.adding(days: 1, calendar: calendar)
        }
        return days
    }
}

/// Inclusive range of calendar days.
struct DateRange: Hashable, Sendable, Codable {
    var from: CalendarDay
    var to: CalendarDay

    func contains(_ day: CalendarDay) -> Bool { from <= day && day <= to }
}

/// A 24h wall-clock time, `HH:MM`.
struct WallTime: Hashable, Comparable, Sendable, Codable, CustomStringConvertible {
    var hour: Int
    var minute: Int

    init(hour: Int, minute: Int) {
        self.hour = hour
        self.minute = minute
    }

    init?(string: String) {
        let parts = string.split(separator: ":").compactMap { Int($0) }
        guard parts.count >= 2 else { return nil }
        self.init(hour: parts[0], minute: parts[1])
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        let raw = try container.decode(String.self)
        guard let value = WallTime(string: raw) else {
            throw DecodingError.dataCorruptedError(in: container, debugDescription: "Expected HH:MM, got \(raw)")
        }
        self = value
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        try container.encode(description)
    }

    var description: String { String(format: "%02d:%02d", hour, minute) }
    var minutesSinceMidnight: Int { hour * 60 + minute }
    /// 23:59 is how the API spells "until the end of the day".
    var isEndOfDay: Bool { hour == 23 && minute == 59 }

    static func < (lhs: WallTime, rhs: WallTime) -> Bool { lhs.minutesSinceMidnight < rhs.minutesSinceMidnight }

    static let midnight = WallTime(hour: 0, minute: 0)
    static let endOfDay = WallTime(hour: 23, minute: 59)

    /// Converts to/from a `Date` on an arbitrary reference day — handy for `DatePicker`.
    func date(on day: CalendarDay = .today(), calendar: Calendar = .current) -> Date {
        calendar.date(bySettingHour: hour, minute: minute, second: 0, of: day.startDate(calendar: calendar)) ?? Date()
    }

    init(_ date: Date, calendar: Calendar = .current) {
        let parts = calendar.dateComponents([.hour, .minute], from: date)
        self.init(hour: parts.hour ?? 0, minute: parts.minute ?? 0)
    }
}

/// API weekday helpers (0 = Monday … 6 = Sunday).
enum Weekday {
    static let all = Array(0...6)
    static let shortNames = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]
    static let fullNames = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"]
    static let initials = ["M", "T", "W", "T", "F", "S", "S"]

    static func short(_ day: Int) -> String { shortNames[safe: day] ?? "" }
    static func full(_ day: Int) -> String { fullNames[safe: day] ?? "" }
}

extension Array {
    subscript(safe index: Int) -> Element? {
        indices.contains(index) ? self[index] : nil
    }
}
