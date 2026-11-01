import SwiftUI

/// Bricolage Grotesque for display, Instrument Sans for UI. Both are bundled
/// (see Resources/Fonts/README.md). `Font.custom` falls back to the system font
/// when a family isn't installed, and always scales with Dynamic Type.
enum AppFont {
    /// PostScript prefixes of the bundled static TTFs.
    static let displayFamily = "BricolageGrotesque"
    static let bodyFamily = "InstrumentSans"

    /// Display: titles and numbers. Pass the size from the design concept.
    static func display(_ size: CGFloat, _ weight: Font.Weight = .bold, relativeTo style: Font.TextStyle = .title) -> Font {
        .custom(postScriptName(displayFamily, weight), size: size, relativeTo: style).weight(weight)
    }

    /// Body / UI text.
    static func body(_ size: CGFloat, _ weight: Font.Weight = .regular, relativeTo style: Font.TextStyle = .body) -> Font {
        .custom(postScriptName(bodyFamily, weight), size: size, relativeTo: style).weight(weight)
    }

    /// e.g. `BricolageGrotesque-SemiBold`. If the face isn't bundled, `Font.custom` falls back to the
    /// system font at the same size (still Dynamic Type scaled) and `.weight` keeps the intended weight.
    private static func postScriptName(_ family: String, _ weight: Font.Weight) -> String {
        let suffix: String = switch weight {
        case .medium: "Medium"
        case .semibold: "SemiBold"
        case .bold: "Bold"
        case .heavy, .black: "ExtraBold"
        default: "Regular"
        }
        return "\(family)-\(suffix)"
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
