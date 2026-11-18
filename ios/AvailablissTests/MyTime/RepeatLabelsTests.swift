import Foundation
import Testing
@testable import Availabliss

/// Ported from Android `RepeatLabelsTest`. Blocks are read in the device's calendar, as the app does.
struct RepeatLabelsTests {
    /// Monday 16 November 2026: the 3rd Monday.
    let monday = Fixtures.day("2026-11-16")

    private func start(_ day: String, _ hour: Int = 7) -> Date { Fixtures.at(Fixtures.day(day), hour) }

    @Test func ordinals() {
        #expect([1, 2, 3, 4, 11, 12, 13, 21, 22, 23, 31, 101, 111].map(ManualBlock.Repeat.ordinal)
            == ["1st", "2nd", "3rd", "4th", "11th", "12th", "13th", "21st", "22nd", "23rd", "31st", "101st", "111th"])
    }

    @Test func monthlyWeekdayIsTheLastOneInTheMonthsLastSevenDays() {
        // Mon 30 Nov 2026 (5th Monday) and Tue 24 Nov (4th, but within the last 7 days); Mon 23 Nov is the 4th.
        #expect(ManualBlock.Repeat.weekOfMonth(start("2026-11-30")) == "last")
        #expect(ManualBlock.Repeat.weekOfMonth(start("2026-11-24")) == "last")
        #expect(ManualBlock.Repeat.weekOfMonth(start("2026-11-23")) == "4th")
        #expect(ManualBlock.Repeat.weekOfMonth(start("2026-11-16")) == "3rd")
        #expect(ManualBlock.Repeat.weekOfMonth(start("2026-11-01")) == "1st")
        // February 2026 has 28 days.
        #expect(ManualBlock.Repeat.weekOfMonth(start("2026-02-22")) == "last")
        #expect(ManualBlock.Repeat.weekOfMonth(start("2026-02-21")) == "3rd")
    }

    @Test func agendaTags() {
        let all: [ManualBlock.Repeat] = [.none, .daily, .weekdays, .weekly, .fortnightly, .monthly, .monthlyWeekday, .yearly, .unknown]
        #expect(all.map(\.tag) == ["Your block", "Daily", "Weekdays", "Weekly", "Every other week", "Monthly", "Monthly", "Yearly", "Repeats"])
    }

    @Test(.enabled(if: TestLocale.isBritish, TestLocale.reason))
    func menuChoicesReadFromTheStartDate() {
        let from = start("2026-11-16")
        #expect(ManualBlock.Repeat.allCases.map { $0.choice(from: from) } == [
            "Never",
            "Every day",
            "Every weekday (Mon\u{2013}Fri)",
            "Every week on Monday",
            "Every 2 weeks on Monday",
            "Every month on the 16th",
            "Every month on the 3rd Monday",
            "Every year on 16 November",
        ])
        #expect(ManualBlock.Repeat.unknown.choice(from: from) == "Repeats")
        #expect(ManualBlock.Repeat.monthlyWeekday.choice(from: start("2026-11-30")) == "Every month on the last Monday")
        #expect(ManualBlock.Repeat.monthlyWeekday.choice(from: start("2026-11-24")) == "Every month on the last Tuesday")
        #expect(ManualBlock.Repeat.monthlyWeekday.choice(from: start("2026-11-01")) == "Every month on the 1st Sunday")
    }

    private func block(_ repeat: ManualBlock.Repeat, allDay: Bool = false, until: String? = nil) -> ManualBlock {
        let start = allDay ? monday.startDate() : Fixtures.at(monday, 7)
        let end = allDay ? monday.adding(days: 1).startDate() : Fixtures.at(monday, 8)
        return ManualBlock(id: "b", start: start, end: end, allDay: allDay, repeat: `repeat`, until: until.map(Fixtures.day), note: nil)
    }

    @Test(.enabled(if: TestLocale.isBritish, TestLocale.reason))
    func scheduleLines() {
        #expect(block(.daily).scheduleDescription == "Every day · 07:00 – 08:00")
        #expect(block(.weekdays).scheduleDescription == "Weekdays · 07:00 – 08:00")
        #expect(block(.weekly).scheduleDescription == "Every Monday · 07:00 – 08:00")
        #expect(block(.fortnightly).scheduleDescription == "Every other Monday · 07:00 – 08:00")
        #expect(block(.monthly, allDay: true).scheduleDescription == "Monthly on the 16th · All day")
        #expect(block(.yearly, allDay: true).scheduleDescription == "Yearly on 16 Nov · All day")
        #expect(block(.unknown).scheduleDescription == "Repeats · 07:00 – 08:00")
        // A one-off block never shows an end date.
        #expect(block(.none, until: "2026-12-20").scheduleDescription == "Mon 16 Nov · 07:00 – 08:00")
        let untilLine = block(.monthlyWeekday, until: "2026-12-20").scheduleDescription
        #expect(untilLine.hasPrefix("Monthly on the 3rd Monday · 07:00 – 08:00 · until "))
        #expect(untilLine.hasSuffix("20 Dec"))
    }
}
