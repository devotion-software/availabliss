import SwiftUI

/// The sign-up wizard: 1) about you (name + region), 2) calendars, 3) your first group,
/// 4) invite people. Someone who arrived from an invite stops after calendars (they're joining
/// a group already). Finishing sets `onboarded: true`.
struct OnboardingView: View {
    @Environment(AppModel.self) private var app
    @State private var step = 0
    @State private var error: String?
    /// "No calendar? Mark busy times yourself": land on My time at the end.
    @State private var opensMyTime = false
    @State private var createdGroup: Group?

    private var joiningFromInvite: Bool { app.router.pendingInvite != nil }
    /// Two steps when joining from an invite. One opened once you're past calendars (making your own
    /// group) doesn't shorten it: you finish your group, then the invite opens.
    private var stepCount: Int { joiningFromInvite && step < 2 ? 2 : 4 }

    var body: some View {
        ZStack {
            switch step {
            case 0:
                RegionView(api: app.api, mode: .onboarding { go(to: 1) })
                    .transition(transition)
            case 1:
                CalendarsView(
                    api: app.api,
                    mode: .onboarding(
                        onBack: { go(to: 0) },
                        onContinue: { calendarsDone(markByHand: false) },
                        onSkip: { calendarsDone(markByHand: true) }
                    )
                )
                .transition(transition)
            case 2:
                FirstGroupStep(api: app.api, timezone: app.session.user?.timezone ?? TimeZone.current.identifier) { group in
                    createdGroup = group
                    go(to: 3)
                } onSkip: {
                    finish()
                } onBack: {
                    go(to: 1)
                }
                .transition(transition)
            default:
                if let createdGroup {
                    InvitePeopleStep(group: createdGroup, api: app.api) { finish() }
                        .transition(transition)
                }
            }
        }
        .environment(\.onboardingSteps, stepCount)
        .animation(.spring(response: 0.4, dampingFraction: 0.9), value: step)
        .errorAlert($error)
    }

    private var transition: AnyTransition {
        .asymmetric(insertion: .move(edge: .trailing).combined(with: .opacity), removal: .move(edge: .leading).combined(with: .opacity))
    }

    private func go(to next: Int) { withAnimation { step = next } }

    private func calendarsDone(markByHand: Bool) {
        opensMyTime = markByHand
        if !markByHand { Task { await app.deviceCalendar.syncIfAuthorized() } }
        if joiningFromInvite { finish() } else { go(to: 2) }
    }

    private func finish() {
        Task {
            do {
                try await app.session.update(UserPatch(onboarded: true))
                if let createdGroup {
                    app.router.groupsPath = [.group(id: createdGroup.id)]
                } else if opensMyTime {
                    app.router.tab = .myTime
                }
            } catch {
                self.error = error.userMessage
            }
        }
    }
}

/// Step 3: name your first group and say what you're planning. Skippable.
private struct FirstGroupStep: View {
    let api: APIClient
    let timezone: String
    let onCreated: (Group) -> Void
    let onSkip: () -> Void
    let onBack: () -> Void
    @Environment(\.theme) private var theme
    @Environment(\.onboardingSteps) private var steps
    @State private var name = ""
    /// Just the length is chosen here; the rest of the timing keeps its defaults until later.
    @State private var timing: TimingEditorViewModel
    @State private var color = ["t1", "t2", "t3", "t4", "t5"].randomElement() ?? "t1"
    @State private var isSaving = false
    @State private var error: String?

    init(api: APIClient, timezone: String, onCreated: @escaping (Group) -> Void, onSkip: @escaping () -> Void, onBack: @escaping () -> Void) {
        self.api = api
        self.timezone = timezone
        self.onCreated = onCreated
        self.onSkip = onSkip
        self.onBack = onBack
        _timing = State(initialValue: TimingEditorViewModel(draft: TimingEditorViewModel.fresh(.weekend, timezone: timezone), api: api))
    }

    private var trimmed: String { name.trimmingCharacters(in: .whitespacesAndNewlines) }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                HStack {
                    IconButton(systemImage: "chevron.left", label: "Back", action: onBack)
                    Spacer()
                    StepDots(current: 2, total: steps)
                }
                VStack(alignment: .leading, spacing: 6) {
                    Text("Start your first group").displayFont(30, .bold).accessibilityAddTraits(.isHeader)
                    Text("Name the event you’re planning. You can change everything later.")
                        .bodyFont(14).foregroundStyle(theme.muted)
                }
                HStack(spacing: 14) {
                    GroupTile(monogram: monogram, colorToken: color, size: 56, cornerRadius: 18)
                    InputField(placeholder: "e.g. Lake District weekend", text: $name)
                        .textInputAutocapitalization(.words)
                        .submitLabel(.done)
                }
                VStack(alignment: .leading, spacing: 10) {
                    Text("What are you planning?").sectionLabelStyle()
                    FlowLayout(spacing: 8) {
                        ForEach(TimingPreset.allCases) { option in
                            Chip(
                                label: option == .custom ? "More options" : option.label,
                                isOn: timing.preset == option,
                                height: 40,
                                fontSize: 14
                            ) { timing.choose(option) }
                        }
                    }
                    if timing.preset == .custom {
                        CustomLengthEditor(model: timing)
                            .transition(.opacity.combined(with: .move(edge: .top)))
                    }
                }
                .animation(.spring(response: 0.35, dampingFraction: 0.85), value: timing.preset)
            }
            .padding(.horizontal, 20)
            .padding(.top, 20)
        }
        .safeAreaInset(edge: .bottom) {
            VStack(spacing: 4) {
                Button {
                    Task { await create() }
                } label: {
                    LoadingLabel(title: "Create group", isLoading: isSaving)
                }
                .buttonStyle(.filled(.primary, height: 54, cornerRadius: 18))
                .disabled(trimmed.isEmpty || isSaving)
                Button("Skip for now", action: onSkip)
                    .bodyFont(14, .semibold)
                    .foregroundStyle(theme.ink)
                    .frame(maxWidth: .infinity, minHeight: 44)
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 12)
        }
        .screenBackground(OrbLayout.groups)
        .foregroundStyle(theme.ink)
        .errorAlert($error)
    }

    private var monogram: String {
        let letters = trimmed.split(separator: " ").prefix(2).compactMap(\.first).map { String($0).uppercased() }.joined()
        return letters.isEmpty ? "?" : letters
    }

    private func create() async {
        guard !trimmed.isEmpty, !isSaving else { return }
        isSaving = true
        defer { isSaving = false }
        do {
            onCreated(try await api.send(API.Groups.create(CreateGroupBody(name: trimmed, color: color, requirement: timing.draft))))
        } catch {
            self.error = error.userMessage
        }
    }
}

/// Step 4: invite people to the new group, by link, email or username.
private struct InvitePeopleStep: View {
    let group: Group
    let api: APIClient
    let onDone: () -> Void
    @Environment(\.theme) private var theme
    @Environment(\.onboardingSteps) private var steps
    @State private var model: MembersViewModel

    init(group: Group, api: APIClient, onDone: @escaping () -> Void) {
        self.group = group
        self.api = api
        self.onDone = onDone
        _model = State(initialValue: MembersViewModel(groupId: group.id, api: api))
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                HStack {
                    Spacer()
                    StepDots(current: 3, total: steps)
                }
                .frame(height: 44)
                VStack(alignment: .leading, spacing: 6) {
                    Text("Invite people to \(group.name)").displayFont(30, .bold).accessibilityAddTraits(.isHeader)
                    Text("Once they\u{2019}ve added their busy times, you\u{2019}ll see when everyone\u{2019}s free.")
                        .bodyFont(14).foregroundStyle(theme.muted)
                }
                GlassPanel(cornerRadius: 24) {
                    InviteLinkSection(model: model, groupName: group.name)
                }
                GlassPanel(cornerRadius: 24) {
                    Text("Or invite by email or username").bodyFont(15, .semibold)
                    Text("Each person gets their own invite; only they can use it.")
                        .bodyFont(13, relativeTo: .subheadline).foregroundStyle(theme.muted)
                    InviteRecipientsField(draft: model.draft)
                    Button {
                        Task { await model.sendInvites() }
                    } label: {
                        LoadingLabel(title: "Send invites", systemImage: "paperplane", isLoading: model.isSendingInvites)
                    }
                    .buttonStyle(.filled(.secondary, height: 44, cornerRadius: 14, fontSize: 14))
                    .disabled(model.draft.isEmpty || model.draft.isAdding || model.isSendingInvites)
                    if !model.personalInvites.isEmpty {
                        Text("Invited: \(model.personalInvites.compactMap(\.recipient).joined(separator: ", "))")
                            .bodyFont(12, relativeTo: .caption).foregroundStyle(theme.muted)
                    }
                }
            }
            .padding(.horizontal, 20)
            .padding(.top, 20)
        }
        .safeAreaInset(edge: .bottom) {
            Button(model.invites.isEmpty ? "Skip for now" : "Done", action: onDone)
                .buttonStyle(.filled(model.invites.isEmpty ? .secondary : .primary, height: 54, cornerRadius: 18))
                .padding(.horizontal, 20)
                .padding(.bottom, 12)
        }
        .screenBackground(OrbLayout.groups)
        .foregroundStyle(theme.ink)
        .task { await model.load() }
        .errorAlert($model.error)
        .toast($model.toast)
    }
}
