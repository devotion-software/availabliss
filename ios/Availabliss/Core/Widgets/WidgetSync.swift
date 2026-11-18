import Foundation
import WidgetKit

/// Keeps the home-screen widgets in step with the app: writes the `WidgetSnapshot` when your plans
/// or the groups list load, after replying to a proposal, after a push and on sign-out, then asks
/// WidgetKit to redraw. Matches Android's `Widgets`.
///
/// Main-actor isolated, so writes land one at a time in the order they're made: a sign-out can't
/// be overwritten by a load that started before it (loads check they're still signed in).
@MainActor
final class WidgetSync {
    static let shared = WidgetSync()

    private var api: APIClient?
    private var refreshTask: Task<Void, Never>?
    /// Called after the cached groups change (the Siri group picker's phrases are refreshed).
    var onGroupsChanged: (@MainActor () -> Void)?

    private init() {}

    /// Called once by `AppModel`: the client loads use, and whose token says whether we're signed in.
    func configure(api: APIClient) {
        self.api = api
    }

    private var isSignedIn: Bool { api?.bearerToken != nil }

    /// `GET /me/plans` loaded: the "Next plan" widget's plans.
    func onPlans(_ response: MyPlansResponse, now: Date = .now) {
        guard isSignedIn else { return }
        let upcoming = WidgetSnapshots.plans(from: response, now: now)
        write { $0.signedIn = true; $0.plans = upcoming }
    }

    /// The groups list loaded or changed: the proposals waiting for your reply, and the groups Siri can ask about.
    func onGroups(_ groups: [GroupSummary]) {
        guard isSignedIn else { return }
        let waiting = WidgetSnapshots.waiting(from: groups)
        let named = WidgetSnapshots.groups(from: groups)
        let before = WidgetSnapshotStore.load()?.groups
        write {
            $0.signedIn = true
            $0.waiting = Array(waiting.prefix(WidgetSnapshots.maxWaiting))
            $0.waitingCount = waiting.count
            $0.groups = named
        }
        if before != named { onGroupsChanged?() }
    }

    /// Signed out (or the session was rejected): the widgets ask you to sign in.
    func onSignedOut() {
        refreshTask?.cancel()
        refreshTask = nil
        write { _ in .signedOut }
        onGroupsChanged?()
    }

    /// Refetches plans and groups (after a push, a reply, signing in, a background refresh) and
    /// rewrites the snapshot. Overlapping calls share one fetch.
    func refresh() async {
        if let refreshTask { return await refreshTask.value }
        guard let api, isSignedIn else { return }
        let task = Task { @MainActor in
            async let plans = try? api.send(API.Me.plans())
            async let groups = try? api.send(API.Groups.list())
            let (planList, groupList) = await (plans, groups)
            guard !Task.isCancelled else { return }
            if let planList { onPlans(planList) }
            if let groupList { onGroups(groupList.groups) }
        }
        refreshTask = task
        await task.value
        if refreshTask == task { refreshTask = nil }
    }

    /// Whether a push of `kind` can change what the widgets show: proposals (new, confirmed,
    /// changed), reminders and events. Chat messages and the rest don't.
    nonisolated static func refreshesOn(kind: String?) -> Bool {
        guard let kind else { return false }
        return kind.hasPrefix("proposal.") || kind.hasPrefix("event.") || kind == "reminder"
    }

    private func write(_ transform: (inout WidgetSnapshot) -> Void) {
        write { snapshot in
            var copy = snapshot
            transform(&copy)
            return copy
        }
    }

    private func write(_ transform: (WidgetSnapshot) -> WidgetSnapshot) {
        if WidgetSnapshotStore.update(transform) {
            WidgetCenter.shared.reloadAllTimelines()
        }
    }
}

extension WidgetSnapshots {
    /// The plans you're going to that haven't ended at `now`, soonest first, for the snapshot.
    static func plans(from response: MyPlansResponse, now: Date) -> [WidgetPlan] {
        response.going
            .filter { $0.event.end > now }
            .sorted { $0.event.start < $1.event.start }
            .prefix(maxPlans)
            .map { plan in
                WidgetPlan(
                    title: plan.event.title,
                    groupName: plan.groupName,
                    groupId: plan.event.groupId,
                    start: plan.event.start,
                    end: plan.event.end,
                    allDay: plan.event.allDay,
                    proposalId: plan.event.proposalId
                )
            }
    }

    /// Groups with a proposal waiting for your reply, in the groups list's order (archived ones left out).
    static func waiting(from groups: [GroupSummary]) -> [WidgetWaiting] {
        groups.filter { !$0.isArchived }.compactMap { group in
            guard case .needsReply(let proposalId) = group.status else { return nil }
            return WidgetWaiting(groupName: group.name, proposalId: proposalId, groupId: group.id)
        }
    }

    /// Your groups that aren't archived, for the Siri group picker.
    static func groups(from groups: [GroupSummary]) -> [WidgetGroup] {
        groups.filter { !$0.isArchived }.map { WidgetGroup(id: $0.id, name: $0.name) }
    }
}
