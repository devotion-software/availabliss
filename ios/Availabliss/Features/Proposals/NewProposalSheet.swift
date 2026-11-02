import SwiftUI

/// What a new proposal starts from: a slot that works, a free stretch, or nothing (pick by hand).
struct ProposalDraft: Identifiable {
    let id = UUID()
    var start: Date
    var end: Date
    var allDay: Bool
    /// For duration requirements the slot is a maximal free stretch; the proposal can be any part of it.
    var bounds: ClosedRange<Date>?
    /// Prefilled title and note (from the AI planner).
    var title = ""
    var note = ""

    init(start: Date, end: Date, allDay: Bool, bounds: ClosedRange<Date>? = nil, title: String = "", note: String = "") {
        self.start = start
        self.end = end
        self.allDay = allDay
        self.bounds = bounds
        self.title = title
        self.note = note
    }

    init(slot: Slot, requirement: Requirement) {
        if case .duration(let minutes) = requirement.length {
            let end = min(slot.end, slot.start.addingTimeInterval(TimeInterval(minutes * 60)))
            self.init(start: slot.start, end: end, allDay: false, bounds: slot.start...slot.end)
        } else {
            let calendar = Calendar.current
            let allDay = calendar.startOfDay(for: slot.start) == slot.start && calendar.startOfDay(for: slot.end) == slot.end
            self.init(start: slot.start, end: slot.end, allDay: allDay)
        }
    }

    /// Tomorrow evening, for proposing from chat without a slot.
    static func blank() -> ProposalDraft {
        let day = CalendarDay.today().adding(days: 1)
        return ProposalDraft(start: WallTime(hour: 18, minute: 0).date(on: day), end: WallTime(hour: 21, minute: 0).date(on: day), allDay: false)
    }
}

/// Create a proposal: time (prefilled from the slot), optional title, place and note.
/// Times that clash with someone's busy time can still be proposed, with a warning naming who's busy.
struct NewProposalSheet: View {
    let group: Group
    let me: String?
    let api: APIClient
    let onCreated: (Proposal) -> Void
    @Environment(\.dismiss) private var dismiss
    @Environment(\.theme) private var theme
    @State private var draft: ProposalDraft
    @State private var title = ""
    @State private var location = ""
    @State private var note = ""
    @State private var isSaving = false
    @State private var error: String?
    @State private var busyIds: [String] = []

    private var groupId: String { group.id }
    private var clash: String? { Clash.text(busyIds, group: group, me: me) }

    /// Changes whenever the proposed times do, to re-check who's busy.
    private struct TimesKey: Equatable {
        let start: Date, end: Date, allDay: Bool
    }

    init(group: Group, me: String?, draft: ProposalDraft, api: APIClient, onCreated: @escaping (Proposal) -> Void) {
        self.group = group
        self.me = me
        self.api = api
        self.onCreated = onCreated
        _draft = State(initialValue: draft)
        _title = State(initialValue: draft.title)
        _note = State(initialValue: draft.note)
    }

    var body: some View {
        BottomSheet(title: "Propose a time") {
            Text(Formatting.span(start: draft.start, end: draft.end, allDay: draft.allDay))
                .displayFont(24, .bold)
            VStack(spacing: 0) {
                DatePicker("Starts", selection: $draft.start, in: draft.bounds ?? Date.distantPast...Date.distantFuture,
                           displayedComponents: draft.allDay ? .date : [.date, .hourAndMinute])
                Divider().overlay(theme.line)
                DatePicker("Ends", selection: $draft.end, in: draft.start...(draft.bounds?.upperBound ?? Date.distantFuture),
                           displayedComponents: draft.allDay ? .date : [.date, .hourAndMinute])
                Divider().overlay(theme.line)
                Toggle("All day", isOn: $draft.allDay).toggleStyle(.glassSwitch).frame(minHeight: 50)
            }
            .bodyFont(15, .medium)
            .padding(.horizontal, 14)
            .surfaceBackground(cornerRadius: 18)
            InputField(placeholder: "Title (optional)", text: $title)
            InputField(placeholder: "Where? (optional)", text: $location)
            InputField(placeholder: "Note (optional)", text: $note)
            Text("You'll accept automatically, and a card is posted in the group chat.")
                .bodyFont(12, relativeTo: .caption)
                .foregroundStyle(theme.muted)
            if let clash {
                ClashPanel(headline: clash, detail: "You can still propose it; everyone will see the clash before they reply.")
                    .transition(.opacity)
            }
            Button {
                Task { await submit() }
            } label: {
                LoadingLabel(title: clash == nil ? "Send proposal" : "Propose anyway", isLoading: isSaving)
            }
            .buttonStyle(.accent)
            .disabled(isSaving || draft.end <= draft.start)
        }
        .animation(.easeInOut(duration: 0.2), value: clash)
        .task(id: TimesKey(start: draft.start, end: draft.end, allDay: draft.allDay)) { await checkConflicts() }
        .errorAlert($error)
    }

    /// Debounced: a newer change cancels this task before the request goes out.
    private func checkConflicts() async {
        guard draft.end > draft.start else {
            busyIds = []
            return
        }
        do {
            try await Task.sleep(for: .milliseconds(250))
            busyIds = try await api.send(API.Groups.conflicts(groupId: groupId, start: draft.start, end: draft.end)).busyMemberIds
        } catch where error.isCancellation {
            // Superseded by a newer time.
        } catch {
            busyIds = []
        }
    }

    private func submit() async {
        isSaving = true
        defer { isSaving = false }
        func clean(_ text: String) -> String? {
            let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
            return trimmed.isEmpty ? nil : trimmed
        }
        do {
            let proposal = try await api.send(API.Proposals.create(groupId: groupId, CreateProposalBody(
                start: draft.start, end: draft.end, allDay: draft.allDay,
                title: clean(title), location: clean(location), note: clean(note)
            )))
            dismiss()
            onCreated(proposal)
        } catch {
            self.error = error.userMessage
        }
    }
}
