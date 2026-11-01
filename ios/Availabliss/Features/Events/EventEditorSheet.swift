import SwiftUI

/// Add an event straight to a group, or edit / delete one.
struct EventEditorSheet: View {
    enum Target: Identifiable {
        case new
        case edit(GroupEvent)
        var id: String {
            switch self {
            case .new: "new"
            case .edit(let event): event.id
            }
        }
    }

    let groupId: String
    let target: Target
    let api: APIClient
    let onSaved: (GroupEvent?) -> Void
    @Environment(\.dismiss) private var dismiss
    @Environment(\.theme) private var theme
    @State private var title: String
    @State private var start: Date
    @State private var end: Date
    @State private var allDay: Bool
    @State private var location: String
    @State private var note: String
    @State private var isSaving = false
    @State private var error: String?

    init(groupId: String, target: Target, api: APIClient, onSaved: @escaping (GroupEvent?) -> Void) {
        self.groupId = groupId
        self.target = target
        self.api = api
        self.onSaved = onSaved
        let draft = ProposalDraft.blank()
        switch target {
        case .new:
            _title = State(initialValue: "")
            _start = State(initialValue: draft.start)
            _end = State(initialValue: draft.end)
            _allDay = State(initialValue: false)
            _location = State(initialValue: "")
            _note = State(initialValue: "")
        case .edit(let event):
            _title = State(initialValue: event.title)
            _start = State(initialValue: event.start)
            _end = State(initialValue: event.end)
            _allDay = State(initialValue: event.allDay)
            _location = State(initialValue: event.location ?? "")
            _note = State(initialValue: event.note ?? "")
        }
    }

    private var isNew: Bool { if case .new = target { true } else { false } }

    var body: some View {
        BottomSheet(title: isNew ? "Add event" : "Edit event") {
            InputField(placeholder: "What's happening?", text: $title)
            VStack(spacing: 0) {
                Toggle("All day", isOn: $allDay).toggleStyle(.glassSwitch).frame(minHeight: 50)
                Divider().overlay(theme.line)
                DatePicker("Starts", selection: $start, displayedComponents: allDay ? .date : [.date, .hourAndMinute]).frame(minHeight: 50)
                Divider().overlay(theme.line)
                DatePicker("Ends", selection: $end, in: start..., displayedComponents: allDay ? .date : [.date, .hourAndMinute]).frame(minHeight: 50)
            }
            .bodyFont(15, .medium)
            .padding(.horizontal, 14)
            .surfaceBackground(cornerRadius: 18)
            InputField(placeholder: "Where? (optional)", text: $location)
            InputField(placeholder: "Note (optional)", text: $note)
            Button {
                Task { await save() }
            } label: {
                LoadingLabel(title: isNew ? "Add to group" : "Save", isLoading: isSaving)
            }
            .buttonStyle(.primary)
            .disabled(title.trimmingCharacters(in: .whitespaces).isEmpty || end < start || isSaving)
            if case .edit(let event) = target {
                Button("Delete event", role: .destructive) { Task { await delete(event) } }
                    .buttonStyle(.textAction)
                    .foregroundStyle(theme.warnInk)
                    .frame(maxWidth: .infinity)
            }
        }
        .onChange(of: start) { _, newStart in if end < newStart { end = newStart.addingTimeInterval(3600) } }
        .errorAlert($error)
    }

    private var requestBody: EventBody {
        let calendar = Calendar.current
        let startValue = allDay ? calendar.startOfDay(for: start) : start
        let endValue = allDay ? (calendar.date(byAdding: .day, value: 1, to: calendar.startOfDay(for: end)) ?? end) : end
        func clean(_ text: String) -> String? {
            let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
            return trimmed.isEmpty ? nil : trimmed
        }
        return EventBody(title: title.trimmingCharacters(in: .whitespaces), start: startValue, end: endValue, allDay: allDay, location: clean(location), note: clean(note))
    }

    private func save() async {
        isSaving = true
        defer { isSaving = false }
        do {
            let saved: GroupEvent
            switch target {
            case .new: saved = try await api.send(API.Events.create(groupId: groupId, requestBody))
            case .edit(let event): saved = try await api.send(API.Events.update(id: event.id, requestBody))
            }
            onSaved(saved)
            dismiss()
        } catch {
            self.error = error.userMessage
        }
    }

    private func delete(_ event: GroupEvent) async {
        do {
            try await api.send(API.Events.delete(id: event.id))
            onSaved(nil)
            dismiss()
        } catch {
            self.error = error.userMessage
        }
    }
}
