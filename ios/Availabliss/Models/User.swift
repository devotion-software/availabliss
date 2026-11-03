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
    var email: String
    var image: String?
    var themeMode: ThemeMode
    var colorTheme: ColorTheme
    var country: String?
    var subdivision: String?
    var schoolAuthority: String?
    var timezone: String
    var onboarded: Bool

    var firstName: String {
        name.split(separator: " ").first.map(String.init) ?? name
    }
}

/// Body for `PATCH /me`. Only non-nil fields are sent, except in a region update (see `region`).
struct UserPatch: Encodable, Sendable {
    var name: String?
    var themeMode: ThemeMode?
    var colorTheme: ColorTheme?
    var country: String?
    var subdivision: String?
    var schoolAuthority: String?
    var timezone: String?
    var onboarded: Bool?
    /// Sends nil region fields as `null`. Omitting them would keep the old values, so moving from
    /// GB-ENG to Ireland would leave the subdivision (and its holidays) behind.
    private var replacesRegion = false

    init(
        name: String? = nil,
        themeMode: ThemeMode? = nil,
        colorTheme: ColorTheme? = nil,
        country: String? = nil,
        subdivision: String? = nil,
        schoolAuthority: String? = nil,
        timezone: String? = nil,
        onboarded: Bool? = nil
    ) {
        self.name = name
        self.themeMode = themeMode
        self.colorTheme = colorTheme
        self.country = country
        self.subdivision = subdivision
        self.schoolAuthority = schoolAuthority
        self.timezone = timezone
        self.onboarded = onboarded
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
    }

    private enum CodingKeys: String, CodingKey {
        case name, themeMode, colorTheme, country, subdivision, schoolAuthority, timezone, onboarded
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

    func overlaps(_ start: Date, _ end: Date) -> Bool {
        self.start < end && self.end > start
    }
}

struct BusyIntervalsResponse: Decodable, Sendable {
    let intervals: [BusyInterval]
}
