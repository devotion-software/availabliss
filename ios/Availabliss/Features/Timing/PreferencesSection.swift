import SwiftUI

/// Preferred times for a group: everyone can say when they'd rather meet ("weekends after
/// 16:00", "12 – 14 Dec"). Guidelines only: nothing is blocked; the calendar marks the times
/// that work and fit someone's preference.
struct PreferencesSection: View {
    let group: Group
    let api: APIClient
    @Environment(AppModel.self) private var app
    @Environment(\.theme) private var theme
    @State private var preferences: [GroupPreference] = []
    @State private var adding = false
    /// One of yours being changed in the same sheet.
    @State private var editing: GroupPreference?
    @State private var error: String?

    private var me: String? { app.session.user?.id }
    private var mine: [GroupPreference] { preferences.filter { $0.userId == me } }
    private var others: [GroupPreference] { preferences.filter { $0.userId != me } }

    var body: some View {
        GlassPanel(cornerRadius: 24) {
            HStack {
                Label("Preferred times", systemImage: "heart").bodyFont(16, .semibold).foregroundStyle(theme.ink)
                Spacer()
                Button {
                    adding = true
                } label: {
                    Label("Add", systemImage: "plus")
                }
                .buttonStyle(.glassPill)
            }
            Text("Say when you\u{2019}d rather meet. It never rules a date out: times that work and fit someone\u{2019}s preference are marked with a heart.")
                .bodyFont(13, relativeTo: .subheadline)
                .foregroundStyle(theme.muted)
                .fixedSize(horizontal: false, vertical: true)
            if !mine.isEmpty {
                Text("Yours").sectionLabelStyle().padding(.top, 4)
                ForEach(mine) { pref in
                    HStack(spacing: 14) {
                        // Tap it (or Change) to change it in place.
                        Button {
                            editing = pref
                        } label: {
                            Text(pref.label).bodyFont(15, .medium).foregroundStyle(theme.ink)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("Change \(pref.label)")
                        Button("Change") { editing = pref }
                            .bodyFont(13, .semibold, relativeTo: .subheadline)
                            .foregroundStyle(theme.accentInk)
                            .accessibilityHidden(true)
                        Button("Remove") { Task { await remove(pref) } }
                            .bodyFont(13, .semibold, relativeTo: .subheadline)
                            .foregroundStyle(theme.warnInk)
                            .accessibilityLabel("Remove \(pref.label)")
                    }
                    .frame(minHeight: 40)
                }
            }
            if !others.isEmpty {
                Text("Everyone else").sectionLabelStyle().padding(.top, 4)
                ForEach(others) { pref in
                    HStack {
                        Text(pref.label).bodyFont(15, .medium)
                        Spacer()
                        Text(group.member(pref.userId)?.firstName ?? "Someone").bodyFont(13, relativeTo: .subheadline).foregroundStyle(theme.muted)
                    }
                    .frame(minHeight: 40)
                }
            }
        }
        .task(id: group.id) { await load() }
        .task(id: group.id) {
            for await event in app.live.events(forGroup: group.id) where event.kind == .availabilityChanged || event.kind == .reconnected {
                await load()
            }
        }
        .sheet(isPresented: $adding) {
            AddPreferenceSheet { body in
                let made = try await api.send(API.Groups.addPreference(groupId: group.id, body))
                preferences.append(made)
            }
        }
        .sheet(item: $editing) { pref in
            AddPreferenceSheet(editing: pref) { body in
                let changed = try await api.send(API.Groups.updatePreference(groupId: group.id, id: pref.id, body))
                if let index = preferences.firstIndex(where: { $0.id == changed.id }) { preferences[index] = changed }
            }
        }
        .errorAlert($error)
    }

    private func load() async {
        if let list = try? await api.send(API.Groups.preferences(groupId: group.id)).preferences { preferences = list }
    }

    private func remove(_ pref: GroupPreference) async {
        do {
            try await api.send(API.Groups.removePreference(groupId: group.id, id: pref.id))
            preferences.removeAll { $0.id == pref.id }
        } catch {
            self.error = error.userMessage
        }
    }
}

private struct AddPreferenceSheet: View {
    /// Changing one of yours: the sheet starts filled in with it.
    var editing: GroupPreference?
    let save: (NewGroupPreference) async throws -> Void
    @Environment(\.dismiss) private var dismiss
    @Environment(\.theme) private var theme

    private enum Kind: Hashable { case weekly, dates }
    private enum Timing: Hashable { case any, after, between }

    @State private var kind: Kind = .weekly
    @State private var days: Set<Int> = [5, 6]
    @State private var timing: Timing = .any
    @State private var from = WallTime(hour: 16, minute: 0).date()
    @State private var to = WallTime(hour: 22, minute: 0).date()
    @State private var dates: DateRange?
    @State private var isSaving = false
    @State private var error: String?

    var body: some View {
        BottomSheet(title: editing == nil ? "Add a preferred time" : "Change a preferred time") {
            SegmentedControl(
                selection: $kind,
                options: [SegmentOption(value: .weekly, label: "Repeating"), SegmentOption(value: .dates, label: "Specific dates")],
                accessibilityLabel: "Kind of preference"
            )
            if kind == .weekly {
                HStack(spacing: 6) {
                    ForEach(Weekday.all, id: \.self) { day in
                        let on = days.contains(day)
                        Button {
                            if on { days.remove(day) } else { days.insert(day) }
                        } label: {
                            Text(Weekday.initials[day])
                                .bodyFont(14, .bold)
                                .foregroundStyle(on ? theme.onAccent : theme.ink)
                                .frame(maxWidth: .infinity, minHeight: 44)
                                .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(on ? theme.accent : theme.surface))
                        }
                        .buttonStyle(.pressable)
                        .accessibilityLabel(Weekday.full(day))
                        .accessibilityAddTraits(on ? .isSelected : [])
                    }
                }
                SegmentedControl(
                    selection: $timing,
                    options: [
                        SegmentOption(value: .any, label: "Any time"), SegmentOption(value: .after, label: "After"), SegmentOption(value: .between, label: "Between"),
                    ],
                    accessibilityLabel: "Time of day"
                )
                if timing != .any {
                    VStack(spacing: 0) {
                        DatePicker(timing == .after ? "After" : "From", selection: $from, displayedComponents: .hourAndMinute).frame(minHeight: 50)
                        if timing == .between {
                            Divider().overlay(theme.line)
                            DatePicker("Until", selection: $to, displayedComponents: .hourAndMinute).frame(minHeight: 50)
                        }
                    }
                    .padding(.horizontal, 14)
                    .surfaceBackground(cornerRadius: 18)
                }
            } else {
                DayRangePicker(range: $dates)
            }
            Button {
                Task { await submit() }
            } label: {
                LoadingLabel(title: editing == nil ? "Save preference" : "Save changes", isLoading: isSaving)
            }
            .buttonStyle(.filled(.primary, height: 54, cornerRadius: 18))
            .disabled(isSaving || (kind == .dates && dates == nil))
        }
        .errorAlert($error)
        .onAppear(perform: fill)
    }

    /// Starts from the preference being changed.
    private func fill() {
        guard let pref = editing else { return }
        switch pref.kind {
        case .dates:
            kind = .dates
            if let from = pref.from.flatMap(CalendarDay.init(string:)), let to = pref.to.flatMap(CalendarDay.init(string:)) {
                dates = DateRange(from: from, to: to)
            }
        case .weekly:
            kind = .weekly
            days = Set(pref.days)
            if let window = pref.window, let start = WallTime(string: window.from) {
                from = start.date()
                if window.to == "23:59" || window.to == "00:00" {
                    timing = .after
                } else {
                    timing = .between
                    if let end = WallTime(string: window.to) { to = end.date() }
                }
            } else {
                timing = .any
            }
        }
    }

    private func submit() async {
        isSaving = true
        defer { isSaving = false }
        let body: NewGroupPreference
        switch kind {
        case .dates:
            guard let dates else { return }
            body = NewGroupPreference(kind: .dates, from: dates.from.description, to: dates.to.description)
        case .weekly:
            let window: GroupPreference.Window? =
                switch timing {
                case .any: nil
                case .after: .init(from: WallTime(from).description, to: "23:59")
                case .between: .init(from: WallTime(from).description, to: WallTime(to).description)
                }
            body = NewGroupPreference(kind: .weekly, days: days.sorted(), window: window)
        }
        do {
            try await save(body)
            dismiss()
        } catch {
            self.error = error.userMessage
        }
    }
}

private extension WallTime {
    init(_ date: Date) {
        let parts = Calendar.current.dateComponents([.hour, .minute], from: date)
        self.init(hour: parts.hour ?? 0, minute: parts.minute ?? 0)
    }
}
