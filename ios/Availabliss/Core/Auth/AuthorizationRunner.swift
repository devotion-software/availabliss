import AuthenticationServices
import UIKit

enum AuthFlowError: LocalizedError {
    case missingToken
    case invalidServerResponse
    case notConfigured(String)
    /// The provider (e.g. Google's OAuth endpoints) returned an error.
    case provider(String, message: String)

    var errorDescription: String? {
        switch self {
        case .missingToken: "Signed in, but the server didn't return a session token."
        case .invalidServerResponse: "The sign-in response wasn't what we expected."
        case .notConfigured(let what): "\(what) isn't set up for this build."
        case .provider(let what, let message): "\(what) didn't work (\(message)). Please try again."
        }
    }
}

/// Runs an `ASAuthorizationController` (Apple ID or passkey requests) as a single async call.
@MainActor
final class AuthorizationRunner: NSObject {
    private var continuation: CheckedContinuation<ASAuthorization, Error>?
    private var controller: ASAuthorizationController?

    func perform(_ requests: [ASAuthorizationRequest], preferImmediatelyAvailableCredentials: Bool = false) async throws -> ASAuthorization {
        try await withCheckedThrowingContinuation { continuation in
            self.continuation = continuation
            let controller = ASAuthorizationController(authorizationRequests: requests)
            controller.delegate = self
            controller.presentationContextProvider = self
            self.controller = controller
            if preferImmediatelyAvailableCredentials {
                controller.performRequests(options: .preferImmediatelyAvailableCredentials)
            } else {
                controller.performRequests()
            }
        }
    }

    private func finish(_ result: Result<ASAuthorization, Error>) {
        continuation?.resume(with: result)
        continuation = nil
        controller = nil
    }
}

extension AuthorizationRunner: ASAuthorizationControllerDelegate {
    nonisolated func authorizationController(controller: ASAuthorizationController, didCompleteWithAuthorization authorization: ASAuthorization) {
        MainActor.assumeIsolated { finish(.success(authorization)) }
    }

    nonisolated func authorizationController(controller: ASAuthorizationController, didCompleteWithError error: Error) {
        MainActor.assumeIsolated {
            if let authError = error as? ASAuthorizationError, authError.code == .canceled {
                finish(.failure(CancellationError()))
            } else {
                finish(.failure(error))
            }
        }
    }
}

extension AuthorizationRunner: ASAuthorizationControllerPresentationContextProviding {
    nonisolated func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        MainActor.assumeIsolated { UIApplication.shared.keyWindow ?? ASPresentationAnchor() }
    }
}

extension UIApplication {
    /// The foreground key window, used to anchor system auth sheets.
    var keyWindow: UIWindow? {
        connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first(where: \.isKeyWindow)
    }
}
