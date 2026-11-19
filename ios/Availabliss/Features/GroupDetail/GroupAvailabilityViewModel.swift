import Foundation
import Observation

/// Month / Week availability for one group, plus who's free on the selected day.
@MainActor
@Observable
final class GroupAvailabilityViewModel {
    enum Mode: Hashable { case month, week }

    var mode: Mode = .month {
        didSet {
            guard mode != oldValue else { return }
            // Same day, other view: kept if it's on the new page, else what was picked there before.
            if let selection, !isOnPage(selection) {
                remember(selection, on: Self.page(of: selection, mode: oldValue))
                self.selection = rememberedSelections[pageKey]
            }
        }
    }
    private(set) var month: CalendarDay = CalendarDay.today().firstOfMonth
    /// Which week the Week view shows (and where the month paging starts): not a selection.
    var selectedDay: CalendarDay = .today()
    /// The day tapped, whose detail shows underneath; nil until you tap one (and after tapping it again).
    private(set) var selection: CalendarDay?
    /// The day picked on each month or week page (by `pageKey`), restored when you come back to it.
    private var rememberedSelections: [CalendarDay: CalendarDay] = [:]
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

    /// A day's status: from the month on screen, or a neighbouring month already fetched (a week
    /// can run into the next month).
    func day(_ date: CalendarDay) -> AvailabilityDay? { availability?.day(date) ?? months[date.firstOfMonth]?.day(date) }

    /// True when the day works for everyone (and isn't over); unknown days count as working, so
    /// nothing fades while it loads.
    func works(_ date: CalendarDay) -> Bool {
        if date < .today() { return false }
        guard let status = day(date)?.status else { return true }
        return status == .match
    }
    func slot(for date: CalendarDay) -> Slot? { availability?.slot(containing: date) }

    /// The month's upcoming slots, best first: fewer members missing, then the Pro "best date" score
    /// (higher first; none counts lowest), then the earliest.
    func bestOptions(limit: Int = 3, now: Date = .now) -> [Slot] {
        let upcoming = (availability?.slots ?? []).filter { $0.start > now }
        return Array(
            upcoming.sorted { a, b in
                if a.missingCount != b.missingCount { return a.missingCount < b.missingCount }
                let scoreA = a.score ?? -.infinity, scoreB = b.score ?? -.infinity
                if scoreA != scoreB { return scoreA > scoreB }
                return a.start < b.start
            }.prefix(limit))
    }

    // MARK: Loading

    func load() async {
        switch mode {
        case .month: await loadMonth()
        case .week: await loadWeek()
        }
        if let selection, dayDetails[selection] == nil { await loadDay(selection) }
        await findFreeNeighbours()
    }

    // MARK: Previous / next free date

    /// The group's search range: nothing outside it can work, so the search stops at its ends.
    var searchRange: DateRange?
    /// The nearest dates that work for everyone before and after the month or week on screen
    /// (the first day of a run that works, from today on). Nil when there's none that way.
    private(set) var previousFree: CalendarDay?
    private(set) var nextFree: CalendarDay?
    /// Still looking: the buttons wait rather than say there's none.
    private(set) var isFindingFree = true

    /// The first and last day of the month or week on screen.
    private var pageBounds: (start: CalendarDay, end: CalendarDay) {
        switch mode {
        case .month: (month, month.lastOfMonth())
        case .week: (selectedDay.startOfWeek(), selectedDay.startOfWeek().adding(days: 6))
        }
    }

    /// Looks either side of the page for the nearest free dates, fetching further months (within
    /// the search range) when the ones already loaded have none.
    private func findFreeNeighbours() async {
        guard let range = searchRange else { return }
        let key = pageKey, mode = self.mode, generation = self.generation
        let (start, end) = pageBounds
        let earliest = max(range.from, .today())
        let next = await freeDay(after: end, until: range.to)
        let previous = end < earliest ? nil : await freeDay(before: start, from: earliest)
        guard key == pageKey, mode == self.mode, generation == self.generation else { return }
        nextFree = next
        previousFree = previous
        isFindingFree = false
    }

    /// Where a run of days that works for everyone starts (not the middle of a weekend).
    private func startsFreeRun(_ day: AvailabilityDay) -> Bool {
        day.status == .match && day.slotPosition != .middle && day.slotPosition != .end
    }

    private func freeDay(after day: CalendarDay, until last: CalendarDay) async -> CalendarDay? {
        let from = max(day.adding(days: 1), .today())
        var month = from.firstOfMonth
        // A search range is a few months; the cap just keeps a very long one from fetching for ever.
        for _ in 0..<24 where month <= last {
            guard let loaded = try? await fetchMonth(month) else { return nil }
            if let found = loaded.days.first(where: { $0.date >= from && $0.date <= last && startsFreeRun($0) }) {
                return found.date
            }
            month = month.addingMonths(1)
        }
        return nil
    }

    private func freeDay(before day: CalendarDay, from first: CalendarDay) async -> CalendarDay? {
        let until = day.adding(days: -1)
        guard until >= first else { return nil }
        var month = until.firstOfMonth
        for _ in 0..<24 where month >= first.firstOfMonth {
            guard let loaded = try? await fetchMonth(month) else { return nil }
            if let found = loaded.days.last(where: { $0.date <= until && $0.date >= first && startsFreeRun($0) }) {
                return found.date
            }
            month = month.addingMonths(-1)
        }
        return nil
    }

    /// To the month or week holding `day`, with it selected. Follow with `load()`.
    func jump(to day: CalendarDay) {
        lastDelta = day > selectedDay ? 1 : -1
        leavePage()
        month = day.firstOfMonth
        selectedDay = day
        selection = day
        remember(day, on: pageKey)
        if let cached = months[month] { show(cached) }
    }

    func step(by delta: Int) async {
        advance(by: delta)
        await load()
    }

    /// Moves to the previous / next month or week without loading, so the change can be animated.
    /// Follow with `load()`.
    func advance(by delta: Int) {
        lastDelta = delta < 0 ? -1 : 1
        leavePage()
        switch mode {
        case .month:
            month = month.addingMonths(delta)
            selectedDay = month == CalendarDay.today().firstOfMonth ? .today() : month
        case .week: selectedDay = selectedDay.adding(days: 7 * delta)
        }
        if mode != .month, selectedDay.firstOfMonth != month { month = selectedDay.firstOfMonth }
        selection = rememberedSelections[pageKey]
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
        leavePage()
        month = today.firstOfMonth
        selectedDay = today
        selection = rememberedSelections[pageKey]
        if let cached = months[month] { show(cached) }
    }

    /// A sideways swipe is moving the page: taps that end it aren't picks.
    func noteSwipe() { lastSwipeAt = .now }

    /// What one page of the calendar shows: the month, or the week of the selected day.
    var pageKey: CalendarDay { mode == .month ? month : selectedDay.startOfWeek() }

    /// A day tapped in the month or week grid: its detail shows underneath. Tapping it again clears it.
    func pick(_ date: CalendarDay) {
        if let lastSwipeAt, Date.now.timeIntervalSince(lastSwipeAt) < 0.4 { return }
        if selection == date {
            selection = nil
            rememberedSelections[pageKey] = nil
            return
        }
        selection = date
        selectedDay = date
        remember(date, on: pageKey)
    }

    /// Loads who's free on the selected day, if it isn't loaded yet.
    func loadSelectedDay() async {
        if let selection, dayDetails[selection] == nil { await loadDay(selection) }
    }

    /// Keeps this page's pick for when you come back, before paging away (the new page shows its own).
    private func leavePage() {
        if let selection { remember(selection, on: pageKey) }
    }

    private func remember(_ day: CalendarDay, on page: CalendarDay) { rememberedSelections[page] = day }

    /// The month (or week, by its first day) a day is on in `mode`.
    private static func page(of day: CalendarDay, mode: Mode) -> CalendarDay { mode == .month ? day.firstOfMonth : day.startOfWeek() }

    private func isOnPage(_ day: CalendarDay) -> Bool { Self.page(of: day, mode: mode) == pageKey }

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

    /// The displayed month's availability. Nothing is selected until you tap a day.
    private func show(_ loaded: Availability) {
        availability = loaded
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
            } catch  where !error.isCancellation {
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
        let task =
            monthRequests[month]
            ?? {
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
        // A week running into the next (or previous) month needs that month's days too.
        for other in Set(week.map(\.firstOfMonth)) where other != month && months[other] == nil {
            _ = try? await fetchMonth(other)
        }
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
        } catch  where !error.isCancellation {
            self.error = error.userMessage
        } catch {}
    }

    /// Who's free on one day: from the cache, the request already on its way, or a new one.
    @discardableResult
    private func fetchDay(_ date: CalendarDay) async throws -> DayAvailability {
        if let cached = dayDetails[date] { return cached }
        let task =
            dayRequests[date]
            ?? {
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
