import SwiftUI

/// Opened from `/invite/:token` (universal link) or `availabliss://invite/:token`.
struct InviteAcceptView: View {
    let token: String
    let api: APIClient
    @Environment(AppModel.self) private var app
    @Environment(\.dismiss) private var dismiss
    @Environment(\.theme) private var theme
    @State private var preview: InvitePreview?
    @State private var loadError: String?
    @State private var isJoining = false
    @State private var isSwitching = false
    @State private var error: String?

    /// An emailed invite is only for that address; someone signed in as anyone else can't use it.
    private var invitedOtherEmail: String? {
        guard let invited = preview?.email, let mine = app.session.user?.email,
              invited.caseInsensitiveCompare(mine) != .orderedSame else { return nil }
        return invited
    }

    var body: some View {
        BottomSheet(title: "You're invited") {
            if let preview {
                HStack(spacing: 14) {
                    VennLogo(size: 52)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(preview.groupName).displayFont(22, .bold)
                        Text("\(preview.inviterName) invited you · \(Formatting.plural(preview.memberCount, "member"))")
                            .bodyFont(13, relativeTo: .subheadline)
                            .foregroundStyle(theme.muted)
                    }
                }
                Label("Your group only ever sees when you're busy, never what you're doing.", systemImage: "shield")
                    .bodyFont(12, relativeTo: .caption)
                    .foregroundStyle(theme.muted)
                if let invited = invitedOtherEmail {
                    Text("This invite was sent to \(invited), but you\u{2019}re signed in as \(app.session.user?.email ?? "someone else"). Sign in with \(invited) to join.")
                        .bodyFont(14)
                        .foregroundStyle(theme.warnInk)
                        .padding(12)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(theme.warnBg))
                        .fixedSize(horizontal: false, vertical: true)
                    Button {
                        Task { await switchAccount() }
                    } label: {
                        LoadingLabel(title: "Sign in as \(invited)", isLoading: isSwitching)
                    }
                    .buttonStyle(.primary)
                    .disabled(isSwitching)
                } else {
                    Button {
                        Task { await join() }
                    } label: {
                        LoadingLabel(title: "Join \(preview.groupName)", isLoading: isJoining)
                    }
                    .buttonStyle(.primary)
                    .disabled(isJoining)
                }
                Button("Not now") { dismiss() }.buttonStyle(.textAction).frame(maxWidth: .infinity)
            } else if let loadError {
                StatusView(kind: .error(loadError) { Task { await load() } })
            } else {
                ProgressView().frame(maxWidth: .infinity, minHeight: 120)
            }
        }
        .task { await load() }
        .errorAlert($error)
    }

    private func load() async {
        do {
            preview = try await api.send(API.Invites.preview(token: token))
            loadError = nil
        } catch let failure as APIError {
            if case .notFound = failure {
                loadError = "This invite has expired or was already used."
            } else {
                loadError = failure.userMessage
            }
        } catch {
            loadError = error.userMessage
        }
    }

    /// Signs out, keeping the invite so it opens again once they've signed in (with its email prefilled).
    private func switchAccount() async {
        isSwitching = true
        await app.signOut()
        app.router.pendingInvite = Router.InviteToken(token: token)
    }

    private func join() async {
        isJoining = true
        defer { isJoining = false }
        do {
            let group = try await api.send(API.Invites.accept(token: token))
            dismiss()
            app.router.open(.group(id: group.id))
        } catch {
            self.error = error.userMessage
        }
    }
}
