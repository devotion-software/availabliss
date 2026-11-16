import SwiftUI

/// A start and an end, each a date with its time underneath, and an All day switch. Tapping a
/// date opens a calendar under the row, tapping a time opens a time wheel. Moving the start keeps
/// the length, so the end follows it; the end can't go before the start.
///
/// All day: `end` is the last day (inclusive); the times are hidden.
struct DateRangeInput: View {
    @Binding var start: Date
    @Binding var end: Date
    @Binding var allDay: Bool
    /// Limits both ends (e.g. a free slot being proposed).
    var bounds: ClosedRange<Date>?
    /// The earliest start date offered.
    var earliest: Date?
    /// Opens on this picker (e.g. a preset that still needs a day).
    var initiallyOpen: Field?

    enum Field: Hashable { case startDate, startTime, endDate, endTime }

    @Environment(\.theme) private var theme
    @State private var open: Field?
    @State private var didOpenInitially = false

    var body: some View {
        VStack(spacing: 12) {
            Toggle("All day", isOn: $allDay.animation(.spring(response: 0.3, dampingFraction: 0.85)))
                .toggleStyle(.glassSwitch)
                .bodyFont(15, .semibold)
                .frame(minHeight: 44)
            HStack(alignment: .top, spacing: 6) {
                side(label: "Starts", date: start, dateField: .startDate, timeField: .startTime)
                Image(systemName: "arrow.right")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(theme.muted)
                    .frame(height: 44)
                    .accessibilityHidden(true)
                side(label: "Ends", date: end, dateField: .endDate, timeField: .endTime)
            }
            picker
        }
        .padding(14)
        .glass(cornerRadius: 22)
        .animation(.spring(response: 0.32, dampingFraction: 0.88), value: open)
        .onChange(of: allDay) { _, isAllDay in
            if isAllDay, open == .startTime || open == .endTime { open = nil }
            // Back to times: give the day sensible hours rather than midnight to midnight.
            if !isAllDay, Calendar.current.isDate(start, inSameDayAs: end) || end <= start {
                end = max(end, start.addingTimeInterval(3600))
            }
        }
        .onAppear {
            guard !didOpenInitially else { return }
            didOpenInitially = true
            open = initiallyOpen
        }
    }

    private func side(label: String, date: Date, dateField: Field, timeField: Field) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            chip(Formatting.shortDay(date), field: dateField, font: .body15, accessibility: "\(label), \(Formatting.longDay(date))")
            if !allDay {
                chip(Formatting.time(date), field: timeField, font: .display22, accessibility: "\(label == "Starts" ? "Start" : "End") time, \(Formatting.time(date))")
                    .transition(.opacity.combined(with: .move(edge: .top)))
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private enum ChipFont { case body15, display22 }

    private func chip(_ text: String, field: Field, font: ChipFont, accessibility: String) -> some View {
        let isOpen = open == field
        return Button {
            open = isOpen ? nil : field
        } label: {
            SwiftUI.Group {
                switch font {
                case .body15: Text(text).bodyFont(15, .semibold)
                case .display22: Text(text).displayFont(22, .bold, relativeTo: .title3).monospacedDigit()
                }
            }
            .lineLimit(1)
            .minimumScaleFactor(0.8)
            .foregroundStyle(isOpen ? theme.accentInk : theme.ink)
            .padding(.horizontal, 10)
            .frame(minHeight: 44)
            .background(Capsule().fill(isOpen ? theme.accentSoft : theme.surface))
            .contentShape(Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(accessibility)
        .accessibilityHint(isOpen ? "Closes the picker" : "Opens a picker")
    }

    @ViewBuilder
    private var picker: some View {
        switch open {
        case .startDate:
            DatePicker("Start date", selection: startBinding, in: startRange, displayedComponents: .date)
                .datePickerStyle(.graphical)
                .tint(theme.accent)
                .transition(.opacity)
        case .endDate:
            DatePicker("End date", selection: endBinding, in: endRange, displayedComponents: .date)
                .datePickerStyle(.graphical)
                .tint(theme.accent)
                .transition(.opacity)
        case .startTime:
            DatePicker("Start time", selection: startBinding, in: startRange, displayedComponents: .hourAndMinute)
                .datePickerStyle(.wheel)
                .labelsHidden()
                .frame(maxWidth: .infinity)
                .transition(.opacity)
        case .endTime:
            DatePicker("End time", selection: endBinding, displayedComponents: .hourAndMinute)
                .datePickerStyle(.wheel)
                .labelsHidden()
                .frame(maxWidth: .infinity)
                .transition(.opacity)
        case nil:
            EmptyView()
        }
    }

    // MARK: Rules

    private var startRange: ClosedRange<Date> {
        let low = [bounds?.lowerBound, earliest].compactMap { $0 }.max() ?? .distantPast
        let high = bounds?.upperBound ?? .distantFuture
        return min(low, high)...high
    }

    private var endRange: ClosedRange<Date> {
        let low = allDay ? Calendar.current.startOfDay(for: start) : start
        return low...max(low, bounds?.upperBound ?? .distantFuture)
    }

    /// Moving the start keeps the length: the end moves with it.
    private var startBinding: Binding<Date> {
        Binding(get: { start }, set: { newStart in
            let length = end.timeIntervalSince(start)
            start = newStart
            end = DateRangeRules.end(afterMovingStartTo: newStart, length: length, allDay: allDay, upper: bounds?.upperBound)
        })
    }

    /// The end can't go before the start: an earlier time keeps the old length from the start instead.
    private var endBinding: Binding<Date> {
        Binding(get: { end }, set: { newEnd in
            end = DateRangeRules.end(settingEndTo: newEnd, start: start, previousEnd: end, allDay: allDay)
        })
    }
}

/// The date range's rules, kept apart so they're easy to reason about.
enum DateRangeRules {
    static func end(afterMovingStartTo start: Date, length: TimeInterval, allDay: Bool, upper: Date?) -> Date {
        var end = start.addingTimeInterval(max(length, allDay ? 0 : 15 * 60))
        if let upper, end > upper { end = upper }
        return end
    }

    static func end(settingEndTo end: Date, start: Date, previousEnd: Date, allDay: Bool) -> Date {
        if allDay { return max(end, Calendar.current.startOfDay(for: start)) }
        guard end <= start else { return end }
        let length = previousEnd.timeIntervalSince(start)
        return start.addingTimeInterval(length > 0 ? length : 3600)
    }
}
