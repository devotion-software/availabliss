import Foundation

struct Member: Codable, Sendable, Identifiable, Hashable {
    let userId: String
    /// The name they go by in this group: `nameInGroup` when they've set one, else their account name.
    var name: String
    /// The name they chose for this group, or nil for their account name (older servers omit it).
    var nameInGroup: String?
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
    /// The group's photo, drawn instead of the monogram tile; nil on older servers too.
    var image: String?
    var role: Role
    /// Archived by an admin (nil from older servers): listed under Archived on the groups list.
    var archived: Bool?
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
    var image: String?
    var role: Role
    var archived: Bool?
    var memberCount: Int
    var syncedCount: Int
    var requirementSummary: String
    var requirement: Requirement
    var members: [Member]
    /// Archive it automatically once its event has passed and nothing else is planned (older servers omit it).
    var autoArchive: Bool?
    var createdAt: Date

    var isArchived: Bool { archived ?? false }
    var archivesAutomatically: Bool { autoArchive ?? false }
    func member(_ userId: String) -> Member? { members.first { $0.userId == userId } }
    var pendingMembers: [Member] { members.filter { !$0.synced } }
}

extension GroupSummary {
    var isArchived: Bool { archived ?? false }
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
    /// true archives the group for everyone, false unarchives it.
    var archived: Bool?
    /// Archive it automatically once its event has passed.
    var autoArchive: Bool?
}

struct RoleBody: Encodable, Sendable { let role: Role }

struct LeaveGroupBody: Encodable, Sendable { let newOwnerId: String? }

/// The name you go by in one group; nil goes back to your account name (sent as `null`, which the server needs).
struct MemberNameBody: Encodable, Sendable {
    let displayName: String?

    enum CodingKeys: String, CodingKey { case displayName }

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(displayName, forKey: .displayName)
    }
}

/// What accepting an invite returns: the group, and whether you've just joined it (false when you were
/// already in it; older servers omit it).
struct JoinedGroup: Decodable, Sendable {
    let id: String
    let name: String
    let joined: Bool?
}

struct NudgeResponse: Decodable, Sendable { let nudged: Int }

struct InviteLink: Decodable, Sendable {
    let token: String
    let url: URL
    let expiresAt: Date
    /// Every invite the request made (one per email).
    var invites: [GroupInvite]?
}

/// An invite that can still be used: a link anyone can use, or one for a particular person
/// (emailed to `email`, or sent to `username`'s Activity).
struct GroupInvite: Decodable, Sendable, Identifiable, Hashable {
    let id: String
    let email: String?
    var username: String?
    /// The account name of the person it was sent to in Activity (older servers omit it).
    var name: String?
    let url: URL
    /// How many people can join with it (nil: no limit). Older servers omit it and `uses`: single-use.
    var maxUses: Int?
    /// How many people have joined with it so far.
    var uses: Int?
    let expiresAt: Date

    /// A link anyone can use (not for a particular person).
    var isLink: Bool { email == nil && username == nil && name == nil }
    /// Who it's for: "@sam", "sam@example.com" or (sent to someone without a username) their name; nil for a link.
    var recipient: String? { username.map { "@\($0)" } ?? email ?? name }

    /// How many people can still join with it (nil: no limit).
    var usesLeft: Int? {
        guard let uses else { return 1 }
        return maxUses.map { max($0 - uses, 0) }
    }

    /// "One person can join with this link." / "5 people can…" / "3 of 5 people can still…" / "Anyone with this link can join."
    var linkSummary: String {
        guard let left = usesLeft else { return "Anyone with this link can join." }
        let total = maxUses ?? 1
        if total == 1 { return "One person can join with this link." }
        if left == total { return "\(total) people can join with this link." }
        return "\(left) of \(total) people can still join with this link."
    }

    /// For the invites list: "Link for one person" / "3 of 5 left" / "Unlimited · 4 joined".
    var linkUsage: String {
        guard let left = usesLeft else {
            return (uses ?? 0) > 0 ? "Unlimited · \(uses ?? 0) joined" : "Unlimited"
        }
        let total = maxUses ?? 1
        return total == 1 ? "Link for one person" : "\(left) of \(total) left"
    }
}

/// How many people can join with an invite link.
enum LinkLimit: Hashable, Sendable {
    case people(Int)
    case unlimited

    /// The choices offered when making a link; one person is what links always were.
    static let choices: [LinkLimit] = [.people(1), .people(5), .people(10), .people(25), .unlimited]
    static let standard: LinkLimit = .people(1)

    var label: String {
        switch self {
        case .people(let n): "\(n)"
        case .unlimited: "Unlimited"
        }
    }

    var accessibilityLabel: String {
        switch self {
        case .people(let n): n == 1 ? "One person" : "\(n) people"
        case .unlimited: "Unlimited"
        }
    }
}

struct GroupInvitesResponse: Decodable, Sendable { let invites: [GroupInvite] }

struct CreateInviteBody: Encodable, Sendable {
    var emails: [String]?
    var usernames: [String]?
    var userIds: [String]?
    /// For a link: how many people can join with it (sent as null for no limit; left out: one).
    var maxUses: LinkLimit?

    private enum CodingKeys: String, CodingKey { case emails, usernames, userIds, maxUses }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encodeIfPresent(emails, forKey: .emails)
        try c.encodeIfPresent(usernames, forKey: .usernames)
        try c.encodeIfPresent(userIds, forKey: .userIds)
        switch maxUses {
        case .people(let n): try c.encode(n, forKey: .maxUses)
        case .unlimited: try c.encodeNil(forKey: .maxUses)
        case nil: break
        }
    }
}

struct InvitePreview: Decodable, Sendable {
    let groupName: String
    let inviterName: String
    let memberCount: Int
    let expiresAt: Date
    /// Who an emailed invite is for; nil for a link anyone can use.
    var email: String?
    /// Who an invite sent by username is for.
    var username: String?
    /// Sent to one person's Activity, so they can decline it (older servers omit it).
    var personal: Bool?
}
