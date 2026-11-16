import Foundation
import Observation

/// Month / Week availability for one group, plus who's free on the selected day.
@MainActor
@Observable
final class GroupAvailabilityViewModel {
    enum Mode: Hashable { case month, week }

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
    /// Months already fetched, and ones on their way (a page change or a prefetch), so paging to a
    /// neighbouring month shows it straight away instead of waiting on the network.
    private var months: [CalendarDay: Availability] = [:]
    private var monthRequests: [CalendarDay: Task<Availability, Error>] = [:]
    /// Day details on their way, shared between what's on screen and prefetching.
    private var dayRequests: [CalendarDay: Task<DayAvailability, Error>] = [:]
    /// Bumped by `invalidate()`, so answers to requests made before it are dropped.
    private var generation = 0
    /// The way the user last paged (1 = later), to prefetch the next week that way.
    private var lastDelta = 1
    /// When a sideways swipe last moved the page. The page follows the finger, so lifting it
    /// would otherwise also tap the day it started on.
    @ObservationIgnored private var lastSwipeAt: Date?

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
        }
    }

    func day(_ date: CalendarDay) -> AvailabilityDay? { availability?.day(date) }
    func slot(for date: CalendarDay) -> Slot? { availability?.slot(containing: date) }

    // MARK: Loading

    func load() async {
        switch mode {
        case .month: await loadMonth()
        case .week: await loadWeek()
        }
        if dayDetails[selectedDay] == nil { await loadDay(selectedDay) }
    }

    func step(by delta: Int) async {
        advance(by: delta)
        await load()
    }

    /// Moves to the previous / next month or week without loading, so the change can be animated.
    /// Follow with `load()`.
    func advance(by delta: Int) {
        lastDelta = delta < 0 ? -1 : 1
        switch mode {
        case .month:
            month = month.addingMonths(delta)
            selectedDay = month == CalendarDay.today().firstOfMonth ? .today() : month
            userPickedDay = false
        case .week: selectedDay = selectedDay.adding(days: 7 * delta)
        }
        if mode != .month, selectedDay.firstOfMonth != month { month = selectedDay.firstOfMonth }
        // Already fetched (usually: neighbours are prefetched): show it as the page slides in.
        if let cached = months[month] { show(cached) }
    }

    /// The month or week on screen doesn't include today, so "Today" is offered.
    var isAwayFromToday: Bool {
        let today = CalendarDay.today()
        return mode == .month ? month != today.firstOfMonth : selectedDay.startOfWeek() != today.startOfWeek()
    }

    /// Back to today's month or week, with today selected. Follow with `load()`.
    func jumpToToday() {
        let today = CalendarDay.today()
        lastDelta = (mode == .month ? today.firstOfMonth > month : today > selectedDay) ? 1 : -1
        month = today.firstOfMonth
        selectedDay = today
        userPickedDay = true
        if let cached = months[month] { show(cached) }
    }

    /// A sideways swipe is moving the page: taps that end it aren't picks.
    func noteSwipe() { lastSwipeAt = .now }

    /// What one page of the calendar shows: the month, or the week of the selected day.
    var pageKey: CalendarDay { mode == .month ? month : selectedDay.startOfWeek() }

    /// A day tapped in the month or week grid: its detail shows underneath.
    func pick(_ date: CalendarDay) {
        if let lastSwipeAt, Date.now.timeIntervalSince(lastSwipeAt) < 0.4 { return }
        selectedDay = date
        userPickedDay = true
    }

    /// Loads who's free on the selected day, if it isn't loaded yet.
    func loadSelectedDay() async {
        if dayDetails[selectedDay] == nil { await loadDay(selectedDay) }
    }

    /// Something changed (busy time, the timing): forget what's cached and reload what's on screen.
    func invalidate() async {
        generation += 1
        monthRequests.values.forEach { $0.cancel() }
        dayRequests.values.forEach { $0.cancel() }
        monthRequests = [:]
        dayRequests = [:]
        months = [:]
        dayDetails = [:]
        await load()
    }

    /// The displayed month's availability, picking a day that works unless the user chose one.
    private func show(_ loaded: Availability) {
        availability = loaded
        // Start on a day that works rather than one outside the group's timing.
        if !userPickedDay, let firstMatch = loaded.days.first(where: { $0.status == .match }) {
            selectedDay = firstMatch.date
        }
    }

    private func loadMonth() async {
        isLoading = availability == nil
        defer { isLoading = false }
        let requested = month
        if let cached = months[requested] {
            if availability?.from != cached.from { show(cached) }
        } else {
            do {
                let loaded = try await fetchMonth(requested)
                guard requested == month else { return }
                show(loaded)
            } catch where !error.isCancellation {
                self.error = error.userMessage
                return
            } catch {
                return
            }
        }
        prefetchMonth(requested.addingMonths(1))
        prefetchMonth(requested.addingMonths(-1))
    }

    /// One month's availability: from the cache, the request already on its way, or a new one.
    private func fetchMonth(_ month: CalendarDay) async throws -> Availability {
        if let cached = months[month] { return cached }
        let task = monthRequests[month] ?? {
            let api = self.api, groupId = self.groupId
            let task = Task { try await api.send(API.GroupAvailability.range(groupId: groupId, from: month, to: month.lastOfMonth())) }
            monthRequests[month] = task
            return task
        }()
        let generation = self.generation
        defer { if generation == self.generation { monthRequests[month] = nil } }
        let loaded = try await task.value
        if generation == self.generation { months[month] = loaded }
        return loaded
    }

    private func prefetchMonth(_ month: CalendarDay) {
        guard months[month] == nil, monthRequests[month] == nil else { return }
        Task { _ = try? await fetchMonth(month) }
    }

    private func loadWeek() async {
        if availability == nil || selectedDay.firstOfMonth != availability?.from.firstOfMonth { await loadMonth() }
        let week = weekDays
        await withTaskGroup(of: Void.self) { group in
            for day in week where dayDetails[day] == nil {
                group.addTask { [weak self] in _ = try? await self?.fetchDay(day) }
            }
        }
        // The next week the user is likely to swipe to.
        let next = week.map { $0.adding(days: 7 * lastDelta) }
        for day in next where dayDetails[day] == nil && dayRequests[day] == nil {
            Task { _ = try? await fetchDay(day) }
        }
    }

    private func loadDay(_ date: CalendarDay) async {
        do {
            _ = try await fetchDay(date)
        } catch where !error.isCancellation {
            self.error = error.userMessage
        } catch {}
    }

    /// Who's free on one day: from the cache, the request already on its way, or a new one.
    @discardableResult
    private func fetchDay(_ date: CalendarDay) async throws -> DayAvailability {
        if let cached = dayDetails[date] { return cached }
        let task = dayRequests[date] ?? {
            let api = self.api, groupId = self.groupId
            let task = Task { try await api.send(API.GroupAvailability.day(groupId: groupId, date: date)) }
            dayRequests[date] = task
            return task
        }()
        let generation = self.generation
        defer { if generation == self.generation { dayRequests[date] = nil } }
        let detail = try await task.value
        if generation == self.generation { dayDetails[date] = detail }
        return detail
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
