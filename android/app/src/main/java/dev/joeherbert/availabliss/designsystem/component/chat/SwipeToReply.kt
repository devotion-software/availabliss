package dev.joeherbert.availabliss.designsystem.component.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.joeherbert.availabliss.designsystem.icon.AvIcons
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Swipe a message right to reply: it follows your finger, a reply arrow fades in behind
 * it, and past the threshold it clicks; let go there to call [onReply]. Screen readers get
 * a "Reply" action instead. With [enabled] false it's just [content].
 */
@Composable
fun SwipeToReply(enabled: Boolean, onReply: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    if (!enabled) {
        Box(modifier) { content() }
        return
    }
    val colors = AvailablissTheme.colors
    val haptics = LocalHapticFeedback.current
    val threshold = with(LocalDensity.current) { 56.dp.toPx() }
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var armed by remember { mutableStateOf(false) }
    val currentOnReply by rememberUpdatedState(onReply)
    Box(
        modifier
            .semantics { customActions = listOf(CustomAccessibilityAction("Reply") { currentOnReply(); true }) }
            .pointerInput(threshold) {
                var raw = 0f
                detectHorizontalDragGestures(
                    onDragStart = { raw = 0f },
                    onDragEnd = {
                        if (armed) currentOnReply()
                        armed = false
                        scope.launch { offset.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) }
                    },
                    onDragCancel = {
                        armed = false
                        scope.launch { offset.animateTo(0f) }
                    },
                ) { change, dx ->
                    raw = (raw + dx).coerceAtLeast(0f)
                    if (raw > 0f) change.consume()
                    // Follows the finger, then resists past the threshold.
                    val shown = if (raw <= threshold) raw else threshold + (raw - threshold) * 0.25f
                    val nowArmed = raw >= threshold
                    if (nowArmed && !armed) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    armed = nowArmed
                    scope.launch { offset.snapTo(shown) }
                }
            },
    ) {
        val progress = (offset.value / threshold).coerceIn(0f, 1f)
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset((offset.value.coerceAtMost(threshold) - 38.dp.toPx()).roundToInt(), 0) }
                .graphicsLayer {
                    alpha = progress
                    val scale = if (armed) 1f else 0.7f + 0.3f * progress
                    scaleX = scale
                    scaleY = scale
                }
                .size(30.dp)
                .clip(CircleShape)
                .background(if (armed) colors.accent else colors.accentSoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(AvIcons.Reply, null, tint = if (armed) colors.onAccent else colors.accentInk, modifier = Modifier.size(16.dp))
        }
        Box(Modifier.offset { IntOffset(offset.value.roundToInt(), 0) }) { content() }
    }
}
