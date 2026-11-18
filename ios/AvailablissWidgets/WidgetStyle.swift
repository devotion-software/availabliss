import SwiftUI
import UIKit
import WidgetKit

/// The app's Dawn colours, light or dark with the system. Widgets can't load the app's fonts, so
/// text uses the system font at the app's sizes and weights (as on Android).
enum WidgetPalette {
    static let background = Color(light: 0xECEEF7, dark: 0x0C0D1D)
    static let ink = Color(light: 0x1A1B3A, dark: 0xF1F2FA)
    static let muted = Color(light: 0x4D5072, dark: 0xA6A9C8)
    static let accent = Color(light: 0x5546FF, dark: 0x6A5CFF)
    static let accentInk = Color(light: 0x3B2FD0, dark: 0xB4ACFF)
    static let accentSoft = Color(light: 0x5546FF, lightAlpha: 0.14, dark: 0x8276FF, darkAlpha: 0.24)
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
    /// The widget's background and layout, shared by both widgets.
    func widgetCard() -> some View {
        frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .containerBackground(for: .widget) { WidgetPalette.background }
    }
}
