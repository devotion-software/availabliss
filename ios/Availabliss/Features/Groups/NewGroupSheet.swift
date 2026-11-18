import SwiftUI

/// New group in two steps: 1) name, colour and optionally a saved template; 2) the full timing
/// editor (days, times, holidays, date range) with a live count of how many times fit.
///
/// "New group with these people" (from a group's More menu or settings) starts it from that group:
/// its name with a 2, its colour and its timing, plus a step between the two listing its other
/// members, all ticked. Each one still ticked gets an invite to accept once the group is made.
struct NewGroupSheet: View {
    let api: APIClient
    /// The group whose people (and colour and timing) this one starts from, if any.
    let basedOn: Group?
    let onCreated: (Group) -> Void
    private enum Step { case details, people, timing }
    @Environment(AppModel.self) private var app
    @Environment(\.dismiss) private var dismiss
    @Environment(\.theme) private var theme
    @State private var step = Step.details
    @State private var name: String
    @State private var color: String
    /// Your saved timings; picking one starts step 2 from it instead of the default.
    @State private var templates: [RequirementTemplate] = []
    @State private var templateId: String?
    /// Timing copied from `basedOn` (picked to start with; a template replaces it).
    @State private var copiesTiming: Bool
    /// The people from `basedOn` to invite (everyone else in it to start with).
    @State private var invitees: Set<String>
    /// Made, but inviting its people failed: the error shows, then the group opens.
    @State private var createdGroup: Group?
    @State private var toast: String?
    /// Free accounts at their group limit see the limit panel instead of the form.
    @State private var limit: Int?
    @State private var showsUpgrade = false
    @State private var timing: TimingEditorViewModel
    @State private var isSaving = false
    @State private var error: String?

    init(api: APIClient, timezone: String, basedOn: Group? = nil, me: String? = nil, onCreated: @escaping (Group) -> Void) {
        self.api = api
        self.basedOn = basedOn
        self.onCreated = onCreated
        let draft = basedOn.map { $0.requirement.forNewGroup(timezone: timezone) } ?? TimingEditorViewModel.fresh(.weekend, timezone: timezone)
        _timing = State(initialValue: TimingEditorViewModel(draft: draft, api: api))
        _name = State(initialValue: basedOn.map { Self.nextName(after: $0.name) } ?? "")
        _color = State(initialValue: basedOn?.color ?? GroupColorPicker.colors.randomElement() ?? "t1")
        _copiesTiming = State(initialValue: basedOn != nil)
        let others = basedOn?.members.filter { $0.userId != me } ?? []
        _people = State(initialValue: others)
        _invitees = State(initialValue: Set(others.map(\.userId)))
    }

    /// The other members of `basedOn`, in its order.
    @State private var people: [Member]

    /// "Book club" → "Book club 2", "Book club 2" → "Book club 3".
    static func nextName(after name: String) -> String {
        let trimmed = name.trimmingCharacters(in: .whitespaces)
        if let space = trimmed.lastIndex(of: " "), let n = Int(trimmed[trimmed.index(after: space)...]), n < 1000 {
            return "\(trimmed[..<space]) \(n + 1)"
        }
        return String("\(trimmed) 2".prefix(80))
    }

    private var steps: [Step] { people.isEmpty ? [.details, .timing] : [.details, .people, .timing] }
    private var stepIndex: Int { steps.firstIndex(of: step) ?? 0 }
    private var trimmedName: String { name.trimmingCharacters(in: .whitespacesAndNewlines) }
    private var timezone: String { app.session.user?.timezone ?? TimeZone.current.identifier }

    var body: some View {
        BottomSheet(title: title) {
            if let limit {
                GroupLimitPanel(limit: limit) { showsUpgrade = true }
            } else {
                switch step {
                case .details: details
                case .people: peopleStep
                case .timing: timingStep
                }
            }
        }
        .safeAreaInset(edge: .bottom) {
            if step == .timing && limit == nil { createBar }
        }
        .onChange(of: error) { _, now in
            // The group was made but its invites weren't sent: once that's been read, open it.
            if now == nil, let createdGroup {
                dismiss()
                onCreated(createdGroup)
            }
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

    private var title: String {
        if limit != nil { return basedOn == nil ? "New group" : "New group with these people" }
        switch step {
        case .details: return basedOn == nil ? "New group" : "New group with these people"
        case .people: return "Who\u{2019}s in?"
        case .timing: return "When are you looking for?"
        }
    }

    // MARK: Step 1

    @ViewBuilder
    private var details: some View {
        StepDots(current: stepIndex, total: steps.count)
        Text(
            basedOn.map { "Starting from \($0.name). Name it and pick a colour; you\u{2019}ll choose who to invite next." }
                ?? "Name it and pick a colour. Timing comes next."
        )
        .bodyFont(14)
        .foregroundStyle(theme.muted)
        HStack(spacing: 14) {
            GroupTile(monogram: monogram, colorToken: color, size: 56, cornerRadius: 18)
            InputField(placeholder: "e.g. Lake District crew", text: $name)
                .submitLabel(.next)
                .onSubmit(next)
        }
        VStack(alignment: .leading, spacing: 8) {
            Text("Colour").sectionLabelStyle()
            GroupColorPicker(selection: color) { color = $0 }
        }
        if !templates.isEmpty || basedOn != nil {
            VStack(alignment: .leading, spacing: 10) {
                Text(basedOn == nil ? "Start from a template" : "Timing").sectionLabelStyle()
                FlowLayout(spacing: 8) {
                    if let basedOn {
                        Chip(label: "Same as \(basedOn.name)", isOn: copiesTiming, height: 40, fontSize: 14, action: toggleCopiedTiming)
                    }
                    ForEach(templates) { template in
                        Chip(label: template.name, isOn: templateId == template.id, height: 40, fontSize: 14) { pick(template) }
                    }
                }
            }
        }
        Button(action: next) {
            LoadingLabel(title: steps[1] == .people ? "Next: people" : "Next: timing", systemImage: "arrow.right")
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
        copiesTiming = false
        timing.reset(to: template.requirementForNewGroup(timezone: timezone))
    }

    /// The other group's timing, or (tapped again) the default.
    private func toggleCopiedTiming() {
        guard let basedOn else { return }
        copiesTiming.toggle()
        templateId = nil
        timing.reset(to: copiesTiming ? basedOn.requirement.forNewGroup(timezone: timezone) : TimingEditorViewModel.fresh(.weekend, timezone: timezone))
    }

    private func next() {
        guard !trimmedName.isEmpty else { return }
        step = steps[min(stepIndex + 1, steps.count - 1)]
    }

    private func back() {
        step = steps[max(stepIndex - 1, 0)]
    }

    // MARK: People

    @ViewBuilder
    private var peopleStep: some View {
        StepDots(current: stepIndex, total: steps.count)
        Text("Everyone ticked gets an invite to \(trimmedName) to accept. Untick anyone you\u{2019}d rather leave out.")
            .bodyFont(14)
            .foregroundStyle(theme.muted)
            .fixedSize(horizontal: false, vertical: true)
        VStack(spacing: 0) {
            ForEach(people) { member in
                let isOn = invitees.contains(member.userId)
                Button {
                    if isOn { invitees.remove(member.userId) } else { invitees.insert(member.userId) }
                } label: {
                    HStack(spacing: 12) {
                        Avatar(name: member.name, imageURL: member.image, color: theme.tile(forUser: member.userId))
                        Text(member.name).bodyFont(15, .semibold).lineLimit(1)
                        Spacer(minLength: 4)
                        Image(systemName: isOn ? "checkmark.circle.fill" : "circle")
                            .font(.system(size: 20))
                            .foregroundStyle(isOn ? theme.accentInk : theme.muted)
                    }
                    .frame(minHeight: 56)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(isOn ? .isSelected : [])
                if member.userId != people.last?.userId { Rectangle().fill(theme.line).frame(height: 1) }
            }
        }
        .padding(.horizontal, 14)
        .glass(cornerRadius: 20)
        HStack(spacing: 8) {
            Button(action: back) {
                Label("Back", systemImage: "chevron.left")
            }
            .buttonStyle(.filled(.secondary, height: 48, cornerRadius: 16, fontSize: 15))
            Button(action: next) {
                LoadingLabel(title: "Next: timing", systemImage: "arrow.right")
            }
            .buttonStyle(.filled(.accent, height: 48, cornerRadius: 16, fontSize: 15))
        }
    }

    // MARK: Step 2

    @ViewBuilder
    private var timingStep: some View {
        StepDots(current: stepIndex, total: steps.count)
        Text("Fine-tune the timing for \(trimmedName). You can change it later.")
            .bodyFont(14)
            .foregroundStyle(theme.muted)
        TimingForm(model: timing)
        SaveTemplateRow(requirement: timing.draft, api: api, toast: $toast) { templates.append($0) }
    }

    private var createBar: some View {
        VStack(alignment: .leading, spacing: 10) {
            FitSummary(model: timing)
            HStack(spacing: 8) {
                Button(action: back) {
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
            // Keep the people's order from the other group.
            let userIds = people.map(\.userId).filter(invitees.contains)
            if !userIds.isEmpty {
                do {
                    _ = try await api.send(API.Groups.createInvite(groupId: group.id, userIds: userIds))
                } catch {
                    createdGroup = group
                    self.error = "\(group.name) was made, but the invites didn\u{2019}t send (\(error.userMessage)). You can invite people from Members."
                    return
                }
            }
            dismiss()
            onCreated(group)
        } catch let failure as APIError {
            switch failure {
            case .upgradeRequired:
                // A free account at its group limit.
                limit = (try? await api.send(API.Me.plan()))?.groupLimit ?? 3
            case .validation(_, let issues) where issues.contains(where: { $0.path == "name" }):
                step = .details
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

/// The group tile colours (`t1` … `t5`) as a row of swatches, the chosen one ringed.
/// Used when creating a group and in its settings.
struct GroupColorPicker: View {
    static let colors = ["t1", "t2", "t3", "t4", "t5"]

    let selection: String
    let onPick: (String) -> Void
    @Environment(\.theme) private var theme

    var body: some View {
        HStack(spacing: 10) {
            ForEach(Self.colors, id: \.self) { token in
                Button {
                    onPick(token)
                } label: {
                    Circle()
                        .fill(theme.tile(token))
                        .frame(width: 40, height: 40)
                        .overlay(Circle().strokeBorder(theme.ink, lineWidth: selection == token ? 2.5 : 0).padding(-4))
                        .frame(width: 48, height: 48)
                }
                .buttonStyle(.pressable)
                .accessibilityLabel("Colour \(token.dropFirst())")
                .accessibilityAddTraits(selection == token ? .isSelected : [])
            }
        }
    }
}
