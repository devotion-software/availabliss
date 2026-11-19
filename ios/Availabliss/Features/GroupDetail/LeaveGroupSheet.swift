import SwiftUI

/// Confirms leaving the group, from Group settings. A group always keeps an owner (and so an admin),
/// following the server's rules:
/// - a member, or an admin while another admin stays, just confirms;
/// - with nobody else an admin, you first pick someone and make them an admin;
/// - the owner then picks which admin becomes the owner;
/// - the only member can't leave: they delete the group instead.
struct LeaveGroupSheet: View {
    let group: Group
    let me: String
    let api: APIClient
    /// Someone was made an admin here: show it everywhere straight away (the live event follows).
    let onMemberUpdated: (Member) -> Void
    let onLeft: () -> Void
    /// Only member, and the owner: close this and open Delete group.
    let onDeleteInstead: () -> Void
    @Environment(\.dismiss) private var dismiss
    @Environment(\.theme) private var theme
    @State private var picked: String?
    @State private var isWorking = false
    @State private var error: String?

    private var myRole: Role { group.member(me)?.role ?? group.role }
    private var others: [Member] { group.members.filter { $0.userId != me } }
    private var otherAdmins: [Member] { others.filter { $0.role.isAdmin } }
    /// Before an admin can go, someone else has to be an admin.
    private var needsAdmin: Bool { myRole.isAdmin && otherAdmins.isEmpty }
    private var choosesOwner: Bool { myRole == .owner && !otherAdmins.isEmpty }

    var body: some View {
        BottomSheet(title: others.isEmpty ? "You\u{2019}re the only member" : needsAdmin ? "Make someone an admin" : "Leave group?") {
            if others.isEmpty {
                caption("Nobody else is in \(group.name), so there\u{2019}s no one to hand it to. Delete the group instead if you\u{2019}re done with it.")
                if myRole == .owner {
                    Button {
                        onDeleteInstead()
                    } label: {
                        Label("Delete group", systemImage: "trash")
                    }
                    .buttonStyle(.filled(.danger))
                }
                Button("Cancel") { dismiss() }.buttonStyle(.filled(.secondary))
            } else if needsAdmin {
                caption("A group needs an admin, and nobody else in \(group.name) is one. Choose someone to make an admin, then you can leave.")
                picker(others)
                Button {
                    Task { await makeAdmin() }
                } label: {
                    LoadingLabel(title: "Make admin", systemImage: "shield", isLoading: isWorking)
                }
                .buttonStyle(.filled(.accent))
                .disabled(picked == nil || isWorking)
                Button("Cancel") { dismiss() }.buttonStyle(.filled(.secondary))
            } else {
                if choosesOwner {
                    caption("Choose who becomes the owner. They\u{2019}ll look after \(group.name), and its bank holidays will follow their region.")
                    picker(otherAdmins)
                }
                caption("You\u{2019}ll need a new invite to come back.")
                Button {
                    Task { await leave() }
                } label: {
                    LoadingLabel(title: "Leave group", systemImage: "rectangle.portrait.and.arrow.right", isLoading: isWorking)
                }
                .buttonStyle(.filled(.warning))
                .disabled(isWorking || (choosesOwner && picked == nil))
                Button("Cancel") { dismiss() }.buttonStyle(.filled(.secondary))
            }
        }
        .onAppear(perform: preselect)
        .onChange(of: needsAdmin) { preselect() }
        .errorAlert($error)
    }

    private func caption(_ text: String) -> some View {
        Text(text)
            .bodyFont(15)
            .foregroundStyle(theme.muted)
            .fixedSize(horizontal: false, vertical: true)
    }

    /// One person to pick: a lone admin is picked for you when choosing the owner.
    private func preselect() {
        picked = choosesOwner && otherAdmins.count == 1 ? otherAdmins[0].userId : nil
    }

    private func picker(_ members: [Member]) -> some View {
        VStack(spacing: 0) {
            ForEach(members) { member in
                Button {
                    picked = member.userId
                } label: {
                    HStack(spacing: 12) {
                        Avatar(name: member.name, imageURL: member.image, color: theme.tile(forUser: member.userId))
                        Text(member.name).bodyFont(15, .semibold).lineLimit(1)
                        Spacer(minLength: 4)
                        if member.role != .member {
                            StatusPill(text: member.role.label, tone: member.role == .owner ? .indigo : .neutral)
                        }
                        Image(systemName: picked == member.userId ? "checkmark.circle.fill" : "circle")
                            .font(.system(size: 20))
                            .foregroundStyle(picked == member.userId ? theme.accentInk : theme.muted)
                    }
                    .frame(minHeight: 56)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(picked == member.userId ? .isSelected : [])
                if member.userId != members.last?.userId { Rectangle().fill(theme.line).frame(height: 1) }
            }
        }
        .padding(.horizontal, 14)
        .glass(cornerRadius: 20)
    }

    /// The Members screen's Make admin, for whoever is picked.
    private func makeAdmin() async {
        guard let picked else { return }
        isWorking = true
        defer { isWorking = false }
        do {
            onMemberUpdated(try await api.send(API.Groups.setRole(groupId: group.id, userId: picked, role: .admin)))
        } catch {
            self.error = error.userMessage
        }
    }

    private func leave() async {
        isWorking = true
        defer { isWorking = false }
        do {
            try await api.send(API.Groups.leave(groupId: group.id, newOwnerId: choosesOwner ? picked : nil))
            dismiss()
            onLeft()
        } catch {
            self.error = error.userMessage
        }
    }
}
