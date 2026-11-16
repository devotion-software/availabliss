import SwiftUI

/// "Block a time": date, all day / exact times / several whole days, repeat, private note.
struct BlockTimeSheet: View {
    enum When: Hashable { case times, allDay, days }
    /// Which date's calendar is open.
    private enum Picking: Hashable { case date, lastDate }

    let editing: ManualBlock?
    let save: (NewManualBlock) async -> Bool
    @Environment(\.dismiss) private var dismiss
    @Environment(\.theme) private var theme
    @State private var date: Date
    /// The last day, for several whole days.
    @State private var lastDate: Date
    @State private var when: When
    @State private var start: Date
    @State private var end: Date
    @State private var repeatMode: ManualBlock.Repeat
    @State private var note: String
    @State private var picking: Picking?
    @State private var isSaving = false

    /// - Parameters:
    ///   - range: Exact times to prefill, in minutes since midnight on `initialDay` (e.g. from a tap
    ///     on the week grid). 1440 means midnight at the end of the day. Defaults to 09:00–10:00.
    ///   - when: Start on exact times, all day, or several days (from `initialDay` to `lastDay`).
    ///   - pickDate: Open with the date's calendar showing (for presets that still need a day).
    ///   - editing: Edit this block instead of creating one; its day, times, repeat and note win.
    init(
        initialDay: CalendarDay, range: (from: Int, to: Int)? = nil, when: When = .times, lastDay: CalendarDay? = nil,
        pickDate: Bool = false, editing: ManualBlock? = nil, save: @escaping (NewManualBlock) async -> Bool
    ) {
        self.editing = editing
        self.save = save
        let wall = { (minutes: Int) in WallTime(hour: (minutes / 60) % 24, minute: minutes % 60).date() }
        if let block = editing {
            let first = CalendarDay(block.start), last = CalendarDay(block.end.addingTimeInterval(-1))
            _date = State(initialValue: first.startDate())
            _lastDate = State(initialValue: max(first, last).startDate())
            _when = State(initialValue: block.allDay ? (last > first ? .days : .allDay) : .times)
            _start = State(initialValue: block.allDay ? wall(9 * 60) : WallTime(block.start).date())
            _end = State(initialValue: block.allDay ? wall(10 * 60) : WallTime(block.end).date())
            _repeatMode = State(initialValue: block.repeat)
            _note = State(initialValue: block.note ?? "")
        } else {
            _date = State(initialValue: initialDay.startDate())
            _lastDate = State(initialValue: max(initialDay, lastDay ?? initialDay.adding(days: when == .days ? 2 : 0)).startDate())
            _when = State(initialValue: when)
            // 24:00 becomes 00:00, which reads as "runs to midnight".
            _start = State(initialValue: wall(range?.from ?? 9 * 60))
            _end = State(initialValue: wall(range?.to ?? 10 * 60))
            _repeatMode = State(initialValue: .none)
            _note = State(initialValue: "")
        }
        _picking = State(initialValue: pickDate ? .date : nil)
    }

    var body: some View {
        BottomSheet(title: editing == nil ? "Block a time" : "Edit block") {
            HStack(spacing: 8) {
                if when == .days {
                    FieldTile(label: "From", value: Formatting.shortDay(date), isActive: picking == .date) { toggle(.date) }
                    FieldTile(label: "To", value: Formatting.shortDay(lastDate), isActive: picking == .lastDate) { toggle(.lastDate) }
                } else {
                    FieldTile(label: "Date", value: Formatting.shortDay(date), isActive: picking == .date) { toggle(.date) }
                    FieldTile(label: "Time", value: timeLabel, isActive: false) {
                        picking = nil
                        when = .times
                    }
                }
            }
            if picking == .date {
                DatePicker(when == .days ? "From" : "Date", selection: $date, in: earliestDate..., displayedComponents: .date)
                    .datePickerStyle(.graphical)
                    .tint(theme.accent)
                    .transition(.opacity.combined(with: .move(edge: .top)))
            } else if picking == .lastDate {
                DatePicker("To", selection: $lastDate, in: date..., displayedComponents: .date)
                    .datePickerStyle(.graphical)
                    .tint(theme.accent)
                    .transition(.opacity.combined(with: .move(edge: .top)))
            }
            SegmentedControl(
                selection: $when,
                options: [
                    SegmentOption(value: .times, label: "Set times"),
                    SegmentOption(value: .allDay, label: "All day"),
                    SegmentOption(value: .days, label: "Several days"),
                ],
                accessibilityLabel: "Set times, all day or several days"
            )
            if when == .times {
                HStack(spacing: 8) {
                    timePicker("From", selection: $start)
                    timePicker("To", selection: $end)
                }
                .transition(.opacity)
            }
            if when != .days {
                VStack(alignment: .leading, spacing: 6) {
                    Text("Repeat").sectionLabelStyle()
                    SegmentedControl(
                        selection: $repeatMode,
                        options: [
                            SegmentOption(value: .none, label: "Once"),
                            SegmentOption(value: .weekly, label: "Weekly"),
                            SegmentOption(value: .fortnightly, label: "Fortnightly"),
                        ],
                        accessibilityLabel: "Repeat"
                    )
                }
            }
            VStack(alignment: .leading, spacing: 6) {
                Text("Note (only you see this)").sectionLabelStyle()
                InputField(placeholder: "e.g. Wedding", text: $note, height: 48, cornerRadius: 14)
            }
            Button {
                Task { await submit() }
            } label: {
                LoadingLabel(title: editing == nil ? "Block this time" : "Save changes", isLoading: isSaving)
            }
            .buttonStyle(.filled(.accent, height: 52))
            .disabled(isSaving)
        }
        .animation(.spring(response: 0.35, dampingFraction: 0.85), value: picking)
        .onChange(of: date) { _, newDate in
            if lastDate < newDate { lastDate = newDate }
            // Several days: once the first day is picked, go straight on to the last.
            if when == .days, picking == .date { picking = .lastDate }
        }
        .onChange(of: when) { _, newWhen in
            if newWhen == .days, lastDate <= date { lastDate = CalendarDay(date).adding(days: 1).startDate() }
            if newWhen != .days, picking == .lastDate { picking = nil }
        }
        .animation(.spring(response: 0.35, dampingFraction: 0.85), value: when)
    }

    private func toggle(_ which: Picking) {
        picking = picking == which ? nil : which
    }

    private func timePicker(_ label: String, selection: Binding<Date>) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label).sectionLabelStyle()
            DatePicker(label, selection: selection, displayedComponents: .hourAndMinute)
                .labelsHidden()
                .bodyFont(14, .semibold)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    /// Today, or the prefilled day if that's earlier (a tap on a past day this week).
    private var earliestDate: Date { min(Calendar.current.startOfDay(for: Date()), Calendar.current.startOfDay(for: date)) }

    private var timeLabel: String {
        switch when {
        case .times: "\(Formatting.time(start)) – \(Formatting.time(end))"
        case .allDay, .days: "All day"
        }
    }

    /// Builds the block's start/end on the chosen day. An end at or before the start runs past midnight.
    private var block: NewManualBlock {
        let day = CalendarDay(date)
        let range: (Date, Date)
        switch when {
        case .allDay:
            range = (day.startDate(), day.adding(days: 1).startDate())
        case .days:
            range = (day.startDate(), max(day, CalendarDay(lastDate)).adding(days: 1).startDate())
        case .times:
            let from = WallTime(start), to = WallTime(end)
            range = (from.date(on: day), to.date(on: to <= from ? day.adding(days: 1) : day))
        }
        // A span of days happens once.
        let repeats = when == .days ? .none : repeatMode
        let trimmed = note.trimmingCharacters(in: .whitespacesAndNewlines)
        return NewManualBlock(
            start: range.0, end: range.1, allDay: when != .times, repeat: repeats,
            until: repeats == .none ? nil : editing?.until, note: trimmed.isEmpty ? nil : trimmed
        )
    }

    private func submit() async {
        isSaving = true
        defer { isSaving = false }
        if await save(block) { dismiss() }
    }
}
