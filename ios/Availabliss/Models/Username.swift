import Foundation

/// Usernames: a unique handle people can be invited by instead of their email. These mirror the
/// server's rules (web/src/lib/usernames.ts) for instant feedback; the server has the final say.
enum Username {
    static let minLength = 3
    static let maxLength = 20

    private static let reserved: Set<String> = [
        "admin", "administrator", "availabliss", "help", "me", "moderator",
        "null", "root", "support", "system", "undefined", "you",
    ]

    /// As stored: trimmed, no leading "@", lowercase.
    static func normalize(_ raw: String) -> String {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        return String(trimmed.drop { $0 == "@" }).lowercased()
    }

    /// Why a (normalized) username can't be used, or nil when it's fine.
    static func problem(_ username: String) -> String? {
        if username.count < minLength { return "At least \(minLength) characters" }
        if username.count > maxLength { return "At most \(maxLength) characters" }
        if username.wholeMatch(of: /[a-z0-9_.]+/) == nil { return "Only letters, numbers, _ and ." }
        if username.hasPrefix(".") || username.hasSuffix(".") || username.contains("..") {
            return "A dot can\u{2019}t come first, last or twice in a row"
        }
        if reserved.contains(username) { return "That one\u{2019}s reserved" }
        return nil
    }
}

/// Someone found by username (`UserSummary` in API.md).
struct UserSummary: Decodable, Sendable, Identifiable, Hashable {
    let id: String
    let username: String
    let name: String
    let image: String?

    /// Their name, or "@username" when they haven't added one.
    var displayName: String {
        let trimmed = name.trimmingCharacters(in: .whitespaces)
        return trimmed.isEmpty ? "@\(username)" : trimmed
    }
}

/// `GET /usernames/:username`: whether it can be yours, and who has it.
struct UsernameCheck: Decodable, Sendable {
    let username: String
    let problem: String?
    /// Valid, and nobody else has it (true when it's already yours).
    let available: Bool
    let user: UserSummary?
}
