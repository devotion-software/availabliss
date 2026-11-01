import SwiftUI

/// New group: name and tile colour. Timing starts as "A weekend" and can be changed after.
struct NewGroupSheet: View {
    let create: (_ name: String, _ color: String) async -> Bool
    @Environment(\.dismiss) private var dismiss
    @Environment(\.theme) private var theme
    @State private var name = ""
    @State private var color = "t1"
    @State private var isSaving = false

    private static let colors = ["t1", "t2", "t3", "t4", "t5"]

    var body: some View {
        BottomSheet(title: "New group") {
            HStack(spacing: 14) {
                GroupTile(monogram: monogram, colorToken: color, size: 56, cornerRadius: 18)
                InputField(placeholder: "e.g. Lake District crew", text: $name)
                    .submitLabel(.done)
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
            Text("You'll be the owner. Set the timing and invite people next.")
                .bodyFont(13)
                .foregroundStyle(theme.muted)
            Button {
                Task {
                    isSaving = true
                    defer { isSaving = false }
                    if await create(name.trimmingCharacters(in: .whitespaces), color) { dismiss() }
                }
            } label: {
                LoadingLabel(title: "Create group", isLoading: isSaving)
            }
            .buttonStyle(.primary)
            .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty || isSaving)
        }
    }

    private var monogram: String {
        let words = name.split(separator: " ").prefix(2)
        let letters = words.compactMap(\.first).map { String($0).uppercased() }.joined()
        return letters.isEmpty ? "?" : letters
    }
}
