import Foundation
import Testing

@testable import Availabliss

/// What the home-screen widgets show and how they word it, compared with Android `WidgetSnapshotsTest`.
/// The labels are formatted with an en-GB London calendar, so they're checked wherever the tests run.
struct WidgetSnapshotTests {
    private let london = Fixtures.calendar("Europe/London")

    private func plan(_ title: String, _ start: String, _ end: String, allDay: Bool = false, proposalId: String? = "p-\(UUID().uuidString)") -> WidgetPlan {
        WidgetPlan(title: title, groupName: "Book club", groupId: "g1", start: Fixtures.date(start), end: Fixtures.date(end), allDay: allDay, proposalId: proposalId)
    }

    private func event(_ id: String, _ title: String, _ start: String, _ end: String) -> String {
        """
        {"id":"\(id)","groupId":"g-\(id)","proposalId":"p-\(id)","title":"\(title)","start":"\(start)","end":"\(end)",
         "allDay":false,"createdBy":"u","createdAt":"2026-11-01T10:00:00Z","groupName":"Group \(id)","myResponse":"accepted"}
        """
    }

    private func summary(_ id: String, _ name: String, status: GroupStatus, archived: Bool? = nil) -> GroupSummary {
        GroupSummary(
            id: id, name: name, color: "t1", monogram: "B", image: nil, role: .member, archived: archived,
            memberCount: 3, syncedCount: 3, requirementSummary: "", status: status, unreadCount: 0, memberPreview: []
        )
    }

    // MARK: Selection

    @Test func snapshotKeepsTheFiveSoonestPlansThatHaventEnded() throws {
        let events = [
            event("past", "Over", "2026-12-01T18:00:00Z", "2026-12-01T20:00:00Z"),
            event("e6", "Six", "2026-12-20T18:00:00Z", "2026-12-20T20:00:00Z"),
            event("on", "On now", "2026-12-02T09:00:00Z", "2026-12-02T12:00:00Z"),
            event("e2", "Two", "2026-12-04T19:30:00Z", "2026-12-04T22:00:00Z"),
            event("e3", "Three", "2026-12-05T18:00:00Z", "2026-12-05T20:00:00Z"),
            event("e4", "Four", "2026-12-10T18:00:00Z", "2026-12-10T20:00:00Z"),
            event("e5", "Five", "2026-12-12T18:00:00Z", "2026-12-12T20:00:00Z"),
        ]
        let response = try Fixtures.decode(MyPlansResponse.self, #"{"going":[\#(events.joined(separator: ","))],"notGoing":[],"past":[]}"#)
        let plans = WidgetSnapshots.plans(from: response, now: Fixtures.date("2026-12-02T10:00:00Z"))
        #expect(plans.map(\.title) == ["On now", "Two", "Three", "Four", "Five"])
        #expect(plans.first?.groupName == "Group on")
        #expect(plans.first?.proposalId == "p-on")
    }

    @Test func waitingListsGroupsThatNeedYourReplyLeavingOutArchivedOnes() {
        let groups = [
            summary("g1", "Lake District crew", status: .needsReply(proposalId: "p1")),
            summary("g2", "Book club", status: .searching),
            summary("g3", "Old flat", status: .needsReply(proposalId: "p3"), archived: true),
            summary("g4", "Five-a-side", status: .needsReply(proposalId: "p4")),
        ]
        #expect(
            WidgetSnapshots.waiting(from: groups) == [
                WidgetWaiting(groupName: "Lake District crew", proposalId: "p1", groupId: "g1"),
                WidgetWaiting(groupName: "Five-a-side", proposalId: "p4", groupId: "g4"),
            ])
        #expect(WidgetSnapshots.groups(from: groups).map(\.name) == ["Lake District crew", "Book club", "Five-a-side"])
    }

    @Test func nextPlanIsTheFirstThatHasntEnded() {
        let plans = [
            plan("Brunch", "2026-12-02T10:00:00Z", "2026-12-02T12:00:00Z"),
            plan("Dinner", "2026-12-02T19:00:00Z", "2026-12-02T22:00:00Z"),
            plan("Walk", "2026-12-05T10:00:00Z", "2026-12-05T14:00:00Z"),
        ]
        #expect(WidgetSnapshots.nextPlan(plans, now: Fixtures.date("2026-12-02T11:00:00Z"))?.title == "Brunch")
        #expect(WidgetSnapshots.nextPlan(plans, now: Fixtures.date("2026-12-02T12:00:00Z"))?.title == "Dinner")
        #expect(WidgetSnapshots.followingPlan(plans, now: Fixtures.date("2026-12-02T12:00:00Z"))?.title == "Walk")
        #expect(WidgetSnapshots.nextPlan(plans, now: Fixtures.date("2026-12-06T00:00:00Z")) == nil)
    }

    // MARK: Labels

    @Test func whenLabelSaysTodayTomorrowOrTheDate() {
        let now = Fixtures.date("2026-12-02T10:00:00Z")  // Wednesday
        func label(_ start: String, _ end: String) -> String {
            WidgetSnapshots.whenLabel(plan("x", start, end), now: now, calendar: london)
        }
        #expect(label("2026-12-02T19:00:00Z", "2026-12-02T22:00:00Z") == "Today 19:00")
        #expect(label("2026-12-03T19:00:00Z", "2026-12-03T22:00:00Z") == "Tomorrow 19:00")
        #expect(label("2026-12-04T19:30:00Z", "2026-12-04T22:00:00Z") == "Fri 4 Dec · 19:30")
        #expect(label("2026-12-02T09:00:00Z", "2026-12-02T12:00:00Z") == "Now")
    }

    @Test func whenLabelUsesTheLocalDay() {
        // 23:30 UTC on 2 Dec is 08:30 on 3 Dec in Tokyo.
        let tokyo = Fixtures.calendar("Asia/Tokyo")
        let late = plan("x", "2026-12-02T23:30:00Z", "2026-12-03T01:00:00Z")
        #expect(WidgetSnapshots.whenLabel(late, now: Fixtures.date("2026-12-02T10:00:00Z"), calendar: tokyo) == "Tomorrow 08:30")
        #expect(WidgetSnapshots.whenLabel(late, now: Fixtures.date("2026-12-02T10:00:00Z"), calendar: london) == "Today 23:30")
    }

    @Test func allDayPlansShowTheDayOnly() {
        let weekend = plan("Lakes", "2026-12-04T00:00:00Z", "2026-12-07T00:00:00Z", allDay: true)
        func label(_ now: String) -> String { WidgetSnapshots.whenLabel(weekend, now: Fixtures.date(now), calendar: london) }
        #expect(label("2026-12-02T10:00:00Z") == "Fri 4 Dec")
        #expect(label("2026-12-03T10:00:00Z") == "Tomorrow")
        #expect(label("2026-12-04T10:00:00Z") == "Today")
        // Still on, on its last day.
        #expect(label("2026-12-06T22:00:00Z") == "Today")
    }

    @Test func waitingWording() {
        #expect(WidgetSnapshots.waitingTitle(1) == "1 proposal waiting for you")
        #expect(WidgetSnapshots.waitingTitle(4) == "4 proposals waiting for you")
        #expect(WidgetSnapshots.moreLabel(count: 5, shown: 3) == "+2 more")
        #expect(WidgetSnapshots.moreLabel(count: 3, shown: 3) == nil)
    }

    // MARK: Timeline

    @Test func timelineHasAnEntryAtEachMidnightAndPlanStartAndEnd() {
        let now = Fixtures.date("2026-12-02T10:00:00Z")
        let plans = [
            plan("Dinner", "2026-12-02T19:00:00Z", "2026-12-02T22:00:00Z"),
            plan("Far off", "2027-01-20T19:00:00Z", "2027-01-20T22:00:00Z"),
        ]
        let dates = WidgetSnapshots.timelineDates(plans, now: now, calendar: london, days: 2)
        #expect(
            dates == [
                Fixtures.date("2026-12-02T19:00:00Z"),
                Fixtures.date("2026-12-02T22:00:00Z"),
                Fixtures.date("2026-12-03T00:00:00Z"),
                Fixtures.date("2026-12-04T00:00:00Z"),
            ])
    }

    // MARK: Links and storage

    @Test func linksOpenTheProposalOrTheGroup() {
        #expect(DeepLink(url: plan("x", "2026-12-02T19:00:00Z", "2026-12-02T22:00:00Z", proposalId: "p1").url) == .proposal(id: "p1"))
        #expect(DeepLink(url: plan("x", "2026-12-02T19:00:00Z", "2026-12-02T22:00:00Z", proposalId: nil).url) == .group(id: "g1"))
        #expect(DeepLink(url: WidgetWaiting(groupName: "A", proposalId: "p9", groupId: "g9").url) == .proposal(id: "p9"))
        #expect(DeepLink(url: WidgetLinks.myPlans) == .myPlans)
    }

    @Test func storeRoundTripsAndReportsChanges() throws {
        let suite = "widget-tests-\(UUID().uuidString)"
        let defaults = try #require(UserDefaults(suiteName: suite))
        defer { defaults.removePersistentDomain(forName: suite) }

        #expect(WidgetSnapshotStore.load(from: defaults) == nil)
        let plans = [plan("Dinner", "2026-12-02T19:00:00Z", "2026-12-02T22:00:00Z")]
        #expect(
            WidgetSnapshotStore.update(in: defaults) {
                var s = $0; s.plans = plans; return s
            })
        #expect(WidgetSnapshotStore.load(from: defaults) == WidgetSnapshot(signedIn: true, plans: plans))
        // The same again isn't a change (no redraw).
        #expect(
            !WidgetSnapshotStore.update(in: defaults) {
                var s = $0; s.plans = plans; return s
            })
        #expect(WidgetSnapshotStore.update(in: defaults) { _ in .signedOut })
        #expect(WidgetSnapshotStore.load(from: defaults) == .signedOut)
    }

    @Test func pushesThatChangeTheWidgets() {
        #expect(WidgetSync.refreshesOn(kind: "proposal.created"))
        #expect(WidgetSync.refreshesOn(kind: "event.updated"))
        #expect(WidgetSync.refreshesOn(kind: "reminder"))
        #expect(!WidgetSync.refreshesOn(kind: "message"))
        #expect(!WidgetSync.refreshesOn(kind: nil))
    }
}

/// The spoken answer to "When's everyone in <group> free this month?".
struct FreeTimeAnswerTests {
    private func slot(_ label: String, _ start: String, _ end: String, missing: [String]? = nil) -> Slot {
        Slot(start: Fixtures.date(start), end: Fixtures.date(end), label: label, missingMemberIds: missing)
    }

    private let now = Fixtures.date("2026-12-01T10:00:00Z")

    @Test func listsTheTimesEveryoneCanMake() {
        let slots = [
            slot("Fri 11 – Sun 13 Dec", "2026-12-11T17:00:00Z", "2026-12-13T22:00:00Z"),
            slot("Fri 4 – Sun 6 Dec", "2026-12-04T17:00:00Z", "2026-12-06T22:00:00Z"),
            slot("Fri 18 – Sun 20 Dec", "2026-12-18T17:00:00Z", "2026-12-20T22:00:00Z", missing: ["u2"]),
        ]
        #expect(
            FreeTimeAnswer.sentence(slots: slots, groupName: "Lake District crew", monthName: "December", now: now)
                == "Fri 4 – Sun 6 Dec and Fri 11 – Sun 13 Dec work for everyone in Lake District crew.")
    }

    @Test func oneTimeWorksAndManyAreCut() {
        let one = [slot("Sat 5 Dec", "2026-12-05T09:00:00Z", "2026-12-05T22:00:00Z")]
        #expect(
            FreeTimeAnswer.sentence(slots: one, groupName: "Book club", monthName: "December", now: now)
                == "Sat 5 Dec works for everyone in Book club.")
        let many = (2...6).map { slot("Day \($0)", "2026-12-0\($0)T09:00:00Z", "2026-12-0\($0)T22:00:00Z") }
        #expect(
            FreeTimeAnswer.sentence(slots: many, groupName: "Book club", monthName: "December", now: now)
                == "Day 2, Day 3, Day 4 and 2 more work for everyone in Book club.")
    }

    @Test func saysWhenNothingWorks() {
        let over = [slot("Mon 30 Nov", "2026-11-30T09:00:00Z", "2026-11-30T22:00:00Z")]
        #expect(
            FreeTimeAnswer.sentence(slots: over, groupName: "Book club", monthName: "December", now: now)
                == "Nothing works for everyone in Book club for the rest of December.")
    }
}
