package dev.joeherbert.availabliss.feature.appearance

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.joeherbert.availabliss.core.network.model.ColorTheme
import dev.joeherbert.availabliss.core.network.model.ThemeMode
import dev.joeherbert.availabliss.designsystem.component.AvSnackbarHost
import dev.joeherbert.availabliss.designsystem.component.AvailablissBackground
import dev.joeherbert.availabliss.designsystem.component.GlassPanel
import dev.joeherbert.availabliss.designsystem.component.OrbLayout
import dev.joeherbert.availabliss.designsystem.component.PillTone
import dev.joeherbert.availabliss.designsystem.component.ScreenHeader
import dev.joeherbert.availabliss.designsystem.component.SectionLabel
import dev.joeherbert.availabliss.designsystem.component.Segment
import dev.joeherbert.availabliss.designsystem.component.SegmentedControl
import dev.joeherbert.availabliss.designsystem.component.SegmentedStyle
import dev.joeherbert.availabliss.designsystem.component.StatusPill
import dev.joeherbert.availabliss.designsystem.component.UserMessageEffect
import dev.joeherbert.availabliss.designsystem.component.rememberSnackbarHostState
import dev.joeherbert.availabliss.designsystem.icon.AvIcons
import dev.joeherbert.availabliss.designsystem.theme.AvailablissColors
import dev.joeherbert.availabliss.designsystem.theme.AvailablissPreviewTheme
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import dev.joeherbert.availabliss.designsystem.theme.availablissColors

@Composable
fun AppearanceScreen(onBack: () -> Unit, viewModel: AppearanceViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = rememberSnackbarHostState()
    UserMessageEffect(state.message, snackbar, viewModel::onMessageShown)
    AvailablissBackground(OrbLayout.Appearance) {
        AppearanceContent(state, onBack, viewModel::selectMode, viewModel::selectTheme)
        AvSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
}

@Composable
fun AppearanceContent(
    state: AppearanceUiState,
    onBack: () -> Unit,
    onSelectMode: (ThemeMode) -> Unit,
    onSelectTheme: (ColorTheme) -> Unit,
) {
    val colors = AvailablissTheme.colors
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeader(title = "Appearance", onBack = onBack)

        GlassPanel(contentPadding = PaddingValues(14.dp)) {
            SectionLabel("Mode")
            SegmentedControl(
                segments = listOf(
                    Segment(ThemeMode.Light, "Light", AvIcons.Sun),
                    Segment(ThemeMode.Dark, "Dark", AvIcons.Moon),
                    Segment(ThemeMode.System, "System", AvIcons.Monitor),
                ),
                selected = state.mode,
                onSelect = onSelectMode,
                style = SegmentedStyle.Inset,
                segmentHeight = 44.dp,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        GlassPanel(contentPadding = PaddingValues(14.dp)) {
            SectionLabel("Colour theme")
            Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ColorTheme.entries.forEach { theme ->
                    ThemeSwatch(
                        theme = theme,
                        swatchColors = availablissColors(colors.isDark, theme),
                        selected = theme == state.theme,
                        onClick = { onSelectTheme(theme) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        PreviewCard()

        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(AvIcons.CloudCheck, null, tint = colors.okInk, modifier = Modifier.size(16.dp))
            Text(
                "Saved to your account · syncs to every device",
                style = AvailablissTheme.type.labelRegular.copy(fontWeight = FontWeight.Normal),
                color = colors.muted,
            )
        }
    }
}

val ColorTheme.label: String get() = name

@Composable
private fun ThemeSwatch(
    theme: ColorTheme,
    swatchColors: AvailablissColors,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AvailablissTheme.colors
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = theme.label },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .widthIn(max = 56.dp)
                .fillMaxWidth()
                .aspectRatio(1f)
                // Selected: 2.5dp ink outline, 3dp outside the swatch (CSS outline-offset).
                .drawBehind {
                    if (selected) {
                        val stroke = 2.5.dp.toPx()
                        val gap = 3.dp.toPx() + stroke / 2
                        drawRoundRect(
                            color = colors.ink,
                            topLeft = Offset(-gap, -gap),
                            size = Size(size.width + gap * 2, size.height + gap * 2),
                            cornerRadius = CornerRadius(18.dp.toPx() + gap),
                            style = Stroke(stroke),
                        )
                    }
                }
                .clip(shape)
                .background(swatchColors.bg)
                .then(if (!selected) Modifier.border(1.dp, colors.line2, shape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val u = size.width / 56f
                fun orb(color: Color, cx: Float, cy: Float, d: Float) {
                    val r = d * u / 2f
                    val center = Offset(cx * u, cy * u)
                    drawCircle(
                        Brush.radialGradient(0f to color, 0.6f to color.copy(alpha = 0.85f), 1f to color.copy(alpha = 0f), center = center, radius = r * 1.25f),
                        radius = r * 1.25f,
                        center = center,
                    )
                }
                // Positions from Appearance.dc.html (40/40/36px orbs, blurred 6px).
                orb(swatchColors.orb1, -8f + 20f, -8f + 20f, 40f)
                orb(swatchColors.orb2, 56f + 10f - 20f, 4f + 20f, 40f)
                orb(swatchColors.orb3, 4f + 18f, 56f + 12f - 18f, 36f)
            }
            Box(
                Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(swatchColors.accent)
                    .border(2.dp, Color.White.copy(alpha = 0.85f), CircleShape),
            )
        }
        Text(theme.label, style = AvailablissTheme.type.captionStrong, color = colors.ink)
    }
}

/** Live preview: a matching weekend band, free and busy days, and the two key buttons. */
@Composable
private fun PreviewCard() {
    val colors = AvailablissTheme.colors
    GlassPanel(contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Preview", Modifier.weight(1f))
            StatusPill("Works", PillTone.Mint)
        }
        Text("Lake District crew", style = AvailablissTheme.type.titleXSmall.copy(fontSize = 18.sp), color = colors.ink)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                PreviewCell("13", "from 18:00", RoundedCornerShape(14.dp, 5.dp, 5.dp, 14.dp), colors.accent, colors.onAccent, Modifier.weight(1f))
                PreviewCell("14", "all free", RoundedCornerShape(5.dp), colors.accent, colors.onAccent, Modifier.weight(1f))
                PreviewCell("15", "all free", RoundedCornerShape(5.dp, 14.dp, 14.dp, 5.dp), colors.accent, colors.onAccent, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                PreviewCell("20", "", RoundedCornerShape(14.dp), colors.surface, colors.ink, Modifier.weight(1f), border = colors.glassBorder)
                PreviewCell("21", "Tom busy", RoundedCornerShape(14.dp), colors.busyBg, colors.ink, Modifier.weight(1f), border = colors.glassBorder, alpha = 0.5f)
                PreviewCell("22", "", RoundedCornerShape(14.dp), colors.surface, colors.ink, Modifier.weight(1f), border = colors.glassBorder)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PreviewButton("Propose", colors.accent, colors.onAccent, Modifier.weight(1f))
            PreviewButton("Add to calendar", colors.solid, colors.onSolid, Modifier.weight(1f))
        }
    }
}

@Composable
private fun PreviewCell(
    day: String,
    note: String,
    shape: Shape,
    background: Color,
    content: Color,
    modifier: Modifier = Modifier,
    border: Color? = null,
    alpha: Float = 1f,
) {
    Column(
        modifier
            .height(48.dp)
            .clip(shape)
            .background(background.copy(alpha = background.alpha * alpha))
            .then(if (border != null) Modifier.border(1.dp, border.copy(alpha = border.alpha * alpha), shape) else Modifier)
            .padding(horizontal = 10.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(day, style = AvailablissTheme.type.bodyStrong.copy(fontSize = 15.sp), color = content.copy(alpha = content.alpha * if (alpha < 1f) 0.6f else 1f))
        Text(note.ifEmpty { " " }, style = AvailablissTheme.type.nano, color = content.copy(alpha = if (alpha < 1f) 0.6f else 1f))
    }
}

@Composable
private fun PreviewButton(label: String, background: Color, content: Color, modifier: Modifier = Modifier) {
    Box(
        modifier.height(42.dp).clip(RoundedCornerShape(13.dp)).background(background),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = AvailablissTheme.type.bodySmallStrong, color = content, maxLines = 1)
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun AppearancePreview() {
    AvailablissPreviewTheme {
        AvailablissBackground(OrbLayout.Appearance) {
            AppearanceContent(AppearanceUiState(mode = ThemeMode.Light, theme = ColorTheme.Dawn), {}, {}, {})
        }
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun AppearanceDarkPreview() {
    AvailablissPreviewTheme(dark = true, theme = ColorTheme.Orchid) {
        AvailablissBackground(OrbLayout.Appearance) {
            AppearanceContent(AppearanceUiState(mode = ThemeMode.Dark, theme = ColorTheme.Orchid), {}, {}, {})
        }
    }
}
