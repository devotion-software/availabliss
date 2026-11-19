import Foundation

/// The "How long?" chips, mapped to `Requirement.length` + `window` exactly as the presets
/// table in API.md.
enum TimingPreset: String, CaseIterable, Identifiable {
    case twoHours, evening, oneDay, weekend, week, custom

    var id: String { rawValue }

    var label: String {
        switch self {
        case .twoHours: "2 hours"
        case .evening: "An evening"
        case .oneDay: "One day"
        case .weekend: "A weekend"
        case .week: "A week"
        case .custom: "Custom"
        }
    }

    static let eveningWindow = TimeWindow(from: WallTime(hour: 18, minute: 0), to: .endOfDay)
    static let defaultDurationWindow = TimeWindow(from: WallTime(hour: 8, minute: 0), to: WallTime(hour: 23, minute: 0))
    static let weekendSpan = RequirementLength.Span(startDay: 4, startTime: WallTime(hour: 18, minute: 0), endDay: 6, endTime: .endOfDay, weeksLater: 0)

    /// The length and window this preset stands for (`custom` keeps whatever is there).
    var shape: (length: RequirementLength, window: TimeWindow?)? {
        switch self {
        case .twoHours: (.duration(minutes: 120), Self.defaultDurationWindow)
        case .evening: (.days(count: 1), Self.eveningWindow)
        case .oneDay: (.days(count: 1), nil)
        case .weekend: (.span(Self.weekendSpan), nil)
        case .week: (.days(count: 7), nil)
        case .custom: nil
        }
    }

    /// Recognises which chip a saved requirement corresponds to.
    static func detect(_ length: RequirementLength, window: TimeWindow?) -> TimingPreset {
        switch length {
        case .duration(120): .twoHours
        case .days(1) where window == eveningWindow: .evening
        case .days(1) where window == nil: .oneDay
        case .days(7) where window == nil: .week
        case .span(let span) where span == weekendSpan: .weekend
        default: .custom
        }
    }
}

/// Which shape a custom length takes.
enum CustomLengthKind: String, CaseIterable, Identifiable {
    case hours, days, span
    var id: String { rawValue }
    var label: String {
        switch self {
        case .hours: "Hours"
        case .days: "Days"
        case .span: "Day to day"
        }
    }

    init(_ length: RequirementLength) {
        switch length {
        case .duration: self = .hours
        case .days: self = .days
        case .span: self = .span
        }
    }
}

extension Requirement {
    /// Applies a preset's length and window. A run of days longer than the days allowed
    /// ("A week" with Fri–Sun) could never fit, so it opens up every day.
    mutating func apply(_ preset: TimingPreset) {
        guard let shape = preset.shape else { return }
        length = shape.length
        window = shape.window
        switch shape.length {
        case .span(let span):
            days = TimingEditorViewModel.days(in: span)
        case .days(let count):
            // Empty `days` means any weekday.
            if !days.isEmpty && count > days.count { days = [] }
        case .duration:
            break
        }
    }

    /// A short, human description of the draft: "A weekend · Fri 18:00 → Sun".
    var draftSummary: String {
        let preset = TimingPreset.detect(length, window: window)
        var parts: [String] = [preset == .custom ? lengthDescription : preset.label]
        switch length {
        case .span(let span):
            parts.append(
                "\(Weekday.short(span.startDay)) \(span.startTime) → \(span.weeksLater == 1 ? "following " : "")\(Weekday.short(span.endDay))"
                    + (span.weeksLater >= 2 ? " \(span.weeksLater) weeks later" : ""))
        default:
            if let window { parts.append("\(window.from)–\(window.to.isEndOfDay ? "late" : window.to.description)") }
        }
        return parts.joined(separator: " · ")
    }

    var lengthDescription: String {
        switch length {
        case .duration(let minutes):
            minutes % 60 == 0 ? Formatting.plural(minutes / 60, "hour") : "\(minutes / 60)h \(minutes % 60)m"
        case .days(let count): Formatting.plural(count, "day")
        case .span(let span): "\(Weekday.short(span.startDay)) → \(Weekday.short(span.endDay))"
        }
    }
}

// MARK: - The timing sentence

/// "We're looking for a weekend, from Friday 18:00 to Sunday night, any time between 20 Nov and 28 Feb."
/// Each phrase is a tappable part of the sentence. Same wording as web's and Android's.
extension Requirement {
    /// "a weekend", "2 hours", "2½ hours", "45 minutes", "3 days in a row".
    var kindPhrase: String {
        switch TimingPreset.detect(length, window: window) {
        case .twoHours: return "2 hours"
        case .evening: return "an evening"
        case .oneDay: return "a whole day"
        case .weekend: return "a weekend"
        case .week: return "a week"
        case .custom: break
        }
        switch length {
        case .duration(let minutes):
            if minutes % 60 == 0 { return Formatting.plural(minutes / 60, "hour") }
            if minutes % 30 == 0 { return minutes < 60 ? "½ hour" : "\(minutes / 60)½ hours" }
            return Formatting.plural(minutes, "minute")
        case .days(let count): return count == 1 ? "a day" : "\(count) days in a row"
        case .span: return "a stretch of days"
        }
    }

    /// "Friday 18:00", "Tuesday" (from the start of the day).
    static func startPhrase(_ span: RequirementLength.Span) -> String {
        let midnight = span.startTime == WallTime(hour: 0, minute: 0)
        return Weekday.full(span.startDay) + (midnight ? "" : " \(span.startTime)")
    }

    /// "Sunday night" (the end of the day), "Sunday 17:00", "the following Thursday night",
    /// "Thursday night 3 weeks later".
    static func endPhrase(_ span: RequirementLength.Span) -> String {
        let day = (span.weeksLater == 1 ? "the following " : "") + Weekday.full(span.endDay)
        let end = span.endTime.isEndOfDay ? "\(day) night" : "\(day) \(span.endTime)"
        return span.weeksLater >= 2 ? "\(end) \(span.weeksLater) weeks later" : end
    }

    /// How many weeks after its start a span can end (as the server allows).
    static let maxWeeksLater = 4

    /// "Ends the same week", "Ends the week after", "Ends 3 weeks later".
    static func weeksLaterLabel(_ weeks: Int) -> String {
        switch weeks {
        case 0: "Ends the same week"
        case 1: "Ends the week after"
        default: "Ends \(weeks) weeks later"
        }
    }

    /// "any time of day", "between 18:00 and midnight".
    static func windowPhrase(_ window: TimeWindow?) -> String {
        guard let window else { return "any time of day" }
        return "between \(window.from) and \(window.to.isEndOfDay ? "midnight" : window.to.description)"
    }

    /// "any day", "weekdays", "weekends", "Fridays to Sundays", "Mondays, Wednesdays and Fridays".
    static func daysPhrase(_ days: [Int]) -> String {
        let sorted = Array(Set(days)).sorted()
        if sorted.isEmpty || sorted.count == 7 { return "any day" }
        if sorted == [0, 1, 2, 3, 4] { return "weekdays" }
        if sorted == [5, 6] { return "weekends" }
        let names = sorted.map { Weekday.full($0) + "s" }
        let contiguous = zip(sorted, sorted.dropFirst()).allSatisfy { $1 == $0 + 1 }
        if contiguous && sorted.count > 2 { return "\(names[0]) to \(names[names.count - 1])" }
        return names.count == 1 ? names[0] : names.dropLast().joined(separator: ", ") + " and " + names[names.count - 1]
    }
}
