import SwiftUI

/// "Save this timing as a template": names the current timing so it can be reused for new groups.
/// The name is asked for in a sheet: inline, the field opened below the fold, behind the bottom bars,
/// so tapping the row looked like it did nothing.
/// Once the timing is saved as one of your templates, the row says so instead of offering to save it again.
struct SaveTemplateRow: View {
    let requirement: Requirement
    /// Suggested name, e.g. the group's.
    var suggestion = ""
    let api: APIClient
    /// Shown after saving ("Saved “X”. …").
    @Binding var toast: String?
    var onSaved: (RequirementTemplate) -> Void = { _ in }
    @Environment(AppModel.self) private var app
    @Environment(\.theme) private var theme
    @State private var isNaming = false
    /// Set by the sheet; the toast waits until the sheet has gone, or it shows under it.
    @State private var saved: RequirementTemplate?
    /// Your templates, to tell whether this timing is already one (nil until loaded).
    @State private var templates: [RequirementTemplate]?

    var body: some View {
        SwiftUI.Group {
            if let match = templates?.matching(requirement) {
                Label("Saved as the template \u{201C}\(match.name)\u{201D}", systemImage: "checkmark")
                    .bodyFont(14, .semibold, relativeTo: .subheadline)
                    .foregroundStyle(theme.muted)
                    .frame(minHeight: 44)
            } else {
                Button {
                    isNaming = true
                } label: {
                    Label("Save this timing as a template", systemImage: "plus")
                        .bodyFont(14, .semibold, relativeTo: .subheadline)
                        .foregroundStyle(theme.accentInk)
                        .frame(minHeight: 44)
                }
                .buttonStyle(.plain)
            }
        }
        .task(id: app.templatesVersion) {
            if let list = try? await api.send(API.Me.templates()).templates { templates = list }
        }
        .sheet(isPresented: $isNaming, onDismiss: {
            guard let saved else { return }
            self.saved = nil
            toast = "Saved \u{201C}\(saved.name)\u{201D}. Pick it next time you create a group."
            templates = (templates ?? []) + [saved]
            onSaved(saved)
        }) {
            SaveTemplateSheet(requirement: requirement, suggestion: suggestion, api: api) { saved = $0 }
        }
    }
}

private struct SaveTemplateSheet: View {
    let requirement: Requirement
    let suggestion: String
    let api: APIClient
    let onSaved: (RequirementTemplate) -> Void
    @Environment(AppModel.self) private var app
    @Environment(\.dismiss) private var dismiss
    @Environment(\.theme) private var theme
    @State private var name = ""
    @State private var isSaving = false
    @State private var error: String?
    @FocusState private var focused: Bool

    private var trimmed: String { name.trimmingCharacters(in: .whitespacesAndNewlines) }

    var body: some View {
        BottomSheet(title: "Save as a template") {
            Text("Pick it next time you create a group. Groups already using this timing aren't affected.")
                .bodyFont(13, relativeTo: .subheadline)
                .foregroundStyle(theme.muted)
                .fixedSize(horizontal: false, vertical: true)
            InputField(placeholder: "e.g. School holiday weekends", text: $name)
                .submitLabel(.done)
                .focused($focused)
                .onSubmit { Task { await save() } }
            Button {
                Task { await save() }
            } label: {
                LoadingLabel(title: "Save template", isLoading: isSaving)
            }
            .buttonStyle(.filled(.primary, height: 54, cornerRadius: 18))
            .disabled(trimmed.isEmpty || isSaving)
        }
        .onAppear {
            name = suggestion
            focused = true
        }
        .errorAlert($error)
    }

    private func save() async {
        guard !trimmed.isEmpty, !isSaving else { return }
        isSaving = true
        defer { isSaving = false }
        do {
            let saved = try await api.send(API.Me.createTemplate(name: trimmed, requirement: requirement))
            app.templatesChanged()
            onSaved(saved)
            dismiss()
        } catch {
            self.error = error.userMessage
        }
    }
}
