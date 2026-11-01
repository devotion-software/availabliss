import SwiftUI

/// One option in a `SegmentedControl`.
struct SegmentOption<Value: Hashable>: Identifiable {
    let value: Value
    let label: String
    var systemImage: String?
    var id: Value { value }
}

/// Segmented picker in the two styles the concept uses.
struct SegmentedControl<Value: Hashable>: View {
    enum Style {
        /// Glass container, selected segment is `solid` (group calendar Month/Week/Day).
        case solid
        /// `line` container, selected segment is a raised `surface` (Repeat, Mode).
        case raised
    }

    @Binding var selection: Value
    let options: [SegmentOption<Value>]
    var style: Style = .raised
    var height: CGFloat = 36
    var accessibilityLabel: String
    @Environment(\.theme) private var theme
    @Namespace private var namespace

    var body: some View {
        HStack(spacing: style == .solid ? 2 : 6) {
            ForEach(options) { option in
                segment(option)
            }
        }
        .padding(style == .solid ? 3 : 4)
        .background(container)
        .accessibilityElement(children: .contain)
        .accessibilityLabel(accessibilityLabel)
    }

    @ViewBuilder
    private var container: some View {
        switch style {
        case .solid: Color.clear.glass(cornerRadius: 13, shadow: false)
        case .raised: RoundedRectangle(cornerRadius: 14, style: .continuous).fill(theme.line)
        }
    }

    private func segment(_ option: SegmentOption<Value>) -> some View {
        let isOn = option.value == selection
        return Button {
            withAnimation(.spring(response: 0.3, dampingFraction: 0.8)) { selection = option.value }
        } label: {
            HStack(spacing: 6) {
                if let image = option.systemImage {
                    Image(systemName: image).font(.system(size: 14, weight: .semibold)).accessibilityHidden(true)
                }
                Text(option.label).lineLimit(1).minimumScaleFactor(0.8)
            }
            .bodyFont(13.5, .semibold, relativeTo: .subheadline)
            .foregroundStyle(foreground(isOn))
            .frame(maxWidth: style == .raised ? .infinity : nil, minHeight: height)
            .padding(.horizontal, style == .solid ? 12 : 4)
            .frame(minWidth: style == .solid ? 52 : nil)
            .background {
                if isOn { selectedBackground.matchedGeometryEffect(id: "segment", in: namespace) }
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isOn ? .isSelected : [])
    }

    private func foreground(_ isOn: Bool) -> Color {
        switch style {
        case .solid: isOn ? theme.onSolid : theme.ink
        case .raised: isOn ? theme.ink : theme.muted
        }
    }

    @ViewBuilder
    private var selectedBackground: some View {
        switch style {
        case .solid:
            RoundedRectangle(cornerRadius: 10, style: .continuous).fill(theme.solid)
        case .raised:
            RoundedRectangle(cornerRadius: 10, style: .continuous)
                .fill(theme.surface)
                .shadow(color: theme.shadow.opacity(0.5), radius: 4, y: 2)
        }
    }
}

/// Selectable capsule chip ("2 hours", "All day").
struct Chip: View {
    let label: String
    let isOn: Bool
    var height: CGFloat = 38
    var fontSize: CGFloat = 13
    let action: () -> Void
    @Environment(\.theme) private var theme

    var body: some View {
        Button(action: action) {
            Text(label)
                .bodyFont(fontSize, .semibold, relativeTo: .subheadline)
                .foregroundStyle(isOn ? theme.onSolid : theme.ink)
                .padding(.horizontal, 14)
                .frame(minHeight: height)
                .background(Capsule().fill(isOn ? theme.solid : theme.surface))
                .overlay(Capsule().strokeBorder(isOn ? theme.solid : theme.glassBorder, lineWidth: 1))
                .frame(minHeight: 44)
                .contentShape(Capsule())
                .animation(.easeOut(duration: 0.2), value: isOn)
        }
        .buttonStyle(.pressable)
        .accessibilityAddTraits(isOn ? .isSelected : [])
    }
}

/// The concept's 52×32 switch: `accent` track when on, `line-2` when off, white knob.
struct GlassSwitchToggleStyle: ToggleStyle {
    @Environment(\.theme) private var theme
    @Environment(\.isEnabled) private var isEnabled

    func makeBody(configuration: Configuration) -> some View {
        Button {
            configuration.isOn.toggle()
        } label: {
            HStack(spacing: 12) {
                configuration.label
                    .frame(maxWidth: .infinity, alignment: .leading)
                track(isOn: configuration.isOn)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .opacity(isEnabled ? 1 : 0.5)
        .accessibilityRepresentation {
            Toggle(isOn: configuration.$isOn) { configuration.label }
        }
    }

    private func track(isOn: Bool) -> some View {
        Capsule()
            .fill(isOn ? theme.accent : theme.line2)
            .frame(width: 52, height: 32)
            .overlay(alignment: isOn ? .trailing : .leading) {
                Circle()
                    .fill(.white)
                    .frame(width: 26, height: 26)
                    .shadow(color: Color(hex: 0x14143C, alpha: 0.25), radius: 3, y: 2)
                    .padding(3)
            }
            .animation(.spring(response: 0.3, dampingFraction: 0.65), value: isOn)
    }
}

extension ToggleStyle where Self == GlassSwitchToggleStyle {
    static var glassSwitch: GlassSwitchToggleStyle { GlassSwitchToggleStyle() }
}

/// "Label / value" tile used for date, time, start and end pickers.
struct FieldTile: View {
    let label: String
    let value: String
    var isActive = false
    let action: () -> Void
    @Environment(\.theme) private var theme

    var body: some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 2) {
                Text(label)
                    .bodyFont(11, .semibold, relativeTo: .caption)
                    .foregroundStyle(theme.muted)
                Text(value)
                    .bodyFont(15, .semibold)
                    .foregroundStyle(theme.ink)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
            }
            .frame(maxWidth: .infinity, minHeight: 60, alignment: .leading)
            .padding(.horizontal, 12)
            .surfaceBackground(cornerRadius: 16, border: isActive ? theme.accent : nil, lineWidth: isActive ? 1.5 : 1)
            .contentShape(Rectangle())
        }
        .buttonStyle(.pressable)
        .accessibilityLabel("\(label), \(value)")
    }
}

/// Text field with the concept's `surface` input styling.
struct InputField: View {
    let placeholder: String
    @Binding var text: String
    var height: CGFloat = 50
    var cornerRadius: CGFloat = 16
    @Environment(\.theme) private var theme
    @FocusState private var isFocused: Bool

    var body: some View {
        TextField("", text: $text, prompt: Text(placeholder).foregroundStyle(theme.muted))
            .bodyFont(16)
            .foregroundStyle(theme.ink)
            .focused($isFocused)
            .padding(.horizontal, 16)
            .frame(minHeight: height)
            .background(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous).fill(theme.surface))
            .overlay(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous).strokeBorder(theme.line2, lineWidth: 1))
            .overlay(
                RoundedRectangle(cornerRadius: cornerRadius + 2, style: .continuous)
                    .strokeBorder(theme.accent, lineWidth: 2)
                    .padding(-4)
                    .opacity(isFocused ? 1 : 0)
            )
            .animation(.easeOut(duration: 0.15), value: isFocused)
    }
}
