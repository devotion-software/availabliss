import Foundation
import Testing
@testable import Availabliss

/// Ported from web `mytime/parts.test.ts` and Android `DaysLayoutTest`.
struct DayLayoutTests {
    let london = Fixtures.calendar("Europe/London")
    /// Monday 2 November 2026 (GMT, so UTC = London).
    let week = Fixtures.day("2026-11-02")

    private func busy(_ start: String, _ end: String, _ source: BusyInterval.Source = .manual) -> BusyInterval {
        BusyInterval(start: Fixtures.date(start), end: Fixtures.date(end), source: source, label: nil, calendarId: nil, blockId: source == .manual ? "b" : nil)
    }

    private func layoutWeek(from start: CalendarDay, _ intervals: [BusyInterval], calendar: Calendar) -> DayLayout {
        DayLayout(days: (0..<7).map { start.adding(days: $0, calendar: calendar) }, intervals: intervals, calendar: calendar)
    }

    @Test func placesIntervalsAtExactMinutesOnTheirDay() throws {
        let layout = layoutWeek(from: week, [busy("2026-11-03T19:00:00Z", "2026-11-03T21:30:00Z")], calendar: london)
        #expect(layout.days.count == 7)
        let tuesday = try #require(layout.timed[Fixtures.day("2026-11-03")])
        #expect(tuesday.count == 1)
        let placed = try #require(tuesday.first)
        #expect(placed.from == 19 * 60)
        #expect(placed.to == 21 * 60 + 30)
        #expect(placed.minutes == 150)
        #expect(placed.lane == 0)
        #expect(placed.lanes == 1)
        #expect(!placed.continuesBefore)
        #expect(!placed.continuesAfter)
        #expect(layout.timed[week]?.isEmpty == true)
        #expect(!layout.hasAllDay)
    }

    @Test func splitsIntervalsAcrossMidnight() throws {
        let layout = layoutWeek(from: week, [busy("2026-11-06T22:00:00Z", "2026-11-07T02:00:00Z")], calendar: london)
        let friday = try #require(layout.timed[Fixtures.day("2026-11-06")]?.first)
        #expect(friday.from == 22 * 60)
        #expect(friday.to == 1440)
        #expect(friday.continuesAfter)
        let saturday = try #require(layout.timed[Fixtures.day("2026-11-07")]?.first)
        #expect(saturday.from == 0)
        #expect(saturday.to == 120)
        #expect(saturday.continuesBefore)
    }

    @Test func wholeDaysGoToTheAllDayRow() {
        let layout = layoutWeek(from: week, [busy("2026-11-07T00:00:00Z", "2026-11-09T00:00:00Z", .calendar)], calendar: london)
        #expect(layout.allDay[Fixtures.day("2026-11-07")]?.count == 1)
        #expect(layout.allDay[Fixtures.day("2026-11-08")]?.count == 1)
        #expect(layout.allDay[Fixtures.day("2026-11-06")]?.isEmpty == true)
        #expect(layout.timed[Fixtures.day("2026-11-07")]?.isEmpty == true)
        #expect(layout.hasAllDay)
    }

    @Test func usesTheGivenTimeZone() throws {
        let newYork = Fixtures.calendar("America/New_York")
        let layout = layoutWeek(from: week, [busy("2026-11-03T09:00:00Z", "2026-11-03T10:00:00Z")], calendar: newYork)
        let placed = try #require(layout.timed[Fixtures.day("2026-11-03")]?.first)
        #expect(placed.from == 4 * 60)
        #expect(placed.to == 5 * 60)
    }

    @Test func dstDaysKeepWallClockPositionsOnThe24HourAxis() throws {
        // 25 October 2026: the clocks go back in London, so the day is 25 hours long.
        #expect(DayLayout.dayLength(Fixtures.day("2026-10-25"), calendar: london) == 1500)
        #expect(DayLayout.dayLength(Fixtures.day("2026-03-29"), calendar: london) == 1380)
        let layout = layoutWeek(from: Fixtures.day("2026-10-19"), [busy("2026-10-25T23:00:00Z", "2026-10-25T23:59:00Z")], calendar: london)
        let sunday = try #require(layout.timed[Fixtures.day("2026-10-25")]?.first)
        // 23:00 UTC is 23:00 GMT, the 25th hour of a 25-hour day: scaled to 23:00 on the axis.
        #expect(Int(sunday.from.rounded()) == Int((1440 * 24.0 / 25).rounded()))
        #expect(DayLayout.minutesIntoDay(Fixtures.date("2026-10-25T23:00:00Z"), on: Fixtures.day("2026-10-25"), calendar: london).rounded() == 1382)
    }

    @Test func overlapsShareAClusterLaneCountBackToBackItemsDoNot() {
        let day = week
        func item(_ label: String, _ from: Double, _ to: Double) -> DayLayout.Placed {
            let interval = BusyInterval(start: Fixtures.date("2026-11-02T00:00:00Z"), end: Fixtures.date("2026-11-02T01:00:00Z"), source: .manual, label: label, calendarId: nil, blockId: label)
            return DayLayout.Placed(interval: interval, day: day, from: from, to: to, continuesBefore: false, continuesAfter: false)
        }
        let out = DayLayout.assignLanes([item("a", 60, 180), item("b", 120, 240), item("c", 200, 260), item("d", 260, 300)])
        let byLabel = Dictionary(uniqueKeysWithValues: out.map { ($0.interval.label!, $0) })
        #expect(["a", "b", "c"].map { byLabel[$0]!.lane } == [0, 1, 0])
        #expect(["a", "b", "c"].map { byLabel[$0]!.lanes } == [2, 2, 2])
        #expect(byLabel["d"]!.lane == 0)
        #expect(byLabel["d"]!.lanes == 1)
    }

    @Test func tapRangeSuggestsTheHourTapped() {
        #expect(DayLayout.tapRange(at: 14 * 60 + 40) == (840, 900))
        #expect(DayLayout.tapRange(at: 1439) == (1380, 1440))
        #expect(DayLayout.tapRange(at: 0) == (0, 60))
        #expect(DayLayout.tapRange(at: -5) == (0, 60))
    }

    @Test func clockFormatsMinutes() {
        #expect(DayLayout.clock(555) == "09:15")
        #expect(DayLayout.clock(1440) == "24:00")
        #expect(DayLayout.clock(59.6) == "01:00")
    }
}
