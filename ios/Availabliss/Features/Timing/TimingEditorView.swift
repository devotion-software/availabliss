import SwiftUI

/// Group timing (admins edit; members see it read-only).
struct TimingEditorView: View {
    let detail: GroupDetailModel
    let group: Group
    let onDone: () -> Void
    @Environment(AppModel.self) private var app
    @Environment(\.theme) private var theme
    @State private var model: TimingEditorViewModel
    /// Back with unsaved changes: asks whether to save or discard them.
    @State private var confirmsLeave = false
    /// What was picked in that sheet, done once it has closed (so a save error can show its alert).
    @State private var leaveChoice: UnsavedTimingSheet.Choice?

    init(detail: GroupDetailModel, group: Group, onDone: @escaping () -> Void) {
        self.detail = detail
        self.group = group
        self.onDone = onDone
        _model = State(initialValue: TimingEditorViewModel(group: group, api: detail.api))
    }

    private var canEdit: Bool { group.role.isAdmin }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                header
                TimingForm(model: model)
                    .disabled(!canEdit)
                WhoNeedsToBeFreeSection(model: model, members: group.members)
                    .disabled(!canEdit)
                // Anyone can keep a copy of a group's timing to reuse, admin or not.
                SaveTemplateRow(requirement: model.draft, suggestion: group.name, api: detail.api, toast: $model.toast)
                // Everyone's preferred times (anyone can add their own).
                PreferencesSection(group: group, api: detail.api)
            }
            .padding(.horizontal, 20)
            .padding(.top, 20)
            .padding(.bottom, 16)
        }
        .tracksScrollUnderTopBar()
        .safeAreaInset(edge: .bottom) { saveBar }
        .screenBackground(OrbLayout.timing)
        .task { await model.loadAuthorities(country: app.session.user?.country) }
        .task(id: model.draft) { await model.refreshPreview() }
        .errorAlert($model.error)
        .toast($model.toast)
        .onAppear {
            LeaveGuard.shouldLeave = {
                guard canEdit, model.hasChanges else { return true }
                if !model.isSaving { confirmsLeave = true }
                return false
            }
        }
        .onDisappear { LeaveGuard.shouldLeave = nil }
        .sheet(isPresented: $confirmsLeave, onDismiss: finishLeaving) {
            UnsavedTimingSheet { choice in
                leaveChoice = choice
                confirmsLeave = false
            }
        }
    }

    private func finishLeaving() {
        guard let choice = leaveChoice else { return }
        leaveChoice = nil
        switch choice {
        case .save: save()
        case .discard: onDone()
        }
    }

    private func save() {
        Task {
            if let updated = await model.save() {
                detail.update(updated)
                onDone()
            }
        }
    }

    private var header: some View {
        HStack {
            Text("Timing").displayFont(19, .semibold).accessibilityAddTraits(.isHeader)
            Spacer()
            StatusPill(text: canEdit ? "Admins only" : "View only", tone: .indigo, systemImage: canEdit ? "shield" : "eye")
        }
    }

    private var saveBar: some View {
        HStack(spacing: 12) {
            FitSummary(model: model)
            Spacer(minLength: 0)
            if canEdit {
                Button(action: save) {
                    LoadingLabel(title: "Save timing", isLoading: model.isSaving)
                }
                .buttonStyle(.filled(.primary, height: 48, cornerRadius: 16, fontSize: 15, fullWidth: false))
                .disabled(!model.hasChanges || model.isSaving)
            }
        }
        .padding(.leading, 16)
        .padding([.trailing, .vertical], 12)
        .glass(cornerRadius: 26)
        .padding(.horizontal, 12)
        .padding(.bottom, 8)
    }
}

/// "Who needs to be free": everyone, or at least N of the required members, plus members who are optional
/// (never stop a time from working). Saved as the requirement's `quorum` and `optionalMemberIds`.
struct WhoNeedsToBeFreeSection: View {
    @Bindable var model: TimingEditorViewModel
    let members: [Member]
    @Environment(\.theme) private var theme

    private var required: Int { model.requiredCount(of: members) }

    var body: some View {
        GlassPanel {
            Text("Who needs to be free").sectionLabelStyle()
            SegmentedControl(
                selection: Binding(get: { model.usesQuorum }, set: { model.setUsesQuorum($0, members: members) }),
                options: [SegmentOption(value: false, label: "Everyone"), SegmentOption(value: true, label: "Most of us")],
                accessibilityLabel: "Who needs to be free"
            )
            if let quorum = model.draft.quorum {
                Stepper(value: Binding(get: { quorum }, set: { model.setQuorum($0, members: members) }), in: 1...max(1, required)) {
                    Text("At least \(quorum) of \(required)").bodyFont(15, .semibold)
                }
                .transition(.opacity.combined(with: .move(edge: .top)))
            }
            Text(
                model.usesQuorum
                    ? "A time works when enough of you are free. We'll show who's missing."
                    : "A time works only when everyone who isn't optional is free."
            )
            .bodyFont(12, relativeTo: .caption)
            .foregroundStyle(theme.muted)
            .fixedSize(horizontal: false, vertical: true)
            if !members.isEmpty {
                VStack(spacing: 0) {
                    ForEach(Array(members.enumerated()), id: \.element.id) { index, member in
                        if index > 0 { Rectangle().fill(theme.line).frame(height: 1) }
                        Toggle(isOn: Binding(get: { model.isOptional(member) }, set: { model.setOptional($0, for: member, members: members) })) {
                            HStack(spacing: 10) {
                                Avatar(name: member.name, imageURL: member.image, color: theme.tile(forUser: member.userId), size: 32)
                                ToggleLabel(title: member.name, subtitle: model.isOptional(member) ? "Optional · never stops a time" : "Needs to be free")
                            }
                        }
                        .toggleStyle(.glassSwitch)
                        .frame(minHeight: 56)
                        .accessibilityLabel("\(member.name) is optional")
                    }
                }
            }
        }
        .animation(.spring(response: 0.35, dampingFraction: 0.85), value: model.usesQuorum)
    }
}

/// Asked on going back from Timing with unsaved changes. Closing it keeps editing.
struct UnsavedTimingSheet: View {
    enum Choice { case save, discard }

    let onChoose: (Choice) -> Void
    @Environment(\.theme) private var theme

    var body: some View {
        BottomSheet(title: "Save your changes?") {
            Text("You've changed the timing but haven't saved it yet.")
                .bodyFont(15)
                .foregroundStyle(theme.muted)
                .fixedSize(horizontal: false, vertical: true)
            Button("Save timing") { onChoose(.save) }.buttonStyle(.primary)
            Button("Discard changes") { onChoose(.discard) }.buttonStyle(.secondary)
        }
    }
}

/// "13 weekends" / "fit this timing between 2 Nov and 1 Feb · …", from the model's live preview.
struct FitSummary: View {
    let model: TimingEditorViewModel
    @Environment(\.theme) private var theme

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(model.previewTitle)
                .displayFont(20, .bold)
                .contentTransition(.numericText())
                .opacity(model.isPreviewing ? 0.6 : 1)
                .animation(.easeOut(duration: 0.2), value: model.previewTitle)
            Text(model.previewSubtitle)
                .bodyFont(12, relativeTo: .caption)
                .foregroundStyle(theme.muted)
                .lineLimit(2)
        }
        .accessibilityElement(children: .combine)
    }
}

/// The timing editor's sections: length, starts/ends and days, holidays, search range and dates.
/// Used by a group's Timing screen and by New group (step 2).
struct TimingForm: View {
    @Bindable var model: TimingEditorViewModel
    @Environment(\.theme) private var theme
    @State private var editingEnds: TimingEndsSheet.Target?
    @State private var showsAddRange = false
    @State private var showsSearchRange = false
    @State private var showsAuthorities = false

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            lengthSection
            startsSection
            extrasSection
            rangeSection
        }
        .sheet(item: $editingEnds) { target in TimingEndsSheet(model: model, target: target) }
        .sheet(isPresented: $showsAddRange) { DateRangeSheet(existing: model.draft.dates) { model.addDateRange($0) } }
        .sheet(isPresented: $showsSearchRange) {
            SearchRangeSheet(range: model.draft.range) { model.draft.range = $0 }
        }
        .sheet(isPresented: $showsAuthorities) {
            SearchablePicker(title: "School holidays", items: model.authorities.map { ($0.id, $0.name) }, selection: model.draft.schoolAuthority) {
                model.draft.schoolAuthority = $0
            }
        }
    }

    private var lengthSection: some View {
        GlassPanel {
            Text("How long?").sectionLabelStyle()
            FlowLayout(spacing: 8) {
                ForEach(TimingPreset.allCases) { preset in
                    Chip(label: preset.label, isOn: model.preset == preset, height: 40, fontSize: 14) { model.choose(preset) }
                }
            }
            if model.preset == .custom {
                CustomLengthEditor(model: model)
                    .transition(.opacity.combined(with: .move(edge: .top)))
            }
        }
        .animation(.spring(response: 0.35, dampingFraction: 0.85), value: model.preset)
    }

    private var startsSection: some View {
        GlassPanel {
            Text("Starts and ends").sectionLabelStyle()
            HStack(spacing: 8) {
                FieldTile(label: "Starts", value: model.startsLabel) { editingEnds = .start }
                FieldTile(label: "Ends", value: model.endsLabel) { editingEnds = .end }
            }
            HStack(spacing: 6) {
                ForEach(Weekday.all, id: \.self) { day in
                    WeekdayToggle(day: day, isOn: model.isDayOn(day)) { model.toggleDay(day) }
                }
            }
            .disabled(model.isSpan)
            .opacity(model.isSpan ? 0.7 : 1)
            .accessibilityElement(children: .contain)
            .accessibilityLabel("Days it can fall on")
            if model.isSpan {
                Text("Set by the start and end days.").bodyFont(12, relativeTo: .caption).foregroundStyle(theme.muted)
            }
        }
    }

    private var extrasSection: some View {
        VStack(spacing: 0) {
            // With every day allowed, bank holidays are already included.
            if !model.allowsEveryDay {
                Toggle(isOn: $model.draft.bankHolidays) {
                    ToggleLabel(title: "Bank holidays", subtitle: "Treat like a weekend day · owner's region")
                }
                .toggleStyle(.glassSwitch)
                .frame(minHeight: 60)
                Rectangle().fill(theme.line).frame(height: 1)
            }
            Toggle(isOn: $model.draft.schoolHolidaysOnly) {
                ToggleLabel(title: "School holidays only", subtitle: model.authorityName.map { "\($0) term dates" } ?? "Choose whose term dates")
            }
            .toggleStyle(.glassSwitch)
            .frame(minHeight: 60)
            if model.draft.schoolHolidaysOnly {
                Button(model.authorityName == nil ? "Choose school authority" : "Change school authority") { showsAuthorities = true }
                    .buttonStyle(.textAction)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .disabled(model.authorities.isEmpty)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 4)
        .glass(cornerRadius: 24)
    }

    private var rangeSection: some View {
        GlassPanel {
            Text("Search between").sectionLabelStyle()
            HStack(spacing: 8) {
                FieldTile(label: "From", value: Formatting.dayMonthYear(model.draft.range.from.startDate())) { showsSearchRange = true }
                Image(systemName: "arrow.right").font(.system(size: 14, weight: .semibold)).foregroundStyle(theme.muted)
                    .accessibilityHidden(true)
                FieldTile(label: "Until", value: Formatting.dayMonthYear(model.draft.range.to.startDate())) { showsSearchRange = true }
            }
            Text("Or only these dates").sectionLabelStyle().padding(.top, 4)
            FlowLayout(spacing: 8) {
                ForEach(model.draft.dates, id: \.self) { range in
                    DateRangeChip(range: range) { model.removeDateRange(range) }
                }
                Button("+ Add date range") { showsAddRange = true }
                    .bodyFont(13, .semibold, relativeTo: .subheadline)
                    .foregroundStyle(theme.ink)
                    .padding(.horizontal, 12)
                    .frame(minHeight: 36)
                    .overlay(Capsule().strokeBorder(theme.line3, style: StrokeStyle(lineWidth: 1, dash: [4, 3])))
                    .frame(minHeight: 44)
            }
        }
    }
}

private struct ToggleLabel: View {
    let title: String
    let subtitle: String
    @Environment(\.theme) private var theme

    var body: some View {
        VStack(alignment: .leading, spacing: 1) {
            Text(title).bodyFont(15, .semibold)
            Text(subtitle).bodyFont(12, relativeTo: .caption).foregroundStyle(theme.muted)
        }
    }
}

private struct WeekdayToggle: View {
    let day: Int
    let isOn: Bool
    let action: () -> Void
    @Environment(\.theme) private var theme

    var body: some View {
        Button(action: action) {
            Text(Weekday.initials[day])
                .bodyFont(14, .bold)
                .foregroundStyle(isOn ? theme.onAccent : theme.ink)
                .frame(maxWidth: .infinity, minHeight: 44)
                .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(isOn ? theme.accent : theme.surface))
                .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).strokeBorder(isOn ? theme.accent : theme.glassBorder, lineWidth: 1))
        }
        .buttonStyle(.pressable)
        .accessibilityLabel(Weekday.full(day))
        .accessibilityAddTraits(isOn ? .isSelected : [])
    }
}

/// Steppers for a custom length.
/// Hours / days / day-to-day, for a length none of the presets cover. Also used by the sign-up wizard.
struct CustomLengthEditor: View {
    @Bindable var model: TimingEditorViewModel
    @Environment(\.theme) private var theme

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            SegmentedControl(
                selection: Binding(get: { model.customKind }, set: { model.chooseCustom($0) }),
                options: CustomLengthKind.allCases.map { SegmentOption(value: $0, label: $0.label) },
                accessibilityLabel: "Custom length"
            )
            switch model.customKind {
            case .hours:
                Stepper(value: $model.durationMinutes, in: 30...(24 * 60), step: 30) {
                    Text(model.draft.lengthDescription).bodyFont(15, .semibold)
                }
            case .days:
                Stepper(value: $model.dayCount, in: 1...31) {
                    Text("\(Formatting.plural(model.dayCount, "day")) in a row").bodyFont(15, .semibold)
                }
            case .span:
                Toggle(isOn: Binding(get: { model.span.weeksLater == 1 }, set: { model.span.weeksLater = $0 ? 1 : 0 })) {
                    ToggleLabel(title: "Ends the following week", subtitle: "e.g. Tue \u{2192} following Thu")
                }
                .toggleStyle(.glassSwitch)
            }
        }
        .padding(12)
        .surfaceBackground(cornerRadius: 16)
    }
}

/// Edits the start or end: weekday + time for spans, the daily window otherwise.
struct TimingEndsSheet: View {
    enum Target: String, Identifiable {
        case start, end
        var id: String { rawValue }
    }

    @Bindable var model: TimingEditorViewModel
    let target: Target
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        BottomSheet(title: target == .start ? "Starts" : "Ends") {
            if model.isSpan {
                Picker("Day", selection: dayBinding) {
                    ForEach(Weekday.all, id: \.self) { Text(Weekday.full($0)).tag($0) }
                }
                .pickerStyle(.wheel)
                .frame(height: 140)
                DatePicker("Time", selection: timeBinding, displayedComponents: .hourAndMinute)
                    .bodyFont(15, .medium)
            } else {
                Toggle(isOn: $model.hasWindow) {
                    Text("Only at certain times of day").bodyFont(15, .medium)
                }
                .toggleStyle(.glassSwitch)
                if model.hasWindow {
                    DatePicker(target == .start ? "From" : "Until", selection: timeBinding, displayedComponents: .hourAndMinute)
                        .bodyFont(15, .medium)
                }
            }
            Button("Done") { dismiss() }.buttonStyle(.primary)
        }
    }

    private var dayBinding: Binding<Int> {
        Binding(
            get: { target == .start ? model.span.startDay : model.span.endDay },
            set: { value in
                var span = model.span
                if target == .start { span.startDay = value } else { span.endDay = value }
                model.span = span
            }
        )
    }

    private var timeBinding: Binding<Date> {
        Binding(
            get: {
                if model.isSpan { return (target == .start ? model.span.startTime : model.span.endTime).date() }
                let window = model.draft.window ?? TimingPreset.defaultDurationWindow
                return (target == .start ? window.from : window.to).date()
            },
            set: { date in
                let time = WallTime(date)
                if model.isSpan {
                    var span = model.span
                    if target == .start { span.startTime = time } else { span.endTime = time }
                    model.span = span
                } else {
                    var window = model.draft.window ?? TimingPreset.defaultDurationWindow
                    if target == .start { window.from = time } else { window.to = time }
                    model.draft.window = window
                }
            }
        )
    }
}

private struct DateRangeChip: View {
    let range: DateRange
    let remove: () -> Void
    @Environment(\.theme) private var theme

    var body: some View {
        let text = "\(Formatting.dayMonth(range.from)) – \(Formatting.dayMonth(range.to))"
        HStack(spacing: 4) {
            Text(text)
            Button(action: remove) {
                Image(systemName: "xmark").font(.system(size: 11, weight: .bold)).frame(width: 28, height: 28)
            }
            .accessibilityLabel("Remove \(text)")
        }
        .bodyFont(13, .semibold, relativeTo: .subheadline)
        .foregroundStyle(theme.accentInk)
        .padding(.leading, 12)
        .padding(.trailing, 4)
        .frame(minHeight: 36)
        .background(Capsule().fill(theme.accentSoft))
        .frame(minHeight: 44)
    }
}

/// Pick a range of days on one calendar. A range the list already covers can't be added; one that overlaps is joined on.
struct DateRangeSheet: View {
    var existing: [DateRange] = []
    let onAdd: (DateRange) -> Void
    @Environment(\.dismiss) private var dismiss
    @Environment(\.theme) private var theme
    @State private var picked: DateRange?

    private var covering: DateRange? { picked.flatMap { TimingEditorViewModel.covering($0, in: existing) } }
    private var joining: [DateRange] {
        guard let range = picked else { return [] }
        return existing.filter { $0.from <= range.to.adding(days: 1) && range.from <= $0.to.adding(days: 1) }
    }

    var body: some View {
        BottomSheet(title: "Add date range") {
            DayRangePicker(range: $picked)
            if let note {
                Text(note)
                    .bodyFont(13, relativeTo: .subheadline)
                    .foregroundStyle(theme.muted)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            Button("Add") {
                guard let picked else { return }
                onAdd(picked)
                dismiss()
            }
            .buttonStyle(.primary)
            .disabled(picked == nil || covering != nil)
        }
    }

    private var note: String? {
        if let covering {
            return covering == picked ? "You\u{2019}ve already added these dates." : "Already included in \(Self.label(covering))."
        }
        guard let first = joining.first else { return nil }
        return joining.count == 1
            ? "Joins up with \(Self.label(first)), so they\u{2019}ll become one range."
            : "Joins up with \(joining.count) of your ranges, so they\u{2019}ll become one."
    }

    private static func label(_ range: DateRange) -> String {
        "\(Formatting.dayMonth(range.from)) \u{2013} \(Formatting.dayMonth(range.to))"
    }
}

/// Pick the days to search between, on one calendar.
struct SearchRangeSheet: View {
    let onDone: (DateRange) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var picked: DateRange?

    init(range: DateRange, onDone: @escaping (DateRange) -> Void) {
        self.onDone = onDone
        _picked = State(initialValue: range)
    }

    var body: some View {
        BottomSheet(title: "Search between") {
            DayRangePicker(range: $picked)
            Button("Done") {
                if let picked { onDone(picked) }
                dismiss()
            }
            .buttonStyle(.primary)
            .disabled(picked == nil)
        }
    }
}

/// Wraps children onto multiple lines (chips).
struct FlowLayout: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let rows = arrange(width: proposal.width ?? .infinity, subviews: subviews)
        let height = rows.reduce(0) { $0 + $1.height } + spacing * CGFloat(max(rows.count - 1, 0))
        let width = rows.map(\.width).max() ?? 0
        return CGSize(width: proposal.width ?? width, height: height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var y = bounds.minY
        for row in arrange(width: bounds.width, subviews: subviews) {
            var x = bounds.minX
            for index in row.indices {
                let size = subviews[index].sizeThatFits(.unspecified)
                subviews[index].place(at: CGPoint(x: x, y: y + (row.height - size.height) / 2), proposal: ProposedViewSize(size))
                x += size.width + spacing
            }
            y += row.height + spacing
        }
    }

    private struct Row {
        var indices: [Int] = []
        var width: CGFloat = 0
        var height: CGFloat = 0
    }

    private func arrange(width: CGFloat, subviews: Subviews) -> [Row] {
        var rows: [Row] = [Row()]
        for index in subviews.indices {
            let size = subviews[index].sizeThatFits(.unspecified)
            let needed = rows[rows.count - 1].indices.isEmpty ? size.width : rows[rows.count - 1].width + spacing + size.width
            if needed > width, !rows[rows.count - 1].indices.isEmpty {
                rows.append(Row())
            }
            var row = rows[rows.count - 1]
            row.width = row.indices.isEmpty ? size.width : row.width + spacing + size.width
            row.height = max(row.height, size.height)
            row.indices.append(index)
            rows[rows.count - 1] = row
        }
        return rows
    }
}
