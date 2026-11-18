import Foundation

/// `POST /groups/:id/planner`: the AI planner's pick of the times that already work for everyone.
struct PlannerResult: Decodable, Sendable, Equatable {
    struct Option: Decodable, Sendable, Equatable {
        let slot: Slot
        let why: String
    }

    struct Idea: Decodable, Sendable, Equatable, Hashable {
        let title: String
        let description: String
    }

    let best: Option
    let alternatives: [Option]
    let ideas: [Idea]
    let checklist: [String]
    /// Prefill for "Propose this".
    let proposalTitle: String
    let proposalNote: String

    init(best: Option, alternatives: [Option] = [], ideas: [Idea] = [], checklist: [String] = [], proposalTitle: String = "", proposalNote: String = "") {
        self.best = best
        self.alternatives = alternatives
        self.ideas = ideas
        self.checklist = checklist
        self.proposalTitle = proposalTitle
        self.proposalNote = proposalNote
    }

    /// Lists the model left out decode as empty (as on Android), rather than failing the whole plan.
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        best = try c.decode(Option.self, forKey: .best)
        alternatives = try c.decodeIfPresent([Option].self, forKey: .alternatives) ?? []
        ideas = try c.decodeIfPresent([Idea].self, forKey: .ideas) ?? []
        checklist = try c.decodeIfPresent([String].self, forKey: .checklist) ?? []
        proposalTitle = try c.decodeIfPresent(String.self, forKey: .proposalTitle) ?? ""
        proposalNote = try c.decodeIfPresent(String.self, forKey: .proposalNote) ?? ""
    }

    private enum CodingKeys: String, CodingKey { case best, alternatives, ideas, checklist, proposalTitle, proposalNote }
}

struct PlannerBody: Encodable, Sendable {
    let notes: String?
}

/// `POST /groups/:id/planner/draft` body: the time picked, and anything the AI should know.
struct ProposalDraftBody: Encodable, Sendable {
    let start: Date
    let end: Date
    let notes: String?
}

/// An AI-drafted proposal title and message (Pro).
struct ProposalDraftText: Decodable, Sendable, Equatable {
    let title: String
    let note: String
}
