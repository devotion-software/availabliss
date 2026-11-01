import Foundation

struct Member: Codable, Sendable, Identifiable, Hashable {
    let userId: String
    var name: String
    var image: String?
    var role: Role
    var synced: Bool
    var joinedAt: Date

    var id: String { userId }
    var firstName: String { name.split(separator: " ").first.map(String.init) ?? name }
}

/// Drives the badge on the groups list.
enum GroupStatus: Codable, Sendable, Equatable {
    case matches(count: Int, noun: SlotNoun)
    case needsReply(proposalId: String)
    case confirmed(eventId: String, start: Date)
    case searching

    private enum CodingKeys: String, CodingKey { case kind, count, noun, proposalId, eventId, start }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        switch try container.decode(String.self, forKey: .kind) {
        case "matches":
            self = .matches(
                count: try container.decode(Int.self, forKey: .count),
                noun: (try? container.decode(SlotNoun.self, forKey: .noun)) ?? .slot
            )
        case "needs_reply": self = .needsReply(proposalId: try container.decode(String.self, forKey: .proposalId))
        case "confirmed":
            self = .confirmed(
                eventId: try container.decode(String.self, forKey: .eventId),
                start: try container.decode(Date.self, forKey: .start)
            )
        default: self = .searching
        }
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        switch self {
        case .matches(let count, let noun):
            try container.encode("matches", forKey: .kind)
            try container.encode(count, forKey: .count)
            try container.encode(noun, forKey: .noun)
        case .needsReply(let proposalId):
            try container.encode("needs_reply", forKey: .kind)
            try container.encode(proposalId, forKey: .proposalId)
        case .confirmed(let eventId, let start):
            try container.encode("confirmed", forKey: .kind)
            try container.encode(eventId, forKey: .eventId)
            try container.encode(start, forKey: .start)
        case .searching:
            try container.encode("searching", forKey: .kind)
        }
    }
}

/// What one matching slot is called ("2 weekends work").
enum SlotNoun: String, Codable, Sendable {
    case weekend, evening, day, week, trip, slot

    func counted(_ count: Int) -> String { "\(count) \(rawValue)\(count == 1 ? "" : "s")" }
}

/// `GroupSummary.memberPreview` entry (first 5 members).
struct MemberPreview: Codable, Sendable, Hashable, Identifiable {
    let userId: String
    let name: String
    let image: String?
    var id: String { userId }
    var avatarPerson: AvatarPerson { AvatarPerson(id: userId, name: name, imageURL: image) }
}

struct GroupSummary: Codable, Sendable, Identifiable, Equatable {
    let id: String
    var name: String
    var color: String
    var monogram: String
    var role: Role
    var memberCount: Int
    var syncedCount: Int
    var requirementSummary: String
    var status: GroupStatus
    var unreadCount: Int
    var memberPreview: [MemberPreview]
}

struct Group: Codable, Sendable, Identifiable, Equatable {
    let id: String
    var name: String
    var color: String
    var monogram: String
    var role: Role
    var memberCount: Int
    var syncedCount: Int
    var requirementSummary: String
    var requirement: Requirement
    var members: [Member]
    var createdAt: Date

    func member(_ userId: String) -> Member? { members.first { $0.userId == userId } }
    var pendingMembers: [Member] { members.filter { !$0.synced } }
}

struct GroupsResponse: Decodable, Sendable { let groups: [GroupSummary] }

struct CreateGroupBody: Encodable, Sendable {
    var name: String
    var color: String?
    var requirement: Requirement?
}

struct UpdateGroupBody: Encodable, Sendable {
    var name: String?
    var color: String?
    var requirement: Requirement?
}

struct RoleBody: Encodable, Sendable { let role: Role }

struct NudgeResponse: Decodable, Sendable { let nudged: Int }

struct InviteLink: Decodable, Sendable {
    let token: String
    let url: URL
    let expiresAt: Date
}

struct CreateInviteBody: Encodable, Sendable { var email: String? }

struct InvitePreview: Decodable, Sendable {
    let groupName: String
    let inviterName: String
    let memberCount: Int
    let expiresAt: Date
}
