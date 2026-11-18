import Foundation
import Testing
@testable import Availabliss

/// The timing editor's date ranges, days and "who needs to be free". Ported from Android
/// `RangeListTest` and `WhoNeedsToBeFreeTest`.
@MainActor
struct TimingEditorTests {
    let christmas = Fixtures.range("2026-12-19", "2027-01-03")
    let members = ["ana", "sam", "tom", "priya", "leo"].map { Fixtures.member($0, $0.capitalized) }

    private func editor() -> TimingEditorViewModel {
        TimingEditorViewModel(
            draft: TimingEditorViewModel.fresh(.weekend, today: Fixtures.day("2026-11-01"), timezone: "Europe/London"),
            api: Fixtures.api { _ in .notFound }
        )
    }

    // MARK: Date ranges

    @Test func rangesAlreadyCoveredAreFound() {
        #expect(TimingEditorViewModel.covering(christmas, in: [christmas]) == christmas)
        #expect(TimingEditorViewModel.covering(Fixtures.range("2026-12-24", "2026-12-26"), in: [christmas]) == christmas)
        #expect(TimingEditorViewModel.covering(Fixtures.range("2026-12-18", "2026-12-20"), in: [christmas]) == nil)
    }

    @Test func rangesThatOverlapOrTouchAreJoined() {
        let merged = TimingEditorViewModel.merging([
            Fixtures.range("2027-01-04", "2027-01-06"), christmas,
            Fixtures.range("2026-12-10", "2026-12-20"), Fixtures.range("2027-02-01", "2027-02-02"),
        ])
        #expect(merged == [Fixtures.range("2026-12-10", "2027-01-06"), Fixtures.range("2027-02-01", "2027-02-02")])
        // A day's gap keeps them apart.
        #expect(TimingEditorViewModel.merging([christmas, Fixtures.range("2027-01-05", "2027-01-06")]).count == 2)
    }

    @Test func addingADateRangeJoinsItAndSkipsOnesAlreadyCovered() {
        let editor = editor()
        editor.addDateRange(christmas)
        editor.addDateRange(Fixtures.range("2027-01-04", "2027-01-05"))
        #expect(editor.draft.dates == [Fixtures.range("2026-12-19", "2027-01-05")])
        editor.addDateRange(Fixtures.range("2026-12-24", "2026-12-26"))
        #expect(editor.draft.dates.count == 1)
        editor.removeDateRange(Fixtures.range("2026-12-19", "2027-01-05"))
        #expect(editor.draft.dates.isEmpty)
        // Custom school holidays are a separate list.
        #expect(editor.draft.customSchoolHolidays.isEmpty)
    }

    // MARK: Days

    @Test func togglingDaysKeepsAtLeastOneAndEveryDayIsEmpty() {
        let editor = editor()
        editor.draft.days = []
        #expect(editor.isDayOn(3))
        editor.toggleDay(3)
        #expect(editor.draft.days == [0, 1, 2, 4, 5, 6])
        editor.toggleDay(3)
        #expect(editor.draft.days.isEmpty)
        editor.draft.days = [5]
        editor.toggleDay(5)
        #expect(editor.draft.days == [5])
    }

    @Test func choosingAPresetMarksChanges() {
        let editor = editor()
        #expect(!editor.hasChanges)
        #expect(editor.preset == .weekend)
        #expect(editor.isSpan)
        editor.choose(.twoHours)
        #expect(editor.preset == .twoHours)
        #expect(editor.hasChanges)
        editor.choose(.custom)
        #expect(editor.preset == .custom)
        #expect(editor.customKind == .hours)
        #expect(editor.draft.length == .duration(minutes: 180))
    }

    // MARK: Who needs to be free

    @Test func atLeastStartsOneShortOfEveryone() {
        let editor = editor()
        editor.setUsesQuorum(true, members: members)
        #expect(editor.draft.quorum == 4)
        #expect(editor.usesQuorum)
        editor.setUsesQuorum(false, members: members)
        #expect(editor.draft.quorum == nil)
    }

    @Test func theQuorumStaysBetweenOneAndTheRequiredMembers() {
        let editor = editor()
        editor.setQuorum(5, members: members)
        #expect(editor.draft.quorum == 5)
        editor.setQuorum(9, members: members)
        #expect(editor.draft.quorum == 5)
        editor.setQuorum(-10, members: members)
        #expect(editor.draft.quorum == 1)
    }

    @Test func optionalMembersDontCountAndShrinkTheQuorum() throws {
        let editor = editor()
        editor.setQuorum(5, members: members)
        let leo = try #require(members.first { $0.userId == "leo" })
        let tom = try #require(members.first { $0.userId == "tom" })
        editor.setOptional(true, for: leo, members: members)
        editor.setOptional(true, for: tom, members: members)
        #expect(Set(editor.draft.optionalMemberIds ?? []) == ["leo", "tom"])
        #expect(editor.isOptional(tom))
        #expect(editor.requiredCount(of: members) == 3)
        #expect(editor.draft.quorum == 3)
        editor.setOptional(false, for: tom, members: members)
        #expect(editor.draft.optionalMemberIds == ["leo"])
        // Not raised again: the admin chose the lower number.
        #expect(editor.draft.quorum == 3)
        editor.setOptional(false, for: leo, members: members)
        // Nobody optional any more: nil (encoded as an empty list).
        #expect(editor.draft.optionalMemberIds == nil)
    }

    @Test func optionalMembersWithoutAQuorumLeaveItAtEveryone() throws {
        let editor = editor()
        editor.setOptional(true, for: try #require(members.first), members: members)
        #expect(editor.draft.quorum == nil)
    }

    @Test func quorumAndOptionalMembersGoOnTheWire() throws {
        var r = editor().draft
        r.quorum = 3
        r.optionalMemberIds = ["tom"]
        let json = try Fixtures.object(r)
        #expect(json["quorum"] as? Int == 3)
        #expect(json["optionalMemberIds"] as? [String] == ["tom"])
    }
}
