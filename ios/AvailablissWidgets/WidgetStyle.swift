import SwiftUI
import UIKit
import WidgetKit

/// The app's colours in your colour theme (shared by the app, see `WidgetAppearance`), light or dark
/// as you chose in the app (or with the system). Widgets can't load the app's fonts, so text uses the
/// system font at the app's sizes and weights (as on Android).
enum WidgetPalette {
    static let background = Color(light: 0xECEEF7, dark: 0x0C0D1D)
    static let ink = Color(light: 0x1A1B3A, dark: 0xF1F2FA)
    static let muted = Color(light: 0x4D5072, dark: 0xA6A9C8)
    static var accent: Color { theme.accent }
    static var accentInk: Color { theme.accentInk }
    static var accentSoft: Color { theme.accentSoft }
    static var orbs: [Color] { theme.orbs.map { Color(light: $0, dark: $0) } }

    static var theme: WidgetTheme { WidgetTheme(rawValue: WidgetAppearance.colorTheme) ?? .dawn }
}

/// The app's colour themes (matching the app's `ThemePalette` and web's / Android's themes).
enum WidgetTheme: String {
    case dawn, lagoon, sunset, orchid, grove

    var accent: Color {
        switch self {
        case .dawn: Color(light: 0x5546FF, dark: 0x6A5CFF)
        case .lagoon: Color(light: 0x0B7F8A, dark: 0x0B7F8A)
        case .sunset: Color(light: 0xC9401F, dark: 0xC9401F)
        case .orchid: Color(light: 0x9333C9, dark: 0xA646DB)
        case .grove: Color(light: 0x2D7A4A, dark: 0x2D7A4A)
        }
    }

    var accentInk: Color {
        switch self {
        case .dawn: Color(light: 0x3B2FD0, dark: 0xB4ACFF)
        case .lagoon: Color(light: 0x06646D, dark: 0x7FDDE4)
        case .sunset: Color(light: 0xA8341A, dark: 0xFFB199)
        case .orchid: Color(light: 0x7A27AA, dark: 0xDDB0F7)
        case .grove: Color(light: 0x22633B, dark: 0x9FE0B6)
        }
    }

    var accentSoft: Color {
        switch self {
        case .dawn: Color(light: 0x5546FF, lightAlpha: 0.14, dark: 0x8276FF, darkAlpha: 0.24)
        case .lagoon: Color(light: 0x0B7F8A, lightAlpha: 0.14, dark: 0x139CA8, darkAlpha: 0.26)
        case .sunset: Color(light: 0xC9401F, lightAlpha: 0.13, dark: 0xE0532F, darkAlpha: 0.26)
        case .orchid: Color(light: 0x9333C9, lightAlpha: 0.13, dark: 0xA646DB, darkAlpha: 0.26)
        case .grove: Color(light: 0x2D7A4A, lightAlpha: 0.14, dark: 0x3A9460, darkAlpha: 0.26)
        }
    }

    /// The three background orbs.
    var orbs: [UInt32] {
        switch self {
        case .dawn: [0xFFB894, 0x9DA6FF, 0x8EE3CC]
        case .lagoon: [0x86E3E0, 0x8EC5FF, 0xB5EBA8]
        case .sunset: [0xFFA585, 0xFF9CC0, 0xFFD37E]
        case .orchid: [0xF4B0E0, 0xC9A8FF, 0xA9B8FF]
        case .grove: [0xC3E88F, 0x8FD8B5, 0xF3D78A]
        }
    }
}

/// The app's background behind a widget: its base colour with three soft orbs in your theme's colours
/// (fainter in dark mode, as in the app), placed like the app's Groups screen.
struct WidgetBackground: View {
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        GeometryReader { proxy in
            let side = max(proxy.size.width, proxy.size.height)
            let orbs = WidgetPalette.orbs
            let strength = scheme == .dark ? 0.42 : 0.85
            ZStack {
                WidgetPalette.background
                orb(orbs[0], size: side * 1.0, at: CGPoint(x: 0, y: 0), strength: strength)
                orb(orbs[1], size: side * 0.9, at: CGPoint(x: proxy.size.width, y: proxy.size.height * 0.45), strength: strength)
                orb(orbs[2], size: side * 0.85, at: CGPoint(x: proxy.size.width * 0.2, y: proxy.size.height * 1.05), strength: strength * 0.9)
            }
        }
    }

    /// A blurred circle, as a radial fade (cheaper than a blur for a widget).
    private func orb(_ color: Color, size: CGFloat, at center: CGPoint, strength: Double) -> some View {
        Circle()
            .fill(RadialGradient(colors: [color.opacity(strength), color.opacity(0)], center: .center, startRadius: 0, endRadius: size / 2))
            .frame(width: size, height: size)
            .position(center)
    }
}

extension Color {
    init(light: UInt32, lightAlpha: CGFloat = 1, dark: UInt32, darkAlpha: CGFloat = 1) {
        self.init(
            uiColor: UIColor { traits in
                traits.userInterfaceStyle == .dark ? UIColor(hex: dark, alpha: darkAlpha) : UIColor(hex: light, alpha: lightAlpha)
            })
    }
}

private extension UIColor {
    convenience init(hex: UInt32, alpha: CGFloat) {
        self.init(
            red: CGFloat((hex >> 16) & 0xFF) / 255,
            green: CGFloat((hex >> 8) & 0xFF) / 255,
            blue: CGFloat(hex & 0xFF) / 255,
            alpha: alpha
        )
    }
}

/// "NEXT PLAN", in the accent colour above the widget's content.
struct WidgetLabel: View {
    let text: String

    var body: some View {
        Text(text.uppercased())
            .font(.system(size: 11, weight: .bold))
            .foregroundStyle(WidgetPalette.accent)
            .lineLimit(1)
    }
}

/// A headline and a muted line under it, for the signed-out and empty states.
struct WidgetMessage: View {
    let title: String
    var detail: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title)
                .font(.system(size: 16, weight: .bold))
                .foregroundStyle(WidgetPalette.ink)
                .lineLimit(2)
            if let detail {
                Text(detail)
                    .font(.system(size: 13))
                    .foregroundStyle(WidgetPalette.muted)
                    .lineLimit(2)
            }
        }
    }
}

/// What both widgets show before an account is signed in, or before the app has written anything.
struct NotReadyMessage: View {
    let snapshot: WidgetSnapshot?

    var body: some View {
        if snapshot == nil {
            WidgetMessage(title: "Open Availabliss", detail: "to see your plans here")
        } else {
            WidgetMessage(title: "Sign in to Availabliss", detail: "to see your plans here")
        }
    }
}

extension View {
    /// The widget's background and layout, shared by both widgets, light or dark as chosen in the app.
    func widgetCard() -> some View {
        modifier(WidgetCard())
    }
}

private struct WidgetCard: ViewModifier {
    @Environment(\.colorScheme) private var system

    /// Light or dark as chosen in the app; following the system unless you picked one.
    private var scheme: ColorScheme {
        switch WidgetAppearance.mode {
        case "light": .light
        case "dark": .dark
        default: system
        }
    }

    func body(content: Content) -> some View {
        content
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .containerBackground(for: .widget) { WidgetBackground().environment(\.colorScheme, scheme) }
            .environment(\.colorScheme, scheme)
    }
}
