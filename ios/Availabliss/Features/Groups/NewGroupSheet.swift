import SwiftUI

/// New group in two steps: 1) name, colour and roughly what you're planning; 2) the full timing
/// editor (days, times, holidays, date range) with a live count of how many times fit.
struct NewGroupSheet: View {
    let api: APIClient
    let onCreated: (Group) -> Void
    @Environment(AppModel.self) private var app
    @Environment(\.dismiss) private var dismiss
    @Environment(\.theme) private var theme
    @State private var step = 1
    @State private var name = ""
    @State private var color = NewGroupSheet.colors.randomElement() ?? "t1"
    @State private var preset: TimingPreset = .weekend
    @State private var timing: TimingEditorViewModel
    @State private var isSaving = false
    @State private var error: String?

    private static let colors = ["t1", "t2", "t3", "t4", "t5"]
    private static let quickPresets: [TimingPreset] = [.twoHours, .evening, .oneDay, .weekend, .week]

    init(api: APIClient, timezone: String, onCreated: @escaping (Group) -> Void) {
        self.api = api
        self.onCreated = onCreated
        _timing = State(initialValue: TimingEditorViewModel(draft: TimingEditorViewModel.fresh(.weekend, timezone: timezone), api: api))
    }

    private var trimmedName: String { name.trimmingCharacters(in: .whitespacesAndNewlines) }
    private var timezone: String { app.session.user?.timezone ?? TimeZone.current.identifier }

    var body: some View {
        BottomSheet(title: step == 1 ? "New group" : "When are you looking for?") {
            if step == 1 { details } else { timingStep }
        }
        .safeAreaInset(edge: .bottom) {
            if step == 2 { createBar }
        }
        .task { await timing.loadAuthorities(country: app.session.user?.country) }
        .task(id: timing.draft) { await timing.refreshPreview() }
        .animation(.spring(response: 0.35, dampingFraction: 0.85), value: step)
        .errorAlert($error)
    }

    // MARK: Step 1

    @ViewBuilder
    private var details: some View {
        Text("Name it and pick roughly what you\u{2019}re planning.")
            .bodyFont(14)
            .foregroundStyle(theme.muted)
        StepDots(current: 0, total: 2)
        HStack(spacing: 14) {
            GroupTile(monogram: monogram, colorToken: color, size: 56, cornerRadius: 18)
            InputField(placeholder: "e.g. Lake District crew", text: $name)
                .submitLabel(.next)
                .onSubmit(next)
        }
        VStack(alignment: .leading, spacing: 8) {
            Text("Colour").sectionLabelStyle()
            HStack(spacing: 10) {
                ForEach(Self.colors, id: \.self) { token in
                    Button {
                        color = token
                    } label: {
                        Circle()
                            .fill(theme.tile(token))
                            .frame(width: 40, height: 40)
                            .overlay(Circle().strokeBorder(theme.ink, lineWidth: color == token ? 2.5 : 0).padding(-4))
                            .frame(width: 48, height: 48)
                    }
                    .buttonStyle(.pressable)
                    .accessibilityLabel("Colour \(token.dropFirst())")
                    .accessibilityAddTraits(color == token ? .isSelected : [])
                }
            }
        }
        VStack(alignment: .leading, spacing: 10) {
            Text("What are you planning?").sectionLabelStyle()
            FlowLayout(spacing: 8) {
                ForEach(Self.quickPresets) { option in
                    Chip(label: option.label, isOn: preset == option, height: 40, fontSize: 14) { pick(option) }
                }
            }
            Text(timing.preview?.summary ?? timing.draft.draftSummary)
                .bodyFont(13, relativeTo: .subheadline)
                .foregroundStyle(theme.muted)
        }
        Button(action: next) {
            LoadingLabel(title: "Next: timing", systemImage: "arrow.right")
        }
        .buttonStyle(.accent)
        .disabled(trimmedName.isEmpty)
    }

    /// A fresh timing per preset: "A week" needs every day, "A weekend" Fri–Sun, and so on.
    private func pick(_ option: TimingPreset) {
        preset = option
        timing.reset(to: TimingEditorViewModel.fresh(option, timezone: timezone))
    }

    private func next() {
        guard !trimmedName.isEmpty else { return }
        step = 2
    }

    // MARK: Step 2

    @ViewBuilder
    private var timingStep: some View {
        Text("Fine-tune the timing for \(trimmedName). You can change it later.")
            .bodyFont(14)
            .foregroundStyle(theme.muted)
        StepDots(current: 1, total: 2)
        TimingForm(model: timing)
    }

    private var createBar: some View {
        VStack(alignment: .leading, spacing: 10) {
            FitSummary(model: timing)
            HStack(spacing: 8) {
                Button {
                    step = 1
                } label: {
                    Label("Back", systemImage: "chevron.left")
                }
                .buttonStyle(.filled(.secondary, height: 48, cornerRadius: 16, fontSize: 15))
                Button {
                    Task { await create() }
                } label: {
                    LoadingLabel(title: "Create group", isLoading: isSaving)
                }
                .buttonStyle(.filled(.accent, height: 48, cornerRadius: 16, fontSize: 15))
                .disabled(isSaving)
            }
        }
        .padding(14)
        .glass(cornerRadius: 26)
        .padding(.horizontal, 12)
        .padding(.bottom, 8)
    }

    private func create() async {
        var requirement = timing.draft
        if requirement.timezone.isEmpty { requirement.timezone = timezone }
        if requirement.schoolHolidaysOnly && requirement.schoolAuthority == nil && requirement.customSchoolHolidays.isEmpty {
            error = "Choose whose school holidays to use."
            return
        }
        if requirement.range.to < requirement.range.from {
            error = "The search range ends before it starts."
            return
        }
        isSaving = true
        defer { isSaving = false }
        do {
            let group = try await api.send(API.Groups.create(CreateGroupBody(name: trimmedName, color: color, requirement: requirement)))
            dismiss()
            onCreated(group)
        } catch let failure as APIError {
            // A free account at its group limit gets a 402 `upgrade_required` with a message to show.
            if case .validation(_, let issues) = failure, issues.contains(where: { $0.path == "name" }) { step = 1 }
            error = failure.userMessage
        } catch {
            self.error = error.userMessage
        }
    }

    private var monogram: String {
        let words = name.split(separator: " ").prefix(2)
        let letters = words.compactMap(\.first).map { String($0).uppercased() }.joined()
        return letters.isEmpty ? "?" : letters
    }
}
