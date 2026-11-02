import SwiftUI

/// Email, Apple and Google sign-in on the You screen. Linking a sign-in that already has its own
/// Availabliss account offers to merge that account into this one.
struct SignInMethodsSection: View {
    @Binding var toast: String?
    @Binding var error: String?
    @Environment(AppModel.self) private var app
    @Environment(\.theme) private var theme
    @State private var accounts: LinkedAccounts?
    @State private var linking: LinkProvider?
    /// A sign-in waiting for the merge to be confirmed, with the token to resend.
    @State private var pending: Pending?

    private struct Pending: Identifiable {
        let provider: LinkProvider
        let idToken: String
        let nonce: String?
        let candidate: MergeCandidate
        var id: String { provider.rawValue }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Sign-in methods").sectionLabelStyle().accessibilityAddTraits(.isHeader)
            Text("Link Apple or Google to sign in with them. If one already has its own account, you can merge it into this one.")
                .bodyFont(13, relativeTo: .subheadline)
                .foregroundStyle(theme.muted)
            VStack(spacing: 0) {
                row(icon: Image(systemName: "envelope"), title: "Email code", subtitle: app.session.user?.email ?? "") {
                    linkedBadge("On")
                }
                ForEach(accounts?.available ?? []) { provider in
                    Rectangle().fill(theme.line).frame(height: 1)
                    row(icon: icon(provider), title: provider.label, subtitle: accounts?.isLinked(provider) == true ? "Linked" : "Not linked") {
                        if accounts?.isLinked(provider) == true {
                            linkedBadge("Linked")
                        } else {
                            Button {
                                Task { await link(provider) }
                            } label: {
                                LoadingLabel(title: "Link", isLoading: linking == provider)
                            }
                            .buttonStyle(.filled(.secondary, height: 36, cornerRadius: 12, fontSize: 14))
                            .fixedSize()
                            .disabled(linking != nil)
                            .accessibilityLabel("Link \(provider.label)")
                        }
                    }
                }
            }
        }
        .padding(16)
        .glass(cornerRadius: 24)
        .task(id: app.accountVersion) { await load() }
        .confirmationDialog(
            pending.map { "Merge \($0.candidate.name.isEmpty ? $0.candidate.email : $0.candidate.name) into this account?" } ?? "",
            isPresented: Binding(get: { pending != nil }, set: { if !$0 { pending = nil } }),
            titleVisibility: .visible,
            presenting: pending
        ) { pending in
            Button("Merge accounts", role: .destructive) { Task { await merge(pending) } }
            Button("Cancel", role: .cancel) {}
        } message: { pending in
            Text(mergeMessage(pending))
        }
    }

    private func row(icon: Image, title: String, subtitle: String, @ViewBuilder trailing: () -> some View) -> some View {
        HStack(spacing: 12) {
            icon
                .font(.system(size: 16, weight: .semibold))
                .frame(width: 34, height: 34)
                .background(theme.surface, in: RoundedRectangle(cornerRadius: 11, style: .continuous))
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 1) {
                Text(title).bodyFont(15, .semibold)
                Text(subtitle).bodyFont(12, relativeTo: .caption).foregroundStyle(theme.muted).lineLimit(1)
            }
            Spacer(minLength: 8)
            trailing()
        }
        .frame(minHeight: 56)
        .accessibilityElement(children: .combine)
    }

    private func linkedBadge(_ text: String) -> some View {
        Label(text, systemImage: "checkmark")
            .bodyFont(12, .semibold, relativeTo: .caption)
            .foregroundStyle(theme.okInk)
    }

    private func icon(_ provider: LinkProvider) -> Image {
        provider == .apple ? Image(systemName: "apple.logo") : Image(systemName: "g.circle")
    }

    private func mergeMessage(_ pending: Pending) -> String {
        let groups = pending.candidate.groups == 1 ? "1 group" : "\(pending.candidate.groups) groups"
        return "That \(pending.provider.label) sign-in belongs to \(pending.candidate.email) (in \(groups)). Its groups, busy times, calendars, chats and Pro move into this account, then it's deleted. This can't be undone."
    }

    private func load() async {
        if let accounts = try? await app.api.send(API.Me.accounts()) { self.accounts = accounts }
    }

    private func link(_ provider: LinkProvider) async {
        linking = provider
        defer { linking = nil }
        do {
            let token = try await app.auth.linkToken(provider)
            do {
                _ = try await app.auth.link(provider, idToken: token.idToken, nonce: token.nonce, merge: false)
                toast = "\(provider.label) linked"
                await load()
            } catch APIError.mergeCandidate(let candidate, _) {
                pending = Pending(provider: provider, idToken: token.idToken, nonce: token.nonce, candidate: candidate)
            }
        } catch where error.isCancellation {
        } catch {
            self.error = error.userMessage
        }
    }

    private func merge(_ pending: Pending) async {
        linking = pending.provider
        defer { linking = nil }
        do {
            _ = try await app.auth.link(pending.provider, idToken: pending.idToken, nonce: pending.nonce, merge: true)
            toast = "Accounts merged"
            await app.accountMerged()
        } catch {
            self.error = error.userMessage
        }
    }
}
