import Foundation

/// Where a connection's calendars come from. A provider a newer server adds decodes as `.unknown`
/// (shown as "Calendar"), so one new kind of connection can't stop the whole list loading.
enum CalendarProvider: String, Codable, Sendable, TolerantDecodable {
    case google, microsoft, caldav, device, unknown

    static let fallback = CalendarProvider.unknown

    var displayName: String {
        switch self {
        case .google: "Google"
        case .microsoft: "Outlook"
        case .caldav: "iCloud"
        case .device: "This iPhone"
        case .unknown: "Calendar"
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

/// GET /me/calendar-feed and POST /me/calendar-feed/reset: your secret link to subscribe to
/// your plans. `webcalUrl` is for calendar apps to subscribe to; `url` is the one to copy.
struct CalendarFeed: Decodable, Sendable, Equatable {
    let url: URL
    let webcalUrl: URL
}

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
        /// Hash of the event's series id (shared by every occurrence), so a series can be marked not busy.
        var eventKey: String?
        /// The event's name: stored encrypted and only ever shown to you.
        var title: String?
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
        case none, daily, weekdays, weekly, fortnightly, monthly, monthlyWeekday, yearly
        /// A value from a newer server.
        case unknown

        static var allCases: [Repeat] { [.none, .daily, .weekdays, .weekly, .fortnightly, .monthly, .monthlyWeekday, .yearly] }

        init(from decoder: Decoder) throws {
            self = Repeat(rawValue: try decoder.singleValueContainer().decode(String.self)) ?? .unknown
        }
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
