import Foundation
import Observation

@MainActor
@Observable
final class SignInViewModel {
    enum Step: Equatable { case email, code }
    enum Method: Equatable { case email, apple, google, passkey }

    var step: Step = .email
    var email = ""
    var code = ""
    private(set) var busyMethod: Method?
    var error: String?

    private let auth: AuthService
    private let completeSignIn: (String) async throws -> Void

    init(auth: AuthService, completeSignIn: @escaping (String) async throws -> Void) {
        self.auth = auth
        self.completeSignIn = completeSignIn
    }

    static let codeLength = 6

    var isEmailValid: Bool {
        let trimmed = email.trimmingCharacters(in: .whitespaces)
        return trimmed.contains("@") && trimmed.contains(".") && trimmed.count >= 5
    }

    var showsGoogle: Bool { auth.isGoogleConfigured }

    func sendCode() async {
        guard isEmailValid else {
            error = "Enter a valid email address."
            return
        }
        await run(.email) {
            try await self.auth.sendCode(to: self.email.trimmingCharacters(in: .whitespaces).lowercased())
            self.code = ""
            self.step = .code
        }
    }

    /// Called as digits are typed; submits automatically once all six are in.
    func codeChanged() async {
        code = String(code.filter(\.isNumber).prefix(Self.codeLength))
        guard code.count == Self.codeLength, busyMethod == nil else { return }
        await run(.email) {
            let token = try await self.auth.signIn(email: self.email.trimmingCharacters(in: .whitespaces).lowercased(), code: self.code)
            try await self.completeSignIn(token)
        }
        if error != nil { code = "" }
    }

    func useDifferentEmail() {
        step = .email
        code = ""
    }

    func signInWithApple() async {
        await run(.apple) { try await self.completeSignIn(try await self.auth.signInWithApple()) }
    }

    func signInWithGoogle() async {
        await run(.google) { try await self.completeSignIn(try await self.auth.signInWithGoogle()) }
    }

    func signInWithPasskey() async {
        await run(.passkey) { try await self.completeSignIn(try await self.auth.signInWithPasskey()) }
    }

    private func run(_ method: Method, _ work: @escaping () async throws -> Void) async {
        guard busyMethod == nil else { return }
        busyMethod = method
        defer { busyMethod = nil }
        do {
            try await work()
        } catch where error.isCancellation {
            // User backed out of a system sheet.
        } catch {
            self.error = error.userMessage
        }
    }
}
