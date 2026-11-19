import CoreGraphics
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
    /// Photos, GIFs and files sent with it, in order (missing from older servers).
    var attachments: [Attachment]?
    /// With attachments: the caption, or nil. (`body` then holds a summary such as "📷 Photo" for older apps.)
    var caption: String?
    /// A shared place (missing from older servers). `body` then holds a fallback for older apps.
    var location: MessageLocation?

    var isDeleted: Bool { deleted == true }
    var files: [Attachment] { isDeleted ? [] : attachments ?? [] }
    /// The text to show in its bubble: the caption when it has attachments, else the body.
    var text: String? { place != nil ? nil : files.isEmpty ? body : caption }
    /// The place it shares, unless deleted.
    var place: MessageLocation? { isDeleted ? nil : location }
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
        let caption = text.flatMap { $0.isEmpty ? nil : $0 }
        let raw = kind == .poll ? poll?.question : place.map(\.summary) ?? (files.isEmpty ? body : caption ?? Attachment.summary(files))
        let text = raw.map { $0.split(whereSeparator: \.isWhitespace).joined(separator: " ") }
        return ReplyPreview(id: id, authorId: authorId, authorName: authorName, kind: kind, text: isDeleted ? nil : text, deleted: isDeleted)
    }
}

/// A place shared in a chat message: a one-off pin (the sender's position, or a place they found).
struct MessageLocation: Codable, Sendable, Equatable, Hashable {
    let lat: Double
    let lng: Double
    /// A place's name, or nil for "my location".
    var name: String?
    var address: String?
    /// How precise a device's position was, in metres.
    var accuracy: Double?

    /// "📍 Dishoom Shoreditch" or "📍 Location", as the server quotes it.
    var summary: String { "📍 \(name ?? "Location")" }
}

/// A photo, GIF, file or Wallet pass in a chat message. `url` / `thumbnailUrl` are signed links: no auth header needed.
struct Attachment: Codable, Sendable, Identifiable, Equatable, Hashable {
    enum Kind: String, Codable, Sendable {
        case image, gif, file, pass, video
        /// A kind this version doesn't know is shown as a file.
        init(from decoder: Decoder) throws {
            self = Kind(rawValue: try decoder.singleValueContainer().decode(String.self)) ?? .file
        }
    }

    let id: String
    let kind: Kind
    let name: String
    let mime: String
    let size: Int
    let width: Int?
    let height: Int?
    let url: String
    /// A photo's preview, or a pass's logo.
    let thumbnailUrl: String?
    /// For a Wallet pass: what its card shows.
    var pass: WalletPass?
    /// For a video: its length in seconds (`thumbnailUrl` is then its poster).
    var duration: Int?

    var isVisual: Bool { kind == .image || kind == .gif || kind == .video }

    /// A video's length: "0:07", "1:35".
    var durationLabel: String {
        let s = max(0, duration ?? 0)
        return "\(s / 60):" + String(format: "%02d", s % 60)
    }
    /// Width over height, when known.
    var aspectRatio: CGFloat? {
        guard let width, let height, width > 0, height > 0 else { return nil }
        return CGFloat(width) / CGFloat(height)
    }

    /// "Photo", "GIF", "3 photos", "Plan.pdf", "Concert ticket", "2 files", "2 passes", "2 attachments".
    static func summary(_ list: [Attachment]) -> String {
        guard let first = list.first else { return "" }
        if list.count == 1 {
            switch first.kind {
            case .gif: return "GIF"
            case .image: return "Photo"
            case .video: return "Video"
            case .pass: return first.pass?.title ?? "Ticket"
            case .file: return first.name
            }
        }
        let files = list.filter { !$0.isVisual }.count
        if list.allSatisfy({ $0.kind == .video }) { return "\(list.count) videos" }
        if files == 0 { return list.contains { $0.kind == .video } ? "\(list.count) photos and videos" : "\(list.count) photos" }
        if list.allSatisfy({ $0.kind == .pass }) { return "\(list.count) passes" }
        return files == list.count ? "\(list.count) files" : "\(list.count) attachments"
    }
}

/// An Apple Wallet pass's details, read by the server from its pass.json.
struct WalletPass: Codable, Sendable, Equatable, Hashable {
    struct Field: Codable, Sendable, Equatable, Hashable {
        let label: String?
        /// The text, or with `date` an ISO 8601 date-time.
        let value: String
        let date: Bool
    }

    /// boardingPass, eventTicket, coupon, storeCard or generic.
    let style: String
    let organizationName: String
    let description: String
    let logoText: String?
    let passTypeIdentifier: String
    let serialNumber: String
    let relevantDate: Date?
    let expirationDate: Date?
    let voided: Bool
    let eventName: String?
    let venue: String?
    let fields: [Field]
    let backgroundColor: String?
    let foregroundColor: String?
    let labelColor: String?
    let count: Int

    /// "Concert ticket", "Boarding pass", "2 tickets".
    var title: String {
        if count > 1 { return "\(count) \(style == "boardingPass" ? "boarding passes" : style == "eventTicket" ? "tickets" : "passes")" }
        if !description.isEmpty { return description }
        switch style {
        case "boardingPass": return "Boarding pass"
        case "coupon": return "Coupon"
        case "storeCard": return "Card"
        case "generic": return "Pass"
        default: return "Ticket"
        }
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
        /// A date poll's option: the time it stands for (nil for a text option, and from older servers).
        var slot: PollSlot?
    }

    let id: String
    let question: String
    let multi: Bool
    var closed: Bool
    var options: [Option]

    var totalVotes: Int { options.reduce(0) { $0 + $1.voterIds.count } }
    /// A date poll: its options stand for times.
    var isDatePoll: Bool { options.contains { $0.slot != nil } }
    /// The option with the most votes (the earliest of a tie); nil before anyone has voted.
    var leadingOption: Option? {
        guard let top = options.map(\.voterIds.count).max(), top > 0 else { return nil }
        return options.first { $0.voterIds.count == top }
    }
}

/// The time a date poll's option stands for, and (sent when posting one) each time to ask about.
struct PollSlot: Codable, Sendable, Hashable {
    let start: Date
    let end: Date
    let allDay: Bool

    init(start: Date, end: Date, allDay: Bool) {
        self.start = start
        self.end = end
        self.allDay = allDay
    }

    private enum CodingKeys: String, CodingKey { case start, end, allDay }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        start = try container.decode(Date.self, forKey: .start)
        end = try container.decode(Date.self, forKey: .end)
        allDay = try container.decodeIfPresent(Bool.self, forKey: .allDay) ?? false
    }

    /// The same time as a proposal draft, for "Propose this".
    var draft: ProposalDraft { ProposalDraft(start: start, end: end, allDay: allDay) }
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

/// A photo or file on a chat's media page, with the message it was sent in.
struct GroupMediaItem: Decodable, Sendable, Identifiable, Equatable {
    let messageId: String
    /// Nil if the sender has deleted their account.
    let authorId: String?
    /// When its message was sent.
    let createdAt: Date
    let attachment: Attachment
    var id: String { attachment.id }
}

/// A page of a chat's photos and GIFs, or files and passes: newest first.
struct GroupMediaPage: Decodable, Sendable {
    let items: [GroupMediaItem]
    let hasMore: Bool
}

struct MessagesPage: Decodable, Sendable {
    let messages: [Message]
    let hasMore: Bool
}

/// A message's thread: the first message of its chain of replies, then every reply under it, oldest first.
struct MessageThread: Decodable, Sendable {
    let rootId: String
    let messages: [Message]
    /// More replies than the server returns (the earliest are kept).
    let truncated: Bool
}

struct SendMessageBody: Encodable, Sendable {
    /// A text poll (`options`) or a date poll (`slots`, 2–5 different times; `multi` defaults to true there).
    struct NewPoll: Encodable, Sendable {
        let question: String
        var options: [String]?
        var slots: [PollSlot]?
        let multi: Bool?
    }
    var body: String?
    var poll: NewPoll?
    /// The message this one replies to (text only).
    var replyToId: String?
    /// Uploaded photos and files to send, in order (`body` is then an optional caption).
    var attachmentIds: [String]?
    /// A place to share (no body).
    var location: MessageLocation?
}

struct ReceiptBody: Encodable, Sendable {
    enum State: String, Encodable, Sendable { case delivered, seen }
    let upTo: String
    let state: State
}

struct VoteBody: Encodable, Sendable { let optionIds: [String] }

// MARK: Search

extension Message {
    /// A search result's line with the match picked out (see `searchSnippet(for:)`).
    struct SearchSnippet: Equatable {
        let before: String
        let match: String
        let after: String
    }

    /// Words kept either side of a match before the line is cut with "…".
    private static let searchContext = 32

    /// The first of the text (or caption), poll question and options, place and file names that contains
    /// `query` (any case), cut down around the match. With no match there, the text from the start.
    /// Same as web's `searchSnippet` and Android's `searchSnippet`.
    func searchSnippet(for query: String) -> SearchSnippet {
        func tidy(_ s: String?) -> String? {
            let t = s?.split(whereSeparator: \.isWhitespace).joined(separator: " ")
            return t?.isEmpty == false ? t : nil
        }
        var candidates: [String?] = []
        if kind == .poll, let poll {
            candidates.append(poll.question)
            candidates += poll.options.map { "\(poll.question) · \($0.label)" }
        }
        candidates.append(files.isEmpty ? (place == nil ? body : nil) : caption)
        candidates += [place?.name, place?.address]
        candidates += files.map(\.name)
        let texts = candidates.compactMap(tidy)
        let needle = query.split(whereSeparator: \.isWhitespace).joined(separator: " ")
        for text in texts where !needle.isEmpty {
            guard let found = text.range(of: needle, options: [.caseInsensitive]) else { continue }
            let start = text.index(found.lowerBound, offsetBy: -Self.searchContext, limitedBy: text.startIndex) ?? text.startIndex
            var before = String(text[start..<found.lowerBound])
            if start > text.startIndex {
                if let space = before.firstIndex(of: " ") { before = String(before[before.index(after: space)...]) }
                before = "…" + before
            }
            return SearchSnippet(before: before, match: String(text[found]), after: String(text[found.upperBound...]))
        }
        return SearchSnippet(before: texts.first ?? body ?? "", match: "", after: "")
    }
}

// MARK: Chats list

/// A row of the chats list (Activity's Chats tab, `GET /chats`).
struct ChatSummary: Decodable, Sendable, Identifiable, Equatable {
    struct GroupInfo: Decodable, Sendable, Equatable {
        let id: String
        let name: String
        let color: String
        let monogram: String
        let image: String?
        let archived: Bool
    }

    /// The latest message as a one-line preview, like a reply's quote.
    struct LastMessage: Decodable, Sendable, Equatable {
        let id: String
        /// Nil for a group update ("Sam joined").
        let authorId: String?
        let authorName: String?
        let kind: Message.Kind
        let text: String?
        let deleted: Bool
        let createdAt: Date

        /// "You: See you there", "Mo: Poll: Pizza?", or a group update as it is. Same as web and Android.
        func preview(me: String?) -> String {
            let line = Message.ReplyPreview(id: id, authorId: authorId, authorName: authorName, kind: kind, text: text, deleted: deleted).snippet
            guard let authorId else { return line }
            let who = authorId == me ? "You" : (authorName?.split(separator: " ").first.map(String.init) ?? "Someone")
            return "\(who): \(line)"
        }
    }

    let group: GroupInfo
    let lastMessage: LastMessage?
    let unreadCount: Int
    let createdAt: Date
    var id: String { group.id }
}

struct ChatList: Decodable, Sendable {
    let chats: [ChatSummary]
}
