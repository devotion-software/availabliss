import Foundation
import Testing

@testable import Availabliss

/// The "How long?" presets and how a timing is described. Ported from Android `RequirementPresetsTest`.
@MainActor
struct RequirementPresetsTests {
    let today = Fixtures.day("2026-11-01")

    private func fresh(_ preset: TimingPreset) -> Requirement {
        TimingEditorViewModel.fresh(preset, today: today, timezone: "Europe/London")
    }

    private func window(_ from: String, _ to: String) -> TimeWindow {
        TimeWindow(from: WallTime(string: from)!, to: WallTime(string: to)!)
    }

    @Test func theDefaultIsAWeekendOverTheNextThreeMonths() {
        let r = Requirement.defaultValue(today: today, timezone: "Europe/London")
        #expect(r.range == Fixtures.range("2026-11-01", "2027-01-31"))
        #expect(r.days == [4, 5, 6])
        #expect(r.bankHolidays)
        #expect(r.quorum == nil)
        #expect(TimingPreset.detect(r.length, window: r.window) == .weekend)
    }

    @Test func twoHoursIsADurationWithAWindow() {
        let r = fresh(.twoHours)
        #expect(r.length == .duration(minutes: 120))
        #expect(r.window == window("08:00", "23:00"))
    }

    @Test func eveningIsOneDayFromSix() {
        let r = fresh(.evening)
        #expect(r.length == .days(count: 1))
        #expect(r.window == window("18:00", "23:59"))
    }

    @Test func oneDayHasNoWindow() {
        let r = fresh(.oneDay)
        #expect(r.length == .days(count: 1))
        #expect(r.window == nil)
    }

    @Test func weekendIsFridaySixToSunday() {
        let r = fresh(.weekend)
        #expect(r.length == .span(.init(startDay: 4, startTime: WallTime(hour: 18, minute: 0), endDay: 6, endTime: .endOfDay, weeksLater: 0)))
        #expect(r.window == nil)
        #expect(r.days == [4, 5, 6])
    }

    @Test func weekIsSevenDaysOnAnyDay() {
        let r = fresh(.week)
        #expect(r.length == .days(count: 7))
        #expect(r.window == nil)
        // Fri–Sun could never hold a week, so it opens up every day (empty = any day).
        #expect(r.days.isEmpty)
    }

    @Test func detectRoundTripsEveryPreset() {
        for preset in TimingPreset.allCases where preset != .custom {
            let r = fresh(preset)
            #expect(TimingPreset.detect(r.length, window: r.window) == preset)
        }
        #expect(TimingPreset.custom.shape == nil)
    }

    @Test func editingTheEveningStartMakesItCustom() {
        let edited = window("19:00", "23:59")
        #expect(TimingPreset.detect(.days(count: 1), window: edited) == .custom)
        #expect(CustomLengthKind(.days(count: 1)) == .days)
        #expect(CustomLengthKind(.duration(minutes: 90)) == .hours)
        let span = RequirementLength.Span(startDay: 1, startTime: .midnight, endDay: 3, endTime: .endOfDay, weeksLater: 1)
        #expect(TimingPreset.detect(.span(span), window: nil) == .custom)
        #expect(CustomLengthKind(.span(span)) == .span)
    }

    @Test func shorterPresetsKeepTheChosenDays() {
        var weekend = fresh(.weekend)
        weekend.apply(.evening)
        #expect(weekend.days == [4, 5, 6])
        weekend.apply(.week)
        #expect(weekend.days.isEmpty)
        #expect(TimingPreset.detect(weekend.length, window: weekend.window) == .week)
    }

    @Test func spanDaysWrapPastSunday() {
        #expect(
            TimingEditorViewModel.days(in: .init(startDay: 5, startTime: WallTime(hour: 9, minute: 0), endDay: 0, endTime: WallTime(hour: 18, minute: 0), weeksLater: 0))
                == [0, 5, 6])
        #expect(TimingEditorViewModel.days(in: TimingPreset.weekendSpan) == [4, 5, 6])
        // "Tue → following Thu" can start any week day.
        #expect(TimingEditorViewModel.days(in: .init(startDay: 1, startTime: .midnight, endDay: 3, endTime: .endOfDay, weeksLater: 1)).isEmpty)
    }

    @Test func slotNounMatchesThePreset() {
        #expect(fresh(.weekend).slotNoun == .weekend)
        #expect(fresh(.evening).slotNoun == .evening)
        #expect(fresh(.oneDay).slotNoun == .day)
        #expect(fresh(.week).slotNoun == .week)
        #expect(fresh(.twoHours).slotNoun == .slot)
        var custom = fresh(.oneDay)
        custom.length = .days(count: 3)
        #expect(custom.slotNoun == .trip)
        custom.length = .duration(minutes: 90)
        #expect(custom.slotNoun == .slot)
    }

    @Test func draftSummaryDescribesTheTiming() {
        #expect(fresh(.weekend).draftSummary == "A weekend · Fri 18:00 → Sun")
        #expect(fresh(.evening).draftSummary == "An evening · 18:00–late")
        #expect(fresh(.twoHours).draftSummary == "2 hours · 08:00–23:00")
        #expect(fresh(.week).draftSummary == "A week")
        var custom = fresh(.twoHours)
        custom.length = .duration(minutes: 90)
        #expect(custom.draftSummary == "1h 30m · 08:00–23:00")
        custom.length = .span(.init(startDay: 1, startTime: .midnight, endDay: 3, endTime: .endOfDay, weeksLater: 1))
        #expect(custom.draftSummary == "Tue → Thu · Tue 00:00 → following Thu")
        custom.length = .days(count: 2)
        custom.window = nil
        #expect(custom.lengthDescription == "2 days")
    }

    @Test func templateSummaryAddsDaysHolidaysAndDates() {
        #expect(fresh(.weekend).templateSummary == "A weekend · Fri 18:00 → Sun")
        var evening = fresh(.evening)
        evening.days = [0, 1, 2, 3, 4]
        #expect(evening.templateSummary == "An evening · 18:00–late · weekdays")
        evening.schoolHolidaysOnly = true
        evening.dates = [Fixtures.range("2026-12-19", "2027-01-03"), Fixtures.range("2027-02-14", "2027-02-21")]
        #expect(evening.templateSummary == "An evening · 18:00–late · weekdays · school holidays · 2 date ranges")
    }

    @Test func describeDays() {
        #expect(Requirement.describeDays([]) == "any day")
        #expect(Requirement.describeDays(Array(0...6)) == "any day")
        #expect(Requirement.describeDays([4, 3, 2, 1, 0]) == "weekdays")
        #expect(Requirement.describeDays([6, 5, 5]) == "weekends")
        #expect(Requirement.describeDays([4, 5, 6]) == "Fri–Sun")
        #expect(Requirement.describeDays([0, 2, 4]) == "Mon, Wed, Fri")
        #expect(Requirement.describeDays([1, 2]) == "Tue, Wed")
    }

    @Test func requirementEncodesExplicitNullsAndTheSpanDiscriminator() throws {
        var r = fresh(.weekend)
        let json = try Fixtures.object(r)
        let length = try #require(json["length"] as? [String: Any])
        #expect(length["type"] as? String == "span")
        #expect(length["weeksLater"] as? Int == 0)
        #expect(length["startTime"] as? String == "18:00")
        #expect(json["window"] is NSNull)
        #expect(json["schoolAuthority"] is NSNull)
        #expect(json["quorum"] is NSNull)
        #expect(json["optionalMemberIds"] as? [String] == [])
        #expect((json["range"] as? [String: Any])?["from"] as? String == "2026-11-01")

        r.quorum = 3
        r.optionalMemberIds = ["tom"]
        let data = try JSONCoding.encoder.encode(r)
        #expect(try JSONCoding.decoder.decode(Requirement.self, from: data) == r)
    }

    @Test func requirementsFromOlderServersDecode() throws {
        let r = try Fixtures.decode(
            Requirement.self,
            """
            {"length":{"type":"duration","minutes":120},"window":{"from":"08:00","to":"23:00"},"days":[],"bankHolidays":true,
             "schoolHolidaysOnly":false,"schoolAuthority":null,"customSchoolHolidays":[],"range":{"from":"2026-11-01","to":"2027-01-31"},
             "dates":[],"timezone":"Europe/London"}
            """)
        #expect(r.quorum == nil)
        #expect(r.optionalMemberIds == nil)
        #expect(r.length == .duration(minutes: 120))
        #expect(throws: DecodingError.self) {
            try Fixtures.decode(RequirementLength.self, #"{"type":"fortnight"}"#)
        }
    }
}
