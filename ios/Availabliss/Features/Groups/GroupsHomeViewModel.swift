import Foundation
import Observation

@MainActor
@Observable
final class GroupsHomeViewModel {
    private(set) var groups: [GroupSummary] = []
    /// The next confirmed plans across all groups, soonest first (a few at most), for the Up next pager.
    private(set) var upNext: [GroupEvent] = []
    private(set) var isLoading = false
    private(set) var loadError: String?
    var error: String?

    let api: APIClient
    static let upNextLimit = 5

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
            WidgetSync.shared.onGroups(groups)
            let now = Date()
            upNext = await Array((upcoming?.events ?? []).filter { $0.end > now }.sorted { $0.start < $1.start }.prefix(Self.upNextLimit))
            loadError = nil
        } catch  where !error.isCancellation {
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

    func group(_ id: String) -> GroupSummary? {
        groups.first { $0.id == id }
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
