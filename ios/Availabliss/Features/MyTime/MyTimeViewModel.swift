import Foundation
import Observation

@MainActor
@Observable
final class MyTimeViewModel {
    private(set) var weekStart: CalendarDay = CalendarDay.today().startOfWeek()
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

    var title: String { Formatting.dayRange(weekStart, weekStart.adding(days: 6)) }

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
        let from = week.startDate()
        let to = week.adding(days: 7).startDate()
        do {
            async let busy = api.send(API.Me.busy(from: from, to: to))
            async let blockList = api.send(API.Blocks.list())
            let loaded = try await busy.intervals
            blocks = try await blockList.blocks
            guard week == weekStart else { return }
            intervals = loaded
            loadedWeek = week
        } catch where !error.isCancellation {
            self.error = error.userMessage
        } catch {}
    }

    func moveWeek(by weeks: Int) async {
        weekStart = weekStart.adding(days: 7 * weeks)
        intervals = []
        await load()
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
