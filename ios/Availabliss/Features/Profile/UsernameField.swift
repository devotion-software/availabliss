import Observation
import SwiftUI

/// Choosing your username: local rules first, then a debounced `GET /usernames/:username`.
/// Used in the sign-up wizard's "About you" and on You.
@MainActor
@Observable
final class UsernameFieldModel {
    enum Status: Equatable {
        /// Still your current username: nothing to save.
        case unchanged
        case checking
        case problem(String)
        /// Someone else has it.
        case taken
        case available
        /// The check failed (offline, rate limited): saving lets the server decide.
        case unverified
    }

    private(set) var text = ""
    private(set) var status: Status = .unchanged
    /// The account's username as saved.
    private(set) var current: String?
    private var prefilled = false
    private var lookup: Task<Void, Never>?
    private let api: APIClient

    init(api: APIClient) {
        self.api = api
    }

    var normalized: String { Username.normalize(text) }
    var hasChanges: Bool { normalized != (current ?? "") }
    var canSave: Bool {
        switch status {
        case .unchanged, .available, .unverified: true
        case .checking, .problem, .taken: false
        }
    }
    /// What to send in `PATCH /me`: nil when it hasn't changed.
    var patchValue: String? { hasChanges ? normalized : nil }

    /// Fills in the account's username once (later calls keep what's been typed).
    func prefill(_ username: String?) {
        guard !prefilled else { return }
        prefilled = true
        current = username
        text = username ?? ""
        status = .unchanged
    }

    /// Typed text: the "@" is shown as a prefix, so a pasted "@Sam" becomes "sam".
    func setText(_ value: String) {
        let cleaned = Username.normalize(value)
        guard cleaned != text else { return }
        text = cleaned
        validate()
    }

    /// After a successful `PATCH /me`.
    func saved(_ username: String?) {
        current = username
        text = username ?? text
        validate()
    }

    /// Shows a save failure inline when it's about the username. Returns true if it was.
    func handleSaveError(_ error: Error) -> Bool {
        guard let error = error as? APIError else { return false }
        switch error {
        case .conflict:
            lookup?.cancel()
            status = .taken
            return true
        case .validation(_, let issues):
            guard let issue = issues.first(where: { $0.path == "username" }) else { return false }
            lookup?.cancel()
            status = .problem(issue.message)
            return true
        default:
            return false
        }
    }

    private func validate() {
        lookup?.cancel()
        let username = normalized
        guard hasChanges else {
            status = .unchanged
            return
        }
        if let problem = Username.problem(username) {
            status = .problem(problem)
            return
        }
        status = .checking
        lookup = Task { [api] in
            try? await Task.sleep(for: .milliseconds(400))
            guard !Task.isCancelled else { return }
            let result: Status
            do {
                let check = try await api.send(API.Usernames.check(username))
                if let problem = check.problem {
                    result = .problem(problem)
                } else {
                    result = check.available ? .available : .taken
                }
            } catch {
                result = .unverified
            }
            guard !Task.isCancelled, username == self.normalized else { return }
            self.status = result
        }
    }
}

/// "@ samtaylor ✓" with the problem, or "Taken", underneath.
struct UsernameField: View {
    let model: UsernameFieldModel
    var focusOnAppear = false
    @Environment(\.theme) private var theme
    @FocusState private var isFocused: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 2) {
                Text("@").bodyFont(16, .semibold).foregroundStyle(theme.muted).accessibilityHidden(true)
                TextField("", text: Binding(get: { model.text }, set: { model.setText($0) }),
                          prompt: Text("username").foregroundStyle(theme.muted))
                    .bodyFont(16)
                    .foregroundStyle(theme.ink)
                    .textContentType(.username)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .submitLabel(.done)
                    .focused($isFocused)
                    .accessibilityLabel("Username")
                    .accessibilityValue(model.text.isEmpty ? "" : "@\(model.text)")
                    .accessibilityHint(message ?? "")
                indicator.frame(width: 22)
            }
            .padding(.leading, 16)
            .padding(.trailing, 12)
            .inputChrome(isFocused: isFocused)
            if let message {
                Text(message)
                    .bodyFont(12, relativeTo: .caption)
                    .foregroundStyle(theme.warnInk)
                    .transition(.opacity)
                    .accessibilityHidden(true)
            }
        }
        .animation(.easeOut(duration: 0.15), value: model.status)
        .onAppear { if focusOnAppear { isFocused = true } }
    }

    private var message: String? {
        switch model.status {
        case .problem(let problem): problem
        case .taken: "Taken"
        default: nil
        }
    }

    @ViewBuilder
    private var indicator: some View {
        switch model.status {
        case .checking:
            ProgressView().controlSize(.small)
        case .available:
            Image(systemName: "checkmark.circle.fill")
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(theme.okInk)
                .accessibilityLabel("Available")
        default:
            Color.clear
        }
    }
}
