import SwiftUI

/// New group in two steps: 1) name, colour and optionally a saved template; 2) the full timing
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
    /// Your saved timings; picking one starts step 2 from it instead of the default.
    @State private var templates: [RequirementTemplate] = []
    @State private var templateId: String?
    @State private var toast: String?
    /// Free accounts at their group limit see the limit panel instead of the form.
    @State private var limit: Int?
    @State private var showsUpgrade = false
    @State private var timing: TimingEditorViewModel
    @State private var isSaving = false
    @State private var error: String?

    private static let colors = ["t1", "t2", "t3", "t4", "t5"]

    init(api: APIClient, timezone: String, onCreated: @escaping (Group) -> Void) {
        self.api = api
        self.onCreated = onCreated
        _timing = State(initialValue: TimingEditorViewModel(draft: TimingEditorViewModel.fresh(.weekend, timezone: timezone), api: api))
    }

    private var trimmedName: String { name.trimmingCharacters(in: .whitespacesAndNewlines) }
    private var timezone: String { app.session.user?.timezone ?? TimeZone.current.identifier }

    var body: some View {
        BottomSheet(title: limit != nil || step == 1 ? "New group" : "When are you looking for?") {
            if let limit {
                GroupLimitPanel(limit: limit) { showsUpgrade = true }
            } else if step == 1 {
                details
            } else {
                timingStep
            }
        }
        .safeAreaInset(edge: .bottom) {
            if step == 2 && limit == nil { createBar }
        }
        .task {
            if let plan = try? await api.send(API.Me.plan()), !plan.canCreateGroup { limit = plan.groupLimit ?? 3 }
        }
        .sheet(isPresented: $showsUpgrade) {
            UpgradeSheet(reason: "You've used your \(limit ?? 3) free groups. Pro gives you as many as you like.") { _ in
                limit = nil
                toast = "Welcome to Pro"
            }
        }
        .task { await timing.loadAuthorities(country: app.session.user?.country) }
        .task { templates = (try? await api.send(API.Me.templates()).templates) ?? [] }
        .task(id: timing.draft) { await timing.refreshPreview() }
        .animation(.spring(response: 0.35, dampingFraction: 0.85), value: step)
        .errorAlert($error)
        .toast($toast)
    }

    // MARK: Step 1

    @ViewBuilder
    private var details: some View {
        Text("Name it and pick a colour. Timing comes next.")
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
        if !templates.isEmpty {
            VStack(alignment: .leading, spacing: 10) {
                Text("Start from a template").sectionLabelStyle()
                FlowLayout(spacing: 8) {
                    ForEach(templates) { template in
                        Chip(label: template.name, isOn: templateId == template.id, height: 40, fontSize: 14) { pick(template) }
                    }
                }
            }
        }
        Button(action: next) {
            LoadingLabel(title: "Next: timing", systemImage: "arrow.right")
        }
        .buttonStyle(.accent)
        .disabled(trimmedName.isEmpty)
    }

    /// Tapping the selected template again goes back to the default timing.
    private func pick(_ template: RequirementTemplate) {
        guard templateId != template.id else {
            templateId = nil
            timing.reset(to: TimingEditorViewModel.fresh(.weekend, timezone: timezone))
            return
        }
        templateId = template.id
        timing.reset(to: template.requirementForNewGroup(timezone: timezone))
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
        SaveTemplateRow(requirement: timing.draft, api: api, toast: $toast) { templates.append($0) }
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
            switch failure {
            case .upgradeRequired:
                // A free account at its group limit.
                limit = (try? await api.send(API.Me.plan()))?.groupLimit ?? 3
            case .validation(_, let issues) where issues.contains(where: { $0.path == "name" }):
                step = 1
                error = failure.userMessage
            default:
                error = failure.userMessage
            }
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
