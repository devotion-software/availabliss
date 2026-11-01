import SwiftUI

/// "Where are you?" — region drives bank holidays and school holidays.
struct RegionView: View {
    enum Mode {
        case onboarding(onContinue: () -> Void)
        case settings
    }

    let mode: Mode
    @Environment(AppModel.self) private var app
    @Environment(\.theme) private var theme
    @State private var model: RegionViewModel
    @State private var picker: PickerKind?

    private enum PickerKind: String, Identifiable {
        case country, subdivision, authority
        var id: String { rawValue }
    }

    init(api: APIClient, mode: Mode) {
        self.mode = mode
        _model = State(initialValue: RegionViewModel(api: api))
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            header
            VStack(alignment: .leading, spacing: 6) {
                Text("Where are you?")
                    .displayFont(30, .bold)
                    .accessibilityAddTraits(.isHeader)
                Text("We use this for bank holidays and school term dates, so your groups can plan around them.")
                    .bodyFont(14)
                    .foregroundStyle(theme.muted)
            }
            fields
            Spacer(minLength: 0)
            Button {
                Task { await save() }
            } label: {
                LoadingLabel(title: isOnboarding ? "Continue" : "Save", isLoading: model.isSaving)
            }
            .buttonStyle(.filled(.primary, height: 54, cornerRadius: 18))
            .disabled(model.country == nil || model.isSaving)
        }
        .padding(.horizontal, 20)
        .padding(.top, 20)
        .padding(.bottom, 24)
        .screenBackground(OrbLayout.calendars)
        .foregroundStyle(theme.ink)
        .chromeless()
        .task { await model.load(user: app.session.user) }
        .sheet(item: $picker) { kind in sheet(for: kind) }
        .errorAlert($model.error)
    }

    private var isOnboarding: Bool {
        if case .onboarding = mode { return true }
        return false
    }

    @ViewBuilder
    private var header: some View {
        if isOnboarding {
            HStack {
                Spacer()
                StepDots(current: 0, total: 2)
            }
            .frame(height: 44)
        } else {
            ScreenHeader(title: "Region", onBack: { app.router.pop() })
        }
    }

    private var fields: some View {
        VStack(spacing: 0) {
            PickerRow(title: "Country", value: model.countryName, placeholder: model.isLoading ? "Loading…" : "Choose") { picker = .country }
            if !model.subdivisions.isEmpty {
                PickerRow(title: "Region", value: model.subdivisionName, placeholder: "Whole country") { picker = .subdivision }
                    .overlay(alignment: .top) { Rectangle().fill(theme.line).frame(height: 1) }
            }
            if !model.authorities.isEmpty {
                PickerRow(title: "School holidays", value: model.authorityName, placeholder: "Not needed") { picker = .authority }
                    .overlay(alignment: .top) { Rectangle().fill(theme.line).frame(height: 1) }
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 4)
        .glass(cornerRadius: 24)
    }

    @ViewBuilder
    private func sheet(for kind: PickerKind) -> some View {
        switch kind {
        case .country:
            SearchablePicker(title: "Country", items: model.countries.map { ($0.code, $0.name) }, selection: model.country) { code in
                Task { await model.selectCountry(code ?? "") }
            }
        case .subdivision:
            SearchablePicker(title: "Region", items: model.subdivisions.map { ($0.code, $0.name) }, selection: model.subdivision, noneLabel: "Whole country") {
                model.subdivision = $0
            }
        case .authority:
            SearchablePicker(title: "School holidays", items: model.authorities.map { ($0.id, $0.name) }, selection: model.schoolAuthority, noneLabel: "Not needed") {
                model.schoolAuthority = $0
            }
        }
    }

    private func save() async {
        guard await model.save(session: app.session) else { return }
        switch mode {
        case .onboarding(let onContinue): onContinue()
        case .settings: app.router.pop()
        }
    }
}

/// A tappable "Title … Value ›" row.
struct PickerRow: View {
    let title: String
    let value: String?
    var placeholder = "Choose"
    let action: () -> Void
    @Environment(\.theme) private var theme

    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Text(title).bodyFont(15, .medium).foregroundStyle(theme.ink)
                Spacer()
                Text(value ?? placeholder)
                    .bodyFont(14, .medium)
                    .foregroundStyle(value == nil ? theme.muted : theme.accentInk)
                    .lineLimit(1)
                Image(systemName: "chevron.right").font(.system(size: 12, weight: .semibold)).foregroundStyle(theme.muted)
            }
            .frame(minHeight: 52)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(title), \(value ?? placeholder)")
    }
}

/// Searchable single-choice list in a sheet. `nil` selection = the optional "none" row.
struct SearchablePicker: View {
    let title: String
    let items: [(id: String, name: String)]
    let selection: String?
    var noneLabel: String?
    let onSelect: (String?) -> Void
    @Environment(\.dismiss) private var dismiss
    @Environment(\.theme) private var theme
    @State private var query = ""

    var body: some View {
        NavigationStack {
            List {
                if let noneLabel, query.isEmpty {
                    row(id: nil, name: noneLabel)
                }
                ForEach(filtered, id: \.id) { item in
                    row(id: item.id, name: item.name)
                }
            }
            .scrollContentBackground(.hidden)
            .background(theme.bg)
            .searchable(text: $query, placement: .navigationBarDrawer(displayMode: .always))
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
            }
        }
        .presentationBackground(theme.bg)
    }

    private var filtered: [(id: String, name: String)] {
        query.isEmpty ? items : items.filter { $0.name.localizedCaseInsensitiveContains(query) }
    }

    private func row(id: String?, name: String) -> some View {
        Button {
            onSelect(id)
            dismiss()
        } label: {
            HStack {
                Text(name).foregroundStyle(theme.ink)
                Spacer()
                if id == selection {
                    Image(systemName: "checkmark").foregroundStyle(theme.accentInk).fontWeight(.semibold)
                }
            }
        }
        .listRowBackground(theme.surface)
        .accessibilityAddTraits(id == selection ? .isSelected : [])
    }
}

/// Onboarding progress: the current step is a longer `ink` bar.
struct StepDots: View {
    let current: Int
    let total: Int
    @Environment(\.theme) private var theme

    var body: some View {
        HStack(spacing: 6) {
            ForEach(0..<total, id: \.self) { index in
                Capsule()
                    .fill(index == current ? theme.ink : theme.line3)
                    .frame(width: index == current ? 30 : 18, height: 6)
            }
        }
        .animation(.spring(response: 0.35), value: current)
        .accessibilityElement()
        .accessibilityLabel("Step \(current + 1) of \(total)")
    }
}
