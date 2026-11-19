import Foundation
import Observation

/// Draft of a group's `Requirement`, edited by admins, or of a new group's timing before it exists.
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
    /// Someone else saved a different timing while you had unsaved edits (saving replaces theirs).
    private(set) var changedElsewhere = false

    private var original: Requirement
    private let groupId: String?
    let api: APIClient

    init(group: Group, api: APIClient) {
        self.draft = group.requirement
        self.original = group.requirement
        self.groupId = group.id
        self.api = api
    }

    /// A timing for a group that doesn't exist yet (New group, step 2).
    init(draft: Requirement, api: APIClient) {
        self.draft = draft
        self.original = draft
        self.groupId = nil
        self.api = api
    }

    /// Replaces the whole draft (a new preset or template picked before editing).
    func reset(to requirement: Requirement) {
        isCustom = false
        draft = requirement
        original = requirement
    }

    /// The group's saved timing changed (someone else saved, live). With no unsaved edits it's taken
    /// as it is; over unsaved edits it says so instead, as web does, and saving replaces theirs.
    func savedTimingChanged(to saved: Requirement) {
        guard saved != original, !isSaving else { return }
        if draft == original || draft == saved {
            draft = saved
            changedElsewhere = false
        } else {
            changedElsewhere = true
        }
        original = saved
    }

    /// "Load theirs": drops your edits for the timing someone else saved.
    func loadSaved() {
        draft = original
        changedElsewhere = false
    }

    /// A fresh timing for a new group: "A week" needs every day, "A weekend" Fri–Sun, and so on.
    static func fresh(_ preset: TimingPreset, today: CalendarDay = .today(), timezone: String) -> Requirement {
        var requirement = Requirement.defaultValue(today: today, timezone: timezone)
        requirement.apply(preset)
        return requirement
    }

    var hasChanges: Bool { draft != original }
    /// Custom stays chosen while you adjust it, even when the length happens to match a preset
    /// (stepping down to 2 hours used to switch to "2 hours" and hide the stepper).
    private var isCustom = false
    var preset: TimingPreset { isCustom ? .custom : TimingPreset.detect(draft.length, window: draft.window) }
    var customKind: CustomLengthKind { CustomLengthKind(draft.length) }
    var isSpan: Bool { if case .span = draft.length { true } else { false } }

    /// Any day of the week can match (none picked means any), so bank holidays need no switch.
    var allowsEveryDay: Bool { !isSpan && (draft.days.isEmpty || Set(draft.days).count >= 7) }

    var authorityName: String? { authorities.first { $0.id == draft.schoolAuthority }?.name }

    // MARK: Length

    func choose(_ preset: TimingPreset) {
        isCustom = preset == .custom
        guard preset.shape != nil else {
            // Custom: keep the current shape, just mark it as custom by nudging to a non-preset value.
            if TimingPreset.detect(draft.length, window: draft.window) != .custom { chooseCustom(.hours) }
            return
        }
        draft.apply(preset)
    }

    func chooseCustom(_ kind: CustomLengthKind) {
        isCustom = true
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
        set { draft.length = .duration(minutes: max(15, min(24 * 60, newValue))) }
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
            "\(span.weeksLater == 1 ? "Next " : span.weeksLater >= 2 ? "\(span.weeksLater) weeks on, " : "")\(Weekday.short(span.endDay)), \(span.endTime.isEndOfDay ? "any time" : "until \(span.endTime)")"
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
        authorities = await RegionCache.shared.authorities(country: country, api: api)
    }

    /// Adds a range, joining it with any it overlaps or runs straight on from. One already covered adds nothing.
    func addDateRange(_ range: DateRange) {
        guard Self.covering(range, in: draft.dates) == nil else { return }
        draft.dates = Self.merging(draft.dates + [range])
    }

    /// The existing range that already holds every day of `range`, if any (an identical one included).
    nonisolated static func covering(_ range: DateRange, in ranges: [DateRange]) -> DateRange? {
        ranges.first { $0.from <= range.from && range.to <= $0.to }
    }

    /// Sorted, with ranges that overlap or touch (one ends the day before the next starts) joined into one.
    nonisolated static func merging(_ ranges: [DateRange]) -> [DateRange] {
        var merged: [DateRange] = []
        for range in ranges.sorted(by: { $0.from < $1.from }) {
            if let last = merged.last, range.from <= last.to.adding(days: 1) {
                merged[merged.count - 1].to = max(last.to, range.to)
            } else {
                merged.append(range)
            }
        }
        return merged
    }

    func removeDateRange(_ range: DateRange) {
        draft.dates.removeAll { $0 == range }
    }

    // MARK: Who needs to be free

    /// Synced and unsynced members who aren't optional: the "M" in "at least N of M".
    func requiredCount(of members: [Member]) -> Int {
        let optional = Set(draft.optionalMemberIds ?? [])
        return members.filter { !optional.contains($0.userId) }.count
    }

    /// Everyone (no quorum) or at least some of the required members.
    var usesQuorum: Bool { draft.quorum != nil }

    /// Switches between everyone and "at least N of M", starting one short of everyone.
    func setUsesQuorum(_ on: Bool, members: [Member]) {
        draft.quorum = on ? max(1, requiredCount(of: members) - 1) : nil
    }

    /// Keeps the quorum within 1…M.
    func setQuorum(_ value: Int, members: [Member]) {
        draft.quorum = max(1, min(max(1, requiredCount(of: members)), value))
    }

    func isOptional(_ member: Member) -> Bool { draft.optionalMemberIds?.contains(member.userId) == true }

    /// Marks a member optional (or required again), keeping any quorum within the new number of required members.
    func setOptional(_ optional: Bool, for member: Member, members: [Member]) {
        var ids = draft.optionalMemberIds ?? []
        ids.removeAll { $0 == member.userId }
        if optional { ids.append(member.userId) }
        draft.optionalMemberIds = ids.isEmpty ? nil : ids
        if let quorum = draft.quorum { setQuorum(quorum, members: members) }
    }

    // MARK: Preview

    /// Call from `.task(id: draft)`: waits briefly so quick edits send one request.
    func refreshPreview() async {
        isPreviewing = true
        do {
            try await Task.sleep(for: .milliseconds(400))
            preview = try await api.send(API.Requirements.preview(draft))
            isPreviewing = false
        } catch  where error.isCancellation {
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
        guard let groupId else { return nil }
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
            original = draft
            changedElsewhere = false
            toast = "Timing saved"
            return group
        } catch {
            self.error = error.userMessage
            return nil
        }
    }

    nonisolated static func days(in span: RequirementLength.Span) -> [Int] {
        if span.weeksLater >= 1 { return [] }
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
