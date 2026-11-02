import Foundation

/// All Better Auth sign-in flows. Each successful flow yields the bearer token from the
/// `set-auth-token` response header; `SessionStore` takes it from there.
@MainActor
struct AuthService {
    let api: APIClient
    let config: AppConfig

    func sendCode(to email: String) async throws {
        try await api.send(API.Auth.sendOTP(email: email))
    }

    func signIn(email: String, code: String) async throws -> String {
        try await token(from: API.Auth.signInOTP(email: email, otp: code))
    }

    func signInWithApple() async throws -> String {
        let result = try await AppleSignIn().signIn()
        return try await token(from: API.Auth.signInSocial(provider: "apple", idToken: result.identityToken, nonce: result.rawNonce))
    }

    var isGoogleConfigured: Bool { google.isConfigured }

    func signInWithGoogle() async throws -> String {
        let idToken = try await google.signIn()
        return try await token(from: API.Auth.signInSocial(provider: "google", idToken: idToken, nonce: nil))
    }

    /// Links Sign in with Apple / Google to the signed-in account. Throws `APIError.mergeCandidate`
    /// when it has its own account; call again with the same token and `merge: true` to merge it.
    func linkToken(_ provider: LinkProvider) async throws -> (idToken: String, nonce: String?) {
        switch provider {
        case .apple:
            let result = try await AppleSignIn().signIn()
            return (result.identityToken, result.rawNonce)
        case .google:
            return (try await google.signIn(), nil)
        }
    }

    func link(_ provider: LinkProvider, idToken: String, nonce: String?, merge: Bool) async throws -> LinkAccountResponse {
        try await api.send(API.Me.linkAccount(LinkAccountBody(provider: provider, idToken: idToken, nonce: nonce, merge: merge ? true : nil)))
    }

    func signInWithPasskey() async throws -> String {
        let options = try await api.send(API.Auth.passkeyAuthenticateOptions())
        let request = try PasskeyCoordinator.assertion(options: options, fallbackRelyingParty: config.associatedDomainHost)
        let authorization = try await AuthorizationRunner().perform([request])
        let json = try PasskeyCoordinator.assertionJSON(from: authorization.credential)
        return try await token(from: API.Auth.passkeyVerifyAuthentication(json))
    }

    /// Adds a passkey to the signed-in account.
    func registerPasskey(name: String) async throws {
        let options = try await api.send(API.Auth.passkeyRegisterOptions())
        let request = try PasskeyCoordinator.registration(options: options, fallbackRelyingParty: config.associatedDomainHost)
        let authorization = try await AuthorizationRunner().perform([request])
        let json = try PasskeyCoordinator.registrationJSON(from: authorization.credential)
        try await api.send(API.Auth.passkeyVerifyRegistration(json, name: name))
    }

    func signOut() async {
        _ = try? await api.send(API.Auth.signOut())
    }

    // MARK: - Private

    private var google: GoogleSignIn {
        GoogleSignIn(clientID: config.googleClientID, redirectScheme: config.googleRedirectScheme)
    }

    private func token(from endpoint: Endpoint<EmptyResponse>) async throws -> String {
        let (_, response) = try await api.sendReturningHeaders(endpoint)
        guard let token = response.value(forHTTPHeaderField: "set-auth-token"), !token.isEmpty else {
            throw AuthFlowError.missingToken
        }
        return token
    }
}
