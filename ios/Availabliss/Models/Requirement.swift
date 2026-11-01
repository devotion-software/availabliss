import Foundation

/// What a group is looking for. Mirrors `Requirement` in docs/API.md.
struct Requirement: Codable, Sendable, Equatable {
    var length: RequirementLength
    var window: TimeWindow?
    var days: [Int]
    var bankHolidays: Bool
    var schoolHolidaysOnly: Bool
    var schoolAuthority: String?
    var customSchoolHolidays: [DateRange]
    var range: DateRange
    var dates: [DateRange]
    var timezone: String

    /// `window` must be encoded as an explicit `null` ("whole day"), not omitted.
    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(length, forKey: .length)
        try container.encode(window, forKey: .window)
        try container.encode(days, forKey: .days)
        try container.encode(bankHolidays, forKey: .bankHolidays)
        try container.encode(schoolHolidaysOnly, forKey: .schoolHolidaysOnly)
        try container.encode(schoolAuthority, forKey: .schoolAuthority)
        try container.encode(customSchoolHolidays, forKey: .customSchoolHolidays)
        try container.encode(range, forKey: .range)
        try container.encode(dates, forKey: .dates)
        try container.encode(timezone, forKey: .timezone)
    }
}

/// `POST /requirements/preview` body and response.
struct RequirementPreviewBody: Encodable, Sendable { let requirement: Requirement }

struct RequirementPreview: Decodable, Sendable {
    let count: Int
    let from: String
    let to: String
    let summary: String
}

struct TimeWindow: Codable, Sendable, Equatable {
    var from: WallTime
    var to: WallTime
}

/// `Requirement.length` — a tagged union on `type`.
enum RequirementLength: Codable, Sendable, Equatable {
    case duration(minutes: Int)
    case days(count: Int)
    case span(Span)

    struct Span: Codable, Sendable, Equatable {
        var startDay: Int
        var startTime: WallTime
        var endDay: Int
        var endTime: WallTime
        /// 0 = same week, 1 = "Tue → following Thu".
        var weeksLater: Int
    }

    private enum CodingKeys: String, CodingKey {
        case type, minutes, count, startDay, startTime, endDay, endTime, weeksLater
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        switch try container.decode(String.self, forKey: .type) {
        case "duration":
            self = .duration(minutes: try container.decode(Int.self, forKey: .minutes))
        case "days":
            self = .days(count: try container.decode(Int.self, forKey: .count))
        case "span":
            self = .span(Span(
                startDay: try container.decode(Int.self, forKey: .startDay),
                startTime: try container.decode(WallTime.self, forKey: .startTime),
                endDay: try container.decode(Int.self, forKey: .endDay),
                endTime: try container.decode(WallTime.self, forKey: .endTime),
                weeksLater: try container.decode(Int.self, forKey: .weeksLater)
            ))
        case let other:
            throw DecodingError.dataCorruptedError(forKey: .type, in: container, debugDescription: "Unknown length type \(other)")
        }
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        switch self {
        case .duration(let minutes):
            try container.encode("duration", forKey: .type)
            try container.encode(minutes, forKey: .minutes)
        case .days(let count):
            try container.encode("days", forKey: .type)
            try container.encode(count, forKey: .count)
        case .span(let span):
            try container.encode("span", forKey: .type)
            try container.encode(span.startDay, forKey: .startDay)
            try container.encode(span.startTime, forKey: .startTime)
            try container.encode(span.endDay, forKey: .endDay)
            try container.encode(span.endTime, forKey: .endTime)
            try container.encode(span.weeksLater, forKey: .weeksLater)
        }
    }
}

extension Requirement {
    /// Sensible default for a new group: a weekend over the next three months.
    static func defaultValue(today: CalendarDay = .today(), timezone: String = TimeZone.current.identifier) -> Requirement {
        Requirement(
            length: .span(.init(startDay: 4, startTime: WallTime(hour: 18, minute: 0), endDay: 6, endTime: .endOfDay, weeksLater: 0)),
            window: nil,
            days: [4, 5, 6],
            bankHolidays: true,
            schoolHolidaysOnly: false,
            schoolAuthority: nil,
            customSchoolHolidays: [],
            range: DateRange(from: today, to: today.addingMonths(3).adding(days: -1)),
            dates: [],
            timezone: timezone
        )
    }
}
