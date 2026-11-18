import Foundation
import Testing
@testable import Availabliss

/// Days and wall-clock times as the API sends them, and the shared date wording.
struct CalendarValuesTests {
    let london = Fixtures.calendar("Europe/London")

    @Test func calendarDaysParseAndPrint() throws {
        let day = try #require(CalendarDay(string: "2026-11-02"))
        #expect(day == CalendarDay(year: 2026, month: 11, day: 2))
        #expect(day.description == "2026-11-02")
        #expect(CalendarDay(string: "2026-11") == nil)
        #expect(CalendarDay(string: "soon") == nil)
        #expect(try Fixtures.decode([CalendarDay].self, #"["2026-01-09"]"#) == [CalendarDay(year: 2026, month: 1, day: 9)])
        #expect(throws: DecodingError.self) { try Fixtures.decode(CalendarDay.self, #""9 Jan""#) }
        #expect(CalendarDay(year: 2026, month: 12, day: 31) < CalendarDay(year: 2027, month: 1, day: 1))
    }

    @Test func weekdaysStartOnMonday() {
        let monday = Fixtures.day("2026-11-16")
        #expect(monday.weekday(calendar: london) == 0)
        #expect(Fixtures.day("2026-11-22").weekday(calendar: london) == 6)
        #expect(Fixtures.day("2026-11-19").startOfWeek(calendar: london) == monday)
        #expect(Fixtures.day("2026-11-22").startOfWeek(calendar: london) == monday)
        #expect(Weekday.short(4) == "Fri")
        #expect(Weekday.full(6) == "Sunday")
        #expect(Weekday.short(7) == "")
    }

    @Test func monthsAndRanges() {
        #expect(Fixtures.day("2026-02-10").lastOfMonth(calendar: london) == Fixtures.day("2026-02-28"))
        #expect(Fixtures.day("2028-02-10").lastOfMonth(calendar: london) == Fixtures.day("2028-02-29"))
        #expect(Fixtures.day("2026-11-30").addingMonths(3, calendar: london) == Fixtures.day("2027-02-01"))
        #expect(Fixtures.day("2026-12-31").adding(days: 1, calendar: london) == Fixtures.day("2027-01-01"))
        // Across the clocks going back.
        #expect(Fixtures.day("2026-10-24").adding(days: 2, calendar: london) == Fixtures.day("2026-10-26"))
        #expect(Fixtures.day("2026-12-30").through(Fixtures.day("2027-01-02"), calendar: london).map(\.description)
            == ["2026-12-30", "2026-12-31", "2027-01-01", "2027-01-02"])
        #expect(Fixtures.range("2026-12-19", "2027-01-03").contains(Fixtures.day("2027-01-03")))
        #expect(!Fixtures.range("2026-12-19", "2027-01-03").contains(Fixtures.day("2027-01-04")))
    }

    @Test func wallTimes() throws {
        let time = try #require(WallTime(string: "07:05"))
        #expect(time.description == "07:05")
        #expect(time.minutesSinceMidnight == 425)
        #expect(WallTime.endOfDay.isEndOfDay)
        #expect(!WallTime.midnight.isEndOfDay)
        #expect(WallTime(string: "7") == nil)
        #expect(WallTime(hour: 9, minute: 0) < WallTime(hour: 18, minute: 30))
        #expect(try Fixtures.encode(TimeWindow(from: .midnight, to: .endOfDay)) == #"{"from":"00:00","to":"23:59"}"#)
    }

    @Test func isoDatesWithAndWithoutFractionalSeconds() {
        #expect(JSONCoding.parseDate("2026-11-13T18:00:00.000Z") == JSONCoding.parseDate("2026-11-13T18:00:00Z"))
        #expect(JSONCoding.parseDate("13/11/2026") == nil)
        #expect(JSONCoding.formatDate(Fixtures.date("2026-11-13T18:00:00Z")) == "2026-11-13T18:00:00.000Z")
    }

    @Test func plurals() {
        #expect(Formatting.plural(1, "day") == "1 day")
        #expect(Formatting.plural(3, "day") == "3 days")
        #expect(Formatting.plural(2, "person", "people") == "2 people")
        #expect(Formatting.plural(0, "weekend") == "0 weekends")
    }

    @Test func memberSummariesShortenLongLists() {
        #expect(Formatting.memberSummary(["Ana", "Priya", "Tom", "Leo", "Sam", "Jo"]) == "Ana, Priya, Tom and 3 others")
        #expect(Formatting.memberSummary(["Ana", "Priya", "Tom", "Leo", "Sam"], visible: 2) == "Ana, Priya and 3 others")
    }

    @Test(.enabled(if: TestLocale.isBritish, TestLocale.reason))
    func spansReadAsTheServerLabelsThem() {
        let friday = Fixtures.day("2026-11-13")
        // An evening within a day, one that runs to midnight, an all-day one and a weekend.
        #expect(Formatting.span(start: Fixtures.at(friday, 18, 30), end: Fixtures.at(friday, 21), allDay: false) == "Fri 13 Nov · 18:30 – 21:00")
        #expect(Formatting.span(start: Fixtures.at(friday, 18), end: friday.adding(days: 1).startDate(), allDay: false) == "Fri 13 Nov · from 18:00")
        #expect(Formatting.span(start: friday.startDate(), end: friday.adding(days: 1).startDate(), allDay: true) == "Fri 13 Nov")
        #expect(Formatting.span(start: Fixtures.at(friday, 18), end: friday.adding(days: 3).startDate(), allDay: false) == "Fri 13 – Sun 15 Nov")
        #expect(Formatting.dayRange(Fixtures.day("2026-11-02"), Fixtures.day("2026-11-08")) == "2 – 8 Nov")
        #expect(Formatting.dayRange(Fixtures.day("2026-12-28"), Fixtures.day("2027-01-03")) == "28 Dec – 3 Jan")
        #expect(Formatting.list(["Ana", "Priya", "Tom"]) == "Ana, Priya and Tom")
    }
}
