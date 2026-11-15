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

    let groupId: String
    private let api: APIClient

    init(groupId: String, api: APIClient) {
        self.groupId = groupId
        self.api = api
    }

    var isAdmin: Bool { group?.role.isAdmin ?? false }
    var isOwner: Bool { group?.role == .owner }

    var sortedMembers: [Member] {
        let order: [Role: Int] = [.owner: 0, .admin: 1, .member: 2]
        return (group?.members ?? []).sorted { (order[$0.role] ?? 3, $0.name) < (order[$1.role] ?? 3, $1.name) }
    }

    /// The newest link anyone can use.
    var link: GroupInvite? { invites.first { $0.email == nil } }

    func load() async {
        do {
            group = try await api.send(API.Groups.get(id: groupId))
            if isAdmin { invites = (try? await api.send(API.Groups.invites(groupId: groupId)).invites) ?? invites }
        } catch where !error.isCancellation {
            self.error = error.userMessage
        } catch {}
    }

    func createLink() async {
        isCreatingInvite = true
        defer { isCreatingInvite = false }
        do {
            let made = try await api.send(API.Groups.createInvite(groupId: groupId))
            invites.insert(contentsOf: made.invites ?? [], at: 0)
        } catch {
            self.error = error.userMessage
        }
    }

    /// Emails each address its own invite. Returns false (with `error` set) if any address is invalid.
    func sendInvites(to text: String) async -> Bool {
        let parts = text.split(whereSeparator: { $0 == "," || $0 == ";" || $0.isWhitespace }).map { $0.lowercased() }
        let emails = Array(NSOrderedSet(array: parts)) as? [String] ?? parts
        if let bad = emails.first(where: { !Self.looksLikeEmail($0) }) {
            error = "\u{201C}\(bad)\u{201D} isn\u{2019}t a valid email address."
            return false
        }
        guard !emails.isEmpty else { return false }
        isSendingInvites = true
        defer { isSendingInvites = false }
        do {
            let made = try await api.send(API.Groups.createInvite(groupId: groupId, emails: emails))
            invites.removeAll { invite in emails.contains(invite.email ?? "") }
            invites.insert(contentsOf: made.invites ?? [], at: 0)
            toast = emails.count == 1 ? "Invite sent to \(emails[0])" : "Invites sent to \(emails.count) people"
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
            toast = invite.email.map { "Invite for \($0) revoked" } ?? "Link revoked: it no longer works"
        } catch {
            self.error = error.userMessage
        }
    }

    private static func looksLikeEmail(_ text: String) -> Bool {
        text.wholeMatch(of: /[^@\s]+@[^@\s]+\.[^@\s]+/) != nil
    }

    func setRole(_ role: Role, for member: Member) async {
        do {
            let updated = try await api.send(API.Groups.setRole(groupId: groupId, userId: member.userId, role: role))
            if let index = group?.members.firstIndex(where: { $0.userId == member.userId }) { group?.members[index] = updated }
        } catch {
            self.error = error.userMessage
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

    func leave(me: String) async -> Bool {
        do {
            try await api.send(API.Groups.removeMember(groupId: groupId, userId: me))
            return true
        } catch {
            self.error = error.userMessage
            return false
        }
    }

    func deleteGroup() async -> Bool {
        do {
            try await api.send(API.Groups.delete(id: groupId))
            return true
        } catch {
            self.error = error.userMessage
            return false
        }
    }
}
