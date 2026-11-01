package dev.joeherbert.availabliss.designsystem.component.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.joeherbert.availabliss.designsystem.component.Avatar
import dev.joeherbert.availabliss.designsystem.component.AvatarPerson
import dev.joeherbert.availabliss.designsystem.component.glass
import dev.joeherbert.availabliss.designsystem.theme.AvailablissPreviewTheme
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme

/** Incoming bubbles: glass, tail bottom-left. Own bubbles: solid, tail bottom-right. */
val IncomingBubbleShape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 6.dp)
val OwnBubbleShape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 6.dp, bottomStart = 18.dp)

/**
 * A chat message bubble. Incoming messages show the author's 28dp avatar (or a spacer
 * when [showAvatar] is false, for consecutive messages); own messages align right.
 */
@Composable
fun MessageBubble(
    text: String,
    own: Boolean,
    modifier: Modifier = Modifier,
    author: AvatarPerson? = null,
    showAvatar: Boolean = true,
    onClick: (() -> Unit)? = null,
    accessibilityLabel: String? = null,
) {
    val colors = AvailablissTheme.colors
    if (own) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            BubbleText(
                text = text,
                modifier = modifier
                    .widthIn(max = 270.dp)
                    .clip(OwnBubbleShape)
                    .background(colors.solid)
                    .then(clickable(onClick, accessibilityLabel))
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                own = true,
            )
        }
    } else {
        Row(
            modifier.widthIn(max = 290.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            if (author != null && showAvatar) Avatar(author, size = 28.dp) else Spacer(Modifier.size(28.dp))
            BubbleText(
                text = text,
                modifier = Modifier
                    .glass(IncomingBubbleShape, shadow = false)
                    .then(clickable(onClick, accessibilityLabel))
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                own = false,
            )
        }
    }
}

@Composable
private fun BubbleText(text: String, modifier: Modifier, own: Boolean) {
    val colors = AvailablissTheme.colors
    Text(text, style = AvailablissTheme.type.body, color = if (own) colors.onSolid else colors.ink, modifier = modifier)
}

private fun clickable(onClick: (() -> Unit)?, label: String?): Modifier =
    if (onClick == null) Modifier
    else Modifier
        .clickable(role = Role.Button, onClickLabel = "Message info", onClick = onClick)
        .then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier)

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
                MessageBubble("I'll book somewhere to stay. Vote below", own = true, onClick = {})
            }
        }
    }
}
