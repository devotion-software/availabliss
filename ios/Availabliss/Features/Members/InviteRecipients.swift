import Observation
import SwiftUI

/// The people about to be invited: typed emails, and usernames looked up to show who they are.
/// Used by Members and the sign-up wizard's invite step.
@MainActor
@Observable
final class InviteDraft {
    enum Recipient: Identifiable, Hashable {
        case email(String)
        case person(UserSummary)

        var id: String {
            switch self {
            case .email(let email): "email:\(email)"
            case .person(let person): "user:\(person.id)"
            }
        }
    }

    /// Who the typed username belongs to, looked up as you type.
    enum Lookup: Equatable {
        case none, checking, notFound
        case found(UserSummary)
    }

    static let maxPeople = 20

    private(set) var text = ""
    private(set) var recipients: [Recipient] = []
    private(set) var lookup: Lookup = .none
    /// Why the typed text couldn't be added (it stays typed so it can be fixed).
    private(set) var problem: String?
    private(set) var isAdding = false
    private var found: [String: UserSummary] = [:]
    private var lookupTask: Task<Void, Never>?
    private let api: APIClient

    init(api: APIClient) {
        self.api = api
    }

    var emails: [String] {
        recipients.compactMap { if case .email(let email) = $0 { email } else { nil } }
    }

    var usernames: [String] {
        recipients.compactMap { if case .person(let person) = $0 { person.username } else { nil } }
    }

    var hasTyped: Bool { !Self.tokens(text).isEmpty }
    var isEmpty: Bool { recipients.isEmpty && !hasTyped }

    func setText(_ value: String) {
        guard value != text else { return }
        text = value
        problem = nil
        previewLookup()
    }

    func remove(_ recipient: Recipient) {
        recipients.removeAll { $0.id == recipient.id }
        problem = nil
    }

    func clear() {
        lookupTask?.cancel()
        text = ""
        recipients = []
        lookup = .none
        problem = nil
    }

    /// Adds everything typed. Returns false, with `problem` set and the bad entries left in the
    /// field, when something couldn't be added.
    @discardableResult
    func add() async -> Bool {
        let parts = Self.tokens(text)
        guard !parts.isEmpty, !isAdding else { return problem == nil }
        lookupTask?.cancel()
        isAdding = true
        defer { isAdding = false }
        var leftover: [String] = []
        var firstProblem: String?
        for part in parts {
            var added: Recipient?
            var partProblem: String?
            if Self.isEmailLike(part) {
                let email = part.lowercased()
                if Self.looksLikeEmail(email) {
                    added = .email(email)
                } else {
                    partProblem = "\u{201C}\(part)\u{201D} isn\u{2019}t a valid email address."
                }
            } else {
                let username = Username.normalize(part)
                do {
                    if Username.problem(username) == nil, let person = try await resolve(username) {
                        added = .person(person)
                    } else {
                        partProblem = parts.count == 1 ? "No one has that username" : "No one has the username @\(username)"
                    }
                } catch {
                    partProblem = error.userMessage
                }
            }
            if let added, !recipients.contains(added) {
                if recipients.count >= Self.maxPeople {
                    partProblem = "Invite up to \(Self.maxPeople) people at a time"
                } else {
                    recipients.append(added)
                }
            }
            if let partProblem {
                leftover.append(part)
                firstProblem = firstProblem ?? partProblem
            }
        }
        text = leftover.joined(separator: ", ")
        lookup = .none
        problem = firstProblem
        return firstProblem == nil
    }

    /// Something with an "@" after its first character is meant as an email; anything else is a username.
    static func isEmailLike(_ text: String) -> Bool { text.dropFirst().contains("@") }

    static func looksLikeEmail(_ text: String) -> Bool {
        text.wholeMatch(of: /[^@\s]+@[^@\s]+\.[^@\s]+/) != nil
    }

    private static func tokens(_ text: String) -> [String] {
        text.split(whereSeparator: { $0 == "," || $0 == ";" || $0.isWhitespace }).map(String.init)
    }

    /// While a single username is typed, look it up (debounced: the lookup is rate limited).
    private func previewLookup() {
        lookupTask?.cancel()
        let parts = Self.tokens(text)
        guard parts.count == 1, let only = parts.first, !Self.isEmailLike(only) else {
            lookup = .none
            return
        }
        let username = Username.normalize(only)
        guard username.count >= Username.minLength else {
            lookup = .none
            return
        }
        guard Username.problem(username) == nil else {
            lookup = .notFound
            return
        }
        if let cached = found[username] {
            lookup = .found(cached)
            return
        }
        lookup = .checking
        lookupTask = Task {
            try? await Task.sleep(for: .milliseconds(400))
            guard !Task.isCancelled else { return }
            let result: Lookup
            do {
                result = try await resolve(username).map(Lookup.found) ?? .notFound
            } catch {
                result = .none
            }
            guard !Task.isCancelled else { return }
            self.lookup = result
        }
    }

    private func resolve(_ username: String) async throws -> UserSummary? {
        if let cached = found[username] { return cached }
        let check = try await api.send(API.Usernames.check(username))
        if let person = check.user { found[username] = person }
        return check.user
    }
}

/// "Email or username" field, with who a username belongs to, and the people added so far.
struct InviteRecipientsField: View {
    let draft: InviteDraft
    @Environment(\.theme) private var theme

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            InputField(placeholder: "Email or username", text: Binding(get: { draft.text }, set: { draft.setText($0) }),
                       height: 48, cornerRadius: 14)
                .keyboardType(.emailAddress)
                .textContentType(.emailAddress)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .submitLabel(.next)
                .onSubmit { Task { await draft.add() } }
            feedback
            if !draft.recipients.isEmpty {
                VStack(spacing: 0) {
                    ForEach(draft.recipients) { recipient in
                        RecipientRow(recipient: recipient) { draft.remove(recipient) }
                    }
                }
            }
        }
        .animation(.easeOut(duration: 0.15), value: draft.lookup)
        .animation(.easeOut(duration: 0.15), value: draft.recipients)
    }

    @ViewBuilder
    private var feedback: some View {
        if let problem = draft.problem {
            message(problem)
        } else {
            switch draft.lookup {
            case .none:
                EmptyView()
            case .checking:
                HStack(spacing: 8) {
                    ProgressView().controlSize(.small)
                    Text("Looking\u{2026}").bodyFont(12, relativeTo: .caption).foregroundStyle(theme.muted)
                }
                .frame(minHeight: 30)
            case .notFound:
                message("No one has that username")
            case .found(let person):
                PersonLine(person: person) {
                    Button {
                        Task { await draft.add() }
                    } label: {
                        Label("Add", systemImage: "plus")
                    }
                    .bodyFont(13, .semibold, relativeTo: .subheadline)
                    .foregroundStyle(theme.accentInk)
                    .frame(minWidth: 44, minHeight: 44)
                    .accessibilityLabel("Add \(person.displayName)")
                }
            }
        }
    }

    private func message(_ text: String) -> some View {
        Text(text)
            .bodyFont(12, relativeTo: .caption)
            .foregroundStyle(theme.warnInk)
            .fixedSize(horizontal: false, vertical: true)
    }
}

/// One person or address about to be invited, with a button to take them off.
private struct RecipientRow: View {
    let recipient: InviteDraft.Recipient
    let remove: () -> Void
    @Environment(\.theme) private var theme

    var body: some View {
        switch recipient {
        case .person(let person):
            PersonLine(person: person) { removeButton(person.displayName) }
        case .email(let email):
            HStack(spacing: 10) {
                Image(systemName: "envelope")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(theme.accentInk)
                    .frame(width: 30, height: 30)
                    .background(RoundedRectangle(cornerRadius: 9).fill(theme.accentSoft))
                    .accessibilityHidden(true)
                Text(email).bodyFont(14, .semibold).lineLimit(1).truncationMode(.middle)
                Spacer(minLength: 4)
                removeButton(email)
            }
        }
    }

    private func removeButton(_ who: String) -> some View {
        Button(action: remove) {
            Image(systemName: "xmark.circle.fill")
                .font(.system(size: 18))
                .foregroundStyle(theme.muted)
                .frame(width: 44, height: 44)
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Don\u{2019}t invite \(who)")
    }
}

/// Avatar, name and @username, with something on the end.
private struct PersonLine<Trailing: View>: View {
    let person: UserSummary
    @ViewBuilder let trailing: () -> Trailing
    @Environment(\.theme) private var theme

    var body: some View {
        HStack(spacing: 10) {
            Avatar(name: person.displayName, imageURL: person.image, color: theme.tile(forUser: person.id), size: 30)
            VStack(alignment: .leading, spacing: 0) {
                Text(person.displayName).bodyFont(14, .semibold).lineLimit(1)
                Text("@\(person.username)").bodyFont(12, relativeTo: .caption).foregroundStyle(theme.muted).lineLimit(1)
            }
            .accessibilityElement(children: .combine)
            Spacer(minLength: 4)
            trailing()
        }
    }
}
