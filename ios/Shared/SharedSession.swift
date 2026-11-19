import Foundation
import Security

// Compiled into the app and the share extension (see project.yml).

/// The session token, mirrored by the app into a Keychain item in the App Group (an App Group is also
/// a Keychain access group), so the share extension can send to a chat as you. Signing out clears it.
enum SharedSession {
    private static let service = "dev.joeherbert.availabliss.shared"
    private static let account = "auth.bearer"

    private static var query: [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecAttrAccessGroup as String: AppGroup.id,
        ]
    }

    static var token: String? {
        var q = query
        q[kSecReturnData as String] = true
        q[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: AnyObject?
        guard SecItemCopyMatching(q as CFDictionary, &result) == errSecSuccess, let data = result as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    /// Saves (or with nil removes) the token. Best effort: without the App Group (an unsigned
    /// simulator build) sharing just doesn't work.
    static func save(_ token: String?) {
        SecItemDelete(query as CFDictionary)
        guard let token else { return }
        var insert = query
        insert[kSecValueData as String] = Data(token.utf8)
        insert[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        SecItemAdd(insert as CFDictionary, nil)
    }
}
