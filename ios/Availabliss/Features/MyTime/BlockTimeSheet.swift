import SwiftUI

/// "Block a time": when (a date with its time underneath, to another, or all day), repeat, private note.
struct BlockTimeSheet: View {
    /// How a preset opens the sheet: exact times, one whole day, or several whole days.
    enum When: Hashable { case times, allDay, days }

    let editing: ManualBlock?
    let save: (NewManualBlock) async -> Bool
    @Environment(\.dismiss) private var dismiss
    @Environment(\.theme) private var theme
    @State private var start: Date
    /// All day: the last day.
    @State private var end: Date
    @State private var allDay: Bool
    @State private var repeatMode: ManualBlock.Repeat
    @State private var until: Date?
    @State private var note: String
    @State private var isSaving = false
    private let opensOnDate: Bool

    /// - Parameters:
    ///   - range: Exact times to prefill, in minutes since midnight on `initialDay` (e.g. from a tap
    ///     on the week grid). 1440 means midnight at the end of the day. Defaults to 09:00–10:00.
    ///   - when: Start on exact times, all day, or several days (from `initialDay` to `lastDay`).
    ///   - pickDate: Open with the start date's calendar showing (for presets that still need a day).
    ///   - editing: Edit this block instead of creating one; its times, repeat and note win.
    init(
        initialDay: CalendarDay, range: (from: Int, to: Int)? = nil, when: When = .times, lastDay: CalendarDay? = nil,
        pickDate: Bool = false, editing: ManualBlock? = nil, save: @escaping (NewManualBlock) async -> Bool
    ) {
        self.editing = editing
        self.save = save
        opensOnDate = pickDate
        if let block = editing {
            _start = State(initialValue: block.start)
            _end = State(initialValue: block.allDay ? block.end.addingTimeInterval(-1) : block.end)
            _allDay = State(initialValue: block.allDay)
            _repeatMode = State(initialValue: block.repeat == .unknown ? .none : block.repeat)
            _until = State(initialValue: block.until?.startDate())
            _note = State(initialValue: block.note ?? "")
        } else {
            let from = range?.from ?? 9 * 60, to = range?.to ?? 10 * 60
            let at = { (minutes: Int) in initialDay.startDate().addingTimeInterval(TimeInterval(minutes * 60)) }
            switch when {
            case .times:
                _start = State(initialValue: at(from))
                // An end at or before the start runs past midnight.
                _end = State(initialValue: to > from ? at(to) : at(to + 24 * 60))
            case .allDay, .days:
                _start = State(initialValue: initialDay.startDate())
                let last = when == .days ? max(initialDay.adding(days: 2), lastDay ?? initialDay) : initialDay
                _end = State(initialValue: last.startDate())
            }
            _allDay = State(initialValue: when != .times)
            _repeatMode = State(initialValue: .none)
            _until = State(initialValue: nil)
            _note = State(initialValue: "")
        }
    }

    var body: some View {
        BottomSheet(title: editing == nil ? "Block a time" : "Edit block") {
            DateRangeInput(
                start: $start, end: $end, allDay: $allDay,
                earliest: min(Calendar.current.startOfDay(for: .now), Calendar.current.startOfDay(for: start)),
                initiallyOpen: opensOnDate ? .startDate : nil
            )
            repeatRows
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
        .animation(.spring(response: 0.35, dampingFraction: 0.85), value: repeatMode)
        .animation(.spring(response: 0.35, dampingFraction: 0.85), value: until)
    }

    /// Repeat (a menu of choices worded from the start date), then when it ends.
    private var repeatRows: some View {
        VStack(spacing: 0) {
            HStack {
                Text("Repeat").bodyFont(15, .semibold)
                Spacer(minLength: 8)
                Menu {
                    Picker("Repeat", selection: $repeatMode) {
                        ForEach(ManualBlock.Repeat.allCases, id: \.self) { option in
                            Text(option.choice(from: start)).tag(option)
                        }
                    }
                } label: {
                    menuLabel(repeatMode.choice(from: start))
                }
                .accessibilityLabel("Repeat, \(repeatMode.choice(from: start))")
            }
            .frame(minHeight: 50)
            if repeatMode != .none {
                theme.line.frame(height: 1)
                HStack {
                    Text("Ends").bodyFont(15, .semibold)
                    Spacer(minLength: 8)
                    if let until {
                        DatePicker(
                            "Ends on",
                            selection: Binding(get: { until }, set: { self.until = $0 }),
                            in: Calendar.current.startOfDay(for: start)...,
                            displayedComponents: .date
                        )
                        .labelsHidden()
                        .tint(theme.accent)
                    }
                    Menu {
                        Button("Never") { until = nil }
                        Button("On a date") { until = until ?? Calendar.current.date(byAdding: .month, value: 3, to: start) }
                    } label: {
                        menuLabel(until == nil ? "Never" : "On")
                    }
                    .accessibilityLabel(until == nil ? "Ends, never" : "Ends on a date")
                }
                .frame(minHeight: 50)
                .transition(.opacity.combined(with: .move(edge: .top)))
            }
        }
        .padding(.horizontal, 14)
        .glass(cornerRadius: 22)
    }

    private func menuLabel(_ text: String) -> some View {
        HStack(spacing: 4) {
            Text(text).bodyFont(14, .semibold).lineLimit(1)
            Image(systemName: "chevron.up.chevron.down").font(.system(size: 11, weight: .bold))
        }
        .foregroundStyle(theme.accentInk)
        .frame(minHeight: 44)
        .contentShape(Rectangle())
    }

    /// The block to save. All day runs from the first day's midnight to the midnight after the last.
    private var block: NewManualBlock {
        let calendar = Calendar.current
        let range: (Date, Date) = allDay
            ? (calendar.startOfDay(for: start), CalendarDay(max(end, start)).adding(days: 1).startDate())
            : (start, end)
        let trimmed = note.trimmingCharacters(in: .whitespacesAndNewlines)
        return NewManualBlock(
            start: range.0, end: range.1, allDay: allDay, repeat: repeatMode,
            until: repeatMode == .none ? nil : until.map { CalendarDay($0) },
            note: trimmed.isEmpty ? nil : trimmed
        )
    }

    private func submit() async {
        isSaving = true
        defer { isSaving = false }
        if await save(block) { dismiss() }
    }
}
