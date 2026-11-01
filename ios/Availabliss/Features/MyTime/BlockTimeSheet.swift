import SwiftUI

/// "Block a time": date, all day / morning / evening / custom, repeat, private note.
struct BlockTimeSheet: View {
    enum When: String, CaseIterable, Identifiable {
        case allDay, morning, evening, custom
        var id: String { rawValue }
        var label: String {
            switch self {
            case .allDay: "All day"
            case .morning: "Morning"
            case .evening: "Evening"
            case .custom: "Custom"
            }
        }
    }

    let save: (NewManualBlock) async -> Bool
    @Environment(\.dismiss) private var dismiss
    @Environment(\.theme) private var theme
    @State private var date: Date
    @State private var when: When = .allDay
    @State private var customStart: Date
    @State private var customEnd: Date
    @State private var repeatMode: ManualBlock.Repeat = .none
    @State private var note = ""
    @State private var editing: Field?
    @State private var isSaving = false

    private enum Field { case date, time }

    init(initialDay: CalendarDay, save: @escaping (NewManualBlock) async -> Bool) {
        self.save = save
        _date = State(initialValue: initialDay.startDate())
        _customStart = State(initialValue: WallTime(hour: 14, minute: 0).date())
        _customEnd = State(initialValue: WallTime(hour: 18, minute: 0).date())
    }

    var body: some View {
        BottomSheet(title: "Block a time") {
            HStack(spacing: 8) {
                FieldTile(label: "Date", value: Formatting.shortDay(date), isActive: editing == .date) { toggle(.date) }
                FieldTile(label: "Time", value: timeLabel, isActive: editing == .time) { toggle(.time) }
            }
            if editing == .date {
                DatePicker("Date", selection: $date, in: Calendar.current.startOfDay(for: Date())..., displayedComponents: .date)
                    .datePickerStyle(.graphical)
                    .tint(theme.accent)
                    .transition(.opacity.combined(with: .move(edge: .top)))
            }
            whenChips
            if when == .custom {
                HStack {
                    DatePicker("From", selection: $customStart, displayedComponents: .hourAndMinute)
                    DatePicker("Until", selection: $customEnd, displayedComponents: .hourAndMinute)
                }
                .bodyFont(14, .semibold)
                .labelsHidden()
                .frame(maxWidth: .infinity, alignment: .leading)
                .accessibilityElement(children: .contain)
            }
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
            VStack(alignment: .leading, spacing: 6) {
                Text("Note (only you see this)").sectionLabelStyle()
                InputField(placeholder: "e.g. Wedding", text: $note, height: 48, cornerRadius: 14)
            }
            Button {
                Task { await submit() }
            } label: {
                LoadingLabel(title: "Block this time", isLoading: isSaving)
            }
            .buttonStyle(.filled(.accent, height: 52))
            .disabled(isSaving || !isValid)
        }
        .animation(.spring(response: 0.35, dampingFraction: 0.85), value: editing)
        .animation(.spring(response: 0.35, dampingFraction: 0.85), value: when)
    }

    private var whenChips: some View {
        HStack(spacing: 8) {
            ForEach(When.allCases) { option in
                Chip(label: option.label, isOn: when == option) { when = option }
            }
        }
    }

    private var timeLabel: String {
        switch when {
        case .custom: "\(Formatting.time(customStart)) – \(Formatting.time(customEnd))"
        default: when.label
        }
    }

    private var isValid: Bool { when != .custom || WallTime(customEnd) > WallTime(customStart) }

    private func toggle(_ field: Field) {
        editing = editing == field ? nil : field
        if field == .time, editing == .time { when = .custom }
    }

    /// Builds the block's start/end on the chosen day.
    private var block: NewManualBlock {
        let day = CalendarDay(date)
        let midnight = day.startDate()
        let range: (Date, Date)
        switch when {
        case .allDay: range = (midnight, day.adding(days: 1).startDate())
        case .morning: range = DayPart.morning.interval(on: day)
        case .evening: range = DayPart.evening.interval(on: day)
        case .custom: range = (WallTime(customStart).date(on: day), WallTime(customEnd).date(on: day))
        }
        let trimmed = note.trimmingCharacters(in: .whitespacesAndNewlines)
        return NewManualBlock(start: range.0, end: range.1, allDay: when == .allDay, repeat: repeatMode, until: nil, note: trimmed.isEmpty ? nil : trimmed)
    }

    private func submit() async {
        isSaving = true
        defer { isSaving = false }
        if await save(block) { dismiss() }
    }
}
