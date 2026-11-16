package dev.joeherbert.availabliss.designsystem.component.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.joeherbert.availabliss.designsystem.component.Avatar
import dev.joeherbert.availabliss.designsystem.component.AvatarPerson
import dev.joeherbert.availabliss.designsystem.component.dashedBorder
import dev.joeherbert.availabliss.designsystem.component.glass
import dev.joeherbert.availabliss.designsystem.theme.AvailablissPreviewTheme
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme

/** Incoming bubbles: glass, tail bottom-left. Own bubbles: solid, tail bottom-right. */
val IncomingBubbleShape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 6.dp)
val OwnBubbleShape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 6.dp, bottomStart = 18.dp)

/** An extra action on your own bubble: in its long-press menu and as a screen-reader custom action. */
data class BubbleAction(val label: String, val destructive: Boolean = false, val onClick: () -> Unit)

/**
 * A chat message bubble. Incoming messages show the author's 28dp avatar (or a spacer
 * when [showAvatar] is false, for consecutive messages); own messages align right.
 *
 * [menuActions] (your own messages): a long-press opens a small menu with "Info"
 * (when [onClick] is set) followed by these. [edited] adds a muted "Edited" under the
 * bubble. [deleted] draws an unfilled, dashed, non-tappable bubble with [text] in italics.
 */
@Composable
fun MessageBubble(
    text: String,
    own: Boolean,
    modifier: Modifier = Modifier,
    author: AvatarPerson? = null,
    showAvatar: Boolean = true,
    authorName: String? = null,
    onClick: (() -> Unit)? = null,
    accessibilityLabel: String? = null,
    edited: Boolean = false,
    deleted: Boolean = false,
    menuActions: List<BubbleAction> = emptyList(),
) {
    val colors = AvailablissTheme.colors
    var menuOpen by remember { mutableStateOf(false) }
    val actions = if (deleted) emptyList() else menuActions
    val shape = if (own) OwnBubbleShape else IncomingBubbleShape
    val surface = when {
        deleted -> Modifier.dashedBorder(colors.muted.copy(alpha = 0.6f), shape, width = 1.dp)
        own -> Modifier.clip(shape).background(colors.solid)
        else -> Modifier.glass(shape, shadow = false)
    }
    val bubble: @Composable () -> Unit = {
        Box {
            BubbleText(
                text = text,
                modifier = surface
                    .then(
                        if (deleted) Modifier.semantics { contentDescription = accessibilityLabel ?: "Message deleted" }
                        else interactions(onClick, accessibilityLabel, actions, onLongClick = { menuOpen = true }),
                    )
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                own = own,
                deleted = deleted,
            )
            if (actions.isNotEmpty()) {
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, containerColor = colors.bg) {
                    if (onClick != null) MenuItem("Info", destructive = false) { menuOpen = false; onClick() }
                    actions.forEach { a -> MenuItem(a.label, a.destructive) { menuOpen = false; a.onClick() } }
                }
            }
        }
    }
    if (own) {
        Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
            Box(Modifier.widthIn(max = 270.dp)) { bubble() }
            if (edited) EditedLabel(Modifier.padding(top = 3.dp, end = 4.dp))
        }
    } else Column(modifier) {
        Row(
            Modifier.widthIn(max = 290.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            if (author != null && showAvatar) Avatar(author, size = 28.dp) else Spacer(Modifier.size(28.dp))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                if (authorName != null) {
                    Text(
                        authorName,
                        style = AvailablissTheme.type.captionStrong.copy(fontSize = 11.sp),
                        color = colors.muted,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 4.dp).clearAndSetSemantics {},
                    )
                }
                bubble()
            }
        }
        // Outside the row so the avatar stays level with the bubble. 28dp avatar + 8dp gap + 4dp.
        if (edited) EditedLabel(Modifier.padding(start = 40.dp, top = 3.dp))
    }
}

@Composable
private fun EditedLabel(modifier: Modifier) {
    Text(
        "Edited",
        style = AvailablissTheme.type.micro.copy(fontWeight = FontWeight.Medium),
        color = AvailablissTheme.colors.muted,
        // Already in the bubble's description.
        modifier = modifier.clearAndSetSemantics {},
    )
}

@Composable
private fun MenuItem(label: String, destructive: Boolean, onClick: () -> Unit) {
    val colors = AvailablissTheme.colors
    DropdownMenuItem(
        text = { Text(label, style = AvailablissTheme.type.bodySmallStrong, color = if (destructive) colors.warnInk else colors.ink) },
        onClick = onClick,
    )
}

@Composable
private fun BubbleText(text: String, modifier: Modifier, own: Boolean, deleted: Boolean = false) {
    val colors = AvailablissTheme.colors
    Text(
        text,
        style = if (deleted) AvailablissTheme.type.body.copy(fontStyle = FontStyle.Italic) else AvailablissTheme.type.body,
        color = when {
            deleted -> colors.muted
            own -> colors.onSolid
            else -> colors.ink
        },
        modifier = modifier,
    )
}

@Composable
private fun interactions(
    onClick: (() -> Unit)?,
    label: String?,
    actions: List<BubbleAction>,
    onLongClick: () -> Unit,
): Modifier {
    if (onClick == null && actions.isEmpty()) return Modifier
    val haptics = LocalHapticFeedback.current
    return Modifier
        .combinedClickable(
            role = Role.Button,
            onClickLabel = if (onClick != null) "Message info" else null,
            onLongClickLabel = if (actions.isNotEmpty()) "Message options" else null,
            onLongClick = if (actions.isNotEmpty()) ({
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onLongClick()
            }) else null,
            onClick = onClick ?: onLongClick,
        )
        .semantics {
            if (label != null) contentDescription = label
            if (actions.isNotEmpty()) customActions = actions.map { a -> CustomAccessibilityAction(a.label) { a.onClick(); true } }
        }
}

/** Centered muted line: day separators ("Today") and system messages. */
@Composable
fun ChatMeta(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = AvailablissTheme.type.micro,
        color = AvailablissTheme.colors.muted,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth().padding(vertical = 2.dp),
    )
}

@Preview
@Composable
private fun MessageBubblePreview() {
    AvailablissPreviewTheme {
        Box(Modifier.background(AvailablissTheme.colors.bg).padding(16.dp)) {
            androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ChatMeta("Today")
                MessageBubble("Has everyone put their calendar in yet? Leo, Jade…", own = false, author = AvatarPerson("p", "Priya"))
                MessageBubble("I'll book somewhere to stay. Vote below", own = true, onClick = {}, edited = true)
                MessageBubble("You deleted this message", own = true, deleted = true)
                MessageBubble("This message was deleted", own = false, author = AvatarPerson("p", "Priya"), deleted = true)
            }
        }
    }
}
