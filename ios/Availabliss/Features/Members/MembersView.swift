import SwiftUI

/// Members & invites: share an invite link, manage roles, leave or delete the group.
struct MembersView: View {
    @Environment(AppModel.self) private var app
    @Environment(\.theme) private var theme
    @State private var model: MembersViewModel
    @State private var confirmation: Confirmation?
    @State private var inviteEmail = ""

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

    private var inviteCard: some View {
        GlassPanel(cornerRadius: 24) {
            Text("Invite people").sectionLabelStyle()
            if let invite = model.invite {
                HStack(spacing: 8) {
                    Text(invite.url.absoluteString)
                        .bodyFont(13, .medium, relativeTo: .subheadline)
                        .lineLimit(1)
                        .truncationMode(.middle)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .textSelection(.enabled)
                    Text("Expires \(Formatting.shortDay(invite.expiresAt))").bodyFont(11, relativeTo: .caption2).foregroundStyle(theme.muted)
                }
                .padding(.horizontal, 12)
                .frame(minHeight: 44)
                .surfaceBackground(cornerRadius: 14)
                ShareLink(item: invite.url, subject: Text("Join \(model.group?.name ?? "my group") on Availabliss"),
                          message: Text("Join \(model.group?.name ?? "my group") on Availabliss so we can find a time that works.")) {
                    Label("Share invite link", systemImage: "square.and.arrow.up")
                }
                .buttonStyle(.accent)
            } else {
                Button {
                    Task { await model.createInvite() }
                } label: {
                    LoadingLabel(title: "Create invite link", systemImage: "link", isLoading: model.isCreatingInvite)
                }
                .buttonStyle(.accent)
            }
            HStack(spacing: 8) {
                InputField(placeholder: "Or invite by email", text: $inviteEmail, height: 44, cornerRadius: 14)
                    .keyboardType(.emailAddress)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                Button("Send") {
                    let email = inviteEmail.trimmingCharacters(in: .whitespaces)
                    Task {
                        await model.createInvite(email: email)
                        inviteEmail = ""
                    }
                }
                .buttonStyle(.filled(.primary, height: 44, cornerRadius: 14, fontSize: 14, fullWidth: false))
                .disabled(!inviteEmail.contains("@") || model.isCreatingInvite)
            }
        }
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
