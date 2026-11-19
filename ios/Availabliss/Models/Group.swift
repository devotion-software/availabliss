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
    /// How often the group meets, for the calendar's "Next one" card (nil: not set, or an older server).
    var rhythm: Rhythm?
    var createdAt: Date

    /// How often a group meets: weekly, fortnightly or monthly (the same weekday in the same week of the
    /// month). With `prompt`, the server suggests the next one in the chat once a plan has ended.
    struct Rhythm: Codable, Sendable, Hashable {
        enum Every: String, Codable, Sendable, CaseIterable {
            case week
            case twoWeeks = "two-weeks"
            case month

            /// A value from a newer server reads as weekly rather than failing the whole group.
            init(from decoder: Decoder) throws {
                self = Every(rawValue: try decoder.singleValueContainer().decode(String.self)) ?? .week
            }

            /// "Every week", "Every two weeks", "Every month".
            var label: String {
                switch self {
                case .week: "Every week"
                case .twoWeeks: "Every two weeks"
                case .month: "Every month"
                }
            }
        }

        var every: Every
        /// Suggest the next one in the chat once a plan has ended.
        var prompt: Bool
    }

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
    /// How often it meets: `.set` saves one, `.clear` turns it off (sent as `null`); nil leaves it alone.
    var rhythm: RhythmChange?

    enum RhythmChange: Sendable {
        case set(Group.Rhythm)
        case clear
    }

    private enum CodingKeys: String, CodingKey { case name, color, requirement, archived, autoArchive, rhythm }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encodeIfPresent(name, forKey: .name)
        try c.encodeIfPresent(color, forKey: .color)
        try c.encodeIfPresent(requirement, forKey: .requirement)
        try c.encodeIfPresent(archived, forKey: .archived)
        try c.encodeIfPresent(autoArchive, forKey: .autoArchive)
        switch rhythm {
        case .set(let value): try c.encode(value, forKey: .rhythm)
        case .clear: try c.encodeNil(forKey: .rhythm)
        case nil: break
        }
    }
}

/// `GET /groups/:id/next`: the free time nearest the group's next meet-up (nil without a rhythm).
struct NextMeetResponse: Decodable, Sendable {
    struct Suggestion: Decodable, Sendable, Equatable {
        /// The day the rhythm points at.
        let target: CalendarDay
        /// The free time nearest it (everyone-free first), or nil when nothing near it works yet.
        let slot: Slot?
    }
    let suggestion: Suggestion?
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

    /// One person is what links always were.
    static let standard: LinkLimit = .people(1)
    /// The most people a link can be for (the server's limit).
    static let maxPeople = 1000

    /// `n` people, kept to 1–`maxPeople`.
    static func people(clamping n: Int) -> LinkLimit { .people(min(max(n, 1), maxPeople)) }

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
