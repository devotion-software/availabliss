import SwiftUI

/// Screen header: glass back button, display title + muted subtitle, optional trailing content.
struct ScreenHeader<Trailing: View>: View {
    let title: String
    var subtitle: String?
    var titleSize: CGFloat = 24
    var backLabel = "Back"
    var onBack: (() -> Void)?
    /// Makes the title a link (shown with a chevron after it).
    var onTitle: (() -> Void)?
    @ViewBuilder var trailing: Trailing
    @Environment(\.theme) private var theme

    var body: some View {
        HStack(spacing: 10) {
            if let onBack {
                IconButton(systemImage: "chevron.left", label: backLabel, action: onBack)
            }
            VStack(alignment: .leading, spacing: 0) {
                if let onTitle {
                    Button(action: onTitle) {
                        HStack(spacing: 4) {
                            Text(title).displayFont(titleSize, .bold).lineLimit(2).multilineTextAlignment(.leading)
                            Image(systemName: "chevron.right")
                                .font(.system(size: titleSize * 0.55, weight: .bold))
                                .foregroundStyle(theme.muted)
                        }
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityAddTraits(.isHeader)
                    .accessibilityHint("Opens the group")
                } else {
                    Text(title)
                        .displayFont(titleSize, .bold)
                        .lineLimit(2)
                        .accessibilityAddTraits(.isHeader)
                }
                if let subtitle {
                    SubtitleText(subtitle)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            trailing
        }
    }
}

extension ScreenHeader where Trailing == EmptyView {
    init(title: String, subtitle: String? = nil, titleSize: CGFloat = 24, backLabel: String = "Back", onBack: (() -> Void)? = nil, onTitle: (() -> Void)? = nil) {
        self.init(title: title, subtitle: subtitle, titleSize: titleSize, backLabel: backLabel, onBack: onBack, onTitle: onTitle) { EmptyView() }
    }
}

private struct SubtitleText: View {
    let text: String
    @Environment(\.theme) private var theme
    init(_ text: String) { self.text = text }
    var body: some View {
        Text(text).bodyFont(12, relativeTo: .caption).foregroundStyle(theme.muted).lineLimit(1)
    }
}

/// Standard sheet chrome: display title + close button over the themed background.
/// Sizes itself to its content.
struct BottomSheet<Content: View>: View {
    let title: String
    @ViewBuilder var content: Content
    @Environment(\.dismiss) private var dismiss
    @Environment(\.theme) private var theme
    @State private var height: CGFloat = 420

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                HStack {
                    Text(title)
                        .displayFont(22, .bold)
                        .accessibilityAddTraits(.isHeader)
                    Spacer()
                    CloseButton { dismiss() }
                }
                content
            }
            .padding(18)
            .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { height = $0 }
        }
        .scrollBounceBehavior(.basedOnSize)
        .foregroundStyle(theme.ink)
        .presentationDetents([.height(min(height + 20, 760)), .large])
        .presentationCornerRadius(32)
        .presentationBackground(theme.bg)
        .presentationDragIndicator(.hidden)
    }
}

/// Section of grouped rows inside a glass panel, separated by `line` hairlines.
struct GlassList<Data: RandomAccessCollection, ID: Hashable, Row: View>: View {
    let data: Data
    let id: KeyPath<Data.Element, ID>
    var header: String?
    var trailingHeader: String?
    var rowMinHeight: CGFloat = 56
    @ViewBuilder var row: (Data.Element) -> Row
    @Environment(\.theme) private var theme

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            if header != nil || trailingHeader != nil {
                HStack {
                    if let header { Text(header) }
                    Spacer()
                    if let trailingHeader { Text(trailingHeader) }
                }
                .bodyFont(12, .semibold, relativeTo: .caption)
                .foregroundStyle(theme.muted)
                .padding(.top, 10)
                .padding(.bottom, 6)
            }
            let firstID = data.first?[keyPath: id]
            ForEach(data, id: id) { element in
                row(element)
                    .frame(maxWidth: .infinity, minHeight: rowMinHeight, alignment: .leading)
                    .overlay(alignment: .top) {
                        if element[keyPath: id] != firstID || header != nil || trailingHeader != nil {
                            Rectangle().fill(theme.line).frame(height: 1)
                        }
                    }
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 4)
        .glass(cornerRadius: 24)
    }
}
