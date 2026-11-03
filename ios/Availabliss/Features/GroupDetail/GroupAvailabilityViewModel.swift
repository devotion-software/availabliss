import Foundation
import Observation

/// Month / Week / Day availability for one group.
@MainActor
@Observable
final class GroupAvailabilityViewModel {
    enum Mode: Hashable { case month, week, day }

    var mode: Mode = .month
    private(set) var month: CalendarDay = CalendarDay.today().firstOfMonth
    var selectedDay: CalendarDay = .today()
    /// True once the user taps a day; reloads then keep their choice.
    private var userPickedDay = false
    private(set) var availability: Availability?
    private(set) var dayDetails: [CalendarDay: DayAvailability] = [:]
    private(set) var isLoading = false
    private(set) var isNudging = false
    var error: String?
    var toast: String?

    private let groupId: String
    private let api: APIClient

    init(groupId: String, api: APIClient) {
        self.groupId = groupId
        self.api = api
    }

    // MARK: Derived

    /// Weekday columns worth drawing (all seven if the server sends none).
    var columns: [Int] {
        let visible = availability?.visibleWeekdays.sorted() ?? []
        return visible.isEmpty ? Weekday.all : visible
    }

    /// Weeks of the displayed month; each week holds one entry per visible column (nil = outside the month).
    var monthRows: [[CalendarDay?]] {
        let last = month.lastOfMonth()
        var rows: [[CalendarDay?]] = []
        var weekStart = month.startOfWeek()
        while weekStart <= last {
            let row = columns.map { column -> CalendarDay? in
                let day = weekStart.adding(days: column)
                return day.month == month.month && day.year == month.year ? day : nil
            }
            if row.contains(where: { $0 != nil }) { rows.append(row) }
            weekStart = weekStart.adding(days: 7)
        }
        return rows
    }

    var weekDays: [CalendarDay] {
        let start = selectedDay.startOfWeek()
        return columns.map { start.adding(days: $0) }
    }

    var heading: String {
        switch mode {
        case .month: Formatting.shortMonthYear(month)
        case .week:
            if let first = weekDays.first, let last = weekDays.last { Formatting.dayRange(first, last) } else { "" }
        case .day: Formatting.shortDay(selectedDay)
        }
    }

    func day(_ date: CalendarDay) -> AvailabilityDay? { availability?.day(date) }
    func slot(for date: CalendarDay) -> Slot? { availability?.slot(containing: date) }

    // MARK: Loading

    func load() async {
        switch mode {
        case .month: await loadMonth()
        case .week: await loadWeek()
        case .day: await loadDay(selectedDay)
        }
    }

    func step(by delta: Int) async {
        switch mode {
        case .month:
            month = month.addingMonths(delta)
            selectedDay = month == CalendarDay.today().firstOfMonth ? .today() : month
            userPickedDay = false
        case .week: selectedDay = selectedDay.adding(days: 7 * delta)
        case .day: selectedDay = selectedDay.adding(days: delta)
        }
        if mode != .month, selectedDay.firstOfMonth != month { month = selectedDay.firstOfMonth }
        await load()
    }

    /// A day tapped in the month grid.
    func pick(_ date: CalendarDay) {
        selectedDay = date
        userPickedDay = true
    }

    func showDay(_ date: CalendarDay) async {
        selectedDay = date
        mode = .day
        await loadDay(date)
    }

    func invalidate() async {
        dayDetails = [:]
        await load()
    }

    private func loadMonth() async {
        isLoading = availability == nil
        defer { isLoading = false }
        let requested = month
        do {
            let loaded: Availability = try await api.send(API.GroupAvailability.range(groupId: groupId, from: requested, to: requested.lastOfMonth()))
            guard requested == month else { return }
            availability = loaded
            // Start on a day that works rather than one outside the group's timing.
            if !userPickedDay, let firstMatch = loaded.days.first(where: { $0.status == .match }) {
                selectedDay = firstMatch.date
            }
        } catch where !error.isCancellation {
            self.error = error.userMessage
        } catch {}
    }

    private func loadWeek() async {
        if availability == nil || selectedDay.firstOfMonth != availability?.from.firstOfMonth { await loadMonth() }
        let days = weekDays.filter { dayDetails[$0] == nil }
        let api = self.api
        let groupId = self.groupId
        let results = await withTaskGroup(of: (CalendarDay, DayAvailability?).self) { group in
            for day in days {
                group.addTask { (day, try? await api.send(API.GroupAvailability.day(groupId: groupId, date: day))) }
            }
            var collected: [CalendarDay: DayAvailability] = [:]
            for await (day, detail) in group { if let detail { collected[day] = detail } }
            return collected
        }
        dayDetails.merge(results) { _, new in new }
    }

    private func loadDay(_ date: CalendarDay) async {
        do {
            dayDetails[date] = try await api.send(API.GroupAvailability.day(groupId: groupId, date: date))
        } catch where !error.isCancellation {
            self.error = error.userMessage
        } catch {}
    }

    // MARK: Actions

    func nudge() async {
        isNudging = true
        defer { isNudging = false }
        do {
            let result = try await api.send(API.Groups.nudge(groupId: groupId))
            toast = result.nudged == 0 ? "Everyone's already been nudged" : "Nudged \(Formatting.plural(result.nudged, "person", "people"))"
        } catch {
            self.error = error.userMessage
        }
    }
}

/// A run of time with who is busy in it — used by the week grid.
struct BusySegment: Hashable {
    let start: Date
    let end: Date
    let userIds: [String]
}

enum BusyMerge {
    /// Merges everyone's busy intervals into non-overlapping segments, each listing who's busy.
    static func segments(_ members: [DayAvailability.MemberDay]) -> [BusySegment] {
        let edges = Set(members.flatMap { $0.busy.flatMap { [$0.start, $0.end] } }).sorted()
        var result: [BusySegment] = []
        for (start, end) in zip(edges, edges.dropFirst()) {
            let busy = members.filter { member in member.busy.contains { $0.start < end && $0.end > start } }.map(\.userId)
            guard !busy.isEmpty else { continue }
            if let last = result.last, last.end == start, last.userIds == busy {
                result[result.count - 1] = BusySegment(start: last.start, end: end, userIds: busy)
            } else {
                result.append(BusySegment(start: start, end: end, userIds: busy))
            }
        }
        return result
    }
}
