import EventKit
import SwiftUI

/// Add-to-device-calendar button. With more than one calendar to choose from it asks which one
/// in a sheet (the last one used is ticked), then says "Added to your calendar" while you're
/// still here. Once added it says which calendar the plan is in, with Change to move it to
/// another; if it's been deleted from the calendar since, Add to calendar comes back.
/// Disabled while saving so a double tap can't create two events.
struct AddToCalendarButton: View {
    let event: GroupEvent
    var height: CGFloat = 54
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
        let state = app.deviceCalendar.addedState(event.id)
        VStack(spacing: 8) {
            switch state {
            case .notAdded:
                addButton
            case .added(let calendar):
                if justAdded { addButton }
                AddedToCalendarRow(calendar: calendar, canChange: app.deviceCalendar.canChooseCalendar) {
                    choice = .move(current: calendar)
                }
            }
        }
        .errorAlert($error)
        .sensoryFeedback(.success, trigger: justAdded)
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

/// "In Work · Change": the quiet line once a plan is in the user's calendar.
private struct AddedToCalendarRow: View {
    let calendar: EKCalendar?
    let canChange: Bool
    let change: () -> Void
    @Environment(\.theme) private var theme

    var body: some View {
        HStack(spacing: 8) {
            if let calendar {
                Circle().fill(Color(cgColor: calendar.cgColor)).frame(width: 10, height: 10)
            } else {
                Image(systemName: "calendar.badge.checkmark").font(.system(size: 14, weight: .semibold)).foregroundStyle(theme.okInk)
            }
            Text(calendar.map { "In \($0.title)" } ?? "In your calendar")
                .bodyFont(13, .medium, relativeTo: .subheadline)
                .foregroundStyle(theme.muted)
                .lineLimit(1)
            Spacer(minLength: 8)
            if canChange && calendar != nil {
                Button("Change", action: change)
                    .buttonStyle(.textAction)
                    .accessibilityLabel("Change calendar")
            }
        }
        .frame(minHeight: 32)
    }
}
