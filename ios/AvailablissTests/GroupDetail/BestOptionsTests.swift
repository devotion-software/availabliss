import Foundation
import Testing

@testable import Availabliss

/// "Best options" on a group's calendar, and the "most of us are free" wording. Ported from Android
/// `BestOptionsTest` and `PartialDaysTest`, for the ranking iOS has (no preference tiebreak; see the report).
@MainActor
struct BestOptionsTests {
    let now = Fixtures.date("2026-11-10T12:00:00Z")
    let group = Fixtures.group([Fixtures.member("me", "Sam"), Fixtures.member("ana", "Ana"), Fixtures.member("tom", "Tom"), Fixtures.member("sam", "Sam Lee")])

    private func slot(_ day: Int, missing: [String] = [], score: Double? = nil, fairFor: [String] = [], preferredBy: [String] = []) -> String {
        let date = String(format: "2026-11-%02d", day)
        func list(_ ids: [String]) -> String { "[" + ids.map { "\"\($0)\"" }.joined(separator: ",") + "]" }
        return """
            {"start":"\(date)T18:00:00Z","end":"\(date)T22:00:00Z","label":"Day \(day)","preferredBy":\(list(preferredBy)),
             "missingMemberIds":\(list(missing)),"score":\(score.map { "\($0)" } ?? "null"),"fairFor":\(list(fairFor))}
            """
    }

    /// Loads a month whose slots are `slots`, through a stubbed API.
    private func viewModel(_ slots: [String]) async -> GroupAvailabilityViewModel {
        let availability = """
            {"from":"2026-11-01","to":"2026-11-30","visibleWeekdays":[],"days":[],"slots":[\(slots.joined(separator: ","))],
             "syncedCount":4,"memberCount":4,"pendingMemberIds":[]}
            """
        let api = Fixtures.api { request in
            request.url?.path.hasSuffix("/availability") == true ? .json(availability) : .notFound
        }
        let model = GroupAvailabilityViewModel(groupId: "g1", api: api)
        await model.load()
        return model
    }

    private func labels(_ slots: [Slot]) -> [String] { slots.map(\.label) }

    @Test func rankFewerMissingThenScoreThenSoonest() async {
        let model = await viewModel([
            slot(12, missing: ["ana"]),
            slot(13, score: 1),
            slot(14, score: 3),
            slot(15),
            slot(16, preferredBy: ["ana"]),
            slot(11, missing: ["ana", "tom"], score: 9),
        ])
        #expect(model.availability?.slots.count == 6)
        // Preferences come before soonest: Day 16 (Ana prefers it) above Day 15.
        #expect(labels(model.bestOptions(limit: 10, now: now)) == ["Day 14", "Day 13", "Day 16", "Day 15", "Day 12", "Day 11"])
    }

    @Test func pastSlotsAreSkippedAndTheTopThreeKept() async {
        let model = await viewModel([slot(9), slot(11), slot(12), slot(13), slot(14)])
        #expect(labels(model.bestOptions(now: now)) == ["Day 11", "Day 12", "Day 13"])
        #expect(model.bestOptions(now: Fixtures.date("2026-12-01T00:00:00Z")).isEmpty)
    }

    @Test func slotsDecodeTheirProFields() async throws {
        let model = await viewModel([slot(12, missing: ["tom"], score: 2.5, fairFor: ["ana"], preferredBy: ["me"])])
        let slot = try #require(model.availability?.slots.first)
        #expect(slot.missingCount == 1)
        #expect(slot.score == 2.5)
        #expect(slot.fairFor == ["ana"])
        #expect(slot.preferredBy == ["me"])
        let london = Fixtures.calendar("Europe/London")
        #expect(model.availability?.slot(containing: Fixtures.day("2026-11-12"), calendar: london)?.label == "Day 12")
        #expect(model.availability?.slot(containing: Fixtures.day("2026-11-13"), calendar: london) == nil)
    }

    @Test func missingAndFairLines() {
        #expect(Clash.cantMake(["me"], group: group, me: "me") == "You can\u{2019}t make it")
        #expect(Clash.cantMake(["ana", "tom"], group: group, me: "me") == "Ana and Tom can\u{2019}t make it")
        // Android says "3 people can't make it" here; iOS names them.
        #expect(Clash.cantMake(["ana", "tom", "sam"], group: group, me: "me") == "Ana, Tom and Sam can\u{2019}t make it")
        #expect(Clash.fairFor([], group: group, me: "me") == nil)
        #expect(Clash.fairFor(["me", "ana"], group: group, me: "me") == "Fair for you and Ana, who missed out last time")
    }

    @Test func olderServersDecodeWithoutTheNewFields() throws {
        let availability = try Fixtures.decode(
            Availability.self,
            """
            {"from":"2026-11-01","to":"2026-11-30","visibleWeekdays":[4,5,6],
             "days":[{"date":"2026-11-13","status":"match","busyMemberIds":[],"slotPosition":"start","holiday":null},
                     {"date":"2026-11-14","status":"match","busyMemberIds":["tom"],"slotPosition":"end","holiday":null,"partial":true}],
             "slots":[{"start":"2026-11-13T18:00:00Z","end":"2026-11-15T23:59:00Z","label":"Fri 13 – Sun 15 Nov"}],
             "syncedCount":3,"memberCount":4,"pendingMemberIds":["leo"]}
            """)
        #expect(availability.slots.first?.missingCount == 0)
        #expect(availability.slots.first?.score == nil)
        #expect(availability.day(Fixtures.day("2026-11-13"))?.isPartial == false)
        #expect(availability.day(Fixtures.day("2026-11-14"))?.isPartial == true)
        #expect(availability.checkedUntil == nil)
        #expect(availability.day(Fixtures.day("2026-11-20")) == nil)
    }
}
