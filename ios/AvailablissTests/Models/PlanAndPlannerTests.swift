import Foundation
import Testing
@testable import Availabliss

/// Ported from Android `PlanLabelsTest` and `PlannerResultTest`.
struct PlanAndPlannerTests {
    private let freePlan = """
    {"tier":"free","proUntil":null,"lifetime":false,"willRenew":false,"store":null,"groupLimit":3,"groupsOwned":2,
     "canCreateGroup":true,"aiPlanner":{"available":false,"included":false},
     "purchase":{"ios":null,"android":null,"web":null,"entitlement":"pro"}}
    """

    private func pro(proUntil: String?, lifetime: Bool = false, willRenew: Bool = false) throws -> Plan {
        try Fixtures.decode(Plan.self, """
        {"tier":"pro","proUntil":\(proUntil.map { "\"\($0)\"" } ?? "null"),"lifetime":\(lifetime),"willRenew":\(willRenew),
         "store":"app_store","groupLimit":null,"groupsOwned":7,"canCreateGroup":true,"aiPlanner":{"available":true,"included":true},
         "purchase":{"ios":"appl_x","android":null,"web":null,"entitlement":"pro"}}
        """)
    }

    @Test func decodesThePlanFromTheAPI() throws {
        let plan = try Fixtures.decode(Plan.self, freePlan)
        #expect(!plan.isPro)
        #expect(plan.groupLimit == 3)
        #expect(plan.purchase.android == nil)
        #expect(plan.canCreateGroup)
        #expect(plan.renewalText == nil)
    }

    @Test func proRenewalLines() throws {
        // Midday, so the day is 2 Dec in any time zone the simulator might be in.
        let until = "2026-12-02T12:00:00Z"
        #expect(try pro(proUntil: until, willRenew: true).renewalText == "Renews \(Formatting.dayMonth(CalendarDay(Fixtures.date(until))))")
        #expect(try pro(proUntil: until).renewalText?.hasPrefix("Ends ") == true)
        #expect(try pro(proUntil: nil, lifetime: true).renewalText == "It never expires")
        #expect(try pro(proUntil: nil).renewalText == nil)
        #expect(try pro(proUntil: nil).isPro)
        #expect(try pro(proUntil: nil).groupLimit == nil)
    }

    @Test(.enabled(if: TestLocale.isBritish, TestLocale.reason))
    func proRenewalDateReadsDayThenMonth() throws {
        #expect(try pro(proUntil: "2026-12-02T12:00:00Z", willRenew: true).renewalText == "Renews 2 Dec")
    }

    @Test func parsesThePlannerResult() throws {
        let result = try Fixtures.decode(PlannerResult.self, """
        {
          "best": { "slot": { "start": "2026-11-27T18:00:00.000Z", "end": "2026-11-29T23:59:00.000Z", "label": "Fri 27 – Sun 29 Nov" },
                    "why": "The only weekend everyone's free before December." },
          "alternatives": [
            { "slot": { "start": "2026-12-04T18:00:00.000Z", "end": "2026-12-06T23:59:00.000Z", "label": "Fri 4 – Sun 6 Dec" }, "why": "A week later." }
          ],
          "ideas": [
            { "title": "Catbells at sunrise", "description": "A short climb." },
            { "title": "Pub quiz in Keswick", "description": "Thursdays." }
          ],
          "checklist": ["Propose it", "Book somewhere", "Sort out lifts"],
          "proposalTitle": "Lakes weekend",
          "proposalNote": "Cottage near Keswick?"
        }
        """)
        #expect(result.best.slot.label == "Fri 27 – Sun 29 Nov")
        #expect(result.best.slot.start == Fixtures.date("2026-11-27T18:00:00Z"))
        #expect(result.best.why.hasPrefix("The only weekend"))
        #expect(result.alternatives.count == 1)
        #expect(result.ideas.map(\.title) == ["Catbells at sunrise", "Pub quiz in Keswick"])
        #expect(result.checklist.count == 3)
        #expect(result.proposalTitle == "Lakes weekend")
    }

    @Test func thePreviewFixtureDecodes() {
        #expect(PlannerResult.fixture.alternatives.count == 1)
        #expect(PlannerResult.fixture.proposalTitle == "Lake District weekend")
    }

    @Test func noNotesAreLeftOutOfTheRequest() throws {
        #expect(try Fixtures.encode(PlannerBody(notes: nil)) == "{}")
        #expect(try Fixtures.encode(PlannerBody(notes: "Keep it cheap")) == #"{"notes":"Keep it cheap"}"#)
    }
}
