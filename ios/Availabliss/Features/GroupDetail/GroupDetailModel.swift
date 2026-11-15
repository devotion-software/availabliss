import Foundation
import Observation

/// The group being viewed, shared by its calendar and the Chat / Plans / Timing screens pushed over it
/// (see `GroupDetailStore`). It loads once and follows live updates for as long as any of them is open.
@MainActor
@Observable
final class GroupDetailModel {
    let groupId: String
    private(set) var group: Group?
    private(set) var loadError: String?
    var unreadCount = 0
    /// Whether the chat is on screen, so new messages there don't count as unread.
    @ObservationIgnored var isChatVisible = false

    /// Proposals (by date) and upcoming events, for the calendar's Plans card and the Plans screen.
    private(set) var proposals: [Proposal] = []
    private(set) var events: [GroupEvent] = []
    private(set) var plansLoaded = false

    let api: APIClient
    @ObservationIgnored private let live: LiveEventsClient
    @ObservationIgnored nonisolated(unsafe) private var liveTask: Task<Void, Never>?

    init(groupId: String, api: APIClient, live: LiveEventsClient) {
        self.groupId = groupId
        self.api = api
        self.live = live
    }

    deinit { liveTask?.cancel() }

    var isAdmin: Bool { group?.role.isAdmin ?? false }

    /// Subscribes to live updates and loads whatever isn't loaded yet. Safe to call from every screen.
    func start() async {
        if liveTask == nil {
            liveTask = Task { [weak self, live, groupId] in
                for await event in live.events(forGroup: groupId) {
                    guard let self else { return }
                    await self.apply(event)
                }
            }
        }
        if group == nil { await load() }
        if !plansLoaded { try? await loadPlans() }
    }

    func load() async {
        do {
            group = try await api.send(API.Groups.get(id: groupId))
            loadError = nil
        } catch where !error.isCancellation {
            loadError = error.userMessage
        } catch {}
    }

    func loadPlans() async throws {
        async let proposalList = api.send(API.Proposals.list(groupId: groupId))
        async let eventList = api.send(API.Events.list(groupId: groupId, from: Calendar.current.startOfDay(for: Date())))
        proposals = try await proposalList.proposals.sorted { $0.start < $1.start }
        events = try await eventList.events.sorted { $0.start < $1.start }
        plansLoaded = true
    }

    func update(_ group: Group) { self.group = group }

    /// Applies group, member, proposal, event and message live events, and refetches after a reconnect.
    func apply(_ event: LiveEvent) async {
        switch event.kind {
        case .reconnected:
            await load()
            try? await loadPlans()
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
        case .proposalCreated, .proposalUpdated, .proposalConfirmed, .eventChanged:
            try? await loadPlans()
        case .messageCreated:
            if !isChatVisible { unreadCount += 1 }
        default:
            break
        }
    }
}

/// One `GroupDetailModel` per group while any of its screens is open, so the calendar and the screens
/// pushed over it share one loaded group and one live subscription. Screens hold the model; this only
/// remembers it weakly, so it goes when the last of them is popped.
@MainActor
final class GroupDetailStore {
    private struct Entry { weak var model: GroupDetailModel? }
    private var entries: [String: Entry] = [:]

    func model(for groupId: String, api: APIClient, live: LiveEventsClient) -> GroupDetailModel {
        if let model = entries[groupId]?.model { return model }
        entries = entries.filter { $0.value.model != nil }
        let model = GroupDetailModel(groupId: groupId, api: api, live: live)
        entries[groupId] = Entry(model: model)
        return model
    }
}
