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
    /// A short, human description of the draft: "A weekend · Fri 18:00 → Sun".
    var draftSummary: String {
        let preset = TimingPreset.detect(length, window: window)
        var parts: [String] = [preset == .custom ? lengthDescription : preset.label]
        switch length {
        case .span(let span):
            parts.append("\(Weekday.short(span.startDay)) \(span.startTime) → \(span.weeksLater == 1 ? "following " : "")\(Weekday.short(span.endDay))")
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
