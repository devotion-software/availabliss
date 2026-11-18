import Foundation
import Testing
@testable import Availabliss

/// Ported from web `client/templates.test.ts` and Android `TemplatesTest`.
@MainActor
struct TemplatesTests {
    private func savedWeekend() -> Requirement {
        var saved = TimingEditorViewModel.fresh(.weekend, today: Fixtures.day("2026-01-10"), timezone: "Europe/London")
        saved.range = Fixtures.range("2026-01-10", "2026-03-10")
        return saved
    }

    @Test func templatesRestartTheirSearchRangeTodayAndDropPastDates() {
        var saved = savedWeekend()
        saved.dates = [Fixtures.range("2026-02-01", "2026-02-05"), Fixtures.range("2026-12-20", "2026-12-31")]
        saved.customSchoolHolidays = [Fixtures.range("2026-10-26", "2026-10-30")]
        let template = RequirementTemplate(id: "t1", name: "Weekends", requirement: saved, createdAt: Fixtures.date("2026-01-10T00:00:00Z"))

        let r = template.requirementForNewGroup(today: Fixtures.day("2026-11-02"), timezone: "America/New_York")
        // 10 Jan – 10 Mar is 59 days, kept from today.
        #expect(r.range == Fixtures.range("2026-11-02", "2026-12-31"))
        #expect(r.dates == [Fixtures.range("2026-12-20", "2026-12-31")])
        #expect(r.customSchoolHolidays.isEmpty)
        #expect(r.timezone == "America/New_York")
        #expect(r.length == saved.length)
        #expect(r.days == saved.days)
        // The template itself is untouched.
        #expect(template.requirement.dates.count == 2)
    }

    @Test func aDateRangeEndingTodayIsKept() {
        var saved = savedWeekend()
        saved.dates = [Fixtures.range("2026-10-30", "2026-11-02")]
        #expect(saved.forNewGroup(today: Fixtures.day("2026-11-02"), timezone: "Europe/London").dates == saved.dates)
    }

    @Test func aTimingMatchesTheTemplateItWasSavedAsWhereverItsRangeStarts() {
        let saved = savedWeekend()
        let template = RequirementTemplate(id: "t1", name: "Weekends", requirement: saved, createdAt: Fixtures.date("2026-01-10T00:00:00Z"))
        #expect([template].matching(saved)?.id == "t1")
        let used = saved.forNewGroup(today: Fixtures.day("2026-11-02"), timezone: "America/New_York")
        #expect([template].matching(used)?.id == "t1")

        var otherDays = saved
        otherDays.days = [0, 1]
        #expect(!otherDays.isSameTiming(as: saved))
        var longer = saved
        longer.range = Fixtures.range("2026-01-10", "2026-04-10")
        #expect(!longer.isSameTiming(as: saved))
        // Day order and repeats don't matter.
        var reordered = saved
        reordered.days = [6, 4, 5, 5]
        #expect(reordered.isSameTiming(as: saved))
        #expect([RequirementTemplate]().matching(saved) == nil)
    }

    @Test func whoNeedsToBeFreeNeverComesWithATemplate() {
        // Like Android and web: it belongs to one group's members (and the server doesn't keep it in templates).
        var most = savedWeekend()
        most.quorum = 2
        most.optionalMemberIds = ["tom"]
        #expect(most.isSameTiming(as: savedWeekend()))
        let fromTemplate = most.forNewGroup(today: Fixtures.day("2026-11-01"), timezone: "Europe/London")
        #expect(fromTemplate.quorum == nil)
        #expect(fromTemplate.optionalMemberIds == nil)
    }

    @Test func templatesDecodeFromTheAPI() throws {
        let response = try Fixtures.decode(TemplatesResponse.self, """
        {"templates":[{"id":"t1","name":"Weekends","createdAt":"2026-01-10T00:00:00.000Z","requirement":
          {"length":{"type":"span","startDay":4,"startTime":"18:00","endDay":6,"endTime":"23:59","weeksLater":0},"window":null,
           "days":[4,5,6],"bankHolidays":true,"schoolHolidaysOnly":false,"schoolAuthority":null,"customSchoolHolidays":[],
           "range":{"from":"2026-01-10","to":"2026-03-10"},"dates":[],"timezone":"Europe/London"}}]}
        """)
        #expect(response.templates.map(\.name) == ["Weekends"])
        #expect(response.templates.matching(savedWeekend())?.id == "t1")
    }

    @Test func noOptionalMembersMatchesAnEmptyList() throws {
        // The app always sends `optionalMemberIds: []`, so a template it saved can come back with an
        // empty list while a fresh draft has nil. Both mean "nobody is optional".
        var fromServer = savedWeekend()
        fromServer.optionalMemberIds = []
        #expect(fromServer.isSameTiming(as: savedWeekend()))
        // Nor does a quorum stop a template matching: templates don't keep who needs to be free.
        var withQuorum = savedWeekend()
        withQuorum.quorum = 3
        #expect(withQuorum.isSameTiming(as: savedWeekend()))
    }
}
