import SwiftUI

/// Your saved timing templates: rename or delete. New ones are saved from New group or a group's Timing.
struct TemplatesSection: View {
    let api: APIClient
    @Binding var toast: String?
    @Binding var error: String?
    @Environment(\.theme) private var theme
    @State private var templates: [RequirementTemplate]?
    @State private var renaming: String?
    @State private var name = ""
    @State private var pendingDelete: RequirementTemplate?

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Timing templates").sectionLabelStyle().accessibilityAddTraits(.isHeader)
            if let templates {
                if templates.isEmpty {
                    Text("Save a group's timing as a template (from New group or a group's Timing) to reuse it for new groups.")
                        .bodyFont(13, relativeTo: .subheadline)
                        .foregroundStyle(theme.muted)
                        .fixedSize(horizontal: false, vertical: true)
                } else {
                    VStack(spacing: 0) {
                        ForEach(Array(templates.enumerated()), id: \.element.id) { index, template in
                            if index > 0 { Rectangle().fill(theme.line).frame(height: 1) }
                            row(template)
                        }
                    }
                }
            } else {
                ProgressView().frame(maxWidth: .infinity, minHeight: 44)
            }
        }
        .padding(16)
        .glass(cornerRadius: 24)
        .task { await load() }
        .confirmationDialog(
            "Delete this template?",
            isPresented: Binding(get: { pendingDelete != nil }, set: { if !$0 { pendingDelete = nil } }),
            titleVisibility: .visible,
            presenting: pendingDelete
        ) { template in
            Button("Delete \u{201C}\(template.name)\u{201D}", role: .destructive) { Task { await remove(template) } }
        } message: { _ in
            Text("Groups already using this timing keep it.")
        }
    }

    @ViewBuilder
    private func row(_ template: RequirementTemplate) -> some View {
        if renaming == template.id {
            VStack(alignment: .trailing, spacing: 8) {
                InputField(placeholder: "Name", text: $name, height: 46, cornerRadius: 14)
                    .submitLabel(.done)
                    .onSubmit { Task { await rename(template) } }
                HStack(spacing: 8) {
                    Button("Cancel") { renaming = nil }.buttonStyle(.textAction)
                    Button("Save") { Task { await rename(template) } }
                        .buttonStyle(.filled(.secondary, height: 40, cornerRadius: 12, fontSize: 14, fullWidth: false))
                        .disabled(name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
            .padding(.vertical, 8)
        } else {
            HStack(spacing: 4) {
                VStack(alignment: .leading, spacing: 1) {
                    Text(template.name).bodyFont(15, .semibold)
                    Text(template.requirement.templateSummary)
                        .bodyFont(12, relativeTo: .caption)
                        .foregroundStyle(theme.muted)
                        .lineLimit(2)
                }
                .accessibilityElement(children: .combine)
                Spacer(minLength: 4)
                IconButton(systemImage: "pencil", label: "Rename \(template.name)", size: 40, glass: false) {
                    name = template.name
                    renaming = template.id
                }
                IconButton(systemImage: "trash", label: "Delete \(template.name)", size: 40, glass: false) {
                    pendingDelete = template
                }
            }
            .frame(minHeight: 56)
        }
    }

    private func load() async {
        do {
            templates = try await api.send(API.Me.templates()).templates
        } catch where error.isCancellation {
        } catch {
            templates = []
        }
    }

    private func rename(_ template: RequirementTemplate) async {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        do {
            let updated = try await api.send(API.Me.renameTemplate(id: template.id, name: trimmed))
            templates = templates?.map { $0.id == template.id ? updated : $0 }
            renaming = nil
        } catch {
            self.error = error.userMessage
        }
    }

    private func remove(_ template: RequirementTemplate) async {
        do {
            try await api.send(API.Me.deleteTemplate(id: template.id))
            templates = templates?.filter { $0.id != template.id }
            toast = "Deleted \u{201C}\(template.name)\u{201D}"
        } catch {
            self.error = error.userMessage
        }
    }
}
