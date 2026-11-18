import Foundation

/// Enums from the server decode unknown values to a default instead of failing the
/// whole response (the web can ship a new value before the app knows about it).
protocol TolerantDecodable: RawRepresentable<String>, Decodable {
    static var fallback: Self { get }
}

extension TolerantDecodable {
    init(from decoder: Decoder) throws {
        let raw = try decoder.singleValueContainer().decode(String.self)
        self = Self(rawValue: raw) ?? Self.fallback
    }
}

enum Role: String, Codable, Sendable, CaseIterable, TolerantDecodable {
    case owner, admin, member

    static let fallback = Role.member

    var isAdmin: Bool { self == .owner || self == .admin }

    var label: String {
        switch self {
        case .owner: "Owner"
        case .admin: "Admin"
        case .member: "Member"
        }
    }
}

enum ThemeMode: String, Codable, Sendable, CaseIterable, TolerantDecodable {
    case light, dark, system

    static let fallback = ThemeMode.system
}

enum ColorTheme: String, Codable, Sendable, CaseIterable, TolerantDecodable {
    case dawn, lagoon, sunset, orchid, grove

    static let fallback = ColorTheme.dawn

    var label: String { rawValue.capitalized }
}

struct User: Codable, Sendable, Identifiable, Equatable {
    let id: String
    var name: String
    /// Lowercase, without the "@". Every account gets one when it's made (nil only briefly for older ones).
    var username: String?
    var email: String
    var image: String?
    var themeMode: ThemeMode
    var colorTheme: ColorTheme
    var country: String?
    var subdivision: String?
    var schoolAuthority: String?
    var timezone: String
    var onboarded: Bool
    /// Minutes counted as busy before and after each busy time when the server finds free slots
    /// (0, 15, 30, 60 or 120). Optional so an older server's `User` still decodes.
    var busyBufferMinutes: Int? = nil

    var firstName: String {
        name.split(separator: " ").first.map(String.init) ?? name
    }
}

extension User {
    /// The name other people see (outside a group that has its own name for them): their name, or their email.
    var accountName: String {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? email : trimmed
    }
}

/// Body for `PATCH /me`. Only non-nil fields are sent, except in a region update (see `region`).
struct UserPatch: Encodable, Sendable {
    var name: String?
    /// Any case, with or without "@": the server normalizes it (409 if it's taken).
    var username: String?
    var themeMode: ThemeMode?
    var colorTheme: ColorTheme?
    var country: String?
    var subdivision: String?
    var schoolAuthority: String?
    var timezone: String?
    var onboarded: Bool?
    var busyBufferMinutes: Int?
    /// Sends nil region fields as `null`. Omitting them would keep the old values, so moving from
    /// GB-ENG to Ireland would leave the subdivision (and its holidays) behind.
    private var replacesRegion = false

    init(
        name: String? = nil,
        username: String? = nil,
        themeMode: ThemeMode? = nil,
        colorTheme: ColorTheme? = nil,
        country: String? = nil,
        subdivision: String? = nil,
        schoolAuthority: String? = nil,
        timezone: String? = nil,
        onboarded: Bool? = nil,
        busyBufferMinutes: Int? = nil
    ) {
        self.name = name
        self.username = username
        self.themeMode = themeMode
        self.colorTheme = colorTheme
        self.country = country
        self.subdivision = subdivision
        self.schoolAuthority = schoolAuthority
        self.timezone = timezone
        self.onboarded = onboarded
        self.busyBufferMinutes = busyBufferMinutes
    }

    /// Replaces the whole region: a nil subdivision or school authority clears it on the server.
    static func region(country: String?, subdivision: String?, schoolAuthority: String?, timezone: String?) -> UserPatch {
        var patch = UserPatch(country: country, subdivision: subdivision, schoolAuthority: schoolAuthority, timezone: timezone)
        patch.replacesRegion = true
        return patch
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encodeIfPresent(name, forKey: .name)
        try container.encodeIfPresent(username, forKey: .username)
        try container.encodeIfPresent(themeMode, forKey: .themeMode)
        try container.encodeIfPresent(colorTheme, forKey: .colorTheme)
        if replacesRegion {
            try container.encode(country, forKey: .country)
            try container.encode(subdivision, forKey: .subdivision)
            try container.encode(schoolAuthority, forKey: .schoolAuthority)
        } else {
            try container.encodeIfPresent(country, forKey: .country)
            try container.encodeIfPresent(subdivision, forKey: .subdivision)
            try container.encodeIfPresent(schoolAuthority, forKey: .schoolAuthority)
        }
        try container.encodeIfPresent(timezone, forKey: .timezone)
        try container.encodeIfPresent(onboarded, forKey: .onboarded)
        try container.encodeIfPresent(busyBufferMinutes, forKey: .busyBufferMinutes)
    }

    private enum CodingKeys: String, CodingKey {
        case name, username, themeMode, colorTheme, country, subdivision, schoolAuthority, timezone, onboarded, busyBufferMinutes
    }
}

struct BusyInterval: Codable, Sendable, Hashable {
    enum Source: String, Codable, Sendable { case calendar, manual }

    let start: Date
    let end: Date
    let source: Source
    let label: String?
    let calendarId: String?
    let blockId: String?
    /// Calendar busy: the event's opaque key ('' when the calendar gives no ids).
    var eventKey: String?
    /// Calendar busy: the event repeats, so "not busy" can cover every occurrence.
    var recurring: Bool?
    /// Set when you've marked this event not busy (it doesn't count): the id to undo it with.
    var ignoreId: String?
    /// Calendar busy: the event's name (`label` is the calendar's). Only ever sent to you.
    var title: String?

    var isIgnored: Bool { ignoreId != nil }

    /// What to call it: the event's name, or your block's note, else "Busy".
    var name: String {
        let text = source == .manual ? label : title
        return text?.isEmpty == false ? text! : "Busy"
    }

    func overlaps(_ start: Date, _ end: Date) -> Bool {
        self.start < end && self.end > start
    }
}

struct BusyIgnoreBody: Encodable, Sendable {
    let calendarId: String
    let eventKey: String
    let start: Date
    let end: Date
    let series: Bool
}

struct BusyIgnoreResponse: Decodable, Sendable { let id: String }

struct BusyIntervalsResponse: Decodable, Sendable {
    let intervals: [BusyInterval]
}
