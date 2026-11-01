package dev.joeherbert.availabliss.designsystem.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import dev.joeherbert.availabliss.core.network.model.ColorTheme
import dev.joeherbert.availabliss.core.network.model.ThemeMode

val LocalAvailablissColors = staticCompositionLocalOf { availablissColors(dark = false, theme = ColorTheme.Dawn) }
val LocalAvailablissTypography = staticCompositionLocalOf { AvailablissTypography() }

/** Accessors for the current tokens, e.g. `AvailablissTheme.colors.accent`. */
object AvailablissTheme {
    val colors: AvailablissColors
        @Composable @ReadOnlyComposable get() = LocalAvailablissColors.current

    val type: AvailablissTypography
        @Composable @ReadOnlyComposable get() = LocalAvailablissTypography.current
}

@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
    ThemeMode.System -> isSystemInDarkTheme()
}

/**
 * Root theme. Material 3 components underneath are restyled with the Availabliss
 * tokens (colour scheme + typography mapping), and theme changes cross-fade like
 * the concept's 0.3s transition.
 */
@Composable
fun AvailablissTheme(
    mode: ThemeMode = ThemeMode.System,
    colorTheme: ColorTheme = ColorTheme.Dawn,
    animate: Boolean = true,
    content: @Composable () -> Unit,
) {
    val target = availablissColors(dark = mode.isDark(), theme = colorTheme)
    val colors = if (animate) animatedColors(target) else target
    val typography = remember { AvailablissTypography() }
    CompositionLocalProvider(
        LocalAvailablissColors provides colors,
        LocalAvailablissTypography provides typography,
        LocalContentColor provides colors.ink,
    ) {
        MaterialTheme(
            colorScheme = colors.toMaterialColorScheme(),
            typography = typography.toMaterialTypography(),
            content = content,
        )
    }
}

/** Mini-theme for previews: a fixed mode and theme, no animation. */
@Composable
fun AvailablissPreviewTheme(dark: Boolean = false, theme: ColorTheme = ColorTheme.Dawn, content: @Composable () -> Unit) {
    AvailablissTheme(mode = if (dark) ThemeMode.Dark else ThemeMode.Light, colorTheme = theme, animate = false, content = content)
}

@Composable
private fun animatedColors(target: AvailablissColors): AvailablissColors {
    @Composable
    fun Color.animate(label: String) = animateColorAsState(this, tween(300), label = label).value
    return AvailablissColors(
        isDark = target.isDark,
        bg = target.bg.animate("bg"),
        ink = target.ink.animate("ink"),
        muted = target.muted.animate("muted"),
        neutralInk = target.neutralInk.animate("neutralInk"),
        surface = target.surface.animate("surface"),
        glass = target.glass.animate("glass"),
        glassBorder = target.glassBorder.animate("glassBorder"),
        glassHi = target.glassHi.animate("glassHi"),
        shadow = target.shadow.animate("shadow"),
        line = target.line.animate("line"),
        line2 = target.line2.animate("line2"),
        line3 = target.line3.animate("line3"),
        solid = target.solid.animate("solid"),
        onSolid = target.onSolid.animate("onSolid"),
        onAccent = target.onAccent,
        busyBg = target.busyBg.animate("busyBg"),
        busyLine = target.busyLine.animate("busyLine"),
        ok = target.ok.animate("ok"),
        okBg = target.okBg.animate("okBg"),
        okInk = target.okInk.animate("okInk"),
        warn = target.warn.animate("warn"),
        warnBg = target.warnBg.animate("warnBg"),
        warnInk = target.warnInk.animate("warnInk"),
        t1 = target.t1.animate("t1"),
        t2 = target.t2.animate("t2"),
        t3 = target.t3.animate("t3"),
        t4 = target.t4.animate("t4"),
        t5 = target.t5.animate("t5"),
        orbOpacity = animateFloatAsState(target.orbOpacity, tween(300), label = "orbOpacity").value,
        accent = target.accent.animate("accent"),
        accentInk = target.accentInk.animate("accentInk"),
        accentSoft = target.accentSoft.animate("accentSoft"),
        orb1 = target.orb1.animate("orb1"),
        orb2 = target.orb2.animate("orb2"),
        orb3 = target.orb3.animate("orb3"),
    )
}

private fun AvailablissColors.toMaterialColorScheme() = if (isDark) {
    darkColorScheme(
        primary = accent, onPrimary = onAccent, primaryContainer = accentSoft, onPrimaryContainer = accentInk,
        secondary = solid, onSecondary = onSolid,
        background = bg, onBackground = ink, surface = bg, onSurface = ink,
        surfaceVariant = surface, onSurfaceVariant = muted, surfaceContainer = bg, surfaceContainerHigh = bg,
        surfaceContainerLow = bg, surfaceContainerHighest = bg, surfaceContainerLowest = bg,
        outline = line2, outlineVariant = line, error = warn, onError = Color.White,
        errorContainer = warnBg, onErrorContainer = warnInk, scrim = Color(0xFF0C0D1D),
    )
} else {
    lightColorScheme(
        primary = accent, onPrimary = onAccent, primaryContainer = accentSoft, onPrimaryContainer = accentInk,
        secondary = solid, onSecondary = onSolid,
        background = bg, onBackground = ink, surface = bg, onSurface = ink,
        surfaceVariant = surface, onSurfaceVariant = muted, surfaceContainer = bg, surfaceContainerHigh = bg,
        surfaceContainerLow = bg, surfaceContainerHighest = bg, surfaceContainerLowest = bg,
        outline = line2, outlineVariant = line, error = warn, onError = Color.White,
        errorContainer = warnBg, onErrorContainer = warnInk, scrim = Color(0xFF0C0D1D),
    )
}

private fun AvailablissTypography.toMaterialTypography() = Typography(
    displayLarge = hero,
    displayMedium = displayLarge,
    displaySmall = displayMedium,
    headlineLarge = displaySmall,
    headlineMedium = headlineMedium,
    headlineSmall = headlineSmall,
    titleLarge = titleMedium,
    titleMedium = bodyStrong,
    titleSmall = bodySmallStrong,
    bodyLarge = bodyLarge,
    bodyMedium = body,
    bodySmall = caption,
    labelLarge = bodySmallStrong,
    labelMedium = captionStrong,
    labelSmall = micro,
)
