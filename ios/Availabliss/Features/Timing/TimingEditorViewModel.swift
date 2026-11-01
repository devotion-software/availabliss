import Foundation
import Observation

/// Draft of a group's `Requirement`, edited by admins.
@MainActor
@Observable
final class TimingEditorViewModel {
    var draft: Requirement
    private(set) var authorities: [SchoolAuthority] = []
    private(set) var isSaving = false
    /// How many slots the draft produces before anyone's busy time (`POST /requirements/preview`).
    private(set) var preview: RequirementPreview?
    private(set) var isPreviewing = false
    var error: String?
    var toast: String?

    private let original: Requirement
    private let groupId: String
    private let api: APIClient

    init(group: Group, api: APIClient) {
        self.draft = group.requirement
        self.original = group.requirement
        self.groupId = group.id
        self.api = api
    }

    var hasChanges: Bool { draft != original }
    var preset: TimingPreset { TimingPreset.detect(draft.length, window: draft.window) }
    var customKind: CustomLengthKind { CustomLengthKind(draft.length) }
    var isSpan: Bool { if case .span = draft.length { true } else { false } }

    var authorityName: String? { authorities.first { $0.id == draft.schoolAuthority }?.name }

    // MARK: Length

    func choose(_ preset: TimingPreset) {
        guard let shape = preset.shape else {
            // Custom: keep the current shape, just mark it as custom by nudging to a non-preset value.
            if TimingPreset.detect(draft.length, window: draft.window) != .custom { chooseCustom(.hours) }
            return
        }
        draft.length = shape.length
        draft.window = shape.window
        if case .span(let span) = shape.length { draft.days = Self.days(in: span) }
    }

    func chooseCustom(_ kind: CustomLengthKind) {
        switch kind {
        case .hours:
            draft.length = .duration(minutes: 180)
            draft.window = draft.window ?? TimingPreset.defaultDurationWindow
        case .days:
            draft.length = .days(count: 2)
            draft.window = nil
        case .span:
            let span = RequirementLength.Span(startDay: 1, startTime: .midnight, endDay: 3, endTime: .endOfDay, weeksLater: 0)
            draft.length = .span(span)
            draft.window = nil
            draft.days = Self.days(in: span)
        }
    }

    var durationMinutes: Int {
        get { if case .duration(let minutes) = draft.length { minutes } else { 120 } }
        set { draft.length = .duration(minutes: max(30, min(24 * 60, newValue))) }
    }

    var dayCount: Int {
        get { if case .days(let count) = draft.length { count } else { 1 } }
        set { draft.length = .days(count: max(1, min(31, newValue))) }
    }

    var span: RequirementLength.Span {
        get { if case .span(let span) = draft.length { span } else { TimingPreset.weekendSpan } }
        set {
            draft.length = .span(newValue)
            draft.days = Self.days(in: newValue)
        }
    }

    // MARK: Starts and ends

    var startsLabel: String {
        switch draft.length {
        case .span(let span): "\(Weekday.short(span.startDay)) from \(span.startTime)"
        default: draft.window.map { "From \($0.from)" } ?? "Any time"
        }
    }

    var endsLabel: String {
        switch draft.length {
        case .span(let span):
            "\(span.weeksLater == 1 ? "Next " : "")\(Weekday.short(span.endDay)), \(span.endTime.isEndOfDay ? "any time" : "until \(span.endTime)")"
        default: draft.window.map { $0.to.isEndOfDay ? "Until late" : "Until \($0.to)" } ?? "Any time"
        }
    }

    var hasWindow: Bool {
        get { draft.window != nil }
        set { draft.window = newValue ? (draft.window ?? TimingPreset.defaultDurationWindow) : nil }
    }

    // MARK: Days

    /// Empty `days` means any weekday.
    func isDayOn(_ day: Int) -> Bool { draft.days.isEmpty || draft.days.contains(day) }

    func toggleDay(_ day: Int) {
        var days = draft.days.isEmpty ? Weekday.all : draft.days
        if days.contains(day) {
            guard days.count > 1 else { return }
            days.removeAll { $0 == day }
        } else {
            days.append(day)
        }
        draft.days = days.count == 7 ? [] : days.sorted()
    }

    // MARK: Holidays & ranges

    func loadAuthorities(country: String?) async {
        guard let country, authorities.isEmpty else { return }
        authorities = (try? await api.send(API.Regions.schoolAuthorities(country: country)).authorities) ?? []
    }

    func addDateRange(_ range: DateRange) {
        draft.dates.append(range)
        draft.dates.sort { $0.from < $1.from }
    }

    func removeDateRange(_ range: DateRange) {
        draft.dates.removeAll { $0 == range }
    }

    // MARK: Preview

    /// Call from `.task(id: draft)`: waits briefly so quick edits send one request.
    func refreshPreview() async {
        isPreviewing = true
        do {
            try await Task.sleep(for: .milliseconds(400))
            preview = try await api.send(API.Requirements.preview(draft))
            isPreviewing = false
        } catch where error.isCancellation {
            // Superseded by a newer edit; that task will finish the job.
        } catch {
            preview = nil
            isPreviewing = false
        }
    }

    /// "13 weekends" — or the length while the count loads.
    var previewTitle: String {
        guard let preview else { return preset == .custom ? draft.lengthDescription : preset.label }
        return draft.slotNoun.counted(preview.count)
    }

    var previewSubtitle: String {
        let range = "\(Formatting.dayMonth(draft.range.from)) and \(Formatting.dayMonth(draft.range.to))"
        guard let preview else { return "\(draft.draftSummary) · \(range)" }
        return preview.count == 0 ? "None fit this timing between \(range)" : "fit this timing between \(range) · \(preview.summary)"
    }

    // MARK: Save

    func save() async -> Group? {
        if draft.schoolHolidaysOnly && draft.schoolAuthority == nil && draft.customSchoolHolidays.isEmpty {
            error = "Choose whose school holidays to use."
            return nil
        }
        if draft.range.to < draft.range.from {
            error = "The search range ends before it starts."
            return nil
        }
        isSaving = true
        defer { isSaving = false }
        do {
            let group = try await api.send(API.Groups.update(id: groupId, UpdateGroupBody(requirement: draft)))
            toast = "Timing saved"
            return group
        } catch {
            self.error = error.userMessage
            return nil
        }
    }

    private static func days(in span: RequirementLength.Span) -> [Int] {
        if span.weeksLater == 1 { return [] }
        var days: [Int] = []
        var day = span.startDay
        while true {
            days.append(day)
            if day == span.endDay || days.count == 7 { break }
            day = (day + 1) % 7
        }
        return days.sorted()
    }
}
