package dev.joeherbert.availabliss.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Swipe sideways to step through weeks or months: a drag of at least 60dp to the left calls
 * [onNext], to the right [onPrevious]. The content follows the finger a little while dragging and
 * springs back on release. Vertical scrolling and taps inside still work.
 */
fun Modifier.swipeToStep(onPrevious: () -> Unit, onNext: () -> Unit): Modifier = composed {
    val threshold = with(LocalDensity.current) { 60.dp.toPx() }
    val previous by rememberUpdatedState(onPrevious)
    val next by rememberUpdatedState(onNext)
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val settle = { scope.launch { offset.animateTo(0f, spring(dampingRatio = 0.85f)) } }
    pointerInput(Unit) {
        var total = 0f
        detectHorizontalDragGestures(
            onDragStart = { total = 0f },
            onDragEnd = {
                when {
                    total <= -threshold -> next()
                    total >= threshold -> previous()
                }
                settle()
            },
            onDragCancel = { settle() },
        ) { _, amount ->
            total += amount
            scope.launch { offset.snapTo(total * 0.35f) }
        }
    }.graphicsLayer { translationX = offset.value }
}
