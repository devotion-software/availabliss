import Foundation
import Observation

/// Morning / afternoon / evening — the slots of the My time grid and the block sheet.
enum DayPart: Int, CaseIterable, Identifiable, Sendable {
    case morning, afternoon, evening

    var id: Int { rawValue }
    var label: String {
        switch self {
        case .morning: "Morning"
        case .afternoon: "Afternoon"
        case .evening: "Evening"
        }
    }
    var startHour: Int { [6, 12, 18][rawValue] }
    var endHour: Int { [12, 18, 24][rawValue] }

    func interval(on day: CalendarDay, calendar: Calendar = .current) -> (start: Date, end: Date) {
        let midnight = day.startDate(calendar: calendar)
        return (midnight.addingTimeInterval(TimeInterval(startHour * 3600)), midnight.addingTimeInterval(TimeInterval(endHour * 3600)))
    }
}

/// What one cell of the week grid shows.
enum SlotState: Equatable {
    case free
    case calendar(label: String)
    case manual(label: String, interval: BusyInterval)
}

@MainActor
@Observable
final class MyTimeViewModel {
    private(set) var weekStart: CalendarDay = CalendarDay.today().startOfWeek()
    private(set) var intervals: [BusyInterval] = []
    private(set) var blocks: [ManualBlock] = []
    private(set) var isLoading = false
    private(set) var togglingKey: String?
    var error: String?
    /// A recurring / longer block the user tapped; confirm before deleting it entirely.
    var pendingRemoval: ManualBlock?

    private let api: APIClient

    init(api: APIClient) {
        self.api = api
    }

    var days: [CalendarDay] { (0..<7).map { weekStart.adding(days: $0) } }
    var title: String { Formatting.dayRange(weekStart, weekStart.adding(days: 6)) }

    var recurringBlocks: [ManualBlock] { blocks.filter { $0.repeat != .none } }

    var blockedCount: Int {
        days.reduce(0) { count, day in
            count + DayPart.allCases.filter { if case .manual = state(day, $0) { true } else { false } }.count
        }
    }

    func load() async {
        isLoading = intervals.isEmpty
        defer { isLoading = false }
        let from = weekStart.startDate()
        let to = weekStart.adding(days: 7).startDate()
        do {
            async let busy = api.send(API.Me.busy(from: from, to: to))
            async let blockList = api.send(API.Blocks.list())
            intervals = try await busy.intervals
            blocks = try await blockList.blocks
        } catch where !error.isCancellation {
            self.error = error.userMessage
        } catch {}
    }

    func moveWeek(by weeks: Int) async {
        weekStart = weekStart.adding(days: 7 * weeks)
        intervals = []
        await load()
    }

    func state(_ day: CalendarDay, _ part: DayPart) -> SlotState {
        let slot = part.interval(on: day)
        let overlapping = intervals.filter { $0.overlaps(slot.start, slot.end) }
        if let calendar = overlapping.first(where: { $0.source == .calendar }) {
            return .calendar(label: calendar.label ?? "Busy")
        }
        if let manual = overlapping.first(where: { $0.source == .manual }) {
            let note = manual.blockId.flatMap { id in blocks.first { $0.id == id }?.note }
            return .manual(label: note ?? manual.label ?? "Busy", interval: manual)
        }
        return .free
    }

    func key(_ day: CalendarDay, _ part: DayPart) -> String { "\(day)-\(part.rawValue)" }

    /// Tap: free → block this slot; blocked-by-you → unblock (asking first for repeats).
    func toggle(_ day: CalendarDay, _ part: DayPart) async {
        guard togglingKey == nil else { return }
        switch state(day, part) {
        case .calendar:
            return
        case .free:
            let slot = part.interval(on: day)
            await perform(key(day, part)) {
                let block = try await self.api.send(API.Blocks.create(NewManualBlock(start: slot.start, end: slot.end, allDay: false, repeat: .none, until: nil, note: nil)))
                self.blocks.append(block)
                self.intervals.append(BusyInterval(start: block.start, end: block.end, source: .manual, label: nil, calendarId: nil, blockId: block.id))
            }
        case .manual(_, let interval):
            guard let blockId = interval.blockId, let block = blocks.first(where: { $0.id == blockId }) else {
                await load()
                return
            }
            let slot = part.interval(on: day)
            if block.repeat != .none || block.start < slot.start || block.end > slot.end {
                pendingRemoval = block
            } else {
                await remove(block)
            }
        }
    }

    func remove(_ block: ManualBlock) async {
        await perform(block.id) {
            try await self.api.send(API.Blocks.delete(id: block.id))
            self.blocks.removeAll { $0.id == block.id }
            self.intervals.removeAll { $0.blockId == block.id }
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

    private func perform(_ key: String, _ work: @escaping () async throws -> Void) async {
        togglingKey = key
        defer { togglingKey = nil }
        do { try await work() } catch { self.error = error.userMessage }
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
