import SwiftUI

/// Add an event straight to a group, or edit / delete one (with its cover photo).
/// Only shown to whoever may edit the event (its creator or a group admin), so the cover is editable too.
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
    /// The cover as saved on the server.
    @State private var savedCover: String?
    /// A new cover to upload, or removing it, applied on Save.
    @State private var coverChange: CoverChange?
    @State private var showsPhotoPicker = false
    /// The new event, once created, so a retry after a failed cover upload doesn't create it twice.
    @State private var created: GroupEvent?

    private enum CoverChange {
        case set(jpeg: Data, preview: UIImage)
        case remove
    }

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
            // All day ends at the midnight after the last day; the input shows the last day.
            _end = State(initialValue: event.allDay ? event.end.addingTimeInterval(-1) : event.end)
            _allDay = State(initialValue: event.allDay)
            _location = State(initialValue: event.location ?? "")
            _note = State(initialValue: event.note ?? "")
            _savedCover = State(initialValue: event.image)
        }
    }

    private var isNew: Bool { if case .new = target { true } else { false } }

    var body: some View {
        BottomSheet(title: isNew ? "Add event" : "Edit event") {
            cover
            InputField(placeholder: "What's happening?", text: $title)
            DateRangeInput(start: $start, end: $end, allDay: $allDay)
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
        .jpegPhotoPicker(isPresented: $showsPhotoPicker, maxDimension: 1600) { jpeg in
            guard let jpeg, let preview = UIImage(data: jpeg) else {
                error = "Couldn\u{2019}t read that photo."
                return
            }
            coverChange = .set(jpeg: jpeg, preview: preview)
        }
        .errorAlert($error)
    }

    private var hasCover: Bool {
        switch coverChange {
        case .set: true
        case .remove: false
        case nil: savedCover != nil
        }
    }

    /// The cover (new or saved) with Change / Remove, or "Add a cover photo".
    @ViewBuilder
    private var cover: some View {
        if hasCover {
            SwiftUI.Group {
                if case .set(_, let preview) = coverChange {
                    Color.clear
                        .frame(height: 140)
                        .frame(maxWidth: .infinity)
                        .overlay { Image(uiImage: preview).resizable().scaledToFill() }
                        .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
                } else if let savedCover {
                    EventCover(imageURL: savedCover, cornerRadius: 18)
                }
            }
            .overlay(alignment: .bottomTrailing) {
                Menu {
                    Button {
                        showsPhotoPicker = true
                    } label: {
                        Label("Change photo", systemImage: "photo")
                    }
                    Button(role: .destructive) {
                        coverChange = savedCover == nil ? nil : .remove
                    } label: {
                        Label("Remove photo", systemImage: "trash")
                    }
                } label: {
                    Label("Cover", systemImage: "photo")
                        .bodyFont(13, .semibold, relativeTo: .subheadline)
                        .foregroundStyle(theme.ink)
                        .padding(.horizontal, 12)
                        .frame(minHeight: 36)
                        .glass(in: Capsule(), shadow: false)
                }
                .padding(8)
                .accessibilityLabel("Cover photo")
            }
        } else {
            Button {
                showsPhotoPicker = true
            } label: {
                Label("Add a cover photo", systemImage: "photo")
                    .bodyFont(15, .medium)
                    .foregroundStyle(theme.accentInk)
                    .frame(maxWidth: .infinity, minHeight: 50)
                    .surfaceBackground(cornerRadius: 18)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.pressable)
        }
    }

    private var requestBody: EventBody {
        let calendar = Calendar.current
        let startValue = allDay ? calendar.startOfDay(for: start) : start
        let endValue = allDay ? (calendar.date(byAdding: .day, value: 1, to: calendar.startOfDay(for: end)) ?? end) : end
        func clean(_ text: String) -> String? {
            let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
            return trimmed.isEmpty ? nil : trimmed
        }
        return EventBody(
            title: title.trimmingCharacters(in: .whitespaces), start: startValue, end: endValue, allDay: allDay, location: clean(location), note: clean(note))
    }

    private func save() async {
        isSaving = true
        defer { isSaving = false }
        do {
            var saved: GroupEvent
            switch (target, created) {
            case (_, let created?): saved = try await api.send(API.Events.update(id: created.id, requestBody))
            case (.new, nil):
                saved = try await api.send(API.Events.create(groupId: groupId, requestBody))
                created = saved
            case (.edit(let event), nil): saved = try await api.send(API.Events.update(id: event.id, requestBody))
            }
            // The cover goes up once the event exists (a new event has no id until now).
            switch coverChange {
            case .set(let jpeg, _): saved = try await api.send(API.Events.setImage(id: saved.id, jpeg: jpeg))
            case .remove: saved = try await api.send(API.Events.removeImage(id: saved.id))
            case nil: break
            }
            coverChange = nil
            savedCover = saved.image
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
