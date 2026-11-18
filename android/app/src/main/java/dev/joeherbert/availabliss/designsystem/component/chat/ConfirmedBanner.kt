package dev.joeherbert.availabliss.designsystem.component.chat

import android.os.SystemClock
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.joeherbert.availabliss.designsystem.component.glass
import dev.joeherbert.availabliss.designsystem.icon.AvIcons
import dev.joeherbert.availabliss.designsystem.theme.AvWeights
import dev.joeherbert.availabliss.designsystem.theme.AvailablissPreviewTheme
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import dev.joeherbert.availabliss.designsystem.theme.Shapes
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * "Everyone's in" banner shown in chat when a proposal is confirmed: a success-tinted
 * card with a check, the plan's title and when / where, and [footer] (the Add to
 * calendar button) underneath. Tapping the card outside the footer calls [onClick].
 *
 * With [burstKey] set, the banner plays a one-off confetti burst the first time it's
 * placed on screen (once per key per app session, and never with animations turned off).
 */
@Composable
fun ConfirmedBanner(
    title: String,
    detail: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    burstKey: String? = null,
    /** "Everyone's in", or "It's on" when it was confirmed before everyone accepted. */
    headline: String = "Everyone's in",
    footer: @Composable () -> Unit = {},
) {
    val colors = AvailablissTheme.colors
    val shape = Shapes.row
    val confetti = listOf(colors.accent, colors.ok, colors.warn, colors.t4)
    val label = "$headline. $title, $detail"

    var placed by remember { mutableStateOf(false) }
    val reduceMotion = rememberReduceMotion()
    val haptics = LocalHapticFeedback.current
    val progress = remember(burstKey) { Animatable(0f) }
    var bursting by remember(burstKey) { mutableStateOf(false) }
    LaunchedEffect(burstKey, placed) {
        if (burstKey == null || !placed || reduceMotion) return@LaunchedEffect
        // Only once it has settled on screen: while the list is still loading, rows can be
        // placed for a frame and then pushed out of view (leaving composition cancels this).
        delay(SETTLE_MS)
        if (!ConfettiBursts.claim(burstKey)) return@LaunchedEffect
        bursting = true
        try {
            if (ConfettiBursts.claimHaptic()) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            progress.animateTo(1f, tween(BURST_MS, easing = LinearEasing))
        } finally {
            bursting = false
        }
    }

    Box(modifier.fillMaxWidth().onGloballyPositioned { placed = true }) {
        Column(
            Modifier
                .fillMaxWidth()
                .glass(
                    shape,
                    colors,
                    fill = colors.okBg.copy(alpha = colors.okBg.alpha * 0.6f).compositeOver(colors.glass),
                    border = colors.ok.copy(alpha = 0.35f),
                )
                .then(if (onClick != null) Modifier.clickable(onClickLabel = "Open the plan", role = Role.Button, onClick = onClick) else Modifier)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.fillMaxWidth()) {
                // Static confetti bits in the top-right corner, behind the text.
                Canvas(Modifier.matchParentSize().clearAndSetSemantics { }) { drawStaticConfetti(confetti) }
                Row(
                    Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = label },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(colors.ok), contentAlignment = Alignment.Center) {
                        Icon(AvIcons.Check, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(22.dp))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(headline, style = AvailablissTheme.type.captionStrong, color = colors.okInk, maxLines = 1)
                        Text(
                            title,
                            style = AvailablissTheme.type.bodyStrong.copy(fontWeight = AvWeights.Display650),
                            color = colors.ink,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (detail.isNotEmpty()) {
                            Text(detail, style = AvailablissTheme.type.caption, color = colors.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            footer()
        }
        if (bursting) {
            // Drawn over the card and allowed to spill outside it; no semantics, no input.
            Canvas(Modifier.matchParentSize().clearAndSetSemantics { }) {
                drawBurst(burstPieces(burstKey.orEmpty()), progress.value, confetti)
            }
        }
    }
}

/** Ids of banners that have already burst during this app session. */
object ConfettiBursts {
    private val played = mutableSetOf<String>()

    private var lastHapticAt = 0L

    /** True the first time [id] is claimed. */
    fun claim(id: String): Boolean = synchronized(played) { played.add(id) }

    /** One haptic for several banners bursting together (e.g. opening a chat with a few recent ones). */
    fun claimHaptic(now: Long = SystemClock.uptimeMillis()): Boolean = synchronized(played) {
        (now - lastHapticAt > BURST_MS).also { if (it) lastHapticAt = now }
    }
}

/** System "Remove animations" / animator duration scale 0. */
@Composable
private fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

private const val BURST_MS = 1600
private const val SETTLE_MS = 250L

/** One burst piece; velocities in dp/s, spin in degrees/s. */
private class Piece(
    val vx: Float,
    val vy: Float,
    val rotation: Float,
    val spin: Float,
    val color: Int,
    val circle: Boolean,
)

private fun burstPieces(key: String): List<Piece> {
    val random = Random(key.hashCode())
    return List(36) {
        Piece(
            vx = random.nextFloat() * 440f - 220f,
            vy = -(260f + random.nextFloat() * 300f),
            rotation = random.nextFloat() * 360f,
            spin = random.nextFloat() * 720f - 360f,
            color = it % 4,
            circle = random.nextFloat() < 0.35f,
        )
    }
}

private fun DrawScope.drawBurst(pieces: List<Piece>, progress: Float, palette: List<Color>) {
    val seconds = progress * BURST_MS / 1000f
    val gravity = 900f
    val alpha = if (progress < 0.6f) 1f else (1f - (progress - 0.6f) / 0.4f).coerceIn(0f, 1f)
    val origin = Offset(size.width / 2f, 12.dp.toPx())
    val w = 6.dp.toPx()
    val h = 10.dp.toPx()
    pieces.forEach { p ->
        val x = origin.x + (p.vx * seconds).dp.toPx()
        val y = origin.y + (p.vy * seconds + 0.5f * gravity * seconds * seconds).dp.toPx()
        val color = palette[p.color].copy(alpha = alpha)
        if (p.circle) {
            drawCircle(color, radius = w / 2f, center = Offset(x, y))
        } else {
            rotate(p.rotation + p.spin * seconds, pivot = Offset(x, y)) {
                drawRoundRect(color, topLeft = Offset(x - w / 2f, y - h / 2f), size = Size(w, h), cornerRadius = CornerRadius(1.5.dp.toPx()))
            }
        }
    }
}

/** Eight small rotated bits, fixed positions relative to the top-right corner (dp from right, dp from top). */
private val staticBits = listOf(
    floatArrayOf(10f, 2f, 20f, 0f), floatArrayOf(26f, 10f, -30f, 1f), floatArrayOf(42f, 0f, 55f, 2f),
    floatArrayOf(58f, 8f, 15f, 3f), floatArrayOf(14f, 16f, 70f, 2f), floatArrayOf(76f, 1f, -45f, 1f),
    floatArrayOf(40f, 14f, 0f, 0f), floatArrayOf(92f, 9f, 35f, 3f),
)

private fun DrawScope.drawStaticConfetti(palette: List<Color>) {
    val w = 4.dp.toPx()
    val h = 8.dp.toPx()
    staticBits.forEachIndexed { i, (right, top, angle, colorIndex) ->
        val center = Offset(size.width - right.dp.toPx(), top.dp.toPx() + h / 2f)
        val color = palette[colorIndex.toInt()].copy(alpha = 0.6f)
        if (i % 3 == 2) {
            drawCircle(color, radius = 2.5.dp.toPx(), center = center)
        } else {
            rotate(angle, pivot = center) {
                drawRoundRect(color, topLeft = Offset(center.x - w / 2f, center.y - h / 2f), size = Size(w, h), cornerRadius = CornerRadius(1.dp.toPx()))
            }
        }
    }
}

@Preview
@Composable
private fun ConfirmedBannerPreview() {
    AvailablissPreviewTheme {
        Box(Modifier.background(AvailablissTheme.colors.bg).padding(16.dp)) {
            ConfirmedBanner("Book club: The Dispossessed", "Thu 26 Nov · from 18:00 · Priya's flat", onClick = {})
        }
    }
}
