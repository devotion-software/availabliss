import SwiftUI
import UIKit

/// A message's reactions as pills under it: each emoji with how many reacted with it (yours
/// highlighted). Tapping one reacts with it, or takes yours back.
struct ReactionPills: View {
    let reactions: [MessageReaction]
    let me: String?
    let group: Group
    let isMine: Bool
    let react: (String) -> Void
    @Environment(\.theme) private var theme

    private var mine: String? { Reactions.mine(in: reactions, me: me) }

    var body: some View {
        WrappingHStack(spacing: 4) {
            ForEach(reactions, id: \.emoji) { reaction in
                let isYours = mine == reaction.emoji
                let who = Reactions.names(reaction.userIds, me: me) { group.member($0)?.firstName ?? "Someone" }
                Button {
                    react(reaction.emoji)
                } label: {
                    HStack(spacing: 3) {
                        Text(reaction.emoji).font(.system(size: 15))
                        if reaction.userIds.count > 1 {
                            Text("\(reaction.userIds.count)")
                                .bodyFont(12, .bold, relativeTo: .caption)
                                .monospacedDigit()
                                .foregroundStyle(isYours ? theme.accentInk : theme.muted)
                        }
                    }
                    .padding(.horizontal, 8)
                    .frame(minHeight: 26)
                    .background {
                        Capsule().fill(isYours ? theme.accentSoft : theme.bg.opacity(0.88))
                    }
                    .overlay {
                        Capsule().strokeBorder(isYours ? theme.accent.opacity(0.45) : theme.line2, lineWidth: 1)
                    }
                    .contentShape(Capsule())
                }
                .buttonStyle(.pressable)
                .transition(.scale(scale: 0.6).combined(with: .opacity))
                .accessibilityLabel("\(reaction.emoji), \(who)")
                .accessibilityHint(isYours ? "Removes your reaction" : "Reacts with \(reaction.emoji)")
                .accessibilityAddTraits(isYours ? .isSelected : [])
            }
        }
        .animation(.snappy(duration: 0.22), value: reactions)
        .frame(maxWidth: 290, alignment: isMine ? .trailing : .leading)
        .padding(.horizontal, 6)
    }
}

/// Lays its children out in rows, wrapping onto the next when one is full (trailing-aligned rows
/// stay together; the frame around it does the alignment).
private struct WrappingHStack: Layout {
    var spacing: CGFloat = 4

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let rows = rows(width: proposal.width ?? .infinity, subviews: subviews)
        let width = rows.map(\.width).max() ?? 0
        let height = rows.map(\.height).reduce(0, +) + spacing * CGFloat(max(rows.count - 1, 0))
        return CGSize(width: width, height: height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var y = bounds.minY
        for row in rows(width: bounds.width, subviews: subviews) {
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

    private func rows(width: CGFloat, subviews: Subviews) -> [Row] {
        var rows: [Row] = [Row()]
        for index in subviews.indices {
            let size = subviews[index].sizeThatFits(.unspecified)
            let extra = rows[rows.count - 1].indices.isEmpty ? size.width : size.width + spacing
            if rows[rows.count - 1].width + extra > width, !rows[rows.count - 1].indices.isEmpty {
                rows.append(Row())
            }
            let added = rows[rows.count - 1].indices.isEmpty ? size.width : size.width + spacing
            rows[rows.count - 1].indices.append(index)
            rows[rows.count - 1].width += added
            rows[rows.count - 1].height = max(rows[rows.count - 1].height, size.height)
        }
        return rows.filter { !$0.indices.isEmpty }
    }
}

/// "More reactions": the quick ones and more in a grid, and a field that opens the emoji keyboard
/// for any other.
struct ReactionPickerSheet: View {
    /// Your reaction now, highlighted (picking it again takes it back).
    let current: String?
    let pick: (String) -> Void
    @Environment(\.dismiss) private var dismiss
    @Environment(\.theme) private var theme
    @State private var typed = ""

    private let columns = Array(repeating: GridItem(.flexible(), spacing: 4), count: 8)

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    HStack(spacing: 10) {
                        EmojiField(text: $typed, placeholder: "Type any emoji")
                            .frame(height: 44)
                            .padding(.horizontal, 14)
                            .background(theme.surface, in: Capsule())
                            .overlay(Capsule().strokeBorder(theme.line2, lineWidth: 1))
                            .accessibilityLabel("Any emoji")
                        if let emoji = Reactions.single(typed) {
                            Button {
                                choose(emoji)
                            } label: {
                                Text(emoji).font(.system(size: 26)).frame(width: 44, height: 44)
                            }
                            .buttonStyle(.pressable)
                            .accessibilityLabel("React with \(emoji)")
                        }
                    }
                    LazyVGrid(columns: columns, spacing: 4) {
                        ForEach(Reactions.quick + Reactions.more, id: \.self) { emoji in
                            Button {
                                choose(emoji)
                            } label: {
                                Text(emoji)
                                    .font(.system(size: 28))
                                    .frame(maxWidth: .infinity, minHeight: 44)
                                    .background {
                                        if emoji == current { Circle().fill(theme.accentSoft) }
                                    }
                            }
                            .buttonStyle(.pressable)
                            .accessibilityLabel(emoji == current ? "Remove your \(emoji) reaction" : "React with \(emoji)")
                        }
                    }
                }
                .padding(16)
            }
            .navigationTitle("React")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
            }
        }
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
    }

    private func choose(_ emoji: String) {
        pick(emoji)
        dismiss()
    }
}

/// A text field that opens straight on the emoji keyboard.
private struct EmojiField: UIViewRepresentable {
    @Binding var text: String
    var placeholder: String

    func makeUIView(context: Context) -> EmojiTextField {
        let field = EmojiTextField()
        field.placeholder = placeholder
        field.font = .preferredFont(forTextStyle: .body)
        field.adjustsFontForContentSizeCategory = true
        field.returnKeyType = .done
        field.autocorrectionType = .no
        field.delegate = context.coordinator
        field.addTarget(context.coordinator, action: #selector(Coordinator.changed(_:)), for: .editingChanged)
        return field
    }

    func updateUIView(_ field: EmojiTextField, context: Context) {
        if field.text != text { field.text = text }
    }

    func makeCoordinator() -> Coordinator { Coordinator(text: $text) }

    final class Coordinator: NSObject, UITextFieldDelegate {
        var text: Binding<String>
        init(text: Binding<String>) { self.text = text }

        @objc func changed(_ field: UITextField) {
            // Keep only the latest emoji typed.
            let value = field.text ?? ""
            let last = value.last.map(String.init) ?? ""
            if value != last { field.text = last }
            text.wrappedValue = last
        }

        func textFieldShouldReturn(_ field: UITextField) -> Bool {
            field.resignFirstResponder()
            return true
        }
    }
}

final class EmojiTextField: UITextField {
    // The emoji keyboard, when the person has it (it's on by default); otherwise their usual one.
    override var textInputMode: UITextInputMode? {
        UITextInputMode.activeInputModes.first { $0.primaryLanguage == "emoji" } ?? super.textInputMode
    }
    override var textInputContextIdentifier: String? { "" }
}

/// A heart that pops over a bubble on a double tap.
struct HeartBurst: View {
    let trigger: Int
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var visible = false

    var body: some View {
        Image(systemName: "heart.fill")
            .font(.system(size: 44))
            .foregroundStyle(.red)
            .shadow(color: .black.opacity(0.2), radius: 6, y: 2)
            .scaleEffect(visible ? 1 : 0.4)
            .opacity(visible ? 1 : 0)
            .allowsHitTesting(false)
            .accessibilityHidden(true)
            .onChange(of: trigger) {
                guard !reduceMotion else { return }
                withAnimation(.spring(response: 0.28, dampingFraction: 0.55)) { visible = true }
                withAnimation(.easeOut(duration: 0.25).delay(0.45)) { visible = false }
            }
    }
}
