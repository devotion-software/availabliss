import AuthenticationServices
import CryptoKit
import Foundation

/// Sign in with Apple: returns the identity token plus the raw nonce Better Auth verifies.
@MainActor
struct AppleSignIn {
    struct Result {
        let identityToken: String
        let rawNonce: String
    }

    func signIn() async throws -> Result {
        let rawNonce = Nonce.random()
        let request = ASAuthorizationAppleIDProvider().createRequest()
        request.requestedScopes = [.fullName, .email]
        request.nonce = Nonce.sha256(rawNonce)

        let authorization = try await AuthorizationRunner().perform([request])
        guard let credential = authorization.credential as? ASAuthorizationAppleIDCredential,
              let tokenData = credential.identityToken,
              let token = String(data: tokenData, encoding: .utf8)
        else { throw AuthFlowError.invalidServerResponse }
        return Result(identityToken: token, rawNonce: rawNonce)
    }
}

/// Google sign-in without the GoogleSignIn SDK: OAuth 2.0 authorization code + PKCE in
/// `ASWebAuthenticationSession`, then exchange the code for an ID token. iOS OAuth clients
/// are public clients, so no secret is involved.
@MainActor
struct GoogleSignIn {
    let clientID: String
    /// Reversed client ID, e.g. `com.googleusercontent.apps.123-abc` (registered as a URL scheme).
    let redirectScheme: String

    private static let authorizeURL = URL(string: "https://accounts.google.com/o/oauth2/v2/auth")
    private static let tokenURL = URL(string: "https://oauth2.googleapis.com/token")

    var isConfigured: Bool { !clientID.isEmpty && redirectScheme.hasPrefix("com.googleusercontent.apps.") }

    func signIn() async throws -> String {
        guard isConfigured, let authorizeURL = Self.authorizeURL, let tokenURL = Self.tokenURL else {
            throw AuthFlowError.notConfigured("Google sign-in")
        }
        let verifier = Nonce.random(length: 64)
        let challenge = Base64URL.encode(Data(SHA256.hash(data: Data(verifier.utf8))))
        let state = Nonce.random(length: 24)
        let redirectURI = "\(redirectScheme):/oauth2redirect"

        var components = URLComponents(url: authorizeURL, resolvingAgainstBaseURL: false)
        components?.queryItems = [
            URLQueryItem(name: "client_id", value: clientID),
            URLQueryItem(name: "redirect_uri", value: redirectURI),
            URLQueryItem(name: "response_type", value: "code"),
            URLQueryItem(name: "scope", value: "openid email profile"),
            URLQueryItem(name: "code_challenge", value: challenge),
            URLQueryItem(name: "code_challenge_method", value: "S256"),
            URLQueryItem(name: "state", value: state),
            URLQueryItem(name: "prompt", value: "select_account"),
        ]
        guard let url = components?.url else { throw AuthFlowError.invalidServerResponse }

        let callback = try await WebAuthenticator().authenticate(url: url, callbackScheme: redirectScheme)
        let items = URLComponents(url: callback, resolvingAgainstBaseURL: false)?.queryItems ?? []
        guard items.first(where: { $0.name == "state" })?.value == state,
              let code = items.first(where: { $0.name == "code" })?.value
        else { throw AuthFlowError.invalidServerResponse }

        var request = URLRequest(url: tokenURL)
        request.httpMethod = "POST"
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        let form: [(String, String)] = [
            ("code", code), ("client_id", clientID), ("redirect_uri", redirectURI),
            ("grant_type", "authorization_code"), ("code_verifier", verifier),
        ]
        request.httpBody = Data(form.map { "\($0)=\($1.formEncoded)" }.joined(separator: "&").utf8)

        let (data, _) = try await URLSession.shared.data(for: request)
        struct TokenResponse: Decodable { let id_token: String? }
        guard let idToken = try JSONDecoder().decode(TokenResponse.self, from: data).id_token else {
            throw AuthFlowError.invalidServerResponse
        }
        return idToken
    }
}

enum Nonce {
    static func random(length: Int = 32) -> String {
        let charset = Array("0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz-._")
        var generator = SystemRandomNumberGenerator()
        return String((0..<length).map { _ in charset[Int.random(in: 0..<charset.count, using: &generator)] })
    }

    static func sha256(_ input: String) -> String {
        SHA256.hash(data: Data(input.utf8)).map { String(format: "%02x", $0) }.joined()
    }
}

private extension String {
    var formEncoded: String {
        addingPercentEncoding(withAllowedCharacters: .alphanumerics.union(CharacterSet(charactersIn: "-._~"))) ?? self
    }
}
