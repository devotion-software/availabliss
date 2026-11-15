package dev.joeherbert.availabliss.designsystem.component

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.composed
import androidx.compose.ui.unit.dp

/**
 * Swipe sideways to step through weeks or months: a drag of at least 60dp to the left calls
 * [onNext], to the right [onPrevious]. Vertical scrolling and taps inside still work.
 */
fun Modifier.swipeToStep(onPrevious: () -> Unit, onNext: () -> Unit): Modifier = composed {
    val threshold = with(LocalDensity.current) { 60.dp.toPx() }
    val previous by rememberUpdatedState(onPrevious)
    val next by rememberUpdatedState(onNext)
    pointerInput(Unit) {
        var total = 0f
        detectHorizontalDragGestures(
            onDragStart = { total = 0f },
            onDragEnd = {
                when {
                    total <= -threshold -> next()
                    total >= threshold -> previous()
                }
            },
        ) { _, amount -> total += amount }
    }
}
