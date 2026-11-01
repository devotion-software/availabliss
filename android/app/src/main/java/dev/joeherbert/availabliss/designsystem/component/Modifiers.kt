package dev.joeherbert.availabliss.designsystem.component

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal fun Modifier.drawWithCacheDashed(color: Color, shape: Shape, width: Dp, dash: Dp, gap: Dp): Modifier =
    drawWithCache {
        val strokePx = width.toPx()
        val outline = shape.createOutline(size, layoutDirection, this)
        val path = Path().apply { addOutline(outline) }
        val stroke = Stroke(
            width = strokePx * 2, // half is clipped away by the shape
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash.toPx(), gap.toPx())),
        )
        onDrawWithContent {
            drawContent()
            clipPath(path) { drawPath(path, color, style = stroke) }
        }
    }

/**
 * 135° diagonal stripes, like CSS
 * `repeating-linear-gradient(135deg, stripe 0 [stripeWidth], gap [stripeWidth] [period])`.
 * Used for "blocked by hand" cells and timeline bars.
 */
fun Modifier.stripes(color: Color, stripeWidth: Dp = 6.dp, period: Dp = 12.dp): Modifier = drawWithCache {
    val w = stripeWidth.toPx()
    val p = period.toPx()
    val diag = size.width + size.height
    onDrawBehind {
        clipRect {
            // Lines running from bottom-left to top-right, stepped along the x axis.
            var x = -size.height
            while (x < diag) {
                drawLine(color = color, start = Offset(x, size.height), end = Offset(x + size.height, 0f), strokeWidth = w)
                x += p * 1.4142f
            }
        }
    }
}
