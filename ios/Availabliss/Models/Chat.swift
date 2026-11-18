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
    /// The message this one replies to, as it is now.
    var replyTo: ReplyPreview?

    var isDeleted: Bool { deleted == true }
    var isEdited: Bool { editedAt != nil && !isDeleted }

    /// A quoted message above a reply: who sent it and one line of it.
    struct ReplyPreview: Codable, Sendable, Equatable {
        let id: String
        let authorId: String?
        let authorName: String?
        let kind: Kind
        /// The text, or a poll's question. Nil once deleted.
        var text: String?
        var deleted: Bool

        /// One line for the quote: "Deleted message", "Poll: Which park?" or the text.
        var snippet: String {
            if deleted { return "Deleted message" }
            switch kind {
            case .poll: return "Poll: \(text ?? "")"
            case .proposal: return text ?? "Proposal"
            case .event: return text ?? "Event"
            default: return text ?? ""
            }
        }
    }

    /// Can be replied to: your own or others' text and polls that are still there.
    var canReply: Bool { (kind == .text || kind == .poll) && !isDeleted }

    /// This message as a quote, e.g. for "Replying to …" and to refresh replies after it changes.
    func asReplyPreview(authorName: String?) -> ReplyPreview {
        let raw = kind == .poll ? poll?.question : body
        let text = raw.map { $0.split(whereSeparator: \.isWhitespace).joined(separator: " ") }
        return ReplyPreview(id: id, authorId: authorId, authorName: authorName, kind: kind, text: isDeleted ? nil : text, deleted: isDeleted)
    }
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
    /// The message this one replies to (text only).
    var replyToId: String?
}

struct ReceiptBody: Encodable, Sendable {
    enum State: String, Encodable, Sendable { case delivered, seen }
    let upTo: String
    let state: State
}

struct VoteBody: Encodable, Sendable { let optionIds: [String] }
