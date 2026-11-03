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
    private(set) var user: User?

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

    /// Loads `/me` for a stored token on launch.
    func restore() async {
        guard tokenStore.token != nil else {
            state = .signedOut
            return
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

    private func clearLocalSession() {
        tokenStore.update(nil)
        user = nil
        state = .signedOut
        HTTPCookieStorage.shared.cookies?.forEach(HTTPCookieStorage.shared.deleteCookie)
    }
}
