import SwiftUI

/// The optional AI planner (Pro): asks Claude for the best of the times that already work for
/// everyone, things to do and next steps; one tap turns a time into a proposal.
struct PlannerSheet: View {
    /// Runs the planner (`POST /groups/:id/planner`) with the optional notes.
    let ask: (String?) async throws -> PlannerResult
    /// A time to propose, with the planner's title and note.
    let propose: (Slot, _ title: String, _ note: String) -> Void
    @Environment(\.dismiss) private var dismiss
    @Environment(\.theme) private var theme
    @State private var notes = ""
    @State private var isAsking = false
    @State private var result: PlannerResult?
    @State private var failure: String?
    @FocusState private var notesFocused: Bool

    init(
        result: PlannerResult? = nil,
        ask: @escaping (String?) async throws -> PlannerResult,
        propose: @escaping (Slot, _ title: String, _ note: String) -> Void
    ) {
        self.ask = ask
        self.propose = propose
        _result = State(initialValue: result)
    }

    var body: some View {
        BottomSheet(title: "Plan with AI") {
            Text("Picks the best of the times that work for everyone, with ideas and next steps.")
                .bodyFont(14)
                .foregroundStyle(theme.muted)
                .fixedSize(horizontal: false, vertical: true)
            if let result { results(result) } else { form }
        }
        .animation(.easeInOut(duration: 0.25), value: result)
    }

    // MARK: Ask

    @ViewBuilder
    private var form: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("Anything to bear in mind? (optional)").sectionLabelStyle()
            TextField("", text: $notes, prompt: Text("e.g. We're around Manchester, two of us have kids, keep it cheap").foregroundStyle(theme.muted), axis: .vertical)
                .lineLimit(3...6)
                .focused($notesFocused)
                .bodyFont(15)
                .padding(.horizontal, 14)
                .padding(.vertical, 12)
                .surfaceBackground(cornerRadius: 14, border: theme.line2)
                .onChange(of: notes) { _, value in if value.count > 500 { notes = String(value.prefix(500)) } }
                .disabled(isAsking)
        }
        Label {
            Text("Sends this group's name, timing and free dates, plus your note, to Claude by Anthropic. Never anyone's calendar, busy time or names.")
                .fixedSize(horizontal: false, vertical: true)
        } icon: {
            Image(systemName: "lock.shield")
        }
        .bodyFont(12, relativeTo: .caption)
        .foregroundStyle(theme.muted)
        Button {
            Task { await run() }
        } label: {
            LoadingLabel(title: "Suggest a plan", systemImage: "sparkles", isLoading: isAsking)
        }
        .buttonStyle(.accent)
        .disabled(isAsking)
        if isAsking {
            HStack(spacing: 8) {
                ProgressView().controlSize(.small)
                Text("Weighing up the options. This can take a little while.")
            }
            .bodyFont(13, relativeTo: .subheadline)
            .foregroundStyle(theme.muted)
            .frame(maxWidth: .infinity)
            .transition(.opacity)
        }
        if let failure {
            Label(failure, systemImage: "exclamationmark.triangle")
                .bodyFont(13, relativeTo: .subheadline)
                .foregroundStyle(theme.warnInk)
                .padding(12)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(theme.warnBg))
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    private func run() async {
        isAsking = true
        notesFocused = false
        failure = nil
        defer { isAsking = false }
        let trimmed = notes.trimmingCharacters(in: .whitespacesAndNewlines)
        do {
            result = try await ask(trimmed.isEmpty ? nil : trimmed)
        } catch where error.isCancellation {
        } catch {
            // 402 without Pro, 409 when nothing works yet, 429 past the hourly limit: the server's message says which.
            failure = error.userMessage
        }
    }

    // MARK: Results

    @ViewBuilder
    private func results(_ result: PlannerResult) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Best time").sectionLabelStyle()
            Text(result.best.slot.label).displayFont(22, .bold, relativeTo: .title2)
            Text(result.best.why).bodyFont(14).fixedSize(horizontal: false, vertical: true)
            Button {
                choose(result.best.slot, from: result)
            } label: {
                Label("Propose this", systemImage: "paperplane")
            }
            .buttonStyle(.filled(.accent, height: 46, cornerRadius: 14, fontSize: 15))
            .padding(.top, 4)
        }
        .padding(16)
        .background(RoundedRectangle(cornerRadius: 20, style: .continuous).fill(theme.accentSoft))
        .overlay(RoundedRectangle(cornerRadius: 20, style: .continuous).strokeBorder(theme.accent.opacity(0.35), lineWidth: 1))

        if !result.alternatives.isEmpty {
            section("Also works") {
                ForEach(Array(result.alternatives.enumerated()), id: \.offset) { index, option in
                    if index > 0 { Rectangle().fill(theme.line).frame(height: 1) }
                    HStack(alignment: .center, spacing: 10) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(option.slot.label).bodyFont(15, .semibold)
                            Text(option.why).bodyFont(13, relativeTo: .subheadline).foregroundStyle(theme.muted).fixedSize(horizontal: false, vertical: true)
                        }
                        Spacer(minLength: 4)
                        Button("Propose") { choose(option.slot, from: result) }
                            .buttonStyle(.filled(.secondary, height: 36, cornerRadius: 12, fontSize: 13, fullWidth: false))
                            .frame(minHeight: 44)
                    }
                    .padding(.vertical, 6)
                }
            }
        }

        if !result.ideas.isEmpty {
            section("Ideas") {
                ForEach(result.ideas, id: \.self) { idea in
                    HStack(alignment: .firstTextBaseline, spacing: 8) {
                        Text("\u{2022}").foregroundStyle(theme.accentInk)
                        (Text(idea.title).fontWeight(.semibold) + Text(" \(idea.description)").foregroundColor(theme.muted))
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    .bodyFont(14)
                }
            }
        }

        if !result.checklist.isEmpty {
            section("Next steps") {
                ForEach(Array(result.checklist.enumerated()), id: \.offset) { index, step in
                    HStack(alignment: .firstTextBaseline, spacing: 8) {
                        Text("\(index + 1).").monospacedDigit().foregroundStyle(theme.accentInk).fontWeight(.semibold)
                        Text(step).fixedSize(horizontal: false, vertical: true)
                    }
                    .bodyFont(14)
                }
            }
        }

        Button {
            self.result = nil
        } label: {
            Label("Try again with different notes", systemImage: "arrow.clockwise")
        }
        .buttonStyle(.textAction)
        .frame(maxWidth: .infinity)
    }

    private func section(_ title: String, @ViewBuilder content: () -> some View) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title).sectionLabelStyle().accessibilityAddTraits(.isHeader)
            content()
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .surfaceBackground(cornerRadius: 20)
    }

    private func choose(_ slot: Slot, from result: PlannerResult) {
        propose(slot, result.proposalTitle, result.proposalNote)
        dismiss()
    }
}

#if DEBUG
extension PlannerResult {
    /// A canned answer for previews (decoded like the real response).
    static let fixture: PlannerResult = {
        let json = """
        {
          "best": { "slot": { "start": "2026-11-27T18:00:00.000Z", "end": "2026-11-29T23:59:00.000Z", "label": "Fri 27 – Sun 29 Nov" },
                    "why": "Everyone's free all weekend and it's before the December rush, so it's the easiest one to lock in." },
          "alternatives": [
            { "slot": { "start": "2026-12-04T18:00:00.000Z", "end": "2026-12-06T23:59:00.000Z", "label": "Fri 4 – Sun 6 Dec" },
              "why": "Also clear for all five, a week later if the 27th feels too soon." }
          ],
          "ideas": [
            { "title": "Cottage near Keswick", "description": "Split five ways it's cheaper than a hotel, and there's a pub walk from the door." },
            { "title": "Catbells at sunrise", "description": "A short climb with big views; fine for kids." }
          ],
          "checklist": ["Propose the weekend in the group", "Book somewhere to stay once everyone's in", "Sort out lifts from the station"],
          "proposalTitle": "Lake District weekend",
          "proposalNote": "Thinking a cottage near Keswick, splitting the cost."
        }
        """
        return try! JSONCoding.decoder.decode(PlannerResult.self, from: Data(json.utf8))
    }()
}

#Preview("Planner: results") {
    PlannerSheet(result: .fixture, ask: { _ in .fixture }, propose: { _, _, _ in })
}

#Preview("Planner: ask") {
    PlannerSheet(ask: { _ in try await Task.sleep(for: .seconds(2)); return .fixture }, propose: { _, _, _ in })
}
#endif
