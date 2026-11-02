import SwiftUI

/// "Save this timing as a template": names the current timing so it can be reused for new groups.
struct SaveTemplateRow: View {
    let requirement: Requirement
    /// Suggested name, e.g. the group's.
    var suggestion = ""
    let api: APIClient
    /// Shown after saving ("Saved “X”. …").
    @Binding var toast: String?
    var onSaved: (RequirementTemplate) -> Void = { _ in }
    @Environment(\.theme) private var theme
    @State private var isNaming = false
    @State private var name = ""
    @State private var isSaving = false
    @State private var error: String?

    private var trimmed: String { name.trimmingCharacters(in: .whitespacesAndNewlines) }

    var body: some View {
        SwiftUI.Group {
            if isNaming {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Template name").sectionLabelStyle()
                    InputField(placeholder: "e.g. School holiday weekends", text: $name, height: 48, cornerRadius: 14)
                        .submitLabel(.done)
                        .onSubmit { Task { await save() } }
                    HStack(spacing: 8) {
                        Spacer()
                        Button("Cancel") { isNaming = false }
                            .buttonStyle(.textAction)
                        Button {
                            Task { await save() }
                        } label: {
                            LoadingLabel(title: "Save template", isLoading: isSaving)
                        }
                        .buttonStyle(.filled(.secondary, height: 40, cornerRadius: 12, fontSize: 14, fullWidth: false))
                        .disabled(trimmed.isEmpty || isSaving)
                    }
                }
                .padding(12)
                .surfaceBackground(cornerRadius: 18)
                .transition(.opacity)
            } else {
                Button {
                    name = suggestion
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
        .animation(.easeInOut(duration: 0.2), value: isNaming)
        .errorAlert($error)
    }

    private func save() async {
        guard !trimmed.isEmpty, !isSaving else { return }
        isSaving = true
        defer { isSaving = false }
        do {
            let saved = try await api.send(API.Me.createTemplate(name: trimmed, requirement: requirement))
            toast = "Saved \u{201C}\(saved.name)\u{201D}. Pick it next time you create a group."
            isNaming = false
            onSaved(saved)
        } catch {
            self.error = error.userMessage
        }
    }
}
