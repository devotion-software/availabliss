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
    @State private var isDeclining = false
    @State private var isSwitching = false
    @State private var error: String?
    /// Joined: the sheet then asks what name to go by in the group, and opens it once it goes.
    @State private var joinedGroup: JoinedGroup?
    @State private var nameInGroup = ""
    @State private var isSavingName = false

    private var accountName: String { app.session.user?.accountName ?? "" }
    private var trimmedNameInGroup: String { nameInGroup.trimmingCharacters(in: .whitespacesAndNewlines) }

    /// An emailed invite is only for that address; someone signed in as anyone else can't use it.
    private var invitedOtherEmail: String? {
        guard let invited = preview?.email, let mine = app.session.user?.email,
            invited.caseInsensitiveCompare(mine) != .orderedSame
        else { return nil }
        return invited
    }

    /// Likewise an invite sent by username is only for that account.
    private var invitedOtherUsername: String? {
        guard let invited = preview?.username, let user = app.session.user,
            invited.caseInsensitiveCompare(user.username ?? "") != .orderedSame
        else { return nil }
        return invited
    }

    var body: some View {
        BottomSheet(title: joinedGroup.map { "How should \($0.name) see you?" } ?? "You're invited") {
            if joinedGroup != nil {
                nameStep
            } else if let preview {
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
                    Text(
                        "This invite was sent to \(invited), but you\u{2019}re signed in as \(app.session.user?.email ?? "someone else"). Sign in with \(invited) to join."
                    )
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
                } else if let invited = invitedOtherUsername {
                    Text(
                        "This invite was sent to @\(invited), but you\u{2019}re signed in as \(app.session.user?.username.map { "@\($0)" } ?? "someone else"). Sign in as @\(invited) to join."
                    )
                    .bodyFont(14)
                    .foregroundStyle(theme.warnInk)
                    .padding(12)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(theme.warnBg))
                    .fixedSize(horizontal: false, vertical: true)
                    Button {
                        Task { await switchAccount() }
                    } label: {
                        LoadingLabel(title: "Switch account", isLoading: isSwitching)
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
                    .disabled(isJoining || isDeclining)
                    // Sent to you in Activity (by username, or as one of a group's people): you can turn it down.
                    if preview.personal == true {
                        Button {
                            Task { await decline() }
                        } label: {
                            LoadingLabel(title: "Decline", isLoading: isDeclining)
                        }
                        .buttonStyle(.secondary)
                        .disabled(isJoining || isDeclining)
                    }
                }
                Button("Not now") { dismiss() }.buttonStyle(.textAction).frame(maxWidth: .infinity)
            } else if let loadError {
                StatusView(kind: .error(loadError) { Task { await load() } })
            } else {
                ProgressView().frame(maxWidth: .infinity, minHeight: 120)
            }
        }
        .task { await load() }
        // However the sheet goes (a choice, the close button or a swipe), open the group just joined.
        .onDisappear {
            if let joinedGroup { app.router.open(.group(id: joinedGroup.id)) }
        }
        .errorAlert($error)
    }

    /// After joining: the name to go by in this group, prefilled with the account name. Skippable.
    @ViewBuilder private var nameStep: some View {
        Text("You can go by a different name in this group, like a nickname. Only this group sees it, and you can change it any time in Group settings.")
            .bodyFont(14)
            .foregroundStyle(theme.muted)
            .fixedSize(horizontal: false, vertical: true)
        InputField(placeholder: accountName, text: $nameInGroup)
            .textContentType(.nickname)
            .submitLabel(.done)
            .onSubmit { Task { await useName() } }
        Button {
            Task { await useName() }
        } label: {
            LoadingLabel(title: "Use this name", isLoading: isSavingName)
        }
        .buttonStyle(.primary)
        .disabled(isSavingName || trimmedNameInGroup.isEmpty)
        Button("Keep \(accountName)") { dismiss() }
            .buttonStyle(.textAction)
            .frame(maxWidth: .infinity)
            .disabled(isSavingName)
    }

    private func useName() async {
        guard let joinedGroup, !trimmedNameInGroup.isEmpty, !isSavingName else { return }
        // The account name already: nothing to set.
        guard trimmedNameInGroup != accountName else { return dismiss() }
        isSavingName = true
        defer { isSavingName = false }
        do {
            _ = try await api.send(API.Groups.setMyName(groupId: joinedGroup.id, displayName: trimmedNameInGroup))
            dismiss()
        } catch {
            self.error = error.userMessage
        }
    }

    private func load() async {
        do {
            preview = try await api.send(API.Invites.preview(token: token))
            loadError = nil
        } catch let failure as APIError {
            if case .notFound(let message) = failure {
                // A link that's full says so ("This invite link has been used up"); anything else is gone.
                loadError = message.contains("used up") ? message : "This invite has expired or was already used."
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

    /// Turns the invite down: it goes, with its Activity item, and nobody is told.
    private func decline() async {
        isDeclining = true
        defer { isDeclining = false }
        do {
            _ = try await api.send(API.Invites.decline(token: token))
            dismiss()
        } catch {
            self.error = error.userMessage
        }
    }

    private func join() async {
        isJoining = true
        defer { isJoining = false }
        do {
            let group = try await api.send(API.Invites.accept(token: token))
            if group.joined == false {
                // Already in it: nothing to ask, just open it.
                dismiss()
                app.router.open(.group(id: group.id))
            } else {
                nameInGroup = accountName
                withAnimation(.smooth(duration: 0.25)) { joinedGroup = group }
            }
        } catch {
            self.error = error.userMessage
        }
    }
}
