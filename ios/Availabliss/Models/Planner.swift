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
