import SwiftUI

/// Members & invites: share an invite link, manage roles. (The group's photo, Leave group and
/// Delete group are in Group settings.)
struct MembersView: View {
    @Environment(AppModel.self) private var app
    @Environment(\.theme) private var theme
    @State private var model: MembersViewModel
    @State private var confirmation: Confirmation?
    /// The member whose name in the group is being changed.
    @State private var renaming: Member?

    private enum Confirmation: Identifiable {
        case remove(Member)
        var id: String {
            switch self {
            case .remove(let member): "remove-\(member.userId)"
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
                if model.isAdmin {
                    inviteCard
                }
                if !model.sortedMembers.isEmpty {
                    GlassList(data: model.sortedMembers, id: \.userId, header: Formatting.plural(model.sortedMembers.count, "member"), rowMinHeight: 58) { member in
                        MemberRow(member: member, isMe: member.userId == me, canManage: canManage(member), canRename: canRename(member)) { action in
                            switch action {
                            case .rename: renaming = member
                            case .makeAdmin: Task { await model.setRole(.admin, for: member) }
                            case .makeMember: Task { await model.setRole(.member, for: member) }
                            case .remove: confirmation = .remove(member)
                            }
                        }
                    }
                } else {
                    StatusView(kind: .loading)
                }
            }
            .padding(.horizontal, 20)
            .padding(.top, 20)
            .padding(.bottom, 24)
        }
        .screenBackground(OrbLayout.groups)
        .foregroundStyle(theme.ink)
        .chromeless()
        .task { await model.load() }
        // Someone joining uses up their invite: reload so it leaves the list.
        .task(id: model.groupId) {
            for await event in app.live.events(forGroup: model.groupId) {
                if event.kind == .memberChanged || event.kind == .reconnected { await model.load() }
            }
        }
        .confirmSheet(item: $confirmation) { item in
            switch item {
            case .remove(let member): "Remove \(member.name) from the group?"
            }
        } message: { item in
            switch item {
            case .remove(let member): "\(member.firstName) will lose access to the group's plans and chat."
            }
        } options: { item in
            switch item {
            case .remove(let member):
                [ConfirmOption("Remove \(member.firstName)", kind: .destructive) { Task { await model.remove(member) } }]
            }
        }
        .sheet(item: $renaming) { member in
            let isMe = member.userId == me
            MemberNameSheet(member: member, isMe: isMe, accountName: isMe ? app.session.user?.accountName : nil) { name in
                await model.setName(name, for: member, isMe: isMe)
            }
        }
        .errorAlert($model.error)
        .toast($model.toast)
    }

    private func canManage(_ member: Member) -> Bool {
        guard member.userId != me, member.role != .owner, let role = model.group?.role else { return false }
        return role == .owner || (role == .admin && member.role == .member)
    }

    /// Your own name in the group, or as an admin anyone's.
    private func canRename(_ member: Member) -> Bool { member.userId == me || model.isAdmin }

    /// Two ways in: a link anyone can use, or invites for particular people (by email or username)
    /// that only they can accept. Then the invites still live, each with Revoke.
    private var inviteCard: some View {
        GlassPanel(cornerRadius: 24) {
            Text("Invite people").sectionLabelStyle()
            InviteLinkSection(model: model, groupName: model.group?.name ?? "my group")
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
}

private struct MemberRow: View {
    enum Action { case rename, makeAdmin, makeMember, remove }

    let member: Member
    let isMe: Bool
    let canManage: Bool
    let canRename: Bool
    let perform: (Action) -> Void
    @Environment(\.theme) private var theme

    var body: some View {
        HStack(spacing: 12) {
            Avatar(name: member.name, imageURL: member.image, color: theme.tile(forUser: member.userId))
            VStack(alignment: .leading, spacing: 0) {
                Text(isMe ? "\(member.name) (you)" : member.name).bodyFont(15, .semibold).lineLimit(1)
                HStack(spacing: 6) {
                    Text(member.synced ? "Synced" : "Not synced yet").foregroundStyle(member.synced ? theme.muted : theme.warnInk)
                    if member.nameInGroup != nil {
                        Text("·").foregroundStyle(theme.muted).accessibilityHidden(true)
                        Label("Name in this group", systemImage: "tag")
                            .labelStyle(.titleAndIcon)
                            .foregroundStyle(theme.muted)
                    }
                }
                .bodyFont(12, relativeTo: .caption)
                .lineLimit(1)
            }
            Spacer(minLength: 4)
            if member.role != .member {
                StatusPill(text: member.role.label, tone: member.role == .owner ? .indigo : .neutral)
            }
            if canManage || canRename {
                Menu {
                    if canRename {
                        Button {
                            perform(.rename)
                        } label: {
                            Label("Change name in group", systemImage: "pencil")
                        }
                    }
                    if canManage { manageActions }
                } label: {
                    Image(systemName: "ellipsis").font(.system(size: 16, weight: .semibold)).foregroundStyle(theme.muted).frame(width: 44, height: 44)
                }
                .accessibilityLabel(isMe ? "Your options" : "Manage \(member.name)")
            }
        }
        .accessibilityElement(children: .combine)
    }

    @ViewBuilder private var manageActions: some View {
        if member.role == .member {
            Button {
                perform(.makeAdmin)
            } label: {
                Label("Make admin", systemImage: "shield")
            }
        } else {
            Button {
                perform(.makeMember)
            } label: {
                Label("Remove admin", systemImage: "shield.slash")
            }
        }
        Button(role: .destructive) {
            perform(.remove)
        } label: {
            Label("Remove from group", systemImage: "person.badge.minus")
        }
    }
}

/// Changes the name someone goes by in the group (yours, or as an admin anyone's): prefilled with
/// the name they chose, Save, and a way back to their account name.
private struct MemberNameSheet: View {
    let member: Member
    let isMe: Bool
    /// Yours; others' account names aren't known.
    let accountName: String?
    let save: (String?) async -> Bool
    @Environment(\.dismiss) private var dismiss
    @Environment(\.theme) private var theme
    @State private var name: String
    @State private var isSaving = false

    private static let maxLength = 80

    init(member: Member, isMe: Bool, accountName: String?, save: @escaping (String?) async -> Bool) {
        self.member = member
        self.isMe = isMe
        self.accountName = accountName
        self.save = save
        _name = State(initialValue: member.nameInGroup ?? "")
    }

    private var trimmed: String { name.trimmingCharacters(in: .whitespacesAndNewlines) }
    /// Blank (or, for you, your account name itself) means going by the account name.
    private var nameToSave: String? { trimmed.isEmpty || trimmed == accountName ? nil : trimmed }
    private var changed: Bool { nameToSave != member.nameInGroup }

    var body: some View {
        BottomSheet(title: "Change name in group") {
            Text(caption)
                .bodyFont(13, relativeTo: .subheadline)
                .foregroundStyle(theme.muted)
                .fixedSize(horizontal: false, vertical: true)
            InputField(placeholder: accountName ?? member.name, text: $name)
                .textContentType(isMe ? .nickname : nil)
                .submitLabel(.done)
                .onSubmit { submit(nameToSave) }
                .onChange(of: name) { _, new in if new.count > Self.maxLength { name = String(new.prefix(Self.maxLength)) } }
            Button {
                submit(nameToSave)
            } label: {
                LoadingLabel(title: "Save", isLoading: isSaving)
            }
            .buttonStyle(.accent)
            .disabled(!changed || isSaving)
            if member.nameInGroup != nil {
                Button(isMe ? "Use my account name" : "Use their account name") { submit(nil) }
                    .buttonStyle(.textAction)
                    .frame(maxWidth: .infinity)
                    .disabled(isSaving)
            }
        }
    }

    private var caption: String {
        if isMe {
            let account = accountName.map { ", \($0)" } ?? ""
            return "Only this group sees it. Leave it blank to go by your account name\(account)."
        }
        return "Only this group sees it. Leave it blank for \(member.firstName) to go by their account name."
    }

    private func submit(_ value: String?) {
        guard value != member.nameInGroup, !isSaving else { return }
        Task {
            isSaving = true
            defer { isSaving = false }
            if await save(value) { dismiss() }
        }
    }
}

/// "sam@example.com" / "@sam" · "Invited · invite expires Saturday 28 November", or a link with
/// "3 of 5 left · expires …", with Revoke.
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
        return invite.isLink ? "\(invite.linkUsage) · expires \(expires)" : "Invited · invite expires \(expires)"
    }
}
