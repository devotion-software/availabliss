package dev.joeherbert.availabliss.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import dev.joeherbert.availabliss.core.network.model.ColorTheme
import dev.joeherbert.availabliss.core.network.model.TileColor

/**
 * Every colour token from docs/DESIGN.md "Colour tokens", resolved for one
 * mode (light / dark) × colour theme. Access via `AvailablissTheme.colors`.
 */
@Immutable
data class AvailablissColors(
    val isDark: Boolean,
    // Mode tokens
    val bg: Color,
    val ink: Color,
    val muted: Color,
    val neutralInk: Color,
    val surface: Color,
    val glass: Color,
    val glassBorder: Color,
    val glassHi: Color,
    val shadow: Color,
    val line: Color,
    val line2: Color,
    val line3: Color,
    val solid: Color,
    val onSolid: Color,
    val onAccent: Color,
    val busyBg: Color,
    val busyLine: Color,
    val ok: Color,
    val okBg: Color,
    val okInk: Color,
    val warn: Color,
    val warnBg: Color,
    val warnInk: Color,
    val t1: Color,
    val t2: Color,
    val t3: Color,
    val t4: Color,
    val t5: Color,
    val orbOpacity: Float,
    // Colour-theme tokens
    val accent: Color,
    val accentInk: Color,
    val accentSoft: Color,
    val orb1: Color,
    val orb2: Color,
    val orb3: Color,
) {
    val tiles: List<Color> get() = listOf(t1, t2, t3, t4, t5)

    fun tile(color: TileColor): Color = when (color) {
        TileColor.T1 -> t1
        TileColor.T2 -> t2
        TileColor.T3 -> t3
        TileColor.T4 -> t4
        TileColor.T5 -> t5
    }

    /** Deterministic tile colour for a person, so avatars keep their colour everywhere. */
    fun tileFor(key: String): Color = tiles[Math.floorMod(key.hashCode(), tiles.size)]
}

/** Light/dark variants of a colour-theme token. */
private data class ThemeTokens(
    val accentLight: Color,
    val accentDark: Color,
    val accentInkLight: Color,
    val accentInkDark: Color,
    val accentSoftLight: Color,
    val accentSoftDark: Color,
    val orb1: Color,
    val orb2: Color,
    val orb3: Color,
)

private fun rgba(r: Int, g: Int, b: Int, a: Float) = Color(r, g, b, (a * 255).toInt())

private fun themeTokens(theme: ColorTheme): ThemeTokens = when (theme) {
    ColorTheme.Dawn -> ThemeTokens(
        accentLight = Color(0xFF5546FF), accentDark = Color(0xFF6A5CFF),
        accentInkLight = Color(0xFF3B2FD0), accentInkDark = Color(0xFFB4ACFF),
        accentSoftLight = rgba(85, 70, 255, .14f), accentSoftDark = rgba(130, 118, 255, .24f),
        orb1 = Color(0xFFFFB894), orb2 = Color(0xFF9DA6FF), orb3 = Color(0xFF8EE3CC),
    )
    ColorTheme.Lagoon -> ThemeTokens(
        accentLight = Color(0xFF0B7F8A), accentDark = Color(0xFF0B7F8A),
        accentInkLight = Color(0xFF06646D), accentInkDark = Color(0xFF7FDDE4),
        accentSoftLight = rgba(11, 127, 138, .14f), accentSoftDark = rgba(19, 156, 168, .26f),
        orb1 = Color(0xFF86E3E0), orb2 = Color(0xFF8EC5FF), orb3 = Color(0xFFB5EBA8),
    )
    ColorTheme.Sunset -> ThemeTokens(
        accentLight = Color(0xFFC9401F), accentDark = Color(0xFFC9401F),
        accentInkLight = Color(0xFFA8341A), accentInkDark = Color(0xFFFFB199),
        accentSoftLight = rgba(201, 64, 31, .13f), accentSoftDark = rgba(224, 83, 47, .26f),
        orb1 = Color(0xFFFFA585), orb2 = Color(0xFFFF9CC0), orb3 = Color(0xFFFFD37E),
    )
    ColorTheme.Orchid -> ThemeTokens(
        accentLight = Color(0xFF9333C9), accentDark = Color(0xFFA646DB),
        accentInkLight = Color(0xFF7A27AA), accentInkDark = Color(0xFFDDB0F7),
        accentSoftLight = rgba(147, 51, 201, .13f), accentSoftDark = rgba(166, 70, 219, .26f),
        orb1 = Color(0xFFF4B0E0), orb2 = Color(0xFFC9A8FF), orb3 = Color(0xFFA9B8FF),
    )
    ColorTheme.Grove -> ThemeTokens(
        accentLight = Color(0xFF2D7A4A), accentDark = Color(0xFF2D7A4A),
        accentInkLight = Color(0xFF22633B), accentInkDark = Color(0xFF9FE0B6),
        accentSoftLight = rgba(45, 122, 74, .14f), accentSoftDark = rgba(58, 148, 96, .26f),
        orb1 = Color(0xFFC3E88F), orb2 = Color(0xFF8FD8B5), orb3 = Color(0xFFF3D78A),
    )
}

private val Ink = Color(0xFF1A1B3A)
private val DarkInk = Color(0xFFF1F2FA)

/** Resolves every token for [dark] × [theme]. */
fun availablissColors(dark: Boolean, theme: ColorTheme): AvailablissColors {
    val t = themeTokens(theme)
    return if (!dark) {
        AvailablissColors(
            isDark = false,
            bg = Color(0xFFECEEF7),
            ink = Ink,
            muted = Color(0xFF4D5072),
            neutralInk = Color(0xFF3A3D5C),
            surface = rgba(255, 255, 255, .62f),
            glass = rgba(255, 255, 255, .50f),
            glassBorder = rgba(255, 255, 255, .80f),
            glassHi = rgba(255, 255, 255, .90f),
            shadow = rgba(43, 38, 120, .38f),
            line = Ink.copy(alpha = .07f),
            line2 = Ink.copy(alpha = .14f),
            line3 = Ink.copy(alpha = .28f),
            solid = Ink,
            onSolid = Color.White,
            onAccent = Color.White,
            busyBg = Color.White.copy(alpha = 0.4f), // recedes against free (surface) cells
            busyLine = Color(0xFFB9BCD6),
            ok = Color(0xFF0FA37F),
            okBg = Color(0xFFD3F5EA),
            okInk = Color(0xFF0D6B52),
            warn = Color(0xFFFF8A5E),
            warnBg = Color(0xFFFFE0CF),
            warnInk = Color(0xFF8A3410),
            t1 = Color(0xFFFFD3BF),
            t2 = Color(0xFFD6DAFF),
            t3 = Color(0xFFC8F1E4),
            t4 = Color(0xFFF5D6EE),
            t5 = Color(0xFFFFE7A3),
            orbOpacity = 0.85f,
            accent = t.accentLight,
            accentInk = t.accentInkLight,
            accentSoft = t.accentSoftLight,
            orb1 = t.orb1,
            orb2 = t.orb2,
            orb3 = t.orb3,
        )
    } else {
        AvailablissColors(
            isDark = true,
            bg = Color(0xFF0C0D1D),
            ink = DarkInk,
            muted = Color(0xFFA6A9C8),
            neutralInk = Color(0xFFC9CBE3),
            surface = rgba(255, 255, 255, .07f),
            glass = rgba(26, 28, 56, .50f),
            glassBorder = rgba(255, 255, 255, .12f),
            glassHi = rgba(255, 255, 255, .10f),
            shadow = rgba(0, 0, 0, .60f),
            line = Color.White.copy(alpha = .08f),
            line2 = Color.White.copy(alpha = .16f),
            line3 = Color.White.copy(alpha = .30f),
            solid = DarkInk,
            onSolid = Color(0xFF0C0D1D),
            onAccent = Color.White,
            busyBg = rgba(0, 0, 0, .28f), // sinks below the page so busy ≠ free in dark
            busyLine = Color(0xFF4A4D70),
            ok = Color(0xFF34D3A0),
            okBg = rgba(52, 211, 160, .16f),
            okInk = Color(0xFF7EE8C6),
            warn = Color(0xFFFF9A73),
            warnBg = rgba(255, 138, 94, .18f),
            warnInk = Color(0xFFFFBCA2),
            t1 = Color(0xFF5C3B2E),
            t2 = Color(0xFF363B78),
            t3 = Color(0xFF1E5045),
            t4 = Color(0xFF58304F),
            t5 = Color(0xFF5A4A1E),
            orbOpacity = 0.42f,
            accent = t.accentDark,
            accentInk = t.accentInkDark,
            accentSoft = t.accentSoftDark,
            orb1 = t.orb1,
            orb2 = t.orb2,
            orb3 = t.orb3,
        )
    }
}

/** Fixed brand colours of the venn logo (same in every theme). */
object BrandColors {
    val VennPeach = Color(0xFFFF9E78)
    val VennPeriwinkle = Color(0xFF8F9BFF)
    val VennMint = Color(0xFF6FD8BB)
    /** Text on the warn-coloured chat badge (always dark, per the concept). */
    val BadgeInk = Color(0xFF1A1B3A)
}
