import SwiftUI

/// Group timing (admins edit; members see it read-only).
struct TimingEditorView: View {
    let detail: GroupDetailModel
    let group: Group
    let onDone: () -> Void
    @Environment(AppModel.self) private var app
    @Environment(\.theme) private var theme
    @State private var model: TimingEditorViewModel

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
            }
            .padding(.horizontal, 20)
            .padding(.top, 20)
            .padding(.bottom, 16)
        }
        .safeAreaInset(edge: .bottom) { saveBar }
        .screenBackground(OrbLayout.timing)
        .task { await model.loadAuthorities(country: app.session.user?.country) }
        .task(id: model.draft) { await model.refreshPreview() }
        .errorAlert($model.error)
        .toast($model.toast)
    }

    private var header: some View {
        ScreenHeader(title: "Timing", subtitle: group.name, backLabel: "Back to calendar", onBack: onDone) {
            StatusPill(text: canEdit ? "Admins only" : "View only", tone: .indigo, systemImage: canEdit ? "shield" : "eye")
        }
    }

    private var saveBar: some View {
        HStack(spacing: 12) {
            FitSummary(model: model)
            Spacer(minLength: 0)
            if canEdit {
                Button {
                    Task {
                        if let updated = await model.save() {
                            detail.update(updated)
                            onDone()
                        }
                    }
                } label: {
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
    @State private var showsAuthorities = false

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            lengthSection
            startsSection
            extrasSection
            rangeSection
        }
        .sheet(item: $editingEnds) { target in TimingEndsSheet(model: model, target: target) }
        .sheet(isPresented: $showsAddRange) { DateRangeSheet { model.addDateRange($0) } }
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
            Toggle(isOn: $model.draft.bankHolidays) {
                ToggleLabel(title: "Bank holidays", subtitle: "Treat like a weekend day · owner's region")
            }
            .toggleStyle(.glassSwitch)
            .frame(minHeight: 60)
            Rectangle().fill(theme.line).frame(height: 1)
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
                DayPickerPill(day: $model.draft.range.from, label: "Search from")
                Image(systemName: "arrow.right").font(.system(size: 14, weight: .semibold)).foregroundStyle(theme.muted)
                DayPickerPill(day: $model.draft.range.to, label: "Search until")
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
private struct CustomLengthEditor: View {
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

/// A compact date picker styled as a `surface` pill.
struct DayPickerPill: View {
    @Binding var day: CalendarDay
    let label: String
    @Environment(\.theme) private var theme

    var body: some View {
        DatePicker(label, selection: Binding(get: { day.startDate() }, set: { day = CalendarDay($0) }), displayedComponents: .date)
            .labelsHidden()
            .datePickerStyle(.compact)
            .frame(maxWidth: .infinity, minHeight: 44)
            .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(theme.surface))
            .accessibilityLabel(label)
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

/// Pick a from/to range of days.
struct DateRangeSheet: View {
    let onAdd: (DateRange) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var from = CalendarDay.today()
    @State private var to = CalendarDay.today().adding(days: 6)

    var body: some View {
        BottomSheet(title: "Add date range") {
            HStack(spacing: 8) {
                DayPickerPill(day: $from, label: "From")
                DayPickerPill(day: $to, label: "To")
            }
            Button("Add") {
                onAdd(DateRange(from: min(from, to), to: max(from, to)))
                dismiss()
            }
            .buttonStyle(.primary)
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
