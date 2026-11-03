import Foundation

struct Availability: Decodable, Sendable {
    let from: CalendarDay
    let to: CalendarDay
    let visibleWeekdays: [Int]
    let days: [AvailabilityDay]
    let slots: [Slot]
    let syncedCount: Int
    let memberCount: Int
    let pendingMemberIds: [String]
    /// Synced members whose calendars are only known up to `until`, before the end of this range.
    var checkedUntil: [CoverageGap]? = nil

    struct CoverageGap: Decodable, Sendable, Hashable {
        let userId: String
        let until: Date
    }

    func day(_ date: CalendarDay) -> AvailabilityDay? { days.first { $0.date == date } }

    /// The working slot that covers `date` (in the device calendar), if any.
    func slot(containing date: CalendarDay, calendar: Calendar = .current) -> Slot? {
        let dayStart = date.startDate(calendar: calendar)
        let dayEnd = date.adding(days: 1, calendar: calendar).startDate(calendar: calendar)
        return slots.first { $0.start < dayEnd && $0.end > dayStart }
    }
}

struct AvailabilityDay: Decodable, Sendable, Hashable {
    enum Status: String, Decodable, Sendable { case hidden, busy, free, match }
    enum SlotPosition: String, Decodable, Sendable { case start, middle, end, single }

    let date: CalendarDay
    let status: Status
    let busyMemberIds: [String]
    let slotPosition: SlotPosition?
    let holiday: String?
}

struct Slot: Decodable, Sendable, Hashable, Identifiable {
    let start: Date
    let end: Date
    let label: String
    var id: Date { start }
}

struct TimeSpan: Decodable, Sendable, Hashable {
    let start: Date
    let end: Date
}

struct DayAvailability: Decodable, Sendable {
    struct MemberDay: Decodable, Sendable, Identifiable {
        struct Busy: Decodable, Sendable, Hashable {
            let start: Date
            let end: Date
            let manual: Bool
        }
        let userId: String
        let pending: Bool
        let busy: [Busy]
        var id: String { userId }
    }

    let date: CalendarDay
    let window: TimeSpan?
    let members: [MemberDay]
    let everyoneFree: [TimeSpan]
}
