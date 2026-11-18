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
    /// Unread chat messages, for the Chat button's badge: the server's count from the groups list,
    /// plus messages from others that arrive while the chat isn't on screen.
    var unreadCount = 0
    /// Whether the chat is on screen, so new messages there don't count as unread.
    @ObservationIgnored var isChatVisible = false
    @ObservationIgnored private var unreadLoaded = false

    /// Proposals (by date) and upcoming events, for the calendar's Plans card and the Plans screen.
    private(set) var proposals: [Proposal] = []
    private(set) var events: [GroupEvent] = []
    private(set) var plansLoaded = false

    /// Your mute on this group (nil: not muted), from your notification settings, for the More menu
    /// and the bell beside the subtitle.
    private(set) var mute: GroupMute?
    @ObservationIgnored private var muteLoaded = false

    let api: APIClient
    @ObservationIgnored private let live: LiveEventsClient
    /// The signed-in user, so our own messages don't count as unread.
    @ObservationIgnored private let me: @MainActor () -> String?
    @ObservationIgnored nonisolated(unsafe) private var liveTask: Task<Void, Never>?

    init(groupId: String, api: APIClient, live: LiveEventsClient, me: @escaping @MainActor () -> String?) {
        self.groupId = groupId
        self.api = api
        self.live = live
        self.me = me
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
        if !unreadLoaded { await loadUnread() }
        if !plansLoaded { try? await loadPlans() }
        if !muteLoaded { await loadMute() }
    }

    /// Whether pushes from the group are muted right now (a mute for a while can run out while it's open).
    var isMuted: Bool { mute?.isActive() ?? false }

    /// Reads your mute on this group from your notification settings.
    func loadMute() async {
        guard let settings = try? await api.send(API.Me.notificationSettings()) else { return }
        muteLoaded = true
        mute = settings.mute(for: groupId)
    }

    /// Mutes pushes from the group until `until` (nil: until it's unmuted).
    func setMute(until: Date?) async throws {
        let settings = try await api.send(API.Groups.mute(groupId: groupId, until: until))
        mute = settings.mute(for: groupId)
    }

    func unmute() async throws {
        let settings = try await api.send(API.Groups.unmute(groupId: groupId))
        mute = settings.mute(for: groupId)
    }

    /// Seeds the Chat badge with the server's unread count (only the groups list carries it).
    func loadUnread() async {
        guard let summaries = try? await api.send(API.Groups.list()).groups else { return }
        WidgetSync.shared.onGroups(summaries)
        unreadLoaded = true
        if !isChatVisible { unreadCount = summaries.first { $0.id == groupId }?.unreadCount ?? 0 }
    }

    func load() async {
        do {
            group = try await api.send(API.Groups.get(id: groupId))
            loadError = nil
        } catch  where !error.isCancellation {
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
            await loadUnread()
            try? await loadPlans()
            await loadMute()
        case .groupUpdated:
            // The server builds the payload for whoever made the change, so its role is theirs: use ours.
            if var updated = event.payload(as: Group.self) {
                updated.role = updated.member(me() ?? "")?.role ?? group?.role ?? updated.role
                group = updated
            }
        case .memberChanged:
            if let removed = event.removedMemberId {
                group?.members.removeAll { $0.userId == removed }
            } else if let member = event.payload(as: Member.self) {
                if let index = group?.members.firstIndex(where: { $0.userId == member.userId }) {
                    group?.members[index] = member
                } else {
                    group?.members.append(member)
                }
                // Made an admin, or handed the group by an owner who left: our own role changed.
                if member.userId == me() { group?.role = member.role }
            }
            if let members = group?.members {
                group?.memberCount = members.count
                group?.syncedCount = members.filter(\.synced).count
            }
        case .proposalCreated, .proposalUpdated, .proposalConfirmed, .eventChanged:
            try? await loadPlans()
        case .messageCreated:
            guard let message = event.payload(as: Message.self), message.groupId == groupId else { return }
            if !isChatVisible, message.authorId == nil || message.authorId != me() { unreadCount += 1 }
        case .messageReceipts:
            // Our own "seen" receipt (here or on another device) means the chat has been read.
            if let payload = event.payload(as: MessageReceiptsPayload.self), payload.state == "seen", payload.userId == me() {
                unreadCount = 0
            }
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

    func model(for groupId: String, api: APIClient, live: LiveEventsClient, me: @escaping @MainActor () -> String?) -> GroupDetailModel {
        if let model = entries[groupId]?.model { return model }
        entries = entries.filter { $0.value.model != nil }
        let model = GroupDetailModel(groupId: groupId, api: api, live: live, me: me)
        entries[groupId] = Entry(model: model)
        return model
    }

    /// The group's model if one of its screens is open (nil otherwise, rather than making one).
    func existingModel(for groupId: String) -> GroupDetailModel? { entries[groupId]?.model }
}
