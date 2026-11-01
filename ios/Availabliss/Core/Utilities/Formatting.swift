import Foundation

/// Date / time copy used across screens. Locale-aware, matching the concept in en-GB
/// ("Sunday 1 November", "Wed 11 Nov · 18:30", "Fri 13 – Sun 15 Nov").
enum Formatting {
    static func longDay(_ date: Date) -> String {
        date.formatted(.dateTime.weekday(.wide).day().month(.wide))
    }

    static func shortDay(_ date: Date) -> String {
        date.formatted(.dateTime.weekday(.abbreviated).day().month(.abbreviated))
    }

    static func shortDay(_ day: CalendarDay) -> String { shortDay(day.startDate()) }

    static func time(_ date: Date) -> String {
        date.formatted(.dateTime.hour().minute())
    }

    static func dayAndTime(_ date: Date) -> String {
        "\(shortDay(date)) · \(time(date))"
    }

    static func monthYear(_ day: CalendarDay) -> String {
        day.startDate().formatted(.dateTime.month(.wide).year())
    }

    static func dayMonthYear(_ day: CalendarDay) -> String {
        day.startDate().formatted(.dateTime.day().month(.abbreviated).year())
    }

    static func dayMonth(_ day: CalendarDay) -> String {
        day.startDate().formatted(.dateTime.day().month(.abbreviated))
    }

    /// "2 – 8 November" / "28 Dec – 3 Jan".
    static func dayRange(_ from: CalendarDay, _ to: CalendarDay) -> String {
        if from.month == to.month && from.year == to.year {
            return "\(from.day) – \(to.day) \(to.startDate().formatted(.dateTime.month(.wide)))"
        }
        return "\(dayMonth(from)) – \(dayMonth(to))"
    }

    /// "Fri 13 – Sun 15 Nov" for a span of time, "Wed 11 Nov · 18:30 – 21:00" within a day.
    static func span(start: Date, end: Date, allDay: Bool, calendar: Calendar = .current) -> String {
        let lastInstant = allDay ? end.addingTimeInterval(-1) : end
        if calendar.isDate(start, inSameDayAs: lastInstant) {
            return allDay ? shortDay(start) : "\(shortDay(start)) · \(time(start)) – \(time(end))"
        }
        let startText = start.formatted(.dateTime.weekday(.abbreviated).day())
        return "\(startText) – \(shortDay(lastInstant))"
    }

    /// "2h ago", "now".
    static func relative(_ date: Date, now: Date = Date()) -> String {
        if now.timeIntervalSince(date) < 60 { return "now" }
        return date.formatted(.relative(presentation: .numeric, unitsStyle: .abbreviated))
    }

    /// "Today", "Yesterday" or a short date for chat separators.
    static func chatDay(_ date: Date, calendar: Calendar = .current) -> String {
        if calendar.isDateInToday(date) { return "Today" }
        if calendar.isDateInYesterday(date) { return "Yesterday" }
        return longDay(date)
    }

    static func list(_ names: [String]) -> String {
        names.formatted(.list(type: .and))
    }

    /// "Ana, Priya, Tom and 3 others".
    static func memberSummary(_ names: [String], visible: Int = 3) -> String {
        guard names.count > visible + 1 else { return list(names) }
        return "\(names.prefix(visible).joined(separator: ", ")) and \(names.count - visible) others"
    }

    static func plural(_ count: Int, _ singular: String, _ plural: String? = nil) -> String {
        "\(count) \(count == 1 ? singular : (plural ?? singular + "s"))"
    }
}
