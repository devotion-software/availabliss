import Foundation

enum ResponseKind: String, Codable, Sendable, CaseIterable {
    case pending, accepted, declined
}

struct Proposal: Codable, Sendable, Identifiable, Equatable {
    enum Status: String, Codable, Sendable { case open, confirmed, cancelled }

    struct Response: Codable, Sendable, Equatable {
        let userId: String
        let response: ResponseKind
        let setBy: String?
        let updatedAt: Date
    }

    let id: String
    let groupId: String
    var title: String?
    var location: String?
    var note: String?
    var start: Date
    var end: Date
    var allDay: Bool
    var status: Status
    let createdBy: String
    let createdAt: Date
    var eventId: String?
    var responses: [Response]
    /// Open proposals: members whose calendars or blocked time clash with it (ids only).
    var busyMemberIds: [String] = []

    func response(for userId: String) -> Response? { responses.first { $0.userId == userId } }
    var acceptedCount: Int { responses.filter { $0.response == .accepted }.count }
    /// Who's busy then, while it's still open (closed proposals never warn).
    var openClashIds: [String] { status == .open ? busyMemberIds : [] }
}

extension Proposal {
    /// Decodes `busyMemberIds` as [] when an older server leaves it out.
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        groupId = try c.decode(String.self, forKey: .groupId)
        title = try c.decodeIfPresent(String.self, forKey: .title)
        location = try c.decodeIfPresent(String.self, forKey: .location)
        note = try c.decodeIfPresent(String.self, forKey: .note)
        start = try c.decode(Date.self, forKey: .start)
        end = try c.decode(Date.self, forKey: .end)
        allDay = try c.decode(Bool.self, forKey: .allDay)
        status = try c.decode(Status.self, forKey: .status)
        createdBy = try c.decode(String.self, forKey: .createdBy)
        createdAt = try c.decode(Date.self, forKey: .createdAt)
        eventId = try c.decodeIfPresent(String.self, forKey: .eventId)
        responses = try c.decode([Response].self, forKey: .responses)
        busyMemberIds = try c.decodeIfPresent([String].self, forKey: .busyMemberIds) ?? []
    }
}

/// `GET /groups/:id/conflicts`: who's busy during a time.
struct ConflictsResponse: Decodable, Sendable { let busyMemberIds: [String] }

struct ProposalsResponse: Decodable, Sendable { let proposals: [Proposal] }

struct CreateProposalBody: Encodable, Sendable {
    var start: Date
    var end: Date
    var allDay: Bool?
    var title: String?
    var location: String?
    var note: String?
}

struct RespondBody: Encodable, Sendable { let response: ResponseKind }

struct OverrideBody: Encodable, Sendable {
    let userId: String
    let response: ResponseKind
}

struct GroupEvent: Codable, Sendable, Identifiable, Equatable {
    let id: String
    let groupId: String
    let proposalId: String?
    var title: String
    var location: String?
    var note: String?
    var start: Date
    var end: Date
    var allDay: Bool
    let createdBy: String
    let createdAt: Date
    /// Only present on `GET /me/upcoming`.
    var groupName: String?
}

struct EventsResponse: Decodable, Sendable { let events: [GroupEvent] }

/// Body for creating (all fields) or patching (any subset of) an event.
struct EventBody: Encodable, Sendable {
    var title: String?
    var start: Date?
    var end: Date?
    var allDay: Bool?
    var location: String?
    var note: String?
}
