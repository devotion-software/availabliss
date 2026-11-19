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

    /// The day for `date`: `days` is in date order (one per date), so a binary search rather than a scan
    /// (it's looked up several times per month cell on every redraw).
    func day(_ date: CalendarDay) -> AvailabilityDay? {
        var low = 0
        var high = days.count - 1
        while low <= high {
            let mid = (low + high) / 2
            let found = days[mid].date
            if found == date { return days[mid] }
            if found < date { low = mid + 1 } else { high = mid - 1 }
        }
        return nil
    }

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
    /// A bank holiday in the group owner's region (`holiday` is then its name).
    var bankHoliday: Bool?
    /// Members whose preferred times this day's time that works fits.
    var preferredBy: [String]?
    /// A `match` day whose best time only meets the group's quorum: someone's missing.
    var partial: Bool?

    var isPartial: Bool { partial == true }
    var isBankHoliday: Bool { bankHoliday == true }
}

struct Slot: Decodable, Sendable, Hashable, Identifiable {
    let start: Date
    let end: Date
    let label: String
    /// Members whose preferred times this slot fits.
    var preferredBy: [String]?
    /// Synced members busy at this time (with a quorum or optional members); empty when everyone's free.
    var missingMemberIds: [String]?
    /// Pro: the "best date" score (higher is better), weighing preferences and who's compromised before.
    var score: Double?
    /// Pro: people this time suits who've compromised in the group's recent plans.
    var fairFor: [String]?
    var id: Date { start }

    /// How many synced members can't make it (0 without a quorum).
    var missingCount: Int { missingMemberIds?.count ?? 0 }
}

/// A member's preferred time for a group. A guideline: it never blocks a date.
struct GroupPreference: Codable, Sendable, Identifiable, Hashable {
    enum Kind: String, Codable, Sendable { case dates, weekly }
    struct Window: Codable, Sendable, Hashable { let from: String; let to: String }

    let id: String
    let userId: String
    let kind: Kind
    let from: String?
    let to: String?
    let days: [Int]
    let window: Window?
    /// "Weekends after 16:00", "12 – 14 Dec".
    let label: String
}

struct GroupPreferencesResponse: Decodable, Sendable { let preferences: [GroupPreference] }

/// Body for adding a preference: `dates` (from, to) or `weekly` (days, window).
struct NewGroupPreference: Encodable, Sendable {
    let kind: GroupPreference.Kind
    var from: String?
    var to: String?
    var days: [Int]?
    var window: GroupPreference.Window?
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
    /// With a quorum or optional members: when enough of the group is free (includes `everyoneFree`).
    var mostFree: [TimeSpan]?
}
