package dev.joeherbert.availabliss.designsystem.component.chat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.joeherbert.availabliss.designsystem.icon.AvIcons
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import java.util.Locale
import kotlin.math.roundToInt

enum class AttachmentUiKind { Image, Gif, Video, File }

/** What a file card's icon shows. */
enum class FileIcon { Document, Archive, Audio, Video, Ticket, Generic }

/** An Apple Wallet pass's card: drawn in the pass's own colours. */
@Immutable
data class PassUi(
    val organization: String,
    /** The event's name, or what the pass is ("Concert ticket", "2 tickets"). */
    val headline: String,
    /** "Wed 2 Dec · 19:30", in the phone's time zone. */
    val dateLabel: String? = null,
    val venue: String? = null,
    /** Up to three (label, value) details such as seat and gate. */
    val fields: List<Pair<String?, String>> = emptyList(),
    val background: Color? = null,
    val foreground: Color? = null,
    val label: Color? = null,
    /** The pass's logo (a signed link), or null. */
    val logo: String? = null,
    val boarding: Boolean = false,
    val voided: Boolean = false,
    val count: Int = 1,
)

/** "#rrggbb" → a colour, or null. */
fun passColor(hex: String?): Color? {
    val digits = hex?.removePrefix("#")?.takeIf { it.length == 6 } ?: return null
    return digits.toLongOrNull(16)?.let { Color(0xFF000000 or it) }
}

/** A sent photo, GIF or file, as a bubble draws it. */
@Immutable
data class AttachmentUi(
    val id: String,
    val kind: AttachmentUiKind,
    val name: String,
    /** "1.2 MB". */
    val sizeLabel: String,
    /** What the bubble shows: a photo's preview, a GIF itself, a video's poster; null for files. */
    val preview: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val icon: FileIcon = FileIcon.Generic,
    /** A Wallet pass (kind File): drawn as a pass card rather than a file card. */
    val pass: PassUi? = null,
    /** A video's length in seconds. */
    val duration: Int? = null,
) {
    val visual: Boolean get() = kind != AttachmentUiKind.File

    /** "Photo", "GIF", "Video, 1:05", "Wallet pass: Winter Gig, Roundhouse", or "File: Plan.pdf, 1.2 MB". */
    val accessibilityLabel: String
        get() = when (kind) {
            AttachmentUiKind.Image -> "Photo"
            AttachmentUiKind.Gif -> "GIF"
            AttachmentUiKind.Video -> duration?.let { "Video, ${formatDuration(it)}" } ?: "Video"
            AttachmentUiKind.File -> if (pass != null) "Wallet pass: ${pass.headline}, ${pass.organization}" else "File: $name, $sizeLabel"
        }
}

/** A photo or file picked in the composer, uploading or ready to send. */
@Immutable
data class ComposerAttachment(
    val id: String,
    val kind: AttachmentUiKind,
    val name: String,
    /** A local preview (a content Uri) for photos. */
    val preview: Any? = null,
    /** 0–1 while uploading. */
    val progress: Float = 0f,
    val uploaded: Boolean = false,
    /** Why the upload failed; it can be retried or removed. */
    val error: String? = null,
) {
    val uploading: Boolean get() = !uploaded && error == null
}

// region Pure layout and formatting

/** "512 bytes", "12 KB", "1.2 MB", "15 MB" (1 KB = 1000 bytes, as Android's file sizes). */
fun formatFileSize(bytes: Long): String {
    if (bytes < 1000) return if (bytes == 1L) "1 byte" else "$bytes bytes"
    val units = listOf("KB", "MB", "GB")
    var value = bytes / 1000.0
    var unit = 0
    while (value >= 999.5 && unit < units.lastIndex) {
        value /= 1000.0
        unit++
    }
    val text = if (value < 9.95 && unit > 0) String.format(Locale.UK, "%.1f", value).removeSuffix(".0") else value.roundToInt().toString()
    return "$text ${units[unit]}"
}

/** Shortens a long file name in the middle, keeping its extension: "A very long re…port.pdf". */
fun middleTruncate(name: String, max: Int = 40): String {
    if (name.length <= max) return name
    val dot = name.lastIndexOf('.')
    val extension = if (dot > 0 && name.length - dot <= 8) name.substring(dot) else ""
    val stem = name.removeSuffix(extension)
    val keep = (max - extension.length - 1).coerceAtLeast(2)
    val head = (keep * 2 + 2) / 3
    val tail = keep - head
    return stem.take(head) + "…" + stem.takeLast(tail) + extension
}

/** The icon for a file by its extension or type. */
fun fileIconFor(name: String, mime: String): FileIcon {
    val ext = name.substringAfterLast('.', "").lowercase()
    return when {
        mime.startsWith("audio/") || ext in setOf("mp3", "m4a", "wav", "aac", "ogg", "flac") -> FileIcon.Audio
        mime.startsWith("video/") || ext in setOf("mp4", "mov", "m4v", "webm") -> FileIcon.Video
        ext in setOf("pkpass", "pkpasses") || mime.startsWith("application/vnd.apple.pkpass") -> FileIcon.Ticket
        ext in setOf("zip", "rar", "7z", "gz", "tar") || mime.contains("zip") -> FileIcon.Archive
        ext in setOf("pdf", "txt", "md", "csv", "rtf", "ics", "vcf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp", "pages", "numbers", "key", "epub") ||
            mime.startsWith("text/") || mime == "application/pdf" -> FileIcon.Document
        else -> FileIcon.Generic
    }
}

/** Size of a lone photo in the bubble (dp): its aspect ratio within [maxWidth] × [maxHeight], at least [minSide] each way. */
fun singleImageSize(width: Int?, height: Int?, maxWidth: Float = 240f, maxHeight: Float = 320f, minSide: Float = 96f): Pair<Float, Float> {
    if (width == null || height == null || width <= 0 || height <= 0) return maxWidth to maxWidth * 3 / 4
    val ratio = width.toFloat() / height
    var w = maxWidth
    var h = w / ratio
    if (h > maxHeight) {
        h = maxHeight
        w = h * ratio
    }
    return w.coerceIn(minSide, maxWidth) to h.coerceIn(minSide, maxHeight)
}

/** A video's length: "0:07", "1:05", "1:02:03". */
fun formatDuration(seconds: Int): String {
    val s = seconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(Locale.UK, h, m, sec) else "%d:%02d".format(Locale.UK, m, sec)
}

/** Tiles per row of a gallery of [count] photos: pairs, with an odd one out full width; at most 4 tiles. */
fun galleryRows(count: Int): List<Int> {
    val shown = count.coerceAtMost(MAX_GALLERY_TILES)
    if (shown <= 1) return if (shown == 1) listOf(1) else emptyList()
    return List(shown / 2) { 2 } + if (shown % 2 == 1) listOf(1) else emptyList()
}

/** "+N" on the last tile when there are more photos than tiles; 0 for none. */
fun galleryOverflow(count: Int): Int = (count - MAX_GALLERY_TILES).coerceAtLeast(0)

/** Send is on with text or at least one uploaded attachment, and nothing uploading or failed. */
fun composerCanSend(text: String, attachments: List<ComposerAttachment>, sending: Boolean, allowEmptyText: Boolean = false): Boolean {
    if (sending) return false
    if (attachments.any { !it.uploaded }) return false
    return text.isNotBlank() || attachments.isNotEmpty() || allowEmptyText
}

const val MAX_GALLERY_TILES = 4
const val MAX_ATTACHMENTS = 10

// endregion

internal val GalleryWidth = 240.dp
private val GalleryGap = 4.dp
private val TileShape = RoundedCornerShape(14.dp)

/**
 * The photos and GIFs of a message: one sized by its aspect ratio, or a 2-column grid of
 * square tiles with "+N" on the fourth. Tapping one calls [onOpen] with its id.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AttachmentGallery(images: List<AttachmentUi>, onOpen: (String) -> Unit, modifier: Modifier = Modifier, onLongClick: (() -> Unit)? = null) {
    if (images.isEmpty()) return
    if (images.size == 1) {
        val image = images[0]
        val (w, h) = singleImageSize(image.width, image.height)
        ImageTile(image, onOpen, onLongClick, modifier.size(w.dp, h.dp))
        return
    }
    val rows = galleryRows(images.size)
    val overflow = galleryOverflow(images.size)
    val tile = (GalleryWidth - GalleryGap) / 2
    Column(modifier.width(GalleryWidth), verticalArrangement = Arrangement.spacedBy(GalleryGap)) {
        var index = 0
        rows.forEach { perRow ->
            Row(horizontalArrangement = Arrangement.spacedBy(GalleryGap)) {
                repeat(perRow) {
                    val image = images[index]
                    val last = index == MAX_GALLERY_TILES - 1 && overflow > 0
                    val size = if (perRow == 1) Modifier.size(GalleryWidth, tile) else Modifier.size(tile)
                    ImageTile(image, onOpen, onLongClick, size, overflow = if (last) overflow else 0)
                    index++
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ImageTile(image: AttachmentUi, onOpen: (String) -> Unit, onLongClick: (() -> Unit)?, modifier: Modifier, overflow: Int = 0) {
    val colors = AvailablissTheme.colors
    Box(
        modifier
            .clip(TileShape)
            .background(colors.line2)
            .combinedClickable(
                role = Role.Image,
                onClickLabel = "View",
                onLongClick = onLongClick,
                onClick = { onOpen(image.id) },
            )
            .semantics {
                contentDescription = if (overflow > 0) "${image.accessibilityLabel}, and $overflow more" else image.accessibilityLabel
            },
    ) {
        AsyncImage(
            model = image.preview,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (image.kind == AttachmentUiKind.Video) {
            PlayBadge(Modifier.align(Alignment.Center))
            image.duration?.let { seconds ->
                Text(
                    formatDuration(seconds),
                    style = AvailablissTheme.type.micro.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
        }
        if (image.kind == AttachmentUiKind.Gif) {
            Text(
                "GIF",
                style = AvailablissTheme.type.micro.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
        if (overflow > 0) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                Text("+$overflow", style = AvailablissTheme.type.headlineSmall, color = Color.White)
            }
        }
    }
}

/** A round, dark play button drawn over a video's poster. */
@Composable
fun PlayBadge(modifier: Modifier = Modifier, size: Dp = 44.dp) {
    Canvas(modifier.size(size)) {
        drawCircle(Color.Black.copy(alpha = 0.5f))
        val w = this.size.width
        val triangle = Path().apply {
            moveTo(w * 0.40f, w * 0.30f)
            lineTo(w * 0.72f, w * 0.50f)
            lineTo(w * 0.40f, w * 0.70f)
            close()
        }
        drawPath(triangle, Color.White)
    }
}

fun FileIcon.vector(): ImageVector = when (this) {
    FileIcon.Document -> AvIcons.FileText
    FileIcon.Archive -> AvIcons.Archive
    FileIcon.Audio -> AvIcons.Music
    FileIcon.Video -> AvIcons.Video
    FileIcon.Ticket -> AvIcons.Ticket
    FileIcon.Generic -> AvIcons.File
}

/**
 * A Wallet pass in a message, drawn like the pass: its colours and logo, what and when, a few
 * details, and Open pass (Android has no Apple Wallet: the .pkpass opens in whichever app handles
 * it). [progress] is non-null while it downloads.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WalletPassCard(
    file: AttachmentUi,
    pass: PassUi,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val colors = AvailablissTheme.colors
    val background = pass.background ?: colors.surface
    val ink = pass.foreground ?: colors.ink
    val label = pass.label ?: ink.copy(alpha = 0.72f)
    Column(
        modifier
            .width(GalleryWidth)
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .combinedClickable(
                role = Role.Button,
                onClickLabel = "Open pass",
                onLongClick = onLongClick,
                onClick = { onOpen(file.id) },
            )
            .semantics(mergeDescendants = true) {
                contentDescription = file.accessibilityLabel + listOfNotNull(pass.dateLabel, pass.venue).joinToString { ", $it" } +
                    if (progress != null) ", downloading" else ""
            }
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.clearAndSetSemantics {}, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (pass.logo != null) {
                AsyncImage(
                    model = pass.logo,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.CenterStart,
                    modifier = Modifier.height(24.dp).widthIn(max = 100.dp),
                )
            }
            Text(pass.organization, style = AvailablissTheme.type.bodySmallStrong, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (pass.voided) {
                Text(
                    "Voided",
                    style = AvailablissTheme.type.caption,
                    color = ink,
                    modifier = Modifier.clip(CircleShape).background(ink.copy(alpha = 0.16f)).padding(horizontal = 6.dp, vertical = 1.dp),
                )
            } else {
                Icon(if (pass.boarding) AvIcons.Plane else AvIcons.Ticket, null, tint = label, modifier = Modifier.size(16.dp))
            }
        }
        Column(Modifier.clearAndSetSemantics {}, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(pass.headline, style = AvailablissTheme.type.bodyStrong, color = ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            pass.dateLabel?.let { Text(it, style = AvailablissTheme.type.bodySmallStrong, color = label, maxLines = 1) }
            pass.venue?.let { Text(it, style = AvailablissTheme.type.caption.copy(fontWeight = FontWeight.Normal), color = ink.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        if (pass.fields.isNotEmpty()) {
            Row(Modifier.clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                pass.fields.forEach { (name, value) ->
                    Column(Modifier.widthIn(max = 110.dp)) {
                        if (name != null) Text(name.uppercase(Locale.getDefault()), style = AvailablissTheme.type.caption.copy(fontSize = 9.sp, fontWeight = FontWeight.SemiBold), color = label, maxLines = 1)
                        Text(value, style = AvailablissTheme.type.bodySmallStrong, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(ink.copy(alpha = 0.14f))
                .clearAndSetSemantics {},
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (progress != null) {
                if (progress < 0f) CircularProgressIndicator(color = ink, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                else CircularProgressIndicator(progress = { progress }, color = ink, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
            } else {
                Icon(AvIcons.Download, null, tint = ink, modifier = Modifier.size(16.dp))
            }
            Text(if (pass.count > 1) "Open ${pass.count} passes" else "Open pass", style = AvailablissTheme.type.bodySmallStrong, color = ink)
        }
    }
}

/**
 * A file in a message: icon by type, name (middle-truncated) and size. [progress] is non-null
 * while it downloads to open (below 0 when the size is unknown).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileAttachmentCard(
    file: AttachmentUi,
    own: Boolean,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val colors = AvailablissTheme.colors
    val ink = if (own) colors.onAccent else colors.ink
    val sub = if (own) colors.onAccent.copy(alpha = 0.78f) else colors.muted
    Row(
        modifier
            .width(GalleryWidth)
            .clip(RoundedCornerShape(14.dp))
            .background(if (own) colors.onAccent.copy(alpha = 0.14f) else colors.surface)
            .combinedClickable(
                role = Role.Button,
                onClickLabel = "Open",
                onLongClick = onLongClick,
                onClick = { onOpen(file.id) },
            )
            .semantics(mergeDescendants = true) {
                contentDescription = file.accessibilityLabel + if (progress != null) ", downloading" else ""
            }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (own) colors.onAccent.copy(alpha = 0.18f) else colors.accentSoft),
            contentAlignment = Alignment.Center,
        ) {
            if (progress != null) {
                val color = if (own) colors.onAccent else colors.accentInk
                if (progress < 0f) CircularProgressIndicator(color = color, strokeWidth = 2.5.dp, modifier = Modifier.size(22.dp))
                else CircularProgressIndicator(progress = { progress }, color = color, strokeWidth = 2.5.dp, modifier = Modifier.size(22.dp))
            } else {
                Icon(file.icon.vector(), null, tint = if (own) colors.onAccent else colors.accentInk, modifier = Modifier.size(20.dp))
            }
        }
        Column(Modifier.weight(1f).clearAndSetSemantics {}) {
            Text(middleTruncate(file.name), style = AvailablissTheme.type.bodySmallStrong, color = ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(file.sizeLabel, style = AvailablissTheme.type.caption.copy(fontWeight = FontWeight.Normal), color = sub, maxLines = 1)
        }
    }
}

/** The picked photos and files above the composer: thumbnails and file chips, each removable, with upload progress or an error to retry. */
@Composable
fun AttachmentTray(
    items: List<ComposerAttachment>,
    onRemove: (String) -> Unit,
    onRetry: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier,
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items, key = { it.id }) { item -> TrayItem(item, onRemove, onRetry) }
    }
}

private val TraySize: Dp = 64.dp

@Composable
private fun TrayItem(item: ComposerAttachment, onRemove: (String) -> Unit, onRetry: (String) -> Unit) {
    val colors = AvailablissTheme.colors
    val label = when (item.kind) {
        AttachmentUiKind.Image -> "Photo"
        AttachmentUiKind.Gif -> "GIF"
        AttachmentUiKind.Video -> "Video"
        AttachmentUiKind.File -> "File: ${item.name}"
    }
    val status = when {
        item.error != null -> "upload failed: ${item.error}"
        item.uploading -> "uploading, ${(item.progress * 100).roundToInt()}%"
        else -> "ready"
    }
    Box {
        val shape = RoundedCornerShape(14.dp)
        Box(
            Modifier
                .padding(top = 8.dp, end = 8.dp)
                .height(TraySize)
                .then(if (item.kind == AttachmentUiKind.File) Modifier.width(150.dp) else Modifier.width(TraySize))
                .clip(shape)
                .background(colors.surface)
                .then(if (item.error != null) Modifier.clickable(role = Role.Button, onClickLabel = "Retry upload") { onRetry(item.id) } else Modifier)
                .semantics(mergeDescendants = true) { contentDescription = "$label, $status" },
        ) {
            if (item.kind == AttachmentUiKind.File) {
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(fileIconFor(item.name, "").vector(), null, tint = colors.accentInk, modifier = Modifier.size(20.dp))
                    Text(
                        middleTruncate(item.name, 28),
                        style = AvailablissTheme.type.caption,
                        color = colors.ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                AsyncImage(model = item.preview, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                if (item.kind == AttachmentUiKind.Video && item.uploaded) PlayBadge(Modifier.align(Alignment.Center), size = 26.dp)
            }
            when {
                item.error != null -> Box(Modifier.fillMaxSize().background(colors.warnBg.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
                    Icon(AvIcons.Refresh, null, tint = colors.warnInk, modifier = Modifier.size(22.dp))
                }
                item.uploading -> Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f)), contentAlignment = Alignment.Center) {
                    if (item.progress <= 0f) CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp, modifier = Modifier.size(24.dp))
                    else CircularProgressIndicator(progress = { item.progress }, color = Color.White, trackColor = Color.White.copy(alpha = 0.3f), strokeWidth = 2.5.dp, modifier = Modifier.size(24.dp))
                }
            }
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .size(22.dp)
                .clip(CircleShape)
                .background(colors.solid)
                .clickable(role = Role.Button, onClick = { onRemove(item.id) })
                .semantics { contentDescription = "Remove ${if (item.kind == AttachmentUiKind.File) item.name else label.lowercase(Locale.UK)}" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(AvIcons.Close, null, tint = colors.onSolid, modifier = Modifier.size(10.dp))
        }
    }
}
