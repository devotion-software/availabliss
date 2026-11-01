package dev.joeherbert.availabliss.designsystem.component

import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.joeherbert.availabliss.designsystem.theme.AvailablissColors
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import dev.joeherbert.availabliss.designsystem.theme.Shapes
import dev.joeherbert.availabliss.designsystem.theme.Spacing

/**
 * CSS-style outer shadow (`0 18px 40px -22px shadow`), painted only *outside* the
 * shape like `box-shadow`, so translucent glass doesn't look muddy. Uses a paint
 * shadow layer (rendered on API 28+; omitted on 26–27).
 */
fun Modifier.softShadow(
    shape: Shape,
    color: Color,
    offsetY: Dp = 18.dp,
    blur: Dp = 40.dp,
    spread: Dp = (-22).dp,
): Modifier = drawBehind {
    if (color.alpha == 0f) return@drawBehind
    val spreadPx = spread.toPx()
    val shapePath = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawBehind)) }
    val shadowSize = Size((size.width + 2 * spreadPx).coerceAtLeast(1f), (size.height + 2 * spreadPx).coerceAtLeast(1f))
    val shadowPath = Path().apply {
        addOutline(shape.createOutline(shadowSize, layoutDirection, this@drawBehind))
        translate(Offset(-spreadPx, -spreadPx))
    }
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.toArgb()
        // CSS blur radius ≈ 2σ; Android's shadow radius ≈ σ / 0.57735.
        setShadowLayer(blur.toPx() * 0.85f, 0f, offsetY.toPx(), color.toArgb())
    }
    drawIntoCanvas { canvas ->
        val native = canvas.nativeCanvas
        native.save()
        native.clipOutPath(shapePath.asAndroidPath())
        native.drawPath(shadowPath.asAndroidPath(), paint)
        native.restore()
    }
}

/**
 * The "Dawn glass" panel treatment: translucent `glass` fill, 1px `glass-border`,
 * 1px inner top `glass-hi` highlight and the soft outer shadow. Android has no
 * backdrop blur, so the fill sits over the (blurred) orb layer instead.
 */
fun Modifier.glass(
    shape: Shape,
    colors: AvailablissColors,
    fill: Color = colors.glass,
    shadow: Boolean = true,
    border: Color = colors.glassBorder,
): Modifier = this
    .then(if (shadow) Modifier.softShadow(shape, colors.shadow) else Modifier)
    .clip(shape)
    .background(fill)
    .drawBehind {
        val px = 1.dp.toPx()
        drawRect(colors.glassHi, topLeft = Offset(0f, px), size = Size(size.width, px))
    }
    .border(1.dp, border, shape)

/** Composable-scoped shorthand for [glass] using the current theme. */
@Composable
fun Modifier.glass(shape: Shape = Shapes.card, shadow: Boolean = true): Modifier =
    glass(shape, AvailablissTheme.colors, shadow = shadow)

/** A glass card. Pass [onClick] to make the whole panel tappable. */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = Shapes.card,
    contentPadding: PaddingValues = PaddingValues(Spacing.xl),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(Spacing.m),
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .glass(shape)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick) else Modifier)
            .padding(contentPadding),
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

/** An opaque `surface` card with a `glass-border` (used inside sheets). */
@Composable
fun SurfacePanel(
    modifier: Modifier = Modifier,
    shape: Shape = Shapes.panel,
    contentPadding: PaddingValues = PaddingValues(horizontal = Spacing.xl, vertical = Spacing.xxs),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = AvailablissTheme.colors
    Column(
        modifier = modifier.clip(shape).background(colors.surface).border(1.dp, colors.glassBorder, shape).padding(contentPadding),
        content = content,
    )
}
