package dev.joeherbert.availabliss.designsystem.component.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.joeherbert.availabliss.designsystem.component.PlainIconButton
import dev.joeherbert.availabliss.designsystem.component.glass
import dev.joeherbert.availabliss.designsystem.icon.AvIcons
import dev.joeherbert.availabliss.designsystem.theme.AvailablissPreviewTheme
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import dev.joeherbert.availabliss.designsystem.theme.Shapes

/**
 * Glass composer: + menu toggle (rotates 45° and turns solid when open), message
 * field and accent send button. The action menu (Poll / Propose a time / Add event)
 * slides in above it.
 *
 * [editing]: editing one of your messages. An "Editing message" strip with a cancel
 * button sits above the field, the + toggle is hidden, the field is focused, and the
 * send button becomes a check ("Save").
 *
 * [replyingTo] (a name): your next message replies to theirs. A "Replying to …" strip with
 * [replyingSnippet] and a cancel button sits above the field, and the field is focused.
 *
 * In a thread: no + menu ([showMenu] false), a different [placeholder], the reply strip is always
 * there (✕ only when [canCancelReply]) and the field is focused only when [focusOnReply].
 */
@Composable
fun ChatComposer(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    menuOpen: Boolean,
    onToggleMenu: () -> Unit,
    onPoll: () -> Unit,
    onProposeTime: () -> Unit,
    onAddEvent: () -> Unit,
    modifier: Modifier = Modifier,
    sending: Boolean = false,
    editing: Boolean = false,
    onCancelEdit: () -> Unit = {},
    replyingTo: String? = null,
    replyingSnippet: String = "",
    onCancelReply: () -> Unit = {},
    showMenu: Boolean = true,
    placeholder: String = "Message the group",
    canCancelReply: Boolean = true,
    cancelReplyLabel: String = "Cancel reply",
    focusOnReply: Boolean = true,
    /** Photos and files picked to send, shown as a tray above the field (not while editing). */
    attachments: List<ComposerAttachment> = emptyList(),
    onRemoveAttachment: (String) -> Unit = {},
    onRetryAttachment: (String) -> Unit = {},
    /** Set to offer Photos and File in the + menu. */
    onPhotos: (() -> Unit)? = null,
    onFiles: (() -> Unit)? = null,
    /** Editing a message with attachments: an empty caption can be saved. */
    allowEmptyText: Boolean = false,
) {
    val colors = AvailablissTheme.colors
    val tray = if (editing) emptyList() else attachments
    val canSend = composerCanSend(text, tray, sending, allowEmptyText = editing && allowEmptyText)
    // Local selection, so text set from outside (an edit's prefill, a restored draft)
    // puts the caret at the end while typing keeps it where it is.
    var field by remember { mutableStateOf(TextFieldValue(text, TextRange(text.length))) }
    if (field.text != text) field = TextFieldValue(text, TextRange(text.length))
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(editing) {
        if (editing) {
            runCatching { focus.requestFocus() }
            keyboard?.show()
        }
    }
    val replying = replyingTo != null && !editing
    LaunchedEffect(replying, replyingSnippet, focusOnReply) {
        if (replying && focusOnReply) {
            runCatching { focus.requestFocus() }
            keyboard?.show()
        }
    }
    // Kept while the strip animates out.
    var shownReply by remember { mutableStateOf(replyingTo to replyingSnippet) }
    if (replyingTo != null) shownReply = replyingTo to replyingSnippet
    Column(modifier.fillMaxWidth()) {
        AnimatedVisibility(visible = editing, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Row(
                Modifier
                    .padding(start = 12.dp, end = 12.dp, top = 10.dp)
                    .fillMaxWidth()
                    .glass(RoundedCornerShape(22.dp))
                    .padding(start = 14.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(AvIcons.Pencil, null, tint = colors.accentInk, modifier = Modifier.size(16.dp))
                Text("Editing message", style = AvailablissTheme.type.captionStrong, color = colors.ink, modifier = Modifier.weight(1f))
                PlainIconButton(AvIcons.Close, "Cancel editing", onCancelEdit, size = 40.dp, iconSize = 16.dp, tint = colors.muted)
            }
        }
        AnimatedVisibility(visible = replying, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            val (name, snippet) = shownReply
            Row(
                Modifier
                    .padding(start = 12.dp, end = 12.dp, top = 10.dp)
                    .fillMaxWidth()
                    .glass(RoundedCornerShape(22.dp))
                    .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(Modifier.width(3.dp).height(32.dp).clip(RoundedCornerShape(1.5.dp)).background(colors.accentInk))
                Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                    Text(
                        "Replying to ${if (name == "You") "yourself" else name}",
                        style = AvailablissTheme.type.label,
                        color = colors.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(snippet, style = AvailablissTheme.type.caption, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (canCancelReply) {
                    PlainIconButton(AvIcons.Close, cancelReplyLabel, onCancelReply, size = 40.dp, iconSize = 16.dp, tint = colors.muted)
                } else {
                    Box(Modifier.size(width = 10.dp, height = 40.dp))
                }
            }
        }
        AnimatedVisibility(visible = menuOpen, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Column(
                Modifier
                    .padding(start = 12.dp, end = 12.dp, top = 10.dp)
                    .fillMaxWidth()
                    .glass(RoundedCornerShape(22.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (onPhotos != null || onFiles != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        onPhotos?.let { MenuTile("Photos", AvIcons.Photo, it, Modifier.weight(1f), compact = true) }
                        onFiles?.let { MenuTile("File", AvIcons.Attach, it, Modifier.weight(1f), compact = true) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MenuTile("Poll", AvIcons.Poll, onPoll, Modifier.weight(1f))
                    MenuTile("Propose a time", AvIcons.ProposalCheck, onProposeTime, Modifier.weight(1f))
                    MenuTile("Add event", AvIcons.CalendarAdd, onAddEvent, Modifier.weight(1f))
                }
            }
        }
        AnimatedVisibility(visible = tray.isNotEmpty(), enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            AttachmentTray(tray, onRemoveAttachment, onRetryAttachment, Modifier.padding(top = 10.dp).fillMaxWidth())
        }
        Row(
            Modifier
                .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 18.dp)
                .fillMaxWidth()
                .glass(Shapes.card)
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val rotation by animateFloatAsState(if (menuOpen) 45f else 0f, label = "plus-rotation")
            if (!editing && showMenu) Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (menuOpen) colors.solid else colors.surface)
                    .clickable(role = Role.Button, onClick = onToggleMenu)
                    .semantics {
                        contentDescription = if (onPhotos != null) "Attach, or add a poll, proposal or event" else "Add poll, proposal or event"
                        stateDescription = if (menuOpen) "Expanded" else "Collapsed"
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(AvIcons.Plus, null, tint = if (menuOpen) colors.onSolid else colors.ink, modifier = Modifier.size(20.dp).rotate(rotation))
            }
            BasicTextField(
                value = field,
                onValueChange = {
                    field = it
                    if (it.text != text) onTextChange(it.text)
                },
                textStyle = AvailablissTheme.type.bodyMediumRegular.copy(color = colors.ink),
                cursorBrush = SolidColor(colors.accent),
                maxLines = 5,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Default),
                modifier = Modifier
                    .weight(1f)
                    .padding(start = if (editing || !showMenu) 10.dp else 0.dp)
                    .heightIn(min = 44.dp)
                    .focusRequester(focus)
                    .semantics { contentDescription = "Message" },
                decorationBox = { inner ->
                    Box(Modifier.defaultMinSize(minHeight = 44.dp).padding(vertical = 11.dp), contentAlignment = Alignment.CenterStart) {
                        if (text.isEmpty()) Text(if (tray.isNotEmpty() || (editing && allowEmptyText)) "Add a caption" else placeholder, style = AvailablissTheme.type.bodyMediumRegular, color = colors.muted)
                        inner()
                    }
                },
            )
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(colors.accent)
                    .alpha(if (canSend) 1f else 0.45f)
                    .clickable(enabled = canSend, role = Role.Button, onClick = onSend)
                    .semantics { contentDescription = if (editing) "Save" else "Send" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (editing) AvIcons.Check else AvIcons.ArrowRight, null, tint = colors.onAccent, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** [compact]: icon beside the label in a shorter tile (Photos / File). */
@Composable
private fun MenuTile(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier, compact: Boolean = false) {
    val colors = AvailablissTheme.colors
    val base = modifier
        .defaultMinSize(minHeight = if (compact) 48.dp else 72.dp)
        .clip(Shapes.control)
        .background(colors.surface)
        .clickable(role = Role.Button, onClick = onClick)
    if (compact) {
        Row(
            base.padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            Icon(icon, null, tint = colors.accentInk, modifier = Modifier.size(20.dp))
            Text(label, style = AvailablissTheme.type.captionStrong, color = colors.ink)
        }
    } else {
        Column(
            base.padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        ) {
            Icon(icon, null, tint = colors.accentInk, modifier = Modifier.size(22.dp))
            Text(label, style = AvailablissTheme.type.captionStrong, color = colors.ink)
        }
    }
}

@Preview
@Composable
private fun ChatComposerPreview() {
    AvailablissPreviewTheme {
        Box(Modifier.background(AvailablissTheme.colors.bg)) {
            ChatComposer(
                "", {}, {}, menuOpen = true, onToggleMenu = {}, onPoll = {}, onProposeTime = {}, onAddEvent = {}, onPhotos = {}, onFiles = {},
                attachments = listOf(
                    ComposerAttachment("1", AttachmentUiKind.Image, "IMG_0001.jpg", progress = 0.4f),
                    ComposerAttachment("2", AttachmentUiKind.File, "Itinerary.pdf", uploaded = true),
                    ComposerAttachment("3", AttachmentUiKind.Image, "IMG_0002.jpg", error = "Couldn't upload"),
                ),
            )
        }
    }
}

@Preview
@Composable
private fun ChatComposerReplyingPreview() {
    AvailablissPreviewTheme {
        Box(Modifier.background(AvailablissTheme.colors.bg)) {
            ChatComposer("", {}, {}, menuOpen = false, onToggleMenu = {}, onPoll = {}, onProposeTime = {}, onAddEvent = {}, replyingTo = "Priya", replyingSnippet = "Has everyone put their calendar in yet?")
        }
    }
}

@Preview
@Composable
private fun ChatComposerEditingPreview() {
    AvailablissPreviewTheme {
        Box(Modifier.background(AvailablissTheme.colors.bg)) {
            ChatComposer("I'll book somewhere to stay", {}, {}, menuOpen = false, onToggleMenu = {}, onPoll = {}, onProposeTime = {}, onAddEvent = {}, editing = true)
        }
    }
}
