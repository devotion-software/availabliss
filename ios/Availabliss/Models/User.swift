import Foundation

enum Role: String, Codable, Sendable, CaseIterable {
    case owner, admin, member

    var isAdmin: Bool { self == .owner || self == .admin }

    var label: String {
        switch self {
        case .owner: "Owner"
        case .admin: "Admin"
        case .member: "Member"
        }
    }
}

enum ThemeMode: String, Codable, Sendable, CaseIterable {
    case light, dark, system
}

enum ColorTheme: String, Codable, Sendable, CaseIterable {
    case dawn, lagoon, sunset, orchid, grove

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

/// Body for `PATCH /me`. Only non-nil fields are sent.
struct UserPatch: Encodable, Sendable {
    var name: String?
    var themeMode: ThemeMode?
    var colorTheme: ColorTheme?
    var country: String?
    var subdivision: String?
    var schoolAuthority: String?
    var timezone: String?
    var onboarded: Bool?
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
