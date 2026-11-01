import SwiftUI

/// Six digit boxes backed by one hidden text field, so paste and SMS/email
/// one-time-code autofill work naturally.
struct OneTimeCodeField: View {
    @Binding var code: String
    let length: Int
    var isBusy = false
    @FocusState private var isFocused: Bool
    @Environment(\.theme) private var theme

    var body: some View {
        ZStack {
            TextField("", text: $code)
                .keyboardType(.numberPad)
                .textContentType(.oneTimeCode)
                .focused($isFocused)
                .foregroundStyle(.clear)
                .tint(.clear)
                .accessibilityLabel("One-time code")
                .accessibilityValue(code.isEmpty ? "Empty" : code.map(String.init).joined(separator: " "))

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
