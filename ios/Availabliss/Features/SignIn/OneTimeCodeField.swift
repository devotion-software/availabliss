import SwiftUI
import UIKit

/// Six digit boxes backed by one hidden text field, so paste and SMS/email
/// one-time-code autofill work naturally.
///
/// The hidden field is a plain `UITextField`: tapping the keyboard's "From Mail / Messages"
/// suggestion over a SwiftUI `TextField` with clear text sometimes didn't insert the code.
struct OneTimeCodeField: View {
    @Binding var code: String
    let length: Int
    var isBusy = false
    @State private var isFocused = false
    @Environment(\.theme) private var theme

    var body: some View {
        ZStack {
            CodeTextField(text: $code, isFocused: $isFocused, length: length)

            HStack(spacing: 8) {
                ForEach(0..<length, id: \.self) { index in
                    digitBox(index)
                }
            }
            .allowsHitTesting(false)
            .accessibilityHidden(true)
        }
        .frame(height: 54)
        .contentShape(Rectangle())
        .onTapGesture { isFocused = true }
        .onAppear { isFocused = true }
        .opacity(isBusy ? 0.6 : 1)
    }

    private func digitBox(_ index: Int) -> some View {
        let characters = Array(code)
        let digit = index < characters.count ? String(characters[index]) : ""
        let isCurrent = isFocused && index == min(characters.count, length - 1)
        return Text(digit)
            .displayFont(22, .bold, relativeTo: .title2)
            .foregroundStyle(theme.ink)
            .frame(maxWidth: .infinity, minHeight: 54)
            .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(theme.surface))
            .overlay(
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .strokeBorder(isCurrent ? theme.accent : theme.line2, lineWidth: isCurrent ? 2 : 1)
            )
            .animation(.easeOut(duration: 0.15), value: isCurrent)
    }
}

/// Invisible number-pad `UITextField` with `.oneTimeCode` content type. Keeps only digits,
/// up to `length`, however the text arrives (typing, paste or AutoFill, which can insert
/// the code with spaces or more than once).
private struct CodeTextField: UIViewRepresentable {
    @Binding var text: String
    @Binding var isFocused: Bool
    let length: Int

    func makeUIView(context: Context) -> UITextField {
        let field = UITextField()
        field.keyboardType = .numberPad
        field.textContentType = .oneTimeCode
        field.autocorrectionType = .no
        field.spellCheckingType = .no
        field.textColor = .clear
        field.tintColor = .clear
        field.backgroundColor = .clear
        field.accessibilityLabel = "One-time code"
        field.delegate = context.coordinator
        field.addTarget(context.coordinator, action: #selector(Coordinator.editingChanged(_:)), for: .editingChanged)
        field.setContentHuggingPriority(.defaultLow, for: .horizontal)
        field.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        return field
    }

    func updateUIView(_ field: UITextField, context: Context) {
        context.coordinator.parent = self
        if field.text != text { field.text = text }
        if isFocused, !field.isFirstResponder, field.window != nil {
            DispatchQueue.main.async { field.becomeFirstResponder() }
        }
    }

    func makeCoordinator() -> Coordinator { Coordinator(parent: self) }

    final class Coordinator: NSObject, UITextFieldDelegate {
        var parent: CodeTextField

        init(parent: CodeTextField) { self.parent = parent }

        @objc func editingChanged(_ field: UITextField) {
            let digits = String((field.text ?? "").filter(\.isNumber).prefix(parent.length))
            if field.text != digits { field.text = digits }
            if parent.text != digits { parent.text = digits }
        }

        func textFieldDidBeginEditing(_ textField: UITextField) {
            if !parent.isFocused { parent.isFocused = true }
        }

        func textFieldDidEndEditing(_ textField: UITextField) {
            if parent.isFocused { parent.isFocused = false }
        }
    }
}
