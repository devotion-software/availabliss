import Foundation
import Observation

@MainActor
@Observable
final class GroupsHomeViewModel {
    private(set) var groups: [GroupSummary] = []
    private(set) var upNext: GroupEvent?
    private(set) var isLoading = false
    private(set) var loadError: String?
    var error: String?

    let api: APIClient

    init(api: APIClient) {
        self.api = api
    }

    func load() async {
        isLoading = groups.isEmpty
        defer { isLoading = false }
        do {
            async let groupList = api.send(API.Groups.list())
            async let upcoming = try? api.send(API.Me.upcoming())
            groups = try await groupList.groups
            upNext = await upcoming?.events.filter { $0.end > Date() }.min { $0.start < $1.start }
            loadError = nil
        } catch where !error.isCancellation {
            loadError = error.userMessage
        } catch {}
    }

    func addUnread(groupId: String) {
        guard let index = groups.firstIndex(where: { $0.id == groupId }) else { return }
        groups[index].unreadCount += 1
    }

    func memberCount(forGroup id: String) -> Int? {
        groups.first { $0.id == id }?.memberCount
    }
}

extension GroupSummary {
    /// The badge text and tone for the list.
    var statusBadge: (text: String, tone: StatusPill.Tone) {
        switch status {
        case .matches(let count, let noun): return ("\(noun.counted(count)) \(count == 1 ? "works" : "work")", .indigo)
        case .needsReply: return ("Needs your reply", .apricot)
        case .confirmed(_, let start): return (Formatting.shortDay(start), .mint)
        case .searching: return ("Finding times", .neutral)
        }
    }
}

extension Requirement {
    /// What one slot of this requirement is called ("Propose this weekend").
    var slotNoun: SlotNoun {
        switch TimingPreset.detect(length, window: window) {
        case .weekend: .weekend
        case .evening: .evening
        case .oneDay: .day
        case .week: .week
        case .twoHours: .slot
        case .custom:
            switch length {
            case .span, .days: .trip
            case .duration: .slot
            }
        }
    }
}
