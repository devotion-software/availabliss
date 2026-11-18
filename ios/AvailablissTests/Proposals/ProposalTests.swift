import Foundation
import Testing

@testable import Availabliss

/// Maybes (soft yeses) are counted apart from firm yeses. Ported from Android `ProposalFormattingTest`.
struct ProposalTests {
    private func proposal(_ replies: ResponseKind..., status: Proposal.Status = .open, busy: [String] = []) -> Proposal {
        Proposal(
            id: "p", groupId: "g", title: nil, location: nil, note: nil,
            start: Fixtures.date("2026-11-20T18:00:00Z"), end: Fixtures.date("2026-11-20T21:00:00Z"), allDay: false,
            status: status, createdBy: "u0", createdAt: Fixtures.date("2026-11-18T10:00:00Z"), eventId: nil, confirmedBy: nil,
            responses: replies.enumerated().map { i, reply in
                Proposal.Response(userId: "u\(i)", response: reply, setBy: nil, updatedAt: Fixtures.date("2026-11-18T10:00:00Z"))
            },
            busyMemberIds: busy
        )
    }

    @Test func maybesAreCountedApartFromYeses() {
        let p = proposal(.accepted, .accepted, .maybe, .declined, .pending)
        #expect(p.acceptedCount == 2)
        #expect(p.maybeCount == 1)
        #expect(p.response(for: "u2")?.response == .maybe)
        #expect(p.response(for: "nobody") == nil)
    }

    @Test func theMaybeSuffixOnlyShowsWhenThereAreAny() {
        #expect(proposal(.accepted, .pending).maybeSuffix == "")
        #expect(proposal(.accepted, .maybe).maybeSuffix == " · 1 maybe")
        #expect(proposal(.maybe, .maybe).maybeSuffix == " · 2 maybe")
    }

    @Test func aMaybeIsntEveryoneIn() {
        #expect(proposal(.accepted, .accepted).everyoneIn)
        #expect(!proposal(.accepted, .maybe).everyoneIn)
        #expect(!proposal(.accepted, .pending).everyoneIn)
    }

    @Test func confirmedStatusCountsMaybes() {
        #expect(proposal(.accepted, .accepted, .maybe, .declined, status: .confirmed).confirmedStatus == "It\u{2019}s on · 2 of 4 coming · 1 maybe")
        #expect(proposal(.accepted, .accepted, status: .confirmed).confirmedStatus == "Everyone\u{2019}s in")
        #expect(proposal(.accepted, .declined, status: .confirmed).confirmedStatus == "It\u{2019}s on · 1 of 2 coming")
    }

    @Test func onlyOpenProposalsWarnAboutClashes() {
        #expect(proposal(.pending, busy: ["u0"]).openClashIds == ["u0"])
        #expect(proposal(.accepted, status: .confirmed, busy: ["u0"]).openClashIds.isEmpty)
        #expect(proposal(.accepted, status: .cancelled, busy: ["u0"]).openClashIds.isEmpty)
    }

    @Test func myPlansDecodeTheEventWithTheGroupAndMyReply() throws {
        let plans = try Fixtures.decode(
            MyPlansResponse.self,
            """
            {"going":[{"id":"e1","groupId":"g","proposalId":"p","title":"Lakes","start":"2026-11-27T18:00:00Z","end":"2026-11-29T23:59:00Z",
                       "allDay":false,"createdBy":"u","createdAt":"2026-11-10T10:00:00Z","groupName":"Walkers","myResponse":"maybe"}],
             "notGoing":[],
             "past":[{"id":"e0","groupId":"g","proposalId":null,"title":"Picnic","start":"2026-06-01T12:00:00Z","end":"2026-06-01T15:00:00Z",
                      "allDay":false,"createdBy":"u","createdAt":"2026-05-10T10:00:00Z","groupName":"Walkers"}]}
            """)
        #expect(plans.going.first?.event.title == "Lakes")
        #expect(plans.going.first?.groupName == "Walkers")
        #expect(plans.going.first?.myResponse == .maybe)
        #expect(plans.going.first?.id == "e1")
        #expect(plans.past.first?.myResponse == nil)
        #expect(plans.past.first?.event.image == nil)
    }
}
