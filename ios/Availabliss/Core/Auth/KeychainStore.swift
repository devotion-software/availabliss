import Foundation
import os
import Security

/// Minimal generic-password Keychain wrapper.
struct KeychainStore: Sendable {
    let service: String

    init(service: String = "dev.joeherbert.availabliss") {
        self.service = service
    }

    func string(for key: String) -> String? {
        var query = baseQuery(key)
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: AnyObject?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess,
              let data = result as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    @discardableResult
    func set(_ value: String?, for key: String) -> Bool {
        let query = baseQuery(key)
        guard let value else {
            let status = SecItemDelete(query as CFDictionary)
            return status == errSecSuccess || status == errSecItemNotFound
        }
        let data = Data(value.utf8)
        let update: [String: Any] = [kSecValueData as String: data]
        let status = SecItemUpdate(query as CFDictionary, update as CFDictionary)
        if status == errSecItemNotFound {
            var insert = query
            insert[kSecValueData as String] = data
            insert[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
            return SecItemAdd(insert as CFDictionary, nil) == errSecSuccess
        }
        return status == errSecSuccess
    }

    private func baseQuery(_ key: String) -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key,
        ]
    }
}

/// Holds the Better Auth bearer token. Reads are cached; writes go straight to the Keychain.
/// (After-first-unlock accessibility lets background refresh use the token.)
final class TokenStore: Sendable {
    private static let tokenKey = "auth.bearer"
    /// Set in UserDefaults on first launch. UserDefaults goes with an uninstall but the
    /// Keychain doesn't, so a missing flag means a fresh install and any token is stale.
    private static let installedKey = "auth.installed"
    private let keychain: KeychainStore
    private let cache: OSAllocatedUnfairLock<String?>

    init(keychain: KeychainStore = KeychainStore()) {
        self.keychain = keychain
        let defaults = UserDefaults.standard
        if !defaults.bool(forKey: Self.installedKey) {
            keychain.set(nil, for: Self.tokenKey)
            defaults.set(true, forKey: Self.installedKey)
        }
        self.cache = OSAllocatedUnfairLock(initialState: keychain.string(for: Self.tokenKey))
    }

    var token: String? { cache.withLock { $0 } }

    func update(_ token: String?) {
        keychain.set(token, for: Self.tokenKey)
        cache.withLock { $0 = token }
    }
}

/// A random identifier generated once per install and kept in the Keychain,
/// used as `DeviceSync.deviceId`.
enum DeviceIdentity {
    private static let key = "device.id"

    static func deviceId(keychain: KeychainStore = KeychainStore()) -> String {
        if let existing = keychain.string(for: key) { return existing }
        let id = UUID().uuidString.lowercased()
        keychain.set(id, for: key)
        return id
    }
}
