import Foundation
import Testing

@testable import Availabliss

/// The timing sentence's wording. Same cases as web's `requirements.test.ts`.
@MainActor
struct TimingSentenceTests {
    private func requirement(_ preset: TimingPreset) -> Requirement {
        TimingEditorViewModel.fresh(preset, today: CalendarDay(year: 2026, month: 11, day: 20), timezone: "UTC")
    }

    @Test func kinds() {
        #expect(requirement(.weekend).kindPhrase == "a weekend")
        #expect(requirement(.twoHours).kindPhrase == "2 hours")
        var r = requirement(.twoHours)
        r.length = .duration(minutes: 150)
        #expect(r.kindPhrase == "2½ hours")
        r.length = .duration(minutes: 45)
        #expect(r.kindPhrase == "45 minutes")
        r.length = .days(count: 3)
        r.window = nil
        #expect(r.kindPhrase == "3 days in a row")
    }

    @Test func aSpan() {
        let span = TimingPreset.weekendSpan
        #expect(Requirement.startPhrase(span) == "Friday 18:00")
        #expect(Requirement.endPhrase(span) == "Sunday night")
        var later = span
        later.endTime = WallTime(hour: 17, minute: 0)
        later.weeksLater = 1
        #expect(Requirement.endPhrase(later) == "the following Sunday 17:00")
    }

    @Test func timesAndDays() {
        #expect(Requirement.windowPhrase(nil) == "any time of day")
        #expect(Requirement.windowPhrase(TimingPreset.eveningWindow) == "between 18:00 and midnight")
        #expect(Requirement.daysPhrase([]) == "any day")
        #expect(Requirement.daysPhrase([4, 5, 6]) == "Fridays to Sundays")
        #expect(Requirement.daysPhrase([0, 2, 4]) == "Mondays, Wednesdays and Fridays")
        #expect(Requirement.daysPhrase([5, 6]) == "weekends")
    }
}
