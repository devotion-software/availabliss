import Foundation
import Observation

@MainActor
@Observable
final class MembersViewModel {
    private(set) var group: Group?
    private(set) var invite: InviteLink?
    private(set) var isCreatingInvite = false
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

    func load() async {
        do { group = try await api.send(API.Groups.get(id: groupId)) } catch where !error.isCancellation {
            self.error = error.userMessage
        } catch {}
    }

    func createInvite(email: String? = nil) async {
        isCreatingInvite = true
        defer { isCreatingInvite = false }
        do {
            invite = try await api.send(API.Groups.createInvite(groupId: groupId, email: email))
            if email != nil { toast = "Invite sent" }
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
