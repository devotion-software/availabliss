import Foundation

@testable import Availabliss

/// Shared builders for the unit tests. Times are built in `Calendar.current` (the simulator's zone)
/// unless a test needs a particular zone, so the tests pass wherever they run.
enum Fixtures {
    static func decode<T: Decodable>(_ type: T.Type, _ json: String) throws -> T {
        try JSONCoding.decoder.decode(T.self, from: Data(json.utf8))
    }

    static func encode<T: Encodable>(_ value: T) throws -> String {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys, .withoutEscapingSlashes]
        encoder.dateEncodingStrategy = .custom { date, encoder in
            var container = encoder.singleValueContainer()
            try container.encode(JSONCoding.formatDate(date))
        }
        return String(decoding: try encoder.encode(value), as: UTF8.self)
    }

    /// The JSON object `value` encodes to with the app's encoder.
    static func object<T: Encodable>(_ value: T) throws -> [String: Any] {
        let data = try JSONCoding.encoder.encode(value)
        return try JSONSerialization.jsonObject(with: data) as? [String: Any] ?? [:]
    }

    static func date(_ iso: String) -> Date {
        guard let date = JSONCoding.parseDate(iso) else { fatalError("Bad ISO date \(iso)") }
        return date
    }

    static func day(_ string: String) -> CalendarDay {
        guard let day = CalendarDay(string: string) else { fatalError("Bad day \(string)") }
        return day
    }

    static func range(_ from: String, _ to: String) -> DateRange { DateRange(from: day(from), to: day(to)) }

    /// `hour:minute` on `day`, in `calendar`'s zone.
    static func at(_ day: CalendarDay, _ hour: Int, _ minute: Int = 0, calendar: Calendar = .current) -> Date {
        calendar.date(bySettingHour: hour, minute: minute, second: 0, of: day.startDate(calendar: calendar))!
    }

    static func calendar(_ zone: String) -> Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: zone)!
        calendar.locale = Locale(identifier: "en_GB")
        return calendar
    }

    static func member(_ id: String, _ name: String, role: Role = .member, synced: Bool = true) -> Member {
        Member(userId: id, name: name, nameInGroup: nil, image: nil, role: role, synced: synced, joinedAt: date("2026-01-01T00:00:00Z"))
    }

    static func group(_ members: [Member], role: Role = .member, requirement: Requirement = .defaultValue(today: day("2026-11-01"), timezone: "Europe/London")) -> Group {
        Group(
            id: "g1", name: "Book club", color: "t1", monogram: "B", image: nil, role: role, archived: nil,
            memberCount: members.count, syncedCount: members.filter(\.synced).count, requirementSummary: "",
            requirement: requirement, members: members, autoArchive: nil, rhythm: nil, createdAt: date("2026-01-01T00:00:00Z")
        )
    }

    /// Sam (me), Priya Shah, Alex, Jo, Tom and Lee: the names the web's clash tests use.
    static let people: [Member] = [
        member("me", "Sam Taylor"), member("p", "Priya Shah"), member("a", "Alex"),
        member("j", "Jo"), member("t", "Tom"), member("l", "Lee"),
    ]

    /// An API client whose requests never leave the process: `StubURLProtocol` answers them.
    static func api(_ routes: @escaping @Sendable (URLRequest) -> StubURLProtocol.Reply) -> APIClient {
        let host = "\(UUID().uuidString.lowercased()).stub.test"
        StubURLProtocol.register(host: host, routes)
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [StubURLProtocol.self]
        return APIClient(baseURL: URL(string: "https://\(host)")!, tokenStore: TokenStore(), session: URLSession(configuration: configuration))
    }
}

/// Answers requests for `Fixtures.api` clients. Each client gets its own host, so tests running in
/// parallel never see each other's routes.
final class StubURLProtocol: URLProtocol, @unchecked Sendable {
    struct Reply: Sendable {
        var status = 200
        var body = ""

        static func json(_ body: String, status: Int = 200) -> Reply { Reply(status: status, body: body) }
        static let notFound = Reply(status: 404, body: #"{"message":"Not found","code":"not_found"}"#)
    }

    private static let lock = NSLock()
    nonisolated(unsafe) private static var routes: [String: @Sendable (URLRequest) -> Reply] = [:]

    static func register(host: String, _ handler: @escaping @Sendable (URLRequest) -> Reply) {
        lock.withLock { routes[host] = handler }
    }

    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        let handler = Self.lock.withLock { request.url?.host().flatMap { Self.routes[$0] } }
        let reply = handler?(request) ?? .notFound
        let response = HTTPURLResponse(url: request.url!, statusCode: reply.status, httpVersion: "HTTP/1.1", headerFields: ["Content-Type": "application/json"])!
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: Data(reply.body.utf8))
        client?.urlProtocolDidFinishLoading(self)
    }

    override func stopLoading() {}
}
