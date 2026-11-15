import SwiftUI

/// Members & invites: share an invite link, manage roles, leave or delete the group.
struct MembersView: View {
    @Environment(AppModel.self) private var app
    @Environment(\.theme) private var theme
    @State private var model: MembersViewModel
    @State private var confirmation: Confirmation?

    private enum Confirmation: Identifiable {
        case remove(Member), leave, delete
        var id: String {
            switch self {
            case .remove(let member): "remove-\(member.userId)"
            case .leave: "leave"
            case .delete: "delete"
            }
        }
    }

    init(groupId: String, api: APIClient) {
        _model = State(initialValue: MembersViewModel(groupId: groupId, api: api))
    }

    private var me: String? { app.session.user?.id }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                ScreenHeader(title: "Members", subtitle: model.group?.name, onBack: { app.router.pop() })
                if model.isAdmin { inviteCard }
                if !model.sortedMembers.isEmpty {
                    GlassList(data: model.sortedMembers, id: \.userId, header: Formatting.plural(model.sortedMembers.count, "member"), rowMinHeight: 58) { member in
                        MemberRow(member: member, isMe: member.userId == me, canManage: canManage(member)) { action in
                            switch action {
                            case .makeAdmin: Task { await model.setRole(.admin, for: member) }
                            case .makeMember: Task { await model.setRole(.member, for: member) }
                            case .remove: confirmation = .remove(member)
                            }
                        }
                    }
                } else {
                    StatusView(kind: .loading)
                }
                dangerZone
            }
            .padding(.horizontal, 20)
            .padding(.top, 20)
            .padding(.bottom, 24)
        }
        .screenBackground(OrbLayout.groups)
        .foregroundStyle(theme.ink)
        .chromeless()
        .task { await model.load() }
        .confirmationDialog(confirmationTitle, isPresented: Binding(get: { confirmation != nil }, set: { if !$0 { confirmation = nil } }), titleVisibility: .visible, presenting: confirmation) { item in
            switch item {
            case .remove(let member):
                Button("Remove \(member.firstName)", role: .destructive) { Task { await model.remove(member) } }
            case .leave:
                Button("Leave group", role: .destructive) {
                    Task { if let me, await model.leave(me: me) { app.router.groupsPath = [] } }
                }
            case .delete:
                Button("Delete group", role: .destructive) {
                    Task { if await model.deleteGroup() { app.router.groupsPath = [] } }
                }
            }
        }
        .errorAlert($model.error)
        .toast($model.toast)
    }

    private var confirmationTitle: String {
        switch confirmation {
        case .remove(let member): "Remove \(member.name) from the group?"
        case .leave: "Leave this group? You'll need a new invite to come back."
        case .delete: "Delete this group for everyone? This can't be undone."
        case nil: ""
        }
    }

    private func canManage(_ member: Member) -> Bool {
        guard member.userId != me, member.role != .owner, let role = model.group?.role else { return false }
        return role == .owner || (role == .admin && member.role == .member)
    }

    /// Two ways in: a link anyone can use, or invites for particular people (by email or username)
    /// that only they can accept. Then the invites still live, each with Revoke.
    private var inviteCard: some View {
        GlassPanel(cornerRadius: 24) {
            Text("Invite people").sectionLabelStyle()
            VStack(alignment: .leading, spacing: 4) {
                Text("Share a link").bodyFont(15, .semibold)
                Text("Anyone with the link can join.").bodyFont(13, relativeTo: .subheadline).foregroundStyle(theme.muted)
            }
            if let link = model.link {
                ShareLink(item: link.url, subject: Text("Join \(model.group?.name ?? "my group") on Availabliss"),
                          message: Text("Join \(model.group?.name ?? "my group") on Availabliss so we can find a time that works.")) {
                    Label("Share invite link", systemImage: "square.and.arrow.up")
                }
                .buttonStyle(.accent)
            } else {
                Button {
                    Task { await model.createLink() }
                } label: {
                    LoadingLabel(title: "Create invite link", systemImage: "link", isLoading: model.isCreatingInvite)
                }
                .buttonStyle(.accent)
            }
            Rectangle().fill(theme.line).frame(height: 1).padding(.vertical, 4)
            VStack(alignment: .leading, spacing: 4) {
                Text("Or invite particular people").bodyFont(15, .semibold)
                Text("Add an email or a username. Each person gets their own invite, and only they can use it.")
                    .bodyFont(13, relativeTo: .subheadline)
                    .foregroundStyle(theme.muted)
                    .fixedSize(horizontal: false, vertical: true)
            }
            InviteRecipientsField(draft: model.draft)
            Button(action: sendInvites) {
                LoadingLabel(title: "Send invites", systemImage: "paperplane", isLoading: model.isSendingInvites)
            }
            .buttonStyle(.filled(.secondary, height: 44, cornerRadius: 14, fontSize: 14))
            .disabled(model.draft.isEmpty || model.draft.isAdding || model.isSendingInvites)
            if !model.invites.isEmpty {
                Rectangle().fill(theme.line).frame(height: 1).padding(.vertical, 4)
                Text("Invites").bodyFont(15, .semibold)
                VStack(spacing: 0) {
                    ForEach(model.invites) { invite in
                        InviteRow(invite: invite) { Task { await model.revoke(invite) } }
                        if invite.id != model.invites.last?.id { Rectangle().fill(theme.line).frame(height: 1) }
                    }
                }
            }
        }
    }

    private func sendInvites() {
        Task { await model.sendInvites() }
    }

    private var dangerZone: some View {
        VStack(spacing: 4) {
            if model.group != nil && !model.isOwner {
                Button("Leave group") { confirmation = .leave }.buttonStyle(.textAction).foregroundStyle(theme.warnInk)
            }
            if model.isOwner {
                Button("Delete group") { confirmation = .delete }.buttonStyle(.textAction).foregroundStyle(theme.warnInk)
            }
        }
        .frame(maxWidth: .infinity)
    }
}

private struct MemberRow: View {
    enum Action { case makeAdmin, makeMember, remove }

    let member: Member
    let isMe: Bool
    let canManage: Bool
    let perform: (Action) -> Void
    @Environment(\.theme) private var theme

    var body: some View {
        HStack(spacing: 12) {
            Avatar(name: member.name, imageURL: member.image, color: theme.tile(forUser: member.userId))
            VStack(alignment: .leading, spacing: 0) {
                Text(isMe ? "\(member.name) (you)" : member.name).bodyFont(15, .semibold).lineLimit(1)
                Text(member.synced ? "Synced" : "Not synced yet").bodyFont(12, relativeTo: .caption).foregroundStyle(member.synced ? theme.muted : theme.warnInk)
            }
            Spacer(minLength: 4)
            if member.role != .member {
                StatusPill(text: member.role.label, tone: member.role == .owner ? .indigo : .neutral)
            }
            if canManage {
                Menu {
                    if member.role == .member {
                        Button { perform(.makeAdmin) } label: { Label("Make admin", systemImage: "shield") }
                    } else {
                        Button { perform(.makeMember) } label: { Label("Remove admin", systemImage: "shield.slash") }
                    }
                    Button(role: .destructive) { perform(.remove) } label: { Label("Remove from group", systemImage: "person.badge.minus") }
                } label: {
                    Image(systemName: "ellipsis").font(.system(size: 16, weight: .semibold)).foregroundStyle(theme.muted).frame(width: 44, height: 44)
                }
                .accessibilityLabel("Manage \(member.name)")
            }
        }
        .accessibilityElement(children: .combine)
    }
}

/// "sam@example.com" / "@sam" · "Invited · invite expires Saturday 28 November" with Revoke.
private struct InviteRow: View {
    let invite: GroupInvite
    let revoke: () -> Void
    @Environment(\.theme) private var theme

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: icon)
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(theme.accentInk)
                .frame(width: 30, height: 30)
                .background(RoundedRectangle(cornerRadius: 9).fill(theme.accentSoft))
            VStack(alignment: .leading, spacing: 1) {
                Text(invite.recipient ?? invite.url.absoluteString)
                    .bodyFont(14, .semibold)
                    .lineLimit(1)
                    .truncationMode(.middle)
                Text(status).bodyFont(12, relativeTo: .caption).foregroundStyle(theme.muted).lineLimit(2)
            }
            Spacer(minLength: 4)
            Button("Revoke", action: revoke)
                .bodyFont(13, .semibold, relativeTo: .subheadline)
                .foregroundStyle(theme.warnInk)
                .frame(minHeight: 44)
                .accessibilityLabel(invite.recipient.map { "Revoke the invite for \($0)" } ?? "Revoke this link")
        }
        .frame(minHeight: 56)
    }

    private var icon: String {
        if invite.username != nil { return "person" }
        return invite.email == nil ? "link" : "envelope"
    }

    private var status: String {
        let expires = invite.expiresAt.formatted(.dateTime.weekday(.wide).day().month(.wide))
        return invite.isLink ? "Anyone with the link · expires \(expires)" : "Invited · invite expires \(expires)"
    }
}
