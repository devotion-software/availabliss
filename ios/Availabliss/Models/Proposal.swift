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

    func response(for userId: String) -> Response? { responses.first { $0.userId == userId } }
    var acceptedCount: Int { responses.filter { $0.response == .accepted }.count }
}

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
