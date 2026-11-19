package dev.joeherbert.availabliss.core.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dev.joeherbert.availabliss.MainActivity
import dev.joeherbert.availabliss.core.data.Appearance
import dev.joeherbert.availabliss.core.deeplink.DeepLink
import dev.joeherbert.availabliss.core.network.model.ColorTheme
import dev.joeherbert.availabliss.core.network.model.ThemeMode
import dev.joeherbert.availabliss.designsystem.theme.AvailablissColors
import dev.joeherbert.availabliss.designsystem.theme.availablissColors
import androidx.glance.color.ColorProviders as GlanceColors

/**
 * The app's colours for a widget: the colour theme you chose, in light or dark as you set the
 * app's appearance (following the system when that's "System"). Glance can't load the app's
 * Google Fonts, so text uses the system font at the app's sizes and weights.
 */
internal fun widgetColors(appearance: Appearance): GlanceColors {
    val light = scheme(availablissColors(dark = false, theme = appearance.theme))
    val dark = scheme(availablissColors(dark = true, theme = appearance.theme))
    return when (appearance.mode) {
        ThemeMode.Light -> ColorProviders(light = light, dark = light)
        ThemeMode.Dark -> ColorProviders(light = dark, dark = dark)
        ThemeMode.System -> ColorProviders(light = light, dark = dark)
    }
}

private fun scheme(c: AvailablissColors): ColorScheme {
    val base = if (c.isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = c.accent,
        onPrimary = c.onAccent,
        primaryContainer = c.accentSoft,
        onPrimaryContainer = c.accentInk,
        secondary = c.accentInk,
        background = c.bg,
        onBackground = c.ink,
        surface = c.bg,
        onSurface = c.ink,
        surfaceVariant = c.accentSoft,
        onSurfaceVariant = c.muted,
        outline = c.line2,
        outlineVariant = c.line,
    )
}

/** Opens the app at [link] (or just opens it), through MainActivity's deep link handling. */
internal fun openApp(context: Context, link: DeepLink? = null): Action {
    val intent = Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        data = link?.toUri()
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    return actionStartActivity(intent)
}

/** The appearance the widget is drawn in (for its orb background). */
internal val LocalWidgetAppearance = staticCompositionLocalOf { Appearance() }

/**
 * The widget's rounded background, tappable as a whole with [onClick]: the app's background colour
 * with its three soft orbs in your theme's colours, like the app's screens. In dark mode a scrim of the
 * background colour fades the orbs (as the app shows them fainter in dark), so one picture serves both.
 */
@Composable
internal fun WidgetCard(onClick: Action, content: @Composable ColumnScope.() -> Unit) {
    val appearance = LocalWidgetAppearance.current
    val size = LocalSize.current
    val orbs = remember(appearance.theme, size) { orbBitmap(appearance.theme, size.width.value, size.height.value) }
    val bgDark = availablissColors(dark = true, theme = appearance.theme).bg
    // Faded orbs in dark: Light mode never, Dark always, System with the system.
    val scrim = when (appearance.mode) {
        ThemeMode.Light -> ColorProvider(Color.Transparent, Color.Transparent)
        ThemeMode.Dark -> ColorProvider(bgDark.copy(alpha = DARK_SCRIM), bgDark.copy(alpha = DARK_SCRIM))
        ThemeMode.System -> ColorProvider(Color.Transparent, bgDark.copy(alpha = DARK_SCRIM))
    }
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .cornerRadius(20.dp)
            .background(GlanceTheme.colors.background)
            .clickable(onClick),
    ) {
        Image(ImageProvider(orbs), contentDescription = null, contentScale = ContentScale.Crop, modifier = GlanceModifier.fillMaxSize())
        Spacer(GlanceModifier.fillMaxSize().background(scrim))
        Column(
            modifier = GlanceModifier.fillMaxSize().padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalAlignment = Alignment.Start,
            content = content,
        )
    }
}

/** How much the dark background covers the orbs: the app's dark orbs are about half as strong. */
private const val DARK_SCRIM = 0.5f

/** Pixels per dp in the orb picture: enough for soft gradients, small for the widget's memory limit. */
private const val ORB_SCALE = 2f

/** The three orbs (top left, right, bottom left) at [widthDp] × [heightDp], as soft radial fades. */
private fun orbBitmap(theme: ColorTheme, widthDp: Float, heightDp: Float): Bitmap {
    val w = (widthDp.coerceIn(80f, 420f) * ORB_SCALE).toInt()
    val h = (heightDp.coerceIn(80f, 420f) * ORB_SCALE).toInt()
    val colors = availablissColors(dark = false, theme = theme)
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val side = maxOf(w, h).toFloat()
    fun orb(color: Color, cx: Float, cy: Float, radius: Float, strength: Float) {
        val center = color.copy(alpha = strength).toArgb()
        val edge = color.copy(alpha = 0f).toArgb()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = RadialGradient(cx, cy, radius, center, edge, Shader.TileMode.CLAMP) }
        canvas.drawCircle(cx, cy, radius, paint)
    }
    orb(colors.orb1, 0f, 0f, side * 0.5f, 0.85f)
    orb(colors.orb2, w.toFloat(), h * 0.45f, side * 0.45f, 0.85f)
    orb(colors.orb3, w * 0.2f, h * 1.05f, side * 0.42f, 0.75f)
    return bitmap
}

/** "NEXT PLAN", in the accent colour above the widget's content. */
@Composable
internal fun WidgetLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold),
        maxLines = 1,
    )
    Spacer(GlanceModifier.height(6.dp))
}

/** A headline and a muted line under it, for the signed-out and empty states. */
@Composable
internal fun WidgetMessage(title: String, detail: String? = null) {
    Text(
        text = title,
        style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold),
        maxLines = 2,
    )
    if (detail != null) {
        Spacer(GlanceModifier.height(2.dp))
        Text(
            text = detail,
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
            maxLines = 2,
        )
    }
}

/** What both widgets show before an account is signed in, or before the app has written anything. */
@Composable
internal fun ColumnScope.NotReady(snapshot: WidgetSnapshot?) {
    if (snapshot == null) {
        WidgetMessage("Open Availabliss", "to see your plans here")
    } else {
        WidgetMessage("Sign in to Availabliss", "to see your plans here")
    }
}
