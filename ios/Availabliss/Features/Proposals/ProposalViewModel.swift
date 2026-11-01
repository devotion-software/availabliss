import Foundation
import Observation

@MainActor
@Observable
final class ProposalViewModel {
    private(set) var proposal: Proposal?
    private(set) var group: Group?
    private(set) var event: GroupEvent?
    private(set) var isResponding = false
    private(set) var loadError: String?
    var error: String?

    let proposalId: String
    private let api: APIClient

    init(proposalId: String, api: APIClient) {
        self.proposalId = proposalId
        self.api = api
    }

    func load() async {
        do {
            let proposal = try await api.send(API.Proposals.get(id: proposalId))
            self.proposal = proposal
            if group?.id != proposal.groupId {
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
    var progress: Double { memberCount == 0 ? 0 : Double(acceptedCount) / Double(memberCount) }

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
        if !proposal.allDay { parts.append("From \(Formatting.shortDay(proposal.start).split(separator: " ").first ?? "") \(Formatting.time(proposal.start))") }
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
        return group.members.sorted { lhs, _ in lhs.userId == proposal?.createdBy }
    }

    func note(for member: Member, me: String?) -> String {
        guard let proposal else { return "" }
        if member.userId == proposal.createdBy { return "Proposed this" }
        if let response = proposal.response(for: member.userId), let setBy = response.setBy, setBy != member.userId {
            let admin = setBy == me ? "you" : (group?.member(setBy)?.firstName ?? "an admin")
            return "Set by \(admin) (admin)"
        }
        if !member.synced { return "Not synced yet" }
        if proposal.response(for: member.userId)?.response ?? .pending == .pending { return "Hasn\u{2019}t replied" }
        return member.role.isAdmin ? member.role.label : "Synced"
    }

    // MARK: Actions

    func respond(_ response: ResponseKind) async {
        await run { try await self.api.send(API.Proposals.respond(id: self.proposalId, response)) }
    }

    func override(userId: String, response: ResponseKind) async {
        await run { try await self.api.send(API.Proposals.override(id: self.proposalId, userId: userId, response: response)) }
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
        let events = try? await api.send(API.Events.list(groupId: proposal.groupId, from: proposal.start.addingTimeInterval(-86_400), to: proposal.end.addingTimeInterval(86_400)))
        event = events?.events.first { $0.id == eventId }
    }
}
