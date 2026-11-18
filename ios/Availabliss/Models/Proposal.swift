import Foundation

/// A reply to a proposal. `maybe` is a soft yes ("I can make it if I must"): counted apart from
/// `accepted`, and it doesn't confirm a proposal on its own. Unknown values decode as `pending`.
enum ResponseKind: String, Codable, Sendable, CaseIterable, TolerantDecodable {
    case pending, accepted, maybe, declined

    static let fallback = ResponseKind.pending
}

struct Proposal: Codable, Sendable, Identifiable, Equatable {
    enum Status: String, Codable, Sendable { case open, confirmed, cancelled }

    struct Response: Codable, Sendable, Equatable {
        let userId: String
        let response: ResponseKind
        let setBy: String?
        let updatedAt: Date
        /// Confirmed: said they can't make it after it was confirmed, not yet reviewed. Nil on older servers.
        var droppedOut: Bool?
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
    /// Confirmed before everyone accepted ("Confirm anyway"): who did it. Nil otherwise, and on older servers.
    var confirmedBy: String?
    var responses: [Response]
    /// Confirmed: someone who was coming can't make it any more, and the proposer or an admin
    /// hasn't yet kept the plan or cancelled it.
    var needsReview: Bool = false
    /// Open proposals: members whose calendars or blocked time clash with it (ids only).
    var busyMemberIds: [String] = []

    func response(for userId: String) -> Response? { responses.first { $0.userId == userId } }
    var acceptedCount: Int { responses.filter { $0.response == .accepted }.count }
    /// Soft yeses, counted apart from `acceptedCount`.
    var maybeCount: Int { responses.filter { $0.response == .maybe }.count }
    /// " · 1 maybe" when anyone said maybe, else "" (to go after a count of firm yeses).
    var maybeSuffix: String { maybeCount > 0 ? " \u{00B7} \(maybeCount) maybe" : "" }
    /// Everyone it was put to firmly accepted (a maybe isn't enough). False once confirmed anyway with someone who can't make it or never replied.
    var everyoneIn: Bool { responses.allSatisfy { $0.response == .accepted } }
    /// The status line for a confirmed plan: "Everyone's in", or "It's on · 3 of 4 coming · 1 maybe".
    var confirmedStatus: String {
        everyoneIn ? "Everyone\u{2019}s in" : "It\u{2019}s on \u{00B7} \(acceptedCount) of \(responses.count) coming\(maybeSuffix)"
    }
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
        confirmedBy = try c.decodeIfPresent(String.self, forKey: .confirmedBy)
        responses = try c.decode([Response].self, forKey: .responses)
        needsReview = try c.decodeIfPresent(Bool.self, forKey: .needsReview) ?? false
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
    /// Cover photo; nil on older servers too.
    var image: String?
    let createdBy: String
    let createdAt: Date
    /// Only present on `GET /me/upcoming` and `GET /me/plans`.
    var groupName: String?
}

/// A plan on `GET /me/plans`: the event, its group's name, and your reply to its proposal.
struct MyPlan: Decodable, Sendable, Identifiable, Equatable {
    let event: GroupEvent
    let groupName: String
    /// Nil when it was added straight to the calendar (no proposal to reply to).
    let myResponse: ResponseKind?

    var id: String { event.id }

    private enum CodingKeys: String, CodingKey { case groupName, myResponse }

    /// The event's fields sit alongside `groupName` and `myResponse` in the same object.
    init(from decoder: Decoder) throws {
        event = try GroupEvent(from: decoder)
        let c = try decoder.container(keyedBy: CodingKeys.self)
        groupName = try c.decode(String.self, forKey: .groupName)
        myResponse = try c.decodeIfPresent(ResponseKind.self, forKey: .myResponse)
    }
}

/// `GET /me/plans`: upcoming plans you're going to (or haven't replied to), ones you can't make,
/// and the last year's, newest first.
struct MyPlansResponse: Decodable, Sendable {
    let going: [MyPlan]
    let notGoing: [MyPlan]
    let past: [MyPlan]
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
