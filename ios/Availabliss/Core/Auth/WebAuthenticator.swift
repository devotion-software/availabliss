import AuthenticationServices
import UIKit

/// Async wrapper around `ASWebAuthenticationSession`, used for Google sign-in and
/// for connecting Google / Microsoft calendars.
@MainActor
final class WebAuthenticator: NSObject, ASWebAuthenticationPresentationContextProviding {
    private var session: ASWebAuthenticationSession?

    /// Opens `url` and returns the callback URL the flow redirects to.
    func authenticate(url: URL, callbackScheme: String, ephemeral: Bool = false) async throws -> URL {
        try await withCheckedThrowingContinuation { continuation in
            let completion: ASWebAuthenticationSession.CompletionHandler = { [weak self] callbackURL, error in
                MainActor.assumeIsolated {
                    self?.session = nil
                    if let callbackURL {
                        continuation.resume(returning: callbackURL)
                    } else if let error = error as? ASWebAuthenticationSessionError, error.code == .canceledLogin {
                        continuation.resume(throwing: CancellationError())
                    } else {
                        continuation.resume(throwing: error ?? CancellationError())
                    }
                }
            }
            let session = ASWebAuthenticationSession(url: url, callbackURLScheme: callbackScheme, completionHandler: completion)
            session.presentationContextProvider = self
            session.prefersEphemeralWebBrowserSession = ephemeral
            self.session = session
            if !session.start() {
                self.session = nil
                continuation.resume(throwing: AuthFlowError.invalidServerResponse)
            }
        }
    }

    nonisolated func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor {
        MainActor.assumeIsolated { UIApplication.shared.keyWindow ?? ASPresentationAnchor() }
    }
}
