import Foundation

enum CalendarProvider: String, Codable, Sendable {
    case google, microsoft, caldav, device

    var displayName: String {
        switch self {
        case .google: "Google"
        case .microsoft: "Outlook"
        case .caldav: "iCloud"
        case .device: "This iPhone"
        }
    }
}

struct CalendarConnection: Codable, Sendable, Identifiable, Equatable {
    enum Status: String, Codable, Sendable { case ok, error, reauthRequired = "reauth_required" }

    struct Calendar: Codable, Sendable, Identifiable, Equatable {
        let id: String
        var name: String
        var color: String?
        var busy: Bool
    }

    let id: String
    let provider: CalendarProvider
    var label: String
    var status: Status
    var lastSyncedAt: Date?
    var calendars: [Calendar]
    /// Phone calendars: how far ahead to read. Nil for server calendars.
    var syncUntil: Date? = nil
}

struct ConnectionsResponse: Decodable, Sendable { let connections: [CalendarConnection] }

struct CalDAVBody: Encodable, Sendable {
    let username: String
    let appPassword: String
    var serverUrl: String?
}

struct OAuthURLBody: Encodable, Sendable { let redirect: String }
struct OAuthURLResponse: Decodable, Sendable { let url: URL }
struct OAuthCompleteBody: Encodable, Sendable { let code: String }

struct CalendarBusyBody: Encodable, Sendable { let busy: Bool }

/// Native apps upload busy time from on-device calendars. Titles never leave the device.
struct DeviceSync: Encodable, Sendable {
    struct DeviceCalendar: Encodable, Sendable {
        let externalId: String
        let name: String
        let color: String?
        let busy: Bool?
    }
    struct BusyRange: Encodable, Sendable {
        let calendarExternalId: String
        let start: Date
        let end: Date
    }

    let deviceId: String
    let deviceName: String
    let rangeStart: Date
    let rangeEnd: Date
    /// A sync sent as several ranges: the end of the whole sync, and whether more ranges follow.
    var coveredUntil: Date? = nil
    var more: Bool? = nil
    let calendars: [DeviceCalendar]
    let busy: [BusyRange]
}

struct ManualBlock: Codable, Sendable, Identifiable, Equatable {
    enum Repeat: String, Codable, Sendable, CaseIterable {
        case none, weekly, fortnightly
    }

    let id: String
    var start: Date
    var end: Date
    var allDay: Bool
    var `repeat`: Repeat
    var until: CalendarDay?
    var note: String?
}

/// `Omit<ManualBlock, 'id'>` for `POST /blocks`.
struct NewManualBlock: Encodable, Sendable {
    var start: Date
    var end: Date
    var allDay: Bool
    var `repeat`: ManualBlock.Repeat
    var until: CalendarDay?
    var note: String?

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(start, forKey: .start)
        try container.encode(end, forKey: .end)
        try container.encode(allDay, forKey: .allDay)
        try container.encode(`repeat`, forKey: .repeat)
        try container.encode(until, forKey: .until)
        try container.encode(note, forKey: .note)
    }

    private enum CodingKeys: String, CodingKey { case start, end, allDay, `repeat`, until, note }
}

struct BlocksResponse: Decodable, Sendable { let blocks: [ManualBlock] }

// MARK: - Regions

struct Country: Decodable, Sendable, Identifiable, Hashable {
    let code: String
    let name: String
    var id: String { code }
}

struct Subdivision: Decodable, Sendable, Identifiable, Hashable {
    let code: String
    let name: String
    var id: String { code }
}

struct SchoolAuthority: Decodable, Sendable, Identifiable, Hashable {
    let id: String
    let name: String
}

struct CountriesResponse: Decodable, Sendable { let countries: [Country] }
struct SubdivisionsResponse: Decodable, Sendable { let subdivisions: [Subdivision] }
struct AuthoritiesResponse: Decodable, Sendable { let authorities: [SchoolAuthority] }

struct DetectedRegion: Decodable, Sendable {
    let country: String?
    let subdivision: String?
    let timezone: String?
}

struct Holiday: Decodable, Sendable, Hashable {
    let date: CalendarDay
    let name: String
}

struct HolidaysResponse: Decodable, Sendable { let holidays: [Holiday] }
