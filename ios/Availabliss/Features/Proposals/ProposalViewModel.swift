import Foundation
import Observation

@MainActor
@Observable
final class ProposalViewModel {
    private(set) var proposal: Proposal?
    private(set) var group: Group?
    private(set) var event: GroupEvent?
    private(set) var isResponding = false
    /// The reply being sent, so only its button shows a spinner.
    private(set) var sending: ResponseKind?
    private(set) var loadError: String?
    var error: String?

    let proposalId: String
    private let api: APIClient

    init(proposalId: String, api: APIClient) {
        self.proposalId = proposalId
        self.api = api
    }

    /// `refreshingGroup` refetches the group even when it's already loaded (members may have changed).
    func load(refreshingGroup: Bool = false) async {
        do {
            let proposal = try await api.send(API.Proposals.get(id: proposalId))
            self.proposal = proposal
            if refreshingGroup || group?.id != proposal.groupId {
                group = try await api.send(API.Groups.get(id: proposal.groupId))
            }
            await loadEventIfConfirmed()
            loadError = nil
        } catch where !error.isCancellation {
            loadError = error.userMessage
        } catch {}
    }

    // MARK: Derived

    var isConfirmed: Bool { proposal?.status == .confirmed }
    var memberCount: Int { group?.members.count ?? proposal?.responses.count ?? 0 }
    var acceptedCount: Int { proposal?.acceptedCount ?? 0 }
    var maybeCount: Int { proposal?.maybeCount ?? 0 }
    var progress: Double { memberCount == 0 ? 0 : Double(acceptedCount) / Double(memberCount) }

    /// "Priya is busy then" while open, else nil.
    func clash(me: String?) -> String? {
        Clash.text(proposal?.openClashIds ?? [], group: group, me: me)
    }

    func isBusy(_ me: String?) -> Bool {
        guard let me else { return false }
        return proposal?.openClashIds.contains(me) ?? false
    }

    func myResponse(_ me: String?) -> ResponseKind? {
        guard let me else { return nil }
        return proposal?.response(for: me)?.response
    }

    var title: String {
        guard let proposal else { return "" }
        return proposal.title ?? Formatting.span(start: proposal.start, end: proposal.end, allDay: proposal.allDay)
    }

    var subtitle: String {
        guard let proposal else { return "" }
        var parts: [String] = []
        if proposal.title != nil { parts.append(Formatting.span(start: proposal.start, end: proposal.end, allDay: proposal.allDay)) }
        if !proposal.allDay { parts.append("From \(proposal.start.formatted(.dateTime.weekday(.abbreviated))) \(Formatting.time(proposal.start))") }
        if let location = proposal.location, !location.isEmpty { parts.append(location) }
        return parts.joined(separator: " · ")
    }

    func proposerLine(me: String?) -> String {
        guard let proposal else { return "" }
        let who = proposal.createdBy == me ? "You" : (group?.member(proposal.createdBy)?.firstName ?? "Someone")
        return "\(who) proposed · \(Formatting.relative(proposal.createdAt))"
    }

    /// One row per member, in the order: proposer, then group order.
    var rows: [Member] {
        guard let group else { return [] }
        let proposer = proposal?.createdBy
        return group.members.filter { $0.userId == proposer } + group.members.filter { $0.userId != proposer }
    }

    /// Confirmed: the members who accepted, proposer first.
    var coming: [Member] { members(with: .accepted) }

    /// Confirmed: members who said maybe (might come, not counted as coming).
    var maybe: [Member] { members(with: .maybe) }

    /// Confirmed: members who said they can't make it (confirmed anyway without them).
    var cantMakeIt: [Member] { members(with: .declined) }

    /// Confirmed: members who never replied (confirmed anyway before they did).
    var notReplied: [Member] { members(with: .pending) }

    /// Confirmed: members with no reply at all, i.e. people who joined the group after it was
    /// confirmed (replies are only asked of the members at the time).
    var joinedLater: [Member] {
        guard let proposal else { return [] }
        return rows.filter { proposal.response(for: $0.userId) == nil }
    }

    /// Everyone it was put to can make it ("Everyone's in."), rather than confirmed anyway ("It's on.").
    var everyoneIn: Bool { proposal?.everyoneIn ?? true }

    /// How many it was put to: who's coming, who can't and who never replied.
    var invitedCount: Int { proposal?.responses.count ?? 0 }

    private func members(with response: ResponseKind) -> [Member] {
        guard let proposal else { return [] }
        return rows.filter { proposal.response(for: $0.userId)?.response == response }
    }

    /// The proposer or an admin: who keeps or cancels a confirmed plan someone dropped out of.
    func canReview(me: String?) -> Bool {
        guard let proposal, let group, let me else { return false }
        return proposal.createdBy == me || group.role.isAdmin
    }

    /// Confirmed and waiting on the proposer or an admin to keep it or cancel it.
    func needsReview(me: String?) -> Bool {
        (proposal?.needsReview ?? false) && proposal?.status == .confirmed && canReview(me: me)
    }

    /// "Priya can't make it any more" / "Priya and Tom can't make it any more".
    func droppedOutHeadline(me: String?) -> String {
        guard let proposal else { return "" }
        let names = rows.filter { proposal.response(for: $0.userId)?.droppedOut == true }
            .map { $0.userId == me ? "You" : $0.firstName }
        guard !names.isEmpty else { return "Someone can\u{2019}t make it any more" }
        return "\(Clash.listNames(names, max: 4)) can\u{2019}t make it any more"
    }

    /// Whether this member said they can't make it after it was confirmed (still to be reviewed).
    func droppedOut(_ member: Member) -> Bool {
        proposal?.response(for: member.userId)?.droppedOut == true
    }

    /// The proposer or an admin can confirm an open proposal before everyone has accepted, once
    /// someone besides them can make it (the server checks the same).
    func canConfirmAnyway(me: String?) -> Bool {
        guard let proposal, let group, let me, proposal.status == .open else { return false }
        guard proposal.createdBy == me || group.role.isAdmin else { return false }
        return proposal.responses.contains { $0.response == .accepted && $0.userId != me }
    }

    /// For the Confirm anyway sheet: "Priya can't make it, Ana might make it and Tom hasn't replied."
    func confirmAnywayMessage(me: String?) -> String {
        guard let proposal else { return "" }
        let pending = rows.filter { proposal.response(for: $0.userId)?.response == .pending }
        let declined = rows.filter { proposal.response(for: $0.userId)?.response == .declined }
        let maybe = rows.filter { proposal.response(for: $0.userId)?.response == .maybe }
        func names(_ members: [Member]) -> String {
            Clash.listNames(members.map { $0.userId == me ? "You" : $0.firstName }, max: 4)
        }
        var parts: [String] = []
        if !declined.isEmpty { parts.append("\(names(declined)) can\u{2019}t make it") }
        if !maybe.isEmpty { parts.append("\(names(maybe)) might make it") }
        if !pending.isEmpty { parts.append("\(names(pending)) \(pending.count == 1 ? "hasn\u{2019}t" : "haven\u{2019}t") replied") }
        let who = Clash.listNames(parts, max: parts.count)
        let coming = proposal.acceptedCount
        let maybes = proposal.maybeCount > 0 ? " and \(proposal.maybeCount) maybe" : ""
        return "\(who.isEmpty ? "" : "\(who). ")Everyone will be told it\u{2019}s on, with \(coming) of \(proposal.responses.count) coming\(maybes)."
    }

    func note(for member: Member, me: String?) -> String {
        guard let proposal else { return "" }
        if member.userId == proposal.createdBy { return "Proposed this" }
        if let response = proposal.response(for: member.userId), let setBy = response.setBy, setBy != member.userId {
            let admin = setBy == me ? "you" : (group?.member(setBy)?.firstName ?? "an admin")
            return "Set by \(admin) (admin)"
        }
        if !member.synced { return "Not synced yet" }
        let response = proposal.response(for: member.userId)?.response ?? .pending
        let busy = proposal.openClashIds.contains(member.userId)
        if busy && response == .pending { return "Busy then \u{00B7} hasn\u{2019}t replied" }
        if busy && response == .accepted { return "Busy then, but in" }
        if busy && response == .maybe { return "Busy then, but maybe" }
        if response == .pending { return "Hasn\u{2019}t replied" }
        return member.role.isAdmin ? member.role.label : "Synced"
    }

    // MARK: Actions

    func respond(_ response: ResponseKind) async {
        sending = response
        defer { sending = nil }
        await run { try await self.api.send(API.Proposals.respond(id: self.proposalId, response)) }
        // The plan may now be yours (Next plan) and it's no longer waiting for you.
        if error == nil { await WidgetSync.shared.refresh() }
    }

    func override(userId: String, response: ResponseKind) async {
        await run { try await self.api.send(API.Proposals.override(id: self.proposalId, userId: userId, response: response)) }
    }

    func confirmAnyway() async {
        await run { try await self.api.send(API.Proposals.confirm(id: self.proposalId)) }
    }

    /// "Keep the plan": clears the review; whoever dropped out stays as can't make it.
    func keepPlan() async {
        await run { try await self.api.send(API.Proposals.keep(id: self.proposalId)) }
    }

    func cancel() async -> Bool {
        do {
            try await api.send(API.Proposals.cancel(id: proposalId))
            return true
        } catch {
            self.error = error.userMessage
            return false
        }
    }

    /// Live: `proposal.updated` (Proposal) and `proposal.confirmed` ({ proposal, event }).
    func apply(_ live: LiveEvent) async {
        switch live.kind {
        case .proposalUpdated:
            if let updated = live.payload(as: Proposal.self), updated.id == proposalId { proposal = updated }
        case .proposalConfirmed:
            if let payload = live.payload(as: ProposalConfirmedPayload.self), payload.proposal.id == proposalId {
                proposal = payload.proposal
                event = payload.event
            }
        case .memberChanged, .groupUpdated:
            if let groupId = proposal?.groupId { group = try? await api.send(API.Groups.get(id: groupId)) }
        case .reconnected:
            await load(refreshingGroup: true)
        default:
            break
        }
    }

    private func run(_ work: @escaping () async throws -> Proposal) async {
        isResponding = true
        defer { isResponding = false }
        do {
            proposal = try await work()
            await loadEventIfConfirmed()
        } catch {
            self.error = error.userMessage
        }
    }

    private func loadEventIfConfirmed() async {
        guard let proposal, proposal.status == .confirmed, let eventId = proposal.eventId, event?.id != eventId else { return }
        event = try? await api.send(API.Events.get(id: eventId))
    }
}
