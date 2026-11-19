package dev.joeherbert.availabliss.feature.chat

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import dev.joeherbert.availabliss.core.network.model.Attachment
import dev.joeherbert.availabliss.core.network.model.AttachmentKind
import dev.joeherbert.availabliss.designsystem.component.AvSnackbarHost
import dev.joeherbert.availabliss.designsystem.component.PlainIconButton
import dev.joeherbert.availabliss.designsystem.icon.AvIcons
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme

/**
 * Full-screen photos: swipe between a message's photos and GIFs, pinch or double-tap to zoom,
 * share, and (Android 10+) save to Pictures.
 */
@Composable
fun AttachmentViewer(
    viewer: ViewerUi,
    downloads: Map<String, Float>,
    snackbar: SnackbarHostState,
    onShare: (Attachment) -> Unit,
    onSave: (Attachment) -> Unit,
    onDismiss: () -> Unit,
    /** From the media page: Show in chat for the picture at this index. */
    onShowInChat: ((Int) -> Unit)? = null,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val pager = rememberPagerState(initialPage = viewer.startIndex) { viewer.attachments.size }
        // Swiping between photos only while the current one isn't zoomed in.
        var zoomed by remember { mutableStateOf(false) }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(state = pager, userScrollEnabled = !zoomed, modifier = Modifier.fillMaxSize(), key = { viewer.attachments[it].id }) { page ->
                ZoomableImage(
                    attachment = viewer.attachments[page],
                    onZoomChange = { if (page == pager.currentPage) zoomed = it },
                )
            }
            val current = viewer.attachments.getOrNull(pager.currentPage)
            Row(
                Modifier
                    .fillMaxWidth()
                    .safeDrawingPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                PlainIconButton(AvIcons.Close, "Close", onDismiss, tint = Color.White, background = Color.Black.copy(alpha = 0.35f))
                Spacer(Modifier.weight(1f))
                if (viewer.attachments.size > 1) {
                    Text(
                        "${pager.currentPage + 1} of ${viewer.attachments.size}",
                        style = AvailablissTheme.type.captionStrong,
                        color = Color.White,
                    )
                    Spacer(Modifier.weight(1f))
                }
                if (onShowInChat != null) {
                    PlainIconButton(AvIcons.Chat, "Show in chat", { onShowInChat(pager.currentPage) }, tint = Color.White, background = Color.Black.copy(alpha = 0.35f))
                }
                if (current != null) {
                    if (current.id in downloads) {
                        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp, modifier = Modifier.size(22.dp))
                        }
                    } else {
                        PlainIconButton(AvIcons.Share, "Share", { onShare(current) }, tint = Color.White, background = Color.Black.copy(alpha = 0.35f))
                        if (Build.VERSION.SDK_INT >= 29) {
                            PlainIconButton(AvIcons.Download, "Save to Pictures", { onSave(current) }, tint = Color.White, background = Color.Black.copy(alpha = 0.35f))
                        }
                    }
                }
            }
            AvSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = 16.dp))
        }
    }
}

@Composable
private fun ZoomableImage(attachment: Attachment, onZoomChange: (Boolean) -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    fun set(newScale: Float, newOffset: Offset) {
        scale = newScale.coerceIn(1f, MAX_ZOOM)
        // Keep the zoomed photo covering the screen: no panning past its edges.
        val maxX = box.width * (scale - 1f) / 2f
        val maxY = box.height * (scale - 1f) / 2f
        offset = if (scale == 1f) Offset.Zero else Offset(newOffset.x.coerceIn(-maxX, maxX), newOffset.y.coerceIn(-maxY, maxY))
        onZoomChange(scale > 1f)
    }
    val transform = rememberTransformableState { zoomChange, panChange, _ ->
        set(scale * zoomChange, offset + panChange)
    }
    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { box = it }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { tap ->
                    if (scale > 1f) set(1f, Offset.Zero)
                    else {
                        // Zoom towards where you tapped.
                        val center = Offset(size.width / 2f, size.height / 2f)
                        set(DOUBLE_TAP_ZOOM, (center - tap) * (DOUBLE_TAP_ZOOM - 1f))
                    }
                })
            }
            .transformable(transform, canPan = { scale > 1f }),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = attachment.url,
            contentDescription = if (attachment.kind == AttachmentKind.Gif) "GIF" else "Photo",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
    }
}

private const val MAX_ZOOM = 5f
private const val DOUBLE_TAP_ZOOM = 2.5f
