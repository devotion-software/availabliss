package dev.joeherbert.availabliss.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import dev.joeherbert.availabliss.designsystem.theme.Shapes

/** StatusPill tones: indigo / mint / apricot / neutral. */
enum class PillTone { Indigo, Mint, Apricot, Neutral }

@Composable
fun PillTone.colors(): Pair<Color, Color> {
    val c = AvailablissTheme.colors
    return when (this) {
        PillTone.Indigo -> c.accentSoft to c.accentInk
        PillTone.Mint -> c.okBg to c.okInk
        PillTone.Apricot -> c.warnBg to c.warnInk
        PillTone.Neutral -> c.line to c.neutralInk
    }
}

/** Status badge ("2 weekends work", "Needs your reply", "Accepted"). */
@Composable
fun StatusPill(
    text: String,
    tone: PillTone,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    style: TextStyle = AvailablissTheme.type.micro,
    padding: PaddingValues = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
) {
    val (bg, fg) = tone.colors()
    Row(
        modifier.clip(Shapes.pill).background(bg).padding(padding),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { Icon(it, null, tint = fg, modifier = Modifier.size(12.dp)) }
        Text(text, style = style, color = fg, maxLines = 1)
    }
}

/**
 * Progress ring (sync status, proposal acceptance). [progress] 0..1, drawn from 12
 * o'clock with round caps; optional [content] centred inside.
 */
@Composable
fun SyncRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
    strokeWidth: Dp = 3.dp,
    color: Color = AvailablissTheme.colors.accent,
    trackColor: Color = AvailablissTheme.colors.line2,
    content: (@Composable BoxScope.() -> Unit)? = null,
) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(500), label = "ring")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2
            val arcSize = androidx.compose.ui.geometry.Size(this.size.width - stroke, this.size.height - stroke)
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
            drawArc(trackColor, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            if (animated > 0f) {
                drawArc(color, -90f, 360f * animated, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        content?.invoke(this)
    }
}

/** Small notification dot for tab icons. */
@Composable
fun BadgeDot(modifier: Modifier = Modifier) {
    val c = AvailablissTheme.colors
    Box(modifier.size(9.dp).clip(Shapes.pill).background(c.bg).padding(1.5.dp)) {
        Box(Modifier.fillMaxSize().clip(Shapes.pill).background(c.warn))
    }
}
