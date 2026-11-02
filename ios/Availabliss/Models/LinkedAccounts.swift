import Foundation

/// Sign-in providers that can be linked to an account.
enum LinkProvider: String, Codable, Sendable, CaseIterable, Identifiable {
    case apple, google

    var id: String { rawValue }
    var label: String { self == .apple ? "Apple" : "Google" }
}

/// GET /me/accounts
struct LinkedAccounts: Decodable, Sendable, Equatable {
    struct Linked: Decodable, Sendable, Equatable {
        let provider: LinkProvider
        let linkedAt: Date
    }
    let linked: [Linked]
    /// Providers this server can link.
    let available: [LinkProvider]

    func isLinked(_ provider: LinkProvider) -> Bool { linked.contains { $0.provider == provider } }
}

/// The account a sign-in already belongs to (409 from POST /me/accounts/link), shown before merging.
struct MergeCandidate: Decodable, Sendable, Equatable {
    let name: String
    let email: String
    let groups: Int
}

/// POST /me/accounts/link
struct LinkAccountBody: Encodable, Sendable {
    let provider: LinkProvider
    let idToken: String
    let nonce: String?
    let merge: Bool?
}

struct LinkAccountResponse: Decodable, Sendable {
    let linked: Bool
    let merged: Bool
}
