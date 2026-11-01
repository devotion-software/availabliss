package dev.joeherbert.availabliss.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.joeherbert.availabliss.R

/*
 * Bricolage Grotesque (display) + Instrument Sans (body) via Downloadable Google Fonts.
 * If Google Play services can't provide them, Compose falls back to the system sans.
 */
private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

private val bricolage = GoogleFont("Bricolage Grotesque")
private val instrument = GoogleFont("Instrument Sans")

val DisplayFontFamily = FontFamily(
    Font(googleFont = bricolage, fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = bricolage, fontProvider = provider, weight = FontWeight.SemiBold),
    Font(googleFont = bricolage, fontProvider = provider, weight = FontWeight.Bold),
    Font(googleFont = bricolage, fontProvider = provider, weight = FontWeight.ExtraBold),
)

val BodyFontFamily = FontFamily(
    Font(googleFont = instrument, fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = instrument, fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = instrument, fontProvider = provider, weight = FontWeight.SemiBold),
    Font(googleFont = instrument, fontProvider = provider, weight = FontWeight.Bold),
)

/** Display weights from the concept (650 / 750 resolve to the nearest loaded weight). */
object AvWeights {
    val Display650 = FontWeight(650)
    val Display700 = FontWeight.Bold
    val Display750 = FontWeight(750)
}

private val DisplayTracking = (-0.025).em

private fun display(size: Int, weight: FontWeight, lineHeight: Float) = TextStyle(
    fontFamily = DisplayFontFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = (size * lineHeight).sp,
    letterSpacing = DisplayTracking,
)

private fun body(size: Int, weight: FontWeight, lineHeight: Float = 1.35f) = TextStyle(
    fontFamily = BodyFontFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = (size * lineHeight).sp,
)

/**
 * The type scale used in the concept. Display styles use Bricolage Grotesque with
 * -0.025em tracking; body styles use Instrument Sans.
 */
@Immutable
data class AvailablissTypography(
    /** 44 / 750: sign-in hero. */
    val hero: TextStyle = display(44, AvWeights.Display750, 1.0f),
    /** 40 / 750: "Everyone's in." */
    val displayLarge: TextStyle = display(40, AvWeights.Display750, 1.0f),
    /** 32 / 700: "Hi, Sam". */
    val displayMedium: TextStyle = display(32, AvWeights.Display700, 1.05f),
    /** 30 / 700: onboarding titles. */
    val displaySmall: TextStyle = display(30, AvWeights.Display700, 1.05f),
    /** 26 / 700: proposal date. */
    val headlineLarge: TextStyle = display(26, AvWeights.Display700, 1.05f),
    /** 24 / 700: screen titles. */
    val headlineMedium: TextStyle = display(24, AvWeights.Display700, 1.1f),
    /** 22 / 700: group header, sheet titles, brand wordmark. */
    val headlineSmall: TextStyle = display(22, AvWeights.Display700, 1.1f),
    /** 22 / 650: next-event time. */
    val titleLarge: TextStyle = display(22, AvWeights.Display650, 1.15f),
    /** 20 / 650: section titles ("Your groups"), sticky totals. */
    val titleMedium: TextStyle = display(20, AvWeights.Display650, 1.15f),
    /** 19 / 650: calendar heading. */
    val titleSmall: TextStyle = display(19, AvWeights.Display650, 1.15f),
    /** 17 / 650: week label, poll question, day numbers. */
    val titleXSmall: TextStyle = display(17, AvWeights.Display650, 1.15f),
    /** 16 / 650: card titles in display face. */
    val cardTitle: TextStyle = display(16, AvWeights.Display650, 1.2f),

    /** 16 / 400 / 1.45: intro paragraphs, inputs. */
    val bodyLarge: TextStyle = body(16, FontWeight.Normal, 1.45f),
    /** 16 / 600: list primary text, button labels. */
    val bodyStrong: TextStyle = body(16, FontWeight.SemiBold),
    /** 15 / 600: row titles. */
    val bodyMedium: TextStyle = body(15, FontWeight.SemiBold),
    /** 15 / 500: calendar names. */
    val bodyMediumRegular: TextStyle = body(15, FontWeight.Medium),
    /** 14 / 400 / 1.4: chat bubbles, descriptions. */
    val body: TextStyle = body(14, FontWeight.Normal, 1.4f),
    /** 14 / 600: small buttons and row labels. */
    val bodySmallStrong: TextStyle = body(14, FontWeight.SemiBold),
    /** 13 / 600: section labels, chips. */
    val label: TextStyle = body(13, FontWeight.SemiBold),
    /** 13 / 500: secondary text. */
    val labelRegular: TextStyle = body(13, FontWeight.Medium),
    /** 12 / 500: captions and subtitles. */
    val caption: TextStyle = body(12, FontWeight.Medium),
    /** 12 / 600: emphasised captions, pills. */
    val captionStrong: TextStyle = body(12, FontWeight.SemiBold),
    /** 11 / 600: tab labels, badges, legends. */
    val micro: TextStyle = body(11, FontWeight.SemiBold),
    /** 10 / 600: tiny meta (month abbreviations, timeline ticks). */
    val nano: TextStyle = body(10, FontWeight.SemiBold),
)
