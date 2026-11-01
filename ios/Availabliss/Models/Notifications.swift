import Foundation

/// An in-app notification (`Notification` in API.md; renamed to avoid clashing with Foundation).
struct AppNotification: Decodable, Sendable, Identifiable, Equatable {
    enum Kind: String, Decodable, Sendable {
        case proposalCreated = "proposal.created"
        case proposalConfirmed = "proposal.confirmed"
        case nudge
        case inviteAccepted = "invite.accepted"
        case message
        case eventCreated = "event.created"
        case unknown

        init(from decoder: Decoder) throws {
            let raw = try decoder.singleValueContainer().decode(String.self)
            self = Kind(rawValue: raw) ?? .unknown
        }
    }

    let id: String
    let groupId: String?
    let kind: Kind
    let title: String
    let body: String
    let data: [String: String]
    var readAt: Date?
    let createdAt: Date
}

struct NotificationsResponse: Decodable, Sendable {
    let notifications: [AppNotification]
    let unread: Int
}

struct MarkReadBody: Encodable, Sendable { var ids: [String]? }

struct RegisterDeviceBody: Encodable, Sendable {
    let platform: String
    let token: String
    var name: String?
}

/// One event from `GET /api/v1/live`.
struct LiveEvent: Sendable {
    enum Kind: String, Decodable, Sendable {
        case messageCreated = "message.created"
        case messageReceipts = "message.receipts"
        case pollUpdated = "poll.updated"
        case proposalCreated = "proposal.created"
        case proposalUpdated = "proposal.updated"
        case proposalConfirmed = "proposal.confirmed"
        case eventChanged = "event.changed"
        case groupUpdated = "group.updated"
        case memberChanged = "member.changed"
        case availabilityChanged = "availability.changed"
        case notificationCreated = "notification.created"
    }

    let kind: Kind
    let groupId: String?
    /// The raw JSON of the whole event, so subscribers can decode `payload` as the type they expect.
    let raw: Data

    /// Decodes `payload` as `T`, or returns nil when it isn't one.
    func payload<T: Decodable>(as type: T.Type) -> T? {
        try? JSONCoding.decoder.decode(PayloadBox<T>.self, from: raw).payload
    }

    private struct PayloadBox<T: Decodable>: Decodable { let payload: T }
}

// MARK: - Live event payloads (see "Live updates" in API.md)

/// `proposal.confirmed` → `{ proposal, event }`.
struct ProposalConfirmedPayload: Decodable, Sendable {
    let proposal: Proposal
    let event: GroupEvent
}

/// `message.receipts` → `{ userId, state, receipts: [...] }`.
struct MessageReceiptsPayload: Decodable, Sendable {
    struct Entry: Decodable, Sendable {
        let messageId: String
        let delivered: Int
        let seen: Int
        let recipients: Int
        let seenBy: [String]
    }
    let userId: String
    let state: String
    let receipts: [Entry]
}

/// `event.changed` with `{ id, deleted: true }`, or `member.changed` with `{ userId, removed: true }`.
struct RemovalPayload: Decodable, Sendable {
    let id: String?
    let userId: String?
    let deleted: Bool?
    let removed: Bool?

    var isRemoval: Bool { deleted == true || removed == true }
}

extension LiveEvent {
    /// `event.changed`: the updated event, or nil with `deletedId` set when it was deleted.
    var deletedEventId: String? {
        guard kind == .eventChanged, let removal = payload(as: RemovalPayload.self), removal.isRemoval else { return nil }
        return removal.id
    }

    /// `member.changed` with `removed: true`.
    var removedMemberId: String? {
        guard kind == .memberChanged, let removal = payload(as: RemovalPayload.self), removal.isRemoval else { return nil }
        return removal.userId
    }
}
