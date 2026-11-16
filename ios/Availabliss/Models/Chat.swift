import Foundation

struct Message: Codable, Sendable, Identifiable, Equatable {
    enum Kind: String, Codable, Sendable { case text, poll, proposal, event, system }

    struct Receipt: Codable, Sendable, Equatable {
        let delivered: Int
        let seen: Int
        let recipients: Int
        /// Who has seen it, in the order they read it.
        let seenBy: [String]
    }

    let id: String
    let groupId: String
    let authorId: String?
    let kind: Kind
    var body: String?
    var poll: Poll?
    var proposalId: String?
    var eventId: String?
    let createdAt: Date
    /// When the author last edited the text.
    var editedAt: Date?
    /// Deleted by its author: shown as deleted, with no body.
    var deleted: Bool?
    var receipt: Receipt?

    var isDeleted: Bool { deleted == true }
    var isEdited: Bool { editedAt != nil && !isDeleted }
}

struct EditMessageBody: Encodable, Sendable {
    let body: String
}

struct Poll: Codable, Sendable, Identifiable, Equatable {
    struct Option: Codable, Sendable, Identifiable, Equatable {
        let id: String
        let label: String
        var voterIds: [String]
    }

    let id: String
    let question: String
    let multi: Bool
    var closed: Bool
    var options: [Option]

    var totalVotes: Int { options.reduce(0) { $0 + $1.voterIds.count } }
}

struct MessageInfo: Decodable, Sendable {
    struct Mark: Decodable, Sendable, Hashable {
        let userId: String
        let at: Date
    }
    let messageId: String
    let sentAt: Date
    let seen: [Mark]
    let delivered: [Mark]
    let pending: [String]
}

struct MessagesPage: Decodable, Sendable {
    let messages: [Message]
    let hasMore: Bool
}

struct SendMessageBody: Encodable, Sendable {
    struct NewPoll: Encodable, Sendable {
        let question: String
        let options: [String]
        let multi: Bool?
    }
    var body: String?
    var poll: NewPoll?
}

struct ReceiptBody: Encodable, Sendable {
    enum State: String, Encodable, Sendable { case delivered, seen }
    let upTo: String
    let state: State
}

struct VoteBody: Encodable, Sendable { let optionIds: [String] }
