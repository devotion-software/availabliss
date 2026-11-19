import EventKit
import SwiftUI

/// Add-to-device-calendar button. With more than one calendar to choose from it asks which one
/// in a sheet (the last one used is ticked), then says "Added to your calendar" for a few
/// seconds. After that the button goes: a quiet line says which calendar the plan is in, with
/// Change to move it to another; if it's been deleted from the calendar since, Add to calendar
/// comes back.
/// Disabled while saving so a double tap can't create two events.
struct AddToCalendarButton: View {
    let event: GroupEvent
    var height: CGFloat = 54
    /// Centres the "In Work" chip once it's added (on a centred page); otherwise it sits at the leading edge.
    var centersChip = false
    @Environment(AppModel.self) private var app
    @State private var justAdded = false
    @State private var isSaving = false
    @State private var choice: Choice?
    @State private var error: String?

    private enum Choice: Identifiable {
        case add, move(current: EKCalendar?)
        var id: String {
            switch self {
            case .add: "add"
            case .move: "move"
            }
        }
    }

    var body: some View {
        // Turned off in You (e.g. subscribed to their plans instead): nothing at all.
        if app.session.user?.showsAddToCalendar ?? true { content }
    }

    private var content: some View {
        let state = app.deviceCalendar.addedState(event.id)
        return VStack(spacing: 8) {
            switch state {
            case .notAdded:
                addButton
            case .added(let calendar):
                if justAdded { addButton }
                AddedToCalendarRow(calendar: calendar, canChange: app.deviceCalendar.canChooseCalendar, centered: centersChip) {
                    choice = .move(current: calendar)
                }
            }
        }
        .errorAlert($error)
        .sensoryFeedback(.success, trigger: justAdded)
        .task(id: justAdded) {
            guard justAdded else { return }
            try? await Task.sleep(for: .seconds(3))
            guard !Task.isCancelled else { return }
            withAnimation(.easeInOut(duration: 0.3)) { justAdded = false }
        }
        .task(id: "\(event.start.timeIntervalSince1970)-\(event.end.timeIntervalSince1970)") {
            app.deviceCalendar.updateIfPlanChanged(event)
        }
        .sheet(item: $choice) { choice in
            switch choice {
            case .add:
                CalendarChoiceSheet(
                    title: "Add to calendar",
                    message: "Which calendar should \(event.title) go in?",
                    groups: app.deviceCalendar.writableCalendarsBySource,
                    selectedId: app.deviceCalendar.preferredCalendar?.calendarIdentifier
                ) { calendar in
                    Task { await add(to: calendar) }
                }
            case .move(let current):
                CalendarChoiceSheet(
                    title: "Move to calendar",
                    message: "Choose another calendar and \(event.title) moves there.",
                    groups: app.deviceCalendar.writableCalendarsBySource,
                    selectedId: current?.calendarIdentifier
                ) { calendar in
                    Task { await move(to: calendar) }
                }
            }
        }
    }

    private var addButton: some View {
        Button {
            if app.deviceCalendar.canChooseCalendar {
                choice = .add
            } else {
                Task { await add(to: nil) }
            }
        } label: {
            Label(justAdded ? "Added to your calendar" : "Add to calendar", systemImage: justAdded ? "checkmark" : "calendar.badge.plus")
        }
        .buttonStyle(.filled(justAdded ? .success : .primary, height: height, cornerRadius: height > 50 ? 18 : 16))
        .disabled(justAdded || isSaving)
    }

    private func add(to calendar: EKCalendar?) async {
        guard !isSaving else { return }
        isSaving = true
        defer { isSaving = false }
        do {
            try await app.deviceCalendar.add(event, to: calendar)
            withAnimation { justAdded = true }
        } catch {
            self.error = error.userMessage
        }
    }

    private func move(to calendar: EKCalendar) async {
        guard !isSaving else { return }
        isSaving = true
        defer { isSaving = false }
        do {
            try await app.deviceCalendar.move(event, to: calendar)
        } catch {
            self.error = error.userMessage
        }
    }
}

/// "● In Work ⌄": a small chip once a plan is in the user's calendar. Tapping it (when the calendar can be
/// chosen) moves the plan to another calendar; the row hugs its content instead of spanning the card.
private struct AddedToCalendarRow: View {
    let calendar: EKCalendar?
    let canChange: Bool
    var centered = false
    let change: () -> Void
    @Environment(\.theme) private var theme

    private var interactive: Bool { canChange && calendar != nil }

    var body: some View {
        HStack {
            if centered { Spacer(minLength: 0) }
            Button(action: change) {
                HStack(spacing: 6) {
                    if let calendar {
                        Circle().fill(Color(cgColor: calendar.cgColor)).frame(width: 8, height: 8)
                    } else {
                        Image(systemName: "checkmark").font(.system(size: 11, weight: .bold)).foregroundStyle(theme.okInk)
                    }
                    Text(calendar.map { "In \($0.title)" } ?? "In your calendar")
                        .bodyFont(13, .medium, relativeTo: .subheadline)
                        .foregroundStyle(theme.ink)
                        .lineLimit(1)
                    if interactive {
                        Image(systemName: "chevron.up.chevron.down")
                            .font(.system(size: 10, weight: .semibold))
                            .foregroundStyle(theme.muted)
                    }
                }
                .padding(.horizontal, 10)
                .frame(minHeight: 28)
                .background(Capsule().fill(theme.surface))
                .overlay(Capsule().strokeBorder(theme.line, lineWidth: 1))
                .contentShape(Capsule())
            }
            .buttonStyle(.plain)
            .disabled(!interactive)
            .accessibilityLabel(calendar.map { "In \($0.title)" } ?? "In your calendar")
            .accessibilityHint(interactive ? "Change calendar" : "")
            Spacer(minLength: 0)
        }
    }
}
