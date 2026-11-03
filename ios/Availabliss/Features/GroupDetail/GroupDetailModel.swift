import Foundation
import Observation

/// The group being viewed, shared by its Calendar / Chat / Proposals / Timing tabs.
@MainActor
@Observable
final class GroupDetailModel {
    let groupId: String
    private(set) var group: Group?
    private(set) var loadError: String?
    var unreadCount = 0

    let api: APIClient

    init(groupId: String, api: APIClient) {
        self.groupId = groupId
        self.api = api
    }

    var isAdmin: Bool { group?.role.isAdmin ?? false }

    func load() async {
        do {
            group = try await api.send(API.Groups.get(id: groupId))
            loadError = nil
        } catch where !error.isCancellation {
            loadError = error.userMessage
        } catch {}
    }

    func update(_ group: Group) { self.group = group }

    /// Applies `group.updated` / `member.changed` live events, and refetches after a reconnect.
    func apply(_ event: LiveEvent) async {
        switch event.kind {
        case .reconnected:
            await load()
        case .groupUpdated:
            if let updated = event.payload(as: Group.self) { group = updated }
        case .memberChanged:
            if let removed = event.removedMemberId {
                group?.members.removeAll { $0.userId == removed }
            } else if let member = event.payload(as: Member.self) {
                if let index = group?.members.firstIndex(where: { $0.userId == member.userId }) {
                    group?.members[index] = member
                } else {
                    group?.members.append(member)
                }
            }
            if let members = group?.members {
                group?.memberCount = members.count
                group?.syncedCount = members.filter(\.synced).count
            }
        default:
            break
        }
    }
}
