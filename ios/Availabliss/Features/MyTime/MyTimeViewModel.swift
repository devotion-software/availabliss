import Foundation
import Observation

@MainActor
@Observable
final class MyTimeViewModel {
    /// Days fetched per request as you scroll (the API allows up to 120 per call).
    static let chunkDays = 28
    static let maxRequestDays = 112
    /// How far ahead the list goes.
    static let maxDays = 366

    /// The list's days, from today.
    private(set) var days: [CalendarDay] = []
    private(set) var intervals: [BusyInterval] = []
    private(set) var blocks: [ManualBlock] = []
    private(set) var layout = DayLayout(days: [], intervals: [])
    private(set) var summaries: [CalendarDay: DaySummary] = [:]
    /// False until the first busy time lands.
    private(set) var hasLoaded = false
    private(set) var isLoadingMore = false
    /// Whether any calendar is connected (nil until known).
    private(set) var hasCalendar: Bool?
    var error: String?
    /// A recurring / longer block the user tapped; confirm before deleting it entirely.
    var pendingRemoval: ManualBlock?

    private let api: APIClient
    /// Bumped by every full reload, so a page fetched before it is dropped.
    private var generation = 0
    private var moreTask: Task<Void, Never>?

    init(api: APIClient) {
        self.api = api
    }

    var recurringBlocks: [ManualBlock] { blocks.filter { $0.repeat != .none } }

    /// The list's days in weeks (Monday to Sunday; the first starts today).
    var weeks: [(start: CalendarDay, days: [CalendarDay])] {
        var out: [(start: CalendarDay, days: [CalendarDay])] = []
        for day in days {
            let start = day.startOfWeek()
            if out.last?.start == start { out[out.count - 1].days.append(day) } else { out.append((start, [day])) }
        }
        return out
    }

    func summary(for day: CalendarDay) -> DaySummary {
        summaries[day] ?? DaySummary(day: day, allDay: [], timed: [])
    }

    /// (Re)loads everything on the list so far (at least four weeks from today), and your blocks.
    func load() async {
        generation += 1
        let generation = generation
        let today = CalendarDay.today()
        let count = max(Self.chunkDays, days.first == today ? days.count : 0)
        do {
            async let blockList = api.send(API.Blocks.list())
            async let connections = try? api.send(API.Calendars.list())
            var loaded: [BusyInterval] = []
            var offset = 0
            while offset < count {
                let length = min(Self.maxRequestDays, count - offset)
                loaded += try await fetch(from: today.adding(days: offset), days: length)
                offset += length
            }
            blocks = try await blockList.blocks
            if let connections = await connections { hasCalendar = !connections.connections.isEmpty }
            guard generation == self.generation else { return }
            days = (0..<count).map { today.adding(days: $0) }
            setIntervals(loaded)
        } catch  where !error.isCancellation {
            self.error = error.userMessage
            // Show free days rather than a loader that never ends.
            if !hasLoaded {
                days = (0..<count).map { today.adding(days: $0) }
                setIntervals([])
            }
        } catch {}
    }

    /// The next four weeks, when the list scrolls to its end.
    func loadMore() async {
        if let moreTask { return await moreTask.value }
        guard hasLoaded, let last = days.last, days.count < Self.maxDays else { return }
        let task = Task {
            isLoadingMore = true
            defer { isLoadingMore = false; moreTask = nil }
            let generation = generation
            let from = last.adding(days: 1)
            let count = min(Self.chunkDays, Self.maxDays - days.count)
            do {
                let loaded = try await fetch(from: from, days: count)
                guard generation == self.generation, days.last == last else { return }
                days += (0..<count).map { from.adding(days: $0) }
                setIntervals(intervals + loaded)
            } catch  where !error.isCancellation {
                self.error = error.userMessage
            } catch {}
        }
        moreTask = task
        await task.value
    }

    /// Loads further weeks until `day` is on the list (or the list can't grow).
    func ensureLoaded(through day: CalendarDay) async {
        while let last = days.last, last < day {
            let count = days.count
            await loadMore()
            if days.count == count { return }
        }
    }

    private func fetch(from day: CalendarDay, days: Int) async throws -> [BusyInterval] {
        try await api.send(API.Me.busy(from: day.startDate(), to: day.adding(days: days).startDate())).intervals
    }

    /// Drops the repeats you get where neighbouring pages overlap (a block across the page boundary), then lays the days out.
    private func setIntervals(_ list: [BusyInterval]) {
        var seen = Set<BusyInterval>()
        intervals = list.filter { seen.insert($0).inserted }
        layout = DayLayout(days: days, intervals: intervals)
        var summaries: [CalendarDay: DaySummary] = [:]
        for day in days {
            summaries[day] = DaySummary(day: day, allDay: layout.allDay[day] ?? [], timed: layout.timed[day] ?? [])
        }
        self.summaries = summaries
        hasLoaded = true
    }

    /// Your own block behind a busy interval, if it still exists.
    func block(for interval: BusyInterval) -> ManualBlock? {
        blocks.first { $0.id == interval.blockId }
    }

    /// Marks a calendar event not busy: this occurrence, or (`series`) every occurrence.
    func ignore(_ interval: BusyInterval, series: Bool) async -> Bool {
        guard let calendarId = interval.calendarId else { return false }
        do {
            _ = try await api.send(
                API.Me.ignoreBusy(
                    BusyIgnoreBody(
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
            setIntervals(intervals.filter { $0.blockId != block.id })
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
    /// "Every Tuesday · 19:00 – 21:30", "Monthly on the 3rd Monday · All day · until 20 Dec"
    var scheduleDescription: String {
        let times = allDay ? "All day" : "\(Formatting.time(start)) – \(Formatting.time(end))"
        let ends = until.map { " \u{00B7} until \(Formatting.shortDay($0.startDate()))" } ?? ""
        guard self.repeat != .none else { return "\(Formatting.shortDay(start)) \u{00B7} \(times)" }
        return "\(self.repeat.summary(from: start)) \u{00B7} \(times)\(ends)"
    }
}

extension ManualBlock.Repeat {
    /// The repeat in a schedule line: "Every Tuesday", "Weekdays", "Monthly on the 16th".
    func summary(from start: Date) -> String {
        let weekday = start.formatted(.dateTime.weekday(.wide))
        switch self {
        case .none: return "Once"
        case .daily: return "Every day"
        case .weekdays: return "Weekdays"
        case .weekly: return "Every \(weekday)"
        case .fortnightly: return "Every other \(weekday)"
        case .monthly: return "Monthly on the \(Self.ordinal(Calendar.current.component(.day, from: start)))"
        case .monthlyWeekday: return "Monthly on the \(Self.weekOfMonth(start)) \(weekday)"
        case .yearly: return "Yearly on \(start.formatted(.dateTime.day().month(.abbreviated)))"
        case .unknown: return "Repeats"
        }
    }

    /// The choice in the block sheet's Repeat menu, from the block's start date.
    func choice(from start: Date) -> String {
        let weekday = start.formatted(.dateTime.weekday(.wide))
        switch self {
        case .none: return "Never"
        case .daily: return "Every day"
        case .weekdays: return "Every weekday (Mon\u{2013}Fri)"
        case .weekly: return "Every week on \(weekday)"
        case .fortnightly: return "Every 2 weeks on \(weekday)"
        case .monthly: return "Every month on the \(Self.ordinal(Calendar.current.component(.day, from: start)))"
        case .monthlyWeekday: return "Every month on the \(Self.weekOfMonth(start)) \(weekday)"
        case .yearly: return "Every year on \(start.formatted(.dateTime.day().month(.wide)))"
        case .unknown: return "Repeats"
        }
    }

    /// The agenda's source tag.
    var tag: String {
        switch self {
        case .none: "Your block"
        case .daily: "Daily"
        case .weekdays: "Weekdays"
        case .weekly: "Weekly"
        case .fortnightly: "Every other week"
        case .monthly, .monthlyWeekday: "Monthly"
        case .yearly: "Yearly"
        case .unknown: "Repeats"
        }
    }

    /// "1st", "2nd", "23rd".
    static func ordinal(_ n: Int) -> String {
        let suffix = (11...13).contains(n % 100) ? "th" : [1: "st", 2: "nd", 3: "rd"][n % 10] ?? "th"
        return "\(n)\(suffix)"
    }

    /// "3rd" for the third Monday, or "last" when the date is in its month's last seven days
    /// (matching the server's monthlyWeekday rule).
    static func weekOfMonth(_ date: Date) -> String {
        let calendar = Calendar.current
        let day = calendar.component(.day, from: date)
        let days = calendar.range(of: .day, in: .month, for: date)?.count ?? 31
        return day + 7 > days ? "last" : ordinal((day + 6) / 7)
    }
}
