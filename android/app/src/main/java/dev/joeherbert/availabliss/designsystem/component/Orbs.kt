package dev.joeherbert.availabliss.designsystem.component

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.joeherbert.availabliss.designsystem.theme.AvailablissColors
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import dev.joeherbert.availabliss.designsystem.theme.BrandColors

/** Which theme orb colour an orb uses. */
enum class OrbColor { One, Two, Three }

/**
 * One orb, positioned like the concept's CSS: a [size] circle offset from the left or
 * right and top or bottom edge (negative = off-canvas).
 */
data class Orb(
    val size: Dp,
    val color: OrbColor,
    val left: Dp? = null,
    val right: Dp? = null,
    val top: Dp? = null,
    val bottom: Dp? = null,
)

/** Per-screen orb arrangements, transcribed from each `.dc.html`. */
enum class OrbLayout(val orbs: List<Orb>) {
    SignIn(listOf(Orb(380.dp, OrbColor.One, left = (-160).dp, top = (-60).dp), Orb(360.dp, OrbColor.Two, right = (-170).dp, top = 80.dp), Orb(340.dp, OrbColor.Three, left = 10.dp, top = 330.dp))),
    Calendars(listOf(Orb(320.dp, OrbColor.Three, right = (-120).dp, top = (-90).dp), Orb(320.dp, OrbColor.Two, left = (-170).dp, top = 280.dp), Orb(300.dp, OrbColor.One, right = (-110).dp, bottom = (-60).dp))),
    Main(listOf(Orb(340.dp, OrbColor.One, left = (-140).dp, top = (-110).dp), Orb(300.dp, OrbColor.Two, right = (-150).dp, top = 230.dp), Orb(300.dp, OrbColor.Three, left = (-90).dp, bottom = (-40).dp))),
    MyTime(listOf(Orb(320.dp, OrbColor.Three, left = (-140).dp, top = (-90).dp), Orb(300.dp, OrbColor.One, right = (-150).dp, top = 260.dp), Orb(300.dp, OrbColor.Two, left = (-80).dp, bottom = (-60).dp))),
    GroupCalendar(listOf(Orb(320.dp, OrbColor.Two, right = (-130).dp, top = (-100).dp), Orb(300.dp, OrbColor.One, left = (-150).dp, top = 300.dp), Orb(280.dp, OrbColor.Three, right = (-80).dp, bottom = 40.dp))),
    Timing(listOf(Orb(320.dp, OrbColor.Two, left = (-130).dp, top = (-100).dp), Orb(300.dp, OrbColor.One, right = (-140).dp, top = 330.dp), Orb(300.dp, OrbColor.Three, left = (-60).dp, bottom = (-80).dp))),
    Proposal(listOf(Orb(340.dp, OrbColor.Three, left = (-120).dp, top = (-60).dp), Orb(300.dp, OrbColor.One, right = (-150).dp, top = 200.dp), Orb(320.dp, OrbColor.Two, left = (-40).dp, bottom = (-110).dp))),
    Chat(listOf(Orb(320.dp, OrbColor.One, right = (-130).dp, top = (-80).dp), Orb(320.dp, OrbColor.Three, left = (-170).dp, top = 320.dp), Orb(300.dp, OrbColor.Two, right = (-120).dp, bottom = (-40).dp))),
    Appearance(listOf(Orb(340.dp, OrbColor.One, left = (-130).dp, top = (-100).dp), Orb(320.dp, OrbColor.Two, right = (-150).dp, top = 250.dp), Orb(300.dp, OrbColor.Three, left = (-70).dp, bottom = (-70).dp))),
}

private fun AvailablissColors.orb(color: OrbColor) = when (color) {
    OrbColor.One -> orb1
    OrbColor.Two -> orb2
    OrbColor.Three -> orb3
}

/**
 * Three soft colour orbs. On API 31+ solid circles are blurred with a RenderEffect
 * (60dp, as in the concept); older versions draw radial gradients to fake the blur.
 */
@Composable
fun Orbs(layout: OrbLayout, modifier: Modifier = Modifier) {
    val colors = AvailablissTheme.colors
    val realBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    Canvas(
        modifier
            .fillMaxSize()
            .clearAndSetSemantics { }
            .then(if (realBlur) Modifier.blur(60.dp, BlurredEdgeTreatment.Unbounded) else Modifier),
    ) {
        layout.orbs.forEach { orb ->
            val d = orb.size.toPx()
            val r = d / 2f
            val x = orb.left?.let { it.toPx() + r } ?: (size.width - (orb.right?.toPx() ?: 0f) - r)
            val y = orb.top?.let { it.toPx() + r } ?: (size.height - (orb.bottom?.toPx() ?: 0f) - r)
            val c = colors.orb(orb.color)
            val center = Offset(x, y)
            if (realBlur) {
                drawCircle(c, radius = r, center = center, alpha = colors.orbOpacity)
            } else {
                val op = colors.orbOpacity
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to c.copy(alpha = op),
                        0.55f to c.copy(alpha = op * 0.8f),
                        1f to Color.Transparent,
                        center = center,
                        radius = r * 1.3f,
                    ),
                    radius = r * 1.3f,
                    center = center,
                )
            }
        }
    }
}

/** Screen background: `bg` colour + the screen's orbs, with content on top. */
@Composable
fun AvailablissBackground(
    layout: OrbLayout,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.fillMaxSize().background(AvailablissTheme.colors.bg)) {
        Orbs(layout)
        content()
    }
}

/** The three-circle Availabliss mark. */
@Composable
fun VennLogo(size: Dp, modifier: Modifier = Modifier, contentDescription: String? = "Availabliss logo") {
    val dark = AvailablissTheme.colors.isDark
    Canvas(
        modifier
            .size(size)
            .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier.clearAndSetSemantics { }),
    ) {
        val u = this.size.width / 40f
        val r = 10f * u
        // In dark mode the concept blends the circles with `screen`; approximate with slight transparency.
        val a = if (dark) 0.9f else 1f
        drawCircle(BrandColors.VennPeach, r, Offset(14f * u, 15f * u), alpha = a)
        drawCircle(BrandColors.VennPeriwinkle, r, Offset(26f * u, 15f * u), alpha = if (dark) 0.85f else 0.95f)
        drawCircle(BrandColors.VennMint, r, Offset(20f * u, 25f * u), alpha = if (dark) 0.85f else 0.95f)
    }
}
