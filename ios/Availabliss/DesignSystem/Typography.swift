import SwiftUI

/// Bricolage Grotesque for display, Instrument Sans for UI. Both are bundled
/// as variable fonts (see Resources/Fonts/README.md). `Font.custom` falls back to the
/// system font when a family isn't installed, and always scales with Dynamic Type.
enum AppFont {
    /// Family names of the bundled variable fonts. `.weight` drives their weight axis.
    static let displayFamily = "Bricolage Grotesque"
    static let bodyFamily = "Instrument Sans"

    /// Display: titles and numbers. Pass the size from the design concept.
    static func display(_ size: CGFloat, _ weight: Font.Weight = .bold, relativeTo style: Font.TextStyle = .title) -> Font {
        .custom(displayFamily, size: size, relativeTo: style).weight(weight)
    }

    /// Body / UI text.
    static func body(_ size: CGFloat, _ weight: Font.Weight = .regular, relativeTo style: Font.TextStyle = .body) -> Font {
        .custom(bodyFamily, size: size, relativeTo: style).weight(weight)
    }
}

extension View {
    /// Display type with the design's -0.025em tracking.
    func displayFont(_ size: CGFloat, _ weight: Font.Weight = .bold, relativeTo style: Font.TextStyle = .title) -> some View {
        font(AppFont.display(size, weight, relativeTo: style))
            .tracking(-0.025 * size)
    }

    func bodyFont(_ size: CGFloat, _ weight: Font.Weight = .regular, relativeTo style: Font.TextStyle = .body) -> some View {
        font(AppFont.body(size, weight, relativeTo: style))
    }

    /// The muted 13pt semibold section label ("How long?", "Mode", …).
    func sectionLabelStyle() -> some View {
        modifier(SectionLabelModifier())
    }
}

private struct SectionLabelModifier: ViewModifier {
    @Environment(\.theme) private var theme
    func body(content: Content) -> some View {
        content
            .bodyFont(13, .semibold, relativeTo: .subheadline)
            .foregroundStyle(theme.muted)
            .accessibilityAddTraits(.isHeader)
    }
}
