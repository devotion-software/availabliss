import Foundation
import Observation

@MainActor
@Observable
final class MyTimeViewModel {
    enum Mode: Hashable { case week, month }

    var mode: Mode = .week
    private(set) var weekStart: CalendarDay = CalendarDay.today().startOfWeek()
    private(set) var monthStart: CalendarDay = CalendarDay.today().firstOfMonth
    private(set) var intervals: [BusyInterval] = []
    private(set) var blocks: [ManualBlock] = []
    private(set) var isLoading = false
    /// The week the current `intervals` belong to (nil until the first load lands).
    private(set) var loadedWeek: CalendarDay?
    var error: String?
    /// A recurring / longer block the user tapped; confirm before deleting it entirely.
    var pendingRemoval: ManualBlock?

    private let api: APIClient

    init(api: APIClient) {
        self.api = api
    }

    var title: String {
        switch mode {
        case .week: Formatting.dayRange(weekStart, weekStart.adding(days: 6))
        case .month: Formatting.shortMonthYear(monthStart)
        }
    }

    /// Whole weeks covering the month on screen (rows of seven days; days outside the month included).
    var monthWeeks: [[CalendarDay]] {
        var weeks: [[CalendarDay]] = []
        var start = monthStart.startOfWeek()
        let last = monthStart.lastOfMonth()
        while start <= last {
            weeks.append((0..<7).map { start.adding(days: $0) })
            start = start.adding(days: 7)
        }
        return weeks
    }

    /// Busy intervals touching `day` (for the month view).
    func intervals(on day: CalendarDay) -> [BusyInterval] {
        let start = day.startDate(), end = day.adding(days: 1).startDate()
        return intervals.filter { $0.overlaps(start, end) && !$0.isIgnored }
    }

    var recurringBlocks: [ManualBlock] { blocks.filter { $0.repeat != .none } }

    var layout: WeekLayout { WeekLayout(weekStart: weekStart, intervals: intervals) }

    /// Distinct occurrences of your own blocks this week.
    var blockedCount: Int {
        Set(intervals.filter { $0.source == .manual }.map { "\($0.blockId ?? "")-\($0.start.timeIntervalSince1970)" }).count
    }

    func load() async {
        isLoading = intervals.isEmpty
        defer { isLoading = false }
        let week = weekStart
        let mode = self.mode
        let month = monthStart
        let from = mode == .week ? week.startDate() : month.startOfWeek().startDate()
        let to = mode == .week ? week.adding(days: 7).startDate() : (monthWeeks.last?.last ?? month).adding(days: 1).startDate()
        do {
            async let busy = api.send(API.Me.busy(from: from, to: to))
            async let blockList = api.send(API.Blocks.list())
            let loaded = try await busy.intervals
            blocks = try await blockList.blocks
            guard week == weekStart, mode == self.mode, month == monthStart else { return }
            intervals = loaded
            loadedWeek = week
        } catch where !error.isCancellation {
            self.error = error.userMessage
            // Show the (empty) grid rather than a loader that never ends.
            if loadedWeek == nil { loadedWeek = week }
        } catch {}
    }

    func moveWeek(by weeks: Int) async {
        weekStart = weekStart.adding(days: 7 * weeks)
        intervals = []
        await load()
    }

    /// Previous / next week or month, whichever is showing.
    func move(by steps: Int) async {
        switch mode {
        case .week: await moveWeek(by: steps)
        case .month:
            monthStart = monthStart.addingMonths(steps)
            intervals = []
            await load()
        }
    }

    func setMode(_ newMode: Mode) async {
        guard newMode != mode else { return }
        // Keep roughly the same place: the month of the week on screen, or the first week of the month.
        if newMode == .month {
            monthStart = weekStart.adding(days: 3).firstOfMonth
        } else {
            let today = CalendarDay.today()
            weekStart = (today.firstOfMonth == monthStart ? today : monthStart).startOfWeek()
        }
        mode = newMode
        intervals = []
        await load()
    }

    /// A day tapped in the month: open its week.
    func showWeek(of day: CalendarDay) async {
        weekStart = day.startOfWeek()
        mode = .week
        intervals = []
        await load()
    }

    /// Marks a calendar event not busy: this occurrence, or (`series`) every occurrence.
    func ignore(_ interval: BusyInterval, series: Bool) async -> Bool {
        guard let calendarId = interval.calendarId else { return false }
        do {
            _ = try await api.send(API.Me.ignoreBusy(BusyIgnoreBody(
                calendarId: calendarId, eventKey: interval.eventKey ?? "", start: interval.start, end: interval.end, series: series
            )))
            await load()
            return true
        } catch {
            self.error = error.userMessage
            return false
        }
    }

    /// The event counts as busy again.
    func unignore(_ interval: BusyInterval) async -> Bool {
        guard let id = interval.ignoreId else { return false }
        do {
            try await api.send(API.Me.unignoreBusy(id: id))
            await load()
            return true
        } catch {
            self.error = error.userMessage
            return false
        }
    }

    func remove(_ block: ManualBlock) async {
        do {
            try await api.send(API.Blocks.delete(id: block.id))
            blocks.removeAll { $0.id == block.id }
            intervals.removeAll { $0.blockId == block.id }
        } catch {
            self.error = error.userMessage
        }
        await load()
    }

    func create(_ block: NewManualBlock) async -> Bool {
        do {
            let created = try await api.send(API.Blocks.create(block))
            blocks.append(created)
            await load()
            return true
        } catch {
            self.error = error.userMessage
            return false
        }
    }

    func update(_ id: String, _ block: NewManualBlock) async -> Bool {
        do {
            let updated = try await api.send(API.Blocks.update(id: id, block))
            if let index = blocks.firstIndex(where: { $0.id == id }) { blocks[index] = updated }
            await load()
            return true
        } catch {
            self.error = error.userMessage
            return false
        }
    }
}

extension ManualBlock {
    /// "Every Tuesday · 19:00 – 21:30"
    var scheduleDescription: String {
        let weekday = start.formatted(.dateTime.weekday(.wide))
        let times = allDay ? "All day" : "\(Formatting.time(start)) – \(Formatting.time(end))"
        switch self.repeat {
        case .none: return "\(Formatting.shortDay(start)) · \(times)"
        case .weekly: return "Every \(weekday) · \(times)"
        case .fortnightly: return "Every other \(weekday) · \(times)"
        }
    }
}
