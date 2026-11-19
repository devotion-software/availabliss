package dev.joeherbert.availabliss.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.joeherbert.availabliss.core.network.model.Attachment
import dev.joeherbert.availabliss.core.network.model.AttachmentKind
import dev.joeherbert.availabliss.designsystem.component.AvBottomSheet
import dev.joeherbert.availabliss.designsystem.component.RowDivider
import dev.joeherbert.availabliss.designsystem.component.Segment
import dev.joeherbert.availabliss.designsystem.component.SegmentedControl
import dev.joeherbert.availabliss.designsystem.component.SegmentedStyle
import dev.joeherbert.availabliss.designsystem.component.TextAction
import dev.joeherbert.availabliss.designsystem.component.chat.WalletPassCard
import dev.joeherbert.availabliss.designsystem.component.chat.fileIconFor
import dev.joeherbert.availabliss.designsystem.component.chat.formatDuration
import dev.joeherbert.availabliss.designsystem.component.chat.vector
import dev.joeherbert.availabliss.designsystem.icon.AvIcons
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import dev.joeherbert.availabliss.designsystem.theme.Shapes

private const val COLUMNS = 3

/**
 * Everything sent in the chat: Photos (a grid, newest first, opening the full-screen viewer), Files
 * (with who sent each and when, opening on a tap) and, when any have been sent, Passes (each drawn
 * like the pass), each with Show in chat. Older ones load with Show
 * older. Like web and iOS.
 */
@Composable
fun ChatMediaSheet(
    media: ChatMediaUi,
    onKind: (MediaKind) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onViewPhoto: (Int) -> Unit,
    onOpenFile: (Attachment) -> Unit,
    onShowInChat: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = AvailablissTheme.colors
    AvBottomSheet(title = "Photos and files", onDismissRequest = onDismiss) {
        SegmentedControl(
            segments = media.kinds.map { kind ->
                Segment(
                    kind,
                    kind.title,
                    when (kind) {
                        MediaKind.Media -> AvIcons.Photo
                        MediaKind.Files -> AvIcons.File
                        MediaKind.Passes -> AvIcons.Ticket
                    },
                )
            },
            selected = media.kind,
            onSelect = onKind,
            style = SegmentedStyle.Inset,
            modifier = Modifier.fillMaxWidth(),
        )
        when {
            media.failed && media.items.isEmpty() -> {
                Note("Couldn't load them. Check your connection and try again.")
                TextAction("Try again", onClick = onRetry)
            }
            media.loaded && media.items.isEmpty() ->
                Note("No ${media.kind.title.lowercase()} sent in this chat yet.")
            media.kind == MediaKind.Media -> PhotoGrid(media.items, onViewPhoto)
            media.kind == MediaKind.Passes -> Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                media.items.forEach { item -> PassRow(item, onOpen = { onOpenFile(item.attachment) }, onShowInChat = { onShowInChat(item.messageId) }) }
            }
            else -> Column(Modifier.fillMaxWidth().clip(Shapes.panel).background(colors.surface).padding(horizontal = 14.dp)) {
                media.items.forEachIndexed { index, item ->
                    if (index > 0) RowDivider()
                    FileRow(item, onOpen = { onOpenFile(item.attachment) }, onShowInChat = { onShowInChat(item.messageId) })
                }
            }
        }
        if (media.loading) {
            Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.accentInk, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
            }
        }
        // The sheet isn't a lazy list (it scrolls as a whole), so older ones load on request.
        if (media.hasMore && !media.loading) {
            TextAction("Show older ${media.kind.title.lowercase()}", onClick = onLoadMore, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

/** A pass drawn like the pass (tap to open it), with who sent it, when and Show in chat under it. */
@Composable
private fun PassRow(item: MediaItemUi, onOpen: () -> Unit, onShowInChat: () -> Unit) {
    val ui = item.attachment.toUi()
    val pass = ui.pass ?: return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        WalletPassCard(ui, pass, onOpen = { onOpen() })
        Row(Modifier.fillMaxWidth().padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                item.meta,
                style = AvailablissTheme.type.caption,
                color = AvailablissTheme.colors.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            TextAction("Show in chat", onClick = onShowInChat)
        }
    }
}

@Composable
private fun PhotoGrid(items: List<MediaItemUi>, onViewPhoto: (Int) -> Unit) {
    val colors = AvailablissTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.chunked(COLUMNS).forEachIndexed { row, chunk ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                chunk.forEachIndexed { column, item ->
                    val index = row * COLUMNS + column
                    val a = item.attachment
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.surface)
                            .clickable(role = Role.Button, onClickLabel = "View") { onViewPhoto(index) }
                            .semantics {
                                val what = when (a.kind) {
                                    AttachmentKind.Gif -> "GIF"
                                    AttachmentKind.Video -> a.duration?.let { "Video, ${formatDuration(it)}" } ?: "Video"
                                    else -> "Photo"
                                }
                                contentDescription = "$what, ${item.meta}"
                            },
                    ) {
                        AsyncImage(
                            model = when (a.kind) {
                                AttachmentKind.Gif -> a.url
                                AttachmentKind.Video -> a.thumbnailUrl
                                else -> a.thumbnailUrl ?: a.url
                            },
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (a.kind == AttachmentKind.Video) {
                            Text(
                                "\u25B6 " + (a.duration?.let(::formatDuration) ?: ""),
                                style = AvailablissTheme.type.micro.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(6.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.Black.copy(alpha = 0.55f))
                                    .padding(horizontal = 6.dp, vertical = 1.dp),
                            )
                        }
                        if (a.kind == AttachmentKind.Gif) {
                            Text(
                                "GIF",
                                style = AvailablissTheme.type.micro.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(6.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.Black.copy(alpha = 0.55f))
                                    .padding(horizontal = 6.dp, vertical = 1.dp),
                            )
                        }
                    }
                }
                repeat(COLUMNS - chunk.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun FileRow(item: MediaItemUi, onOpen: () -> Unit, onShowInChat: () -> Unit) {
    val colors = AvailablissTheme.colors
    val a = item.attachment
    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier
                .weight(1f)
                .clickable(role = Role.Button, onClickLabel = "Open", onClick = onOpen)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(colors.accentSoft), contentAlignment = Alignment.Center) {
                Icon(fileIconFor(a.name, a.mime).vector(), null, tint = colors.accentInk, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(a.name, style = AvailablissTheme.type.bodySmallStrong, color = colors.ink, maxLines = 2, overflow = TextOverflow.MiddleEllipsis)
                Text(item.meta, style = AvailablissTheme.type.caption.copy(fontWeight = FontWeight.Normal), color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        TextAction("Show in chat", onClick = onShowInChat)
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = AvailablissTheme.type.labelRegular, color = AvailablissTheme.colors.muted)
}
