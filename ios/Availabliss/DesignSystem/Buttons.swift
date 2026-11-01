import SwiftUI

/// Visual variants of the app's filled buttons.
enum FilledButtonKind {
    /// `solid` / `on-solid` — the main call to action.
    case primary
    /// `accent` / `on-accent` — positive actions on availability ("Propose", "Block this time").
    case accent
    /// `surface` with a `line-2` border.
    case secondary
    /// `ok-bg` / `ok-ink` — a completed state ("Added to your calendar").
    case success
    /// `warn-bg` / `warn-ink` — e.g. "Nudge".
    case warning
}

struct FilledButtonStyle: ButtonStyle {
    var kind: FilledButtonKind = .primary
    var height: CGFloat = 50
    var cornerRadius: CGFloat = 16
    var fontSize: CGFloat = 16
    var fullWidth = true
    @Environment(\.theme) private var theme
    @Environment(\.isEnabled) private var isEnabled

    func makeBody(configuration: Configuration) -> some View {
        let shape = RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
        let colors = colors
        configuration.label
            .bodyFont(fontSize, .semibold, relativeTo: .headline)
            .lineLimit(1)
            .minimumScaleFactor(0.8)
            .foregroundStyle(colors.foreground)
            .padding(.horizontal, 16)
            .frame(maxWidth: fullWidth ? .infinity : nil, minHeight: height)
            .background(shape.fill(colors.background))
            .overlay {
                if kind == .secondary { shape.strokeBorder(theme.line2, lineWidth: 1) }
            }
            .contentShape(shape)
            .opacity(isEnabled ? 1 : 0.5)
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .animation(.spring(response: 0.25, dampingFraction: 0.7), value: configuration.isPressed)
    }

    private var colors: (background: Color, foreground: Color) {
        switch kind {
        case .primary: (theme.solid, theme.onSolid)
        case .accent: (theme.accent, theme.onAccent)
        case .secondary: (theme.surface, theme.ink)
        case .success: (theme.okBg, theme.okInk)
        case .warning: (theme.warnBg, theme.warnInk)
        }
    }
}

/// Glass capsule button ("New group").
struct GlassPillButtonStyle: ButtonStyle {
    var height: CGFloat = 36
    @Environment(\.theme) private var theme

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .bodyFont(13, .semibold, relativeTo: .subheadline)
            .foregroundStyle(theme.ink)
            .padding(.horizontal, 14)
            .frame(minHeight: height)
            .glass(in: Capsule(), shadow: false)
            .frame(minHeight: 44)
            .contentShape(Rectangle())
            .scaleEffect(configuration.isPressed ? 0.96 : 1)
            .animation(.spring(response: 0.25, dampingFraction: 0.7), value: configuration.isPressed)
    }
}

/// Plain text action in `accent-ink` ("Use a different email", "Change").
struct TextActionButtonStyle: ButtonStyle {
    var fontSize: CGFloat = 14
    @Environment(\.theme) private var theme

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .bodyFont(fontSize, .semibold, relativeTo: .subheadline)
            .foregroundStyle(theme.accentInk)
            .frame(minHeight: 44)
            .contentShape(Rectangle())
            .opacity(configuration.isPressed ? 0.6 : 1)
    }
}

/// Subtle press feedback for custom tappable cards and cells.
struct PressableButtonStyle: ButtonStyle {
    var scale: CGFloat = 0.97
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? scale : 1)
            .animation(.spring(response: 0.22, dampingFraction: 0.7), value: configuration.isPressed)
    }
}

extension ButtonStyle where Self == FilledButtonStyle {
    static var primary: FilledButtonStyle { FilledButtonStyle(kind: .primary) }
    static var accent: FilledButtonStyle { FilledButtonStyle(kind: .accent) }
    static var secondary: FilledButtonStyle { FilledButtonStyle(kind: .secondary) }
    static func filled(_ kind: FilledButtonKind, height: CGFloat = 50, cornerRadius: CGFloat = 16, fontSize: CGFloat = 16, fullWidth: Bool = true) -> FilledButtonStyle {
        FilledButtonStyle(kind: kind, height: height, cornerRadius: cornerRadius, fontSize: fontSize, fullWidth: fullWidth)
    }
}

extension ButtonStyle where Self == GlassPillButtonStyle {
    static var glassPill: GlassPillButtonStyle { GlassPillButtonStyle() }
}

extension ButtonStyle where Self == TextActionButtonStyle {
    static var textAction: TextActionButtonStyle { TextActionButtonStyle() }
}

extension ButtonStyle where Self == PressableButtonStyle {
    static var pressable: PressableButtonStyle { PressableButtonStyle() }
}

/// Button label that swaps to a spinner while `isLoading`.
struct LoadingLabel: View {
    let title: String
    var systemImage: String?
    var isLoading = false

    var body: some View {
        ZStack {
            HStack(spacing: 8) {
                if let systemImage {
                    Image(systemName: systemImage).fontWeight(.semibold).accessibilityHidden(true)
                }
                Text(title)
            }
            .opacity(isLoading ? 0 : 1)
            if isLoading { ProgressView().tint(.primary) }
        }
        .accessibilityLabel(title)
    }
}

/// 44pt circular glass icon button. Always pass an accessibility label.
struct IconButton: View {
    let systemImage: String
    let label: String
    var size: CGFloat = 44
    var glass = true
    let action: () -> Void
    @Environment(\.theme) private var theme

    var body: some View {
        Button(action: action) {
            Image(systemName: systemImage)
                .font(.system(size: size * 0.4, weight: .semibold))
                .foregroundStyle(theme.ink)
                .frame(width: size, height: size)
                .modifier(OptionalGlassCircle(enabled: glass))
                .frame(minWidth: 44, minHeight: 44)
                .contentShape(Circle())
        }
        .buttonStyle(.pressable)
        .accessibilityLabel(label)
    }
}

private struct OptionalGlassCircle: ViewModifier {
    let enabled: Bool
    func body(content: Content) -> some View {
        if enabled { content.glass(in: Circle(), shadow: false) } else { content }
    }
}

/// The small round close button used in sheets.
struct CloseButton: View {
    let action: () -> Void
    @Environment(\.theme) private var theme

    var body: some View {
        Button(action: action) {
            Image(systemName: "xmark")
                .font(.system(size: 13, weight: .bold))
                .foregroundStyle(theme.ink)
                .frame(width: 40, height: 40)
                .background(Circle().fill(theme.line))
                .frame(width: 44, height: 44)
                .contentShape(Circle())
        }
        .buttonStyle(.pressable)
        .accessibilityLabel("Close")
    }
}
