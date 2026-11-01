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
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.joeherbert.availabliss.designsystem.component.glass
import dev.joeherbert.availabliss.designsystem.icon.AvIcons
import dev.joeherbert.availabliss.designsystem.theme.AvailablissPreviewTheme
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import dev.joeherbert.availabliss.designsystem.theme.Shapes

/**
 * Glass composer: + menu toggle (rotates 45° and turns solid when open), message
 * field and accent send button. The action menu (Poll / Propose a time / Add event)
 * slides in above it.
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
) {
    val colors = AvailablissTheme.colors
    val canSend = text.isNotBlank() && !sending
    Column(modifier.fillMaxWidth()) {
        AnimatedVisibility(visible = menuOpen, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Row(
                Modifier
                    .padding(start = 12.dp, end = 12.dp, top = 10.dp)
                    .fillMaxWidth()
                    .glass(RoundedCornerShape(22.dp))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                MenuTile("Poll", AvIcons.Poll, onPoll, Modifier.weight(1f))
                MenuTile("Propose a time", AvIcons.ProposalCheck, onProposeTime, Modifier.weight(1f))
                MenuTile("Add event", AvIcons.CalendarAdd, onAddEvent, Modifier.weight(1f))
            }
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
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (menuOpen) colors.solid else colors.surface)
                    .clickable(role = Role.Button, onClick = onToggleMenu)
                    .semantics {
                        contentDescription = "Add poll, proposal or event"
                        stateDescription = if (menuOpen) "Expanded" else "Collapsed"
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(AvIcons.Plus, null, tint = if (menuOpen) colors.onSolid else colors.ink, modifier = Modifier.size(20.dp).rotate(rotation))
            }
            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                textStyle = AvailablissTheme.type.bodyMediumRegular.copy(color = colors.ink),
                cursorBrush = SolidColor(colors.accent),
                maxLines = 5,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Default),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .semantics { contentDescription = "Message" },
                decorationBox = { inner ->
                    Box(Modifier.defaultMinSize(minHeight = 44.dp).padding(vertical = 11.dp), contentAlignment = Alignment.CenterStart) {
                        if (text.isEmpty()) Text("Message the group", style = AvailablissTheme.type.bodyMediumRegular, color = colors.muted)
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
                    .semantics { contentDescription = "Send" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(AvIcons.ArrowRight, null, tint = colors.onAccent, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun MenuTile(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier) {
    val colors = AvailablissTheme.colors
    Column(
        modifier
            .defaultMinSize(minHeight = 72.dp)
            .clip(Shapes.control)
            .background(colors.surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
    ) {
        Icon(icon, null, tint = colors.accentInk, modifier = Modifier.size(22.dp))
        Text(label, style = AvailablissTheme.type.captionStrong, color = colors.ink)
    }
}

@Preview
@Composable
private fun ChatComposerPreview() {
    AvailablissPreviewTheme {
        Box(Modifier.background(AvailablissTheme.colors.bg)) {
            ChatComposer("", {}, {}, menuOpen = true, onToggleMenu = {}, onPoll = {}, onProposeTime = {}, onAddEvent = {})
        }
    }
}
