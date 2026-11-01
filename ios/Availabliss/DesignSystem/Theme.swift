import SwiftUI

/// Every colour token from docs/DESIGN.md, resolved for one (colour scheme × colour theme).
struct Theme: Sendable, Equatable {
    let scheme: ColorScheme
    let colorTheme: ColorTheme

    // Mode tokens
    let bg: Color
    let ink: Color
    let muted: Color
    let neutralInk: Color
    let surface: Color
    let glass: Color
    let glassBorder: Color
    let glassHi: Color
    let shadow: Color
    let line: Color
    let line2: Color
    let line3: Color
    let solid: Color
    let onSolid: Color
    let onAccent: Color
    let busyBg: Color
    let busyLine: Color
    let ok: Color
    let okBg: Color
    let okInk: Color
    let warn: Color
    let warnBg: Color
    let warnInk: Color
    let tiles: [Color]
    let orbOpacity: Double

    // Colour-theme tokens
    let accent: Color
    let accentInk: Color
    let accentSoft: Color
    let orbs: [Color]

    var isDark: Bool { scheme == .dark }

    /// Group tile / avatar colour for an API token (`t1` … `t5`).
    func tile(_ token: String) -> Color {
        let index = Int(token.dropFirst()).map { $0 - 1 } ?? 0
        return tiles[safe: index] ?? tiles[0]
    }

    /// A stable tile colour for a person, derived from their id.
    func tile(forUser id: String) -> Color {
        let sum = id.unicodeScalars.reduce(0) { ($0 &* 31 &+ Int($1.value)) & 0xFFFF }
        return tiles[sum % tiles.count]
    }

    static func make(scheme: ColorScheme, colorTheme: ColorTheme) -> Theme {
        let palette = ThemePalette.palette(for: colorTheme, dark: scheme == .dark)
        return scheme == .dark
            ? .dark(colorTheme: colorTheme, palette: palette)
            : .light(colorTheme: colorTheme, palette: palette)
    }

    static let `default` = make(scheme: .light, colorTheme: .dawn)

    private static func light(colorTheme: ColorTheme, palette: ThemePalette) -> Theme {
        let ink = Color(hex: 0x1A1B3A)
        return Theme(
            scheme: .light, colorTheme: colorTheme,
            bg: Color(hex: 0xECEEF7), ink: ink, muted: Color(hex: 0x4D5072), neutralInk: Color(hex: 0x3A3D5C),
            surface: .white.opacity(0.62), glass: .white.opacity(0.50), glassBorder: .white.opacity(0.80),
            glassHi: .white.opacity(0.90), shadow: Color(hex: 0x2B2678, alpha: 0.38),
            line: ink.opacity(0.07), line2: ink.opacity(0.14), line3: ink.opacity(0.28),
            solid: ink, onSolid: .white, onAccent: .white,
            busyBg: .white.opacity(0.4), busyLine: Color(hex: 0xB9BCD6),
            ok: Color(hex: 0x0FA37F), okBg: Color(hex: 0xD3F5EA), okInk: Color(hex: 0x0D6B52),
            warn: Color(hex: 0xFF8A5E), warnBg: Color(hex: 0xFFE0CF), warnInk: Color(hex: 0x8A3410),
            tiles: [0xFFD3BF, 0xD6DAFF, 0xC8F1E4, 0xF5D6EE, 0xFFE7A3].map { Color(hex: $0) },
            orbOpacity: 0.85,
            accent: palette.accent, accentInk: palette.accentInk, accentSoft: palette.accentSoft, orbs: palette.orbs
        )
    }

    private static func dark(colorTheme: ColorTheme, palette: ThemePalette) -> Theme {
        Theme(
            scheme: .dark, colorTheme: colorTheme,
            bg: Color(hex: 0x0C0D1D), ink: Color(hex: 0xF1F2FA), muted: Color(hex: 0xA6A9C8), neutralInk: Color(hex: 0xC9CBE3),
            surface: .white.opacity(0.07), glass: Color(hex: 0x1A1C38, alpha: 0.50), glassBorder: .white.opacity(0.12),
            glassHi: .white.opacity(0.10), shadow: .black.opacity(0.60),
            line: .white.opacity(0.08), line2: .white.opacity(0.16), line3: .white.opacity(0.30),
            solid: Color(hex: 0xF1F2FA), onSolid: Color(hex: 0x0C0D1D), onAccent: .white,
            busyBg: .black.opacity(0.28), busyLine: Color(hex: 0x4A4D70),
            ok: Color(hex: 0x34D3A0), okBg: Color(hex: 0x34D3A0, alpha: 0.16), okInk: Color(hex: 0x7EE8C6),
            warn: Color(hex: 0xFF9A73), warnBg: Color(hex: 0xFF8A5E, alpha: 0.18), warnInk: Color(hex: 0xFFBCA2),
            tiles: [0x5C3B2E, 0x363B78, 0x1E5045, 0x58304F, 0x5A4A1E].map { Color(hex: $0) },
            orbOpacity: 0.42,
            accent: palette.accent, accentInk: palette.accentInk, accentSoft: palette.accentSoft, orbs: palette.orbs
        )
    }
}

/// The per-colour-theme tokens (accent + orbs).
struct ThemePalette: Sendable {
    let accent: Color
    let accentInk: Color
    let accentSoft: Color
    let orbs: [Color]

    static func palette(for theme: ColorTheme, dark: Bool) -> ThemePalette {
        switch theme {
        case .dawn:
            ThemePalette(
                accent: Color(hex: dark ? 0x6A5CFF : 0x5546FF),
                accentInk: Color(hex: dark ? 0xB4ACFF : 0x3B2FD0),
                accentSoft: dark ? Color(hex: 0x8276FF, alpha: 0.24) : Color(hex: 0x5546FF, alpha: 0.14),
                orbs: [Color(hex: 0xFFB894), Color(hex: 0x9DA6FF), Color(hex: 0x8EE3CC)]
            )
        case .lagoon:
            ThemePalette(
                accent: Color(hex: 0x0B7F8A),
                accentInk: Color(hex: dark ? 0x7FDDE4 : 0x06646D),
                accentSoft: dark ? Color(hex: 0x139CA8, alpha: 0.26) : Color(hex: 0x0B7F8A, alpha: 0.14),
                orbs: [Color(hex: 0x86E3E0), Color(hex: 0x8EC5FF), Color(hex: 0xB5EBA8)]
            )
        case .sunset:
            ThemePalette(
                accent: Color(hex: 0xC9401F),
                accentInk: Color(hex: dark ? 0xFFB199 : 0xA8341A),
                accentSoft: dark ? Color(hex: 0xE0532F, alpha: 0.26) : Color(hex: 0xC9401F, alpha: 0.13),
                orbs: [Color(hex: 0xFFA585), Color(hex: 0xFF9CC0), Color(hex: 0xFFD37E)]
            )
        case .orchid:
            ThemePalette(
                accent: Color(hex: dark ? 0xA646DB : 0x9333C9),
                accentInk: Color(hex: dark ? 0xDDB0F7 : 0x7A27AA),
                accentSoft: dark ? Color(hex: 0xA646DB, alpha: 0.26) : Color(hex: 0x9333C9, alpha: 0.13),
                orbs: [Color(hex: 0xF4B0E0), Color(hex: 0xC9A8FF), Color(hex: 0xA9B8FF)]
            )
        case .grove:
            ThemePalette(
                accent: Color(hex: 0x2D7A4A),
                accentInk: Color(hex: dark ? 0x9FE0B6 : 0x22633B),
                accentSoft: dark ? Color(hex: 0x3A9460, alpha: 0.26) : Color(hex: 0x2D7A4A, alpha: 0.14),
                orbs: [Color(hex: 0xC3E88F), Color(hex: 0x8FD8B5), Color(hex: 0xF3D78A)]
            )
        }
    }
}

extension Color {
    /// `Color(hex: 0x5546FF)` in sRGB.
    init(hex: UInt32, alpha: Double = 1) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: alpha
        )
    }

    /// Parses `#RRGGBB` strings (calendar colours from the API). Returns nil when malformed.
    init?(hexString: String?) {
        guard let hexString else { return nil }
        let trimmed = hexString.trimmingCharacters(in: CharacterSet(charactersIn: "# "))
        guard trimmed.count == 6, let value = UInt32(trimmed, radix: 16) else { return nil }
        self.init(hex: value)
    }
}

// MARK: - Environment

private struct ThemeKey: EnvironmentKey {
    static let defaultValue = Theme.default
}

extension EnvironmentValues {
    var theme: Theme {
        get { self[ThemeKey.self] }
        set { self[ThemeKey.self] = newValue }
    }
}

extension ThemeMode {
    /// `nil` follows the system.
    var colorScheme: ColorScheme? {
        switch self {
        case .light: .light
        case .dark: .dark
        case .system: nil
        }
    }
}

/// Resolves the theme from the current colour scheme and injects it into the environment.
struct ThemeProvider<Content: View>: View {
    let colorTheme: ColorTheme
    @ViewBuilder var content: Content
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        let theme = Theme.make(scheme: colorScheme, colorTheme: colorTheme)
        content
            .environment(\.theme, theme)
            .tint(theme.accent)
            .animation(.easeInOut(duration: 0.3), value: theme)
    }
}
