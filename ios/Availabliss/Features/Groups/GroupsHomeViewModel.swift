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

    private let api: APIClient

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

    func create(name: String, color: String) async -> Group? {
        do {
            let group = try await api.send(API.Groups.create(CreateGroupBody(name: name, color: color, requirement: .defaultValue())))
            await load()
            return group
        } catch {
            self.error = error.userMessage
            return nil
        }
    }

    func memberCount(forGroupNamed name: String?) -> Int? {
        groups.first { $0.name == name }?.memberCount
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
