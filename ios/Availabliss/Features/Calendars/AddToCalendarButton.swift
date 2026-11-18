import EventKit
import SwiftUI

/// Add-to-device-calendar button. With more than one calendar to choose from it asks which one
/// in a sheet (the last one used is ticked), then says "Added to your calendar" while you're
/// still here. An event already added before isn't offered again: the button doesn't show.
/// Disabled while saving so a double tap can't create two events.
struct AddToCalendarButton: View {
    let event: GroupEvent
    var height: CGFloat = 54
    @Environment(AppModel.self) private var app
    @State private var isAdded = false
    @State private var isSaving = false
    @State private var isChoosing = false
    @State private var error: String?

    var body: some View {
        if app.deviceCalendar.isAdded(event.id) && !isAdded {
            EmptyView()
        } else {
            Button {
                if app.deviceCalendar.canChooseCalendar {
                    isChoosing = true
                } else {
                    Task { await add(to: nil) }
                }
            } label: {
                Label(isAdded ? "Added to your calendar" : "Add to calendar", systemImage: isAdded ? "checkmark" : "calendar.badge.plus")
            }
            .buttonStyle(.filled(isAdded ? .success : .primary, height: height, cornerRadius: height > 50 ? 18 : 16))
            .disabled(isAdded || isSaving)
            .errorAlert($error)
            .sensoryFeedback(.success, trigger: isAdded)
            .sheet(isPresented: $isChoosing) {
                CalendarChoiceSheet(
                    title: "Add to calendar",
                    message: "Which calendar should \(event.title) go in?",
                    groups: app.deviceCalendar.writableCalendarsBySource,
                    selectedId: app.deviceCalendar.preferredCalendar?.calendarIdentifier
                ) { calendar in
                    Task { await add(to: calendar) }
                }
            }
        }
    }

    private func add(to calendar: EKCalendar?) async {
        guard !isSaving else { return }
        isSaving = true
        defer { isSaving = false }
        do {
            try await app.deviceCalendar.add(event, to: calendar)
            withAnimation { isAdded = true }
        } catch {
            self.error = error.userMessage
        }
    }
}
