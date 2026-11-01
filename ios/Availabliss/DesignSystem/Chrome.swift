import SwiftUI

/// Screen header: glass back button, display title + muted subtitle, optional trailing content.
struct ScreenHeader<Trailing: View>: View {
    let title: String
    var subtitle: String?
    var titleSize: CGFloat = 24
    var backLabel = "Back"
    var onBack: (() -> Void)?
    @ViewBuilder var trailing: Trailing

    var body: some View {
        HStack(spacing: 10) {
            if let onBack {
                IconButton(systemImage: "chevron.left", label: backLabel, action: onBack)
            }
            VStack(alignment: .leading, spacing: 0) {
                Text(title)
                    .displayFont(titleSize, .bold)
                    .lineLimit(2)
                    .accessibilityAddTraits(.isHeader)
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
    init(title: String, subtitle: String? = nil, titleSize: CGFloat = 24, backLabel: String = "Back", onBack: (() -> Void)? = nil) {
        self.init(title: title, subtitle: subtitle, titleSize: titleSize, backLabel: backLabel, onBack: onBack) { EmptyView() }
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

/// One tab in the floating `TabBar`.
struct TabItem<Value: Hashable>: Identifiable {
    let value: Value
    let title: String
    let systemImage: String
    var badge: TabBadge = .none
    var id: Value { value }
}

enum TabBadge: Equatable {
    case none
    case dot
    case count(Int)
}

/// The floating glass tab bar (66pt tall, radius 24, 20pt from the edges).
struct TabBar<Value: Hashable>: View {
    @Binding var selection: Value
    let items: [TabItem<Value>]
    @Environment(\.theme) private var theme

    var body: some View {
        HStack(spacing: 0) {
            ForEach(items) { item in
                tab(item)
            }
        }
        .padding(.horizontal, 6)
        .frame(height: 66)
        .glass(cornerRadius: 24)
        .padding(.horizontal, 20)
        .padding(.bottom, 8)
    }

    private func tab(_ item: TabItem<Value>) -> some View {
        let isOn = item.value == selection
        return Button {
            selection = item.value
        } label: {
            VStack(spacing: 3) {
                Image(systemName: item.systemImage)
                    .font(.system(size: 19, weight: .regular))
                    .frame(height: 24)
                    .overlay(alignment: .topTrailing) { badge(item.badge) }
                Text(item.title)
                    .bodyFont(11, isOn ? .semibold : .medium, relativeTo: .caption2)
            }
            .foregroundStyle(isOn ? theme.accentInk : theme.muted)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(accessibilityLabel(item))
        .accessibilityAddTraits(isOn ? .isSelected : [])
    }

    @ViewBuilder
    private func badge(_ badge: TabBadge) -> some View {
        switch badge {
        case .none: EmptyView()
        case .dot:
            Circle()
                .fill(theme.warn)
                .frame(width: 8, height: 8)
                .overlay(Circle().stroke(theme.bg, lineWidth: 1.5))
                .offset(x: 6, y: -1)
        case .count(let count):
            Text(count > 99 ? "99+" : "\(count)")
                .font(AppFont.body(10, .bold, relativeTo: .caption2))
                .foregroundStyle(Color(hex: 0x1A1B3A))
                .padding(.horizontal, 4)
                .frame(minWidth: 16, minHeight: 16)
                .background(Capsule().fill(theme.warn))
                .offset(x: 12, y: -4)
        }
    }

    private func accessibilityLabel(_ item: TabItem<Value>) -> String {
        switch item.badge {
        case .none: item.title
        case .dot: "\(item.title), new activity"
        case .count(let count): "\(item.title), \(count) unread"
        }
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
