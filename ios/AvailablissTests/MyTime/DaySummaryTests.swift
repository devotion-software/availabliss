import Foundation
import Testing

@testable import Availabliss

/// Ported from Android `DaySummaryTest` and `MyTimeViewsTest` (free minutes and levels).
struct DaySummaryTests {
    let london = Fixtures.calendar("Europe/London")
    /// Tuesday 17 November 2026 (GMT, so UTC = London).
    let day = Fixtures.day("2026-11-17")

    private func h(_ hour: Int, _ minute: Int = 0) -> Double { Double(hour * 60 + minute) }
    private let dayEnd = Double(DayLayout.dayMinutes)

    private func text(_ busy: [ClosedRange<Double>]) -> String {
        DaySummary.describe(DaySummary.longestFree(DaySummary.merge(busy)))
    }

    private func busy(_ start: String, _ end: String, _ source: BusyInterval.Source = .calendar, label: String? = nil, title: String? = nil, ignored: Bool = false)
        -> BusyInterval
    {
        BusyInterval(
            start: Fixtures.date(start), end: Fixtures.date(end), source: source, label: label, calendarId: source == .calendar ? "c" : nil,
            blockId: source == .manual ? "b" : nil, ignoreId: ignored ? "i" : nil, title: title
        )
    }

    private func row(_ intervals: BusyInterval...) -> DaySummary {
        let layout = DayLayout(days: [day], intervals: intervals, calendar: london)
        return DaySummary(day: day, allDay: layout.allDay[day] ?? [], timed: layout.timed[day] ?? [])
    }

    @Test func nothingBusyIsFreeAllDay() {
        #expect(text([]) == "Free all day")
        // Busy only before 06:00 doesn't count against the day.
        #expect(text([h(1)...h(2)]) == "Free all day")
    }

    @Test func wholeWindowBusyIsBusyAllDay() {
        #expect(text([0...dayEnd]) == "Busy all day")
        #expect(text([h(6)...h(23, 55)]) == "Busy all day")
        #expect(DaySummary.longestFree([0...dayEnd]) == nil)
    }

    @Test func freeEveningIsFreeFrom() {
        #expect(text([h(9)...h(17, 30)]) == "Free from 17:30")
    }

    @Test func freeMorningIsFreeUntil() {
        #expect(text([h(13)...dayEnd]) == "Free until 13:00")
    }

    @Test func longestStretchInTheMiddleIsFreeBetween() {
        #expect(DaySummary.longestFree([h(6)...h(12), h(14)...h(23)]) == h(12)...h(14))
        #expect(text([h(6)...h(12), h(14)...h(23)]) == "Free 12:00 – 14:00")
    }

    @Test func picksTheLongestStretchTiesGoToTheLaterOne() {
        // Free 06–08 (2h), 12–13 (1h), 22–24 (2h): the evening wins the tie.
        #expect(DaySummary.longestFree([h(8)...h(12), h(13)...h(22)]) == h(22)...dayEnd)
    }

    @Test func tinyGapsDontCountAsFree() {
        #expect(text([h(6)...h(23, 59)]) == "Busy all day")
        #expect(text([h(6)...h(12), h(12, 10)...dayEnd]) == "Busy all day")
    }

    @Test func mergeJoinsOverlapsAndTouchingRanges() {
        #expect(
            DaySummary.merge([h(10)...h(12), h(13)...h(14), h(9)...h(11), h(11)...h(11, 30), h(14)...h(15)])
                == [h(9)...h(12), h(13)...h(15)]
        )
    }

    @Test func rowSplitsCalendarAndOwnBlocksAndIgnoresTimeMarkedNotBusy() {
        let summary = row(
            busy("2026-11-17T19:00:00Z", "2026-11-17T21:30:00Z", .manual, label: "Choir"),
            busy("2026-11-17T09:00:00Z", "2026-11-17T17:00:00Z"),
            busy("2026-11-17T22:00:00Z", "2026-11-17T23:00:00Z", ignored: true)
        )
        // Calendar busy first, so your blocks draw on top.
        #expect(
            summary.segments == [
                DaySummary.Segment(from: h(9), to: h(17), manual: false),
                DaySummary.Segment(from: h(19), to: h(21, 30), manual: true),
            ])
        #expect(summary.busy == [h(9)...h(17), h(19)...h(21, 30)])
        // 06:00 – 09:00 is the longest free stretch.
        #expect(summary.text == "Free until 09:00")
        #expect(!summary.isBusyAllDay)
        #expect(summary.names == ["Choir"])
        #expect(summary.hasOwnBlock)
    }

    @Test func rowNamesYourBlocksAndEventsInTimeOrderWithoutRepeats() {
        let summary = row(
            busy("2026-11-17T18:00:00Z", "2026-11-17T19:00:00Z", .manual, label: "Gym"),
            busy("2026-11-17T09:00:00Z", "2026-11-17T10:00:00Z", .manual, label: "Dentist"),
            busy("2026-11-17T11:00:00Z", "2026-11-17T12:00:00Z", label: "Work calendar"),
            busy("2026-11-17T20:00:00Z", "2026-11-17T21:00:00Z", .manual, label: "Gym")
        )
        // A calendar's name is never shown, only the event's (none here).
        #expect(summary.names == ["Dentist", "Gym"])
        #expect(row(busy("2026-11-17T11:00:00Z", "2026-11-17T12:00:00Z", label: "Work calendar")).names.isEmpty)
        #expect(row(busy("2026-11-17T11:00:00Z", "2026-11-17T12:00:00Z", label: "Work calendar", title: "Stand-up")).names == ["Stand-up"])
        #expect(!row(busy("2026-11-17T11:00:00Z", "2026-11-17T12:00:00Z")).hasOwnBlock)
    }

    @Test func allDayItemsMakeTheDayBusy() {
        let summary = row(busy("2026-11-17T00:00:00Z", "2026-11-18T00:00:00Z", .manual, label: "Away"))
        #expect(summary.text == "Busy all day")
        #expect(summary.isBusyAllDay)
        #expect(summary.segments == [DaySummary.Segment(from: 0, to: dayEnd, manual: true)])
        #expect(summary.names == ["Away"])
        #expect(summary.freeLevel == 0)
    }

    @Test func freeMinutesCountOnly0600To2400AndOverlapsOnce() {
        #expect(row().freeMinutes == 18 * 60)
        let summary = row(
            busy("2026-11-17T04:00:00Z", "2026-11-17T07:00:00Z"),
            busy("2026-11-17T09:00:00Z", "2026-11-17T12:00:00Z"),
            busy("2026-11-17T10:00:00Z", "2026-11-17T13:00:00Z", .manual)
        )
        // 06–07 and 09–13 are busy: 5 hours of the 18.
        #expect(summary.freeMinutes == 13 * 60)
    }

    @Test func freeLevelThresholds() {
        #expect(row().freeLevel == 3)
        #expect(row(busy("2026-11-17T06:00:00Z", "2026-11-17T12:00:00Z")).freeLevel == 3)
        #expect(row(busy("2026-11-17T06:00:00Z", "2026-11-17T12:01:00Z")).freeLevel == 2)
        #expect(row(busy("2026-11-17T06:00:00Z", "2026-11-17T18:00:00Z")).freeLevel == 2)
        #expect(row(busy("2026-11-17T06:00:00Z", "2026-11-17T23:30:00Z")).freeLevel == 1)
        #expect(row(busy("2026-11-17T06:00:00Z", "2026-11-17T23:31:00Z")).freeLevel == 0)
    }

    @Test(.enabled(if: TestLocale.isBritish, TestLocale.reason))
    func accessibilityReadsTheBusyRangesAndTheFreeTime() {
        let summary = row(
            busy("2026-11-17T09:00:00Z", "2026-11-17T17:00:00Z"),
            busy("2026-11-17T19:00:00Z", "2026-11-17T21:30:00Z", .manual, label: "Choir")
        )
        #expect(summary.accessibilityText == "Tuesday 17 November, busy 09:00 to 17:00 and 19:00 to 21:30, free until 09:00, Choir")
        let away = row(busy("2026-11-17T00:00:00Z", "2026-11-18T00:00:00Z", .manual, label: "Away"))
        #expect(away.accessibilityText == "Tuesday 17 November, busy all day, Away")
    }
}
