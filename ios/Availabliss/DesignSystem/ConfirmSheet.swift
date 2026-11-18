import SwiftUI

/// One button in a `ConfirmSheet`.
struct ConfirmOption: Identifiable {
    enum Kind { case primary, secondary, destructive }

    let title: String
    var systemImage: String?
    var kind: Kind = .primary
    let action: () -> Void

    var id: String { title }

    init(_ title: String, systemImage: String? = nil, kind: Kind = .primary, action: @escaping () -> Void) {
        self.title = title
        self.systemImage = systemImage
        self.kind = kind
        self.action = action
    }
}

/// Asks to confirm something, or to choose between a few options, in a bottom sheet:
/// title, a short explanation, the options as full-width buttons (destructive ones in the
/// warning style), then Cancel. Tapping an option closes the sheet, then runs its action.
struct ConfirmSheet: View {
    let title: String
    var message: String?
    let options: [ConfirmOption]
    var cancelTitle = "Cancel"
    @Environment(\.dismiss) private var dismiss
    @Environment(\.theme) private var theme

    var body: some View {
        BottomSheet(title: title) {
            if let message, !message.isEmpty {
                Text(message)
                    .bodyFont(15)
                    .foregroundStyle(theme.muted)
                    .fixedSize(horizontal: false, vertical: true)
            }
            ForEach(options) { option in
                Button {
                    dismiss()
                    option.action()
                } label: {
                    LoadingLabel(title: option.title, systemImage: option.systemImage)
                }
                .buttonStyle(.filled(style(option.kind)))
            }
            Button(cancelTitle) { dismiss() }
                .buttonStyle(.filled(.secondary))
        }
    }

    private func style(_ kind: ConfirmOption.Kind) -> FilledButtonKind {
        switch kind {
        case .primary: .primary
        case .secondary: .secondary
        case .destructive: .warning
        }
    }
}

extension View {
    /// Shows a `ConfirmSheet` while `isPresented` is true.
    func confirmSheet(
        _ title: String,
        isPresented: Binding<Bool>,
        message: String? = nil,
        cancelTitle: String = "Cancel",
        options: @escaping () -> [ConfirmOption]
    ) -> some View {
        sheet(isPresented: isPresented) {
            ConfirmSheet(title: title, message: message, options: options(), cancelTitle: cancelTitle)
        }
    }

    /// Shows a `ConfirmSheet` for `item` while it's set.
    func confirmSheet<Item: Identifiable>(
        item: Binding<Item?>,
        title: @escaping (Item) -> String,
        message: @escaping (Item) -> String? = { _ in nil },
        cancelTitle: String = "Cancel",
        options: @escaping (Item) -> [ConfirmOption]
    ) -> some View {
        sheet(item: item) { item in
            ConfirmSheet(title: title(item), message: message(item), options: options(item), cancelTitle: cancelTitle)
        }
    }
}
