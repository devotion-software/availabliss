import Foundation
import Observation

/// Owns who is signed in. The rest of the app reads `user` and `state`.
@MainActor
@Observable
final class SessionStore {
    enum State: Equatable {
        case restoring
        case signedOut
        case failed(String)
        case signedIn
    }

    private(set) var state: State
    /// Cached on the device, so a later launch can open straight away (see `restore`).
    private(set) var user: User? {
        didSet {
            Self.cache(user)
            if user?.id != oldValue?.id { CrashReporting.setUser(id: user?.id) }
        }
    }

    private let api: APIClient
    private let tokenStore: TokenStore
    private let config: AppConfig

    init(api: APIClient, tokenStore: TokenStore, config: AppConfig) {
        self.api = api
        self.tokenStore = tokenStore
        self.config = config
        self.state = tokenStore.token == nil ? .signedOut : .restoring
    }

    var isSignedIn: Bool { state == .signedIn }

    /// Restores the session for a stored token on launch. With the account cached from last
    /// time it's signed in at once and returns true: call `revalidate()` next. Otherwise it waits
    /// for `/me`.
    @discardableResult
    func restore() async -> Bool {
        guard tokenStore.token != nil else {
            state = .signedOut
            return false
        }
        if let cached = Self.cachedUser() {
            user = cached
            state = .signedIn
            return true
        }
        state = .restoring
        do {
            user = try await api.send(API.Me.get())
            state = .signedIn
        } catch let error as APIError where error.isUnauthorized {
            clearLocalSession()
        } catch {
            state = .failed(error.userMessage)
        }
        return false
    }

    /// Refreshes a session restored from the cache. A 401 (signed out elsewhere, expired) ends it;
    /// other failures (offline) keep the cached account. Returns true if the account was refreshed.
    func revalidate() async -> Bool {
        guard isSignedIn else { return false }
        do {
            user = try await api.send(API.Me.get())
            return true
        } catch let error as APIError where error.isUnauthorized {
            clearLocalSession()
        } catch {}
        return false
    }

    /// Stores the token from a successful sign-in and loads the user.
    func completeSignIn(token: String) async throws {
        tokenStore.update(token)
        do {
            user = try await api.send(API.Me.get())
            state = .signedIn
        } catch {
            tokenStore.update(nil)
            throw error
        }
    }

    func refreshUser() async {
        guard isSignedIn, let fresh = try? await api.send(API.Me.get()) else { return }
        user = fresh
    }

    @discardableResult
    func update(_ patch: UserPatch) async throws -> User {
        let updated = try await api.send(API.Me.update(patch))
        user = updated
        return updated
    }

    /// Uploads a new profile picture (or removes it with nil).
    func setAvatar(jpeg: Data?) async throws {
        user = try await api.send(jpeg.map { API.Me.setAvatar(jpeg: $0) } ?? API.Me.removeAvatar())
    }

    /// Applies a change locally before the server confirms it (used for appearance).
    func applyLocally(_ change: (inout User) -> Void) {
        guard var current = user else { return }
        change(&current)
        user = current
    }

    func signOut() async {
        await AuthService(api: api, config: config).signOut()
        clearLocalSession()
    }

    func deleteAccount() async throws {
        try await api.send(API.Me.delete())
        clearLocalSession()
    }

    /// Called when a request sent with `token` comes back 401. The relay hops to the main actor,
    /// so the user may have signed in again by now; only end the session the 401 was about.
    func handleUnauthorized(token: String) {
        guard state == .signedIn || state == .restoring, tokenStore.token == token else { return }
        clearLocalSession()
    }

    // MARK: Cache

    private static let cacheKey = "session.user"

    private static func cachedUser() -> User? {
        guard let data = UserDefaults.standard.data(forKey: cacheKey) else { return nil }
        return try? JSONCoding.decoder.decode(User.self, from: data)
    }

    private static func cache(_ user: User?) {
        if let user, let data = try? JSONCoding.encoder.encode(user) {
            UserDefaults.standard.set(data, forKey: cacheKey)
        } else {
            UserDefaults.standard.removeObject(forKey: cacheKey)
        }
    }

    private func clearLocalSession() {
        tokenStore.update(nil)
        user = nil
        state = .signedOut
        HTTPCookieStorage.shared.cookies?.forEach(HTTPCookieStorage.shared.deleteCookie)
    }
}
