import Foundation
import Observation

@MainActor
@Observable
final class MembersViewModel {
    private(set) var group: Group?
    /// Live invites, newest first (admins only).
    private(set) var invites: [GroupInvite] = []
    private(set) var isCreatingInvite = false
    private(set) var isSendingInvites = false
    var error: String?
    var toast: String?
    /// The emails and usernames about to be invited.
    let draft: InviteDraft

    let groupId: String
    private let api: APIClient

    init(groupId: String, api: APIClient) {
        self.groupId = groupId
        self.api = api
        self.draft = InviteDraft(api: api)
    }

    /// Starts from a group already in hand (e.g. one just made, which has no invites yet), so
    /// nothing needs loading first.
    convenience init(group: Group, api: APIClient) {
        self.init(groupId: group.id, api: api)
        self.group = group
    }

    var isAdmin: Bool { group?.role.isAdmin ?? false }

    var sortedMembers: [Member] {
        let order: [Role: Int] = [.owner: 0, .admin: 1, .member: 2]
        return (group?.members ?? []).sorted { (order[$0.role] ?? 3, $0.name) < (order[$1.role] ?? 3, $1.name) }
    }

    /// The newest link anyone can use.
    var link: GroupInvite? { invites.first(where: \.isLink) }

    /// Invites made for particular people (by email or username).
    var personalInvites: [GroupInvite] { invites.filter { !$0.isLink } }

    func load() async {
        do {
            group = try await api.send(API.Groups.get(id: groupId))
            if isAdmin { invites = (try? await api.send(API.Groups.invites(groupId: groupId)).invites) ?? invites }
        } catch  where !error.isCancellation {
            self.error = error.userMessage
        } catch {}
    }

    /// Makes a link that `limit` people can join with. Returns whether it worked.
    @discardableResult
    func createLink(limit: LinkLimit = .standard) async -> Bool {
        isCreatingInvite = true
        defer { isCreatingInvite = false }
        do {
            let made = try await api.send(API.Groups.createInvite(groupId: groupId, maxUses: limit))
            invites.insert(contentsOf: made.invites ?? [], at: 0)
            return true
        } catch {
            self.error = error.userMessage
            return false
        }
    }

    /// Adds whatever's still typed, then invites everyone in `draft`: emails get an emailed invite,
    /// usernames one in their Activity. Returns false (with the reason shown) if anything failed.
    func sendInvites() async -> Bool {
        guard await draft.add() else { return false }
        let emails = draft.emails
        let usernames = draft.usernames
        guard !emails.isEmpty || !usernames.isEmpty, !isSendingInvites else { return false }
        isSendingInvites = true
        defer { isSendingInvites = false }
        do {
            let made = try await api.send(API.Groups.createInvite(groupId: groupId, emails: emails, usernames: usernames))
            invites.removeAll { invite in
                invite.email.map(emails.contains) == true || invite.username.map(usernames.contains) == true
            }
            invites.insert(contentsOf: made.invites ?? [], at: 0)
            let count = emails.count + usernames.count
            toast = count == 1 ? "Invite sent to \(emails.first ?? "@\(usernames[0])")" : "Invites sent to \(count) people"
            draft.clear()
            return true
        } catch {
            self.error = error.userMessage
            return false
        }
    }

    func revoke(_ invite: GroupInvite) async {
        do {
            try await api.send(API.Groups.revokeInvite(groupId: groupId, inviteId: invite.id))
            invites.removeAll { $0.id == invite.id }
            toast = invite.recipient.map { "Invite for \($0) revoked" } ?? "Link revoked: it no longer works"
        } catch {
            self.error = error.userMessage
        }
    }

    func setRole(_ role: Role, for member: Member) async {
        do {
            let updated = try await api.send(API.Groups.setRole(groupId: groupId, userId: member.userId, role: role))
            if let index = group?.members.firstIndex(where: { $0.userId == member.userId }) { group?.members[index] = updated }
        } catch {
            self.error = error.userMessage
        }
    }

    /// Sets (nil: clears) the name `member` goes by in the group: yours, or as an admin anyone's.
    /// Returns whether it worked.
    func setName(_ name: String?, for member: Member, isMe: Bool) async -> Bool {
        do {
            let updated = try await api.send(
                isMe
                    ? API.Groups.setMyName(groupId: groupId, displayName: name)
                    : API.Groups.setName(groupId: groupId, userId: member.userId, displayName: name))
            if let index = group?.members.firstIndex(where: { $0.userId == member.userId }) { group?.members[index] = updated }
            toast = name == nil ? (isMe ? "Back to your account name" : "Back to their account name") : "Name updated"
            return true
        } catch {
            self.error = error.userMessage
            return false
        }
    }

    func remove(_ member: Member) async {
        do {
            try await api.send(API.Groups.removeMember(groupId: groupId, userId: member.userId))
            group?.members.removeAll { $0.userId == member.userId }
        } catch {
            self.error = error.userMessage
        }
    }

}
