package dev.joeherbert.availabliss.designsystem.component.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.joeherbert.availabliss.core.network.model.Reactions
import dev.joeherbert.availabliss.designsystem.component.AvBottomSheet
import dev.joeherbert.availabliss.designsystem.component.AvTextField
import dev.joeherbert.availabliss.designsystem.icon.AvIcons
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import kotlinx.coroutines.delay

/** One emoji on a message as it's drawn: how many reacted with it, whether you did, and who ("You and Ana"). */
@Immutable
data class ReactionUi(val emoji: String, val count: Int, val mine: Boolean, val who: String)

/**
 * A message's reactions as pills under it: each emoji with how many reacted with it (yours
 * highlighted). Tapping one reacts with it, or takes yours back.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReactionPills(
    reactions: List<ReactionUi>,
    own: Boolean,
    onReact: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    if (reactions.isEmpty()) return
    val colors = AvailablissTheme.colors
    Box(modifier.fillMaxWidth(), contentAlignment = if (own) Alignment.CenterEnd else Alignment.CenterStart) {
        FlowRow(
            Modifier.padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp, if (own) Alignment.End else Alignment.Start),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            reactions.forEach { r ->
                Row(
                    Modifier
                        .heightIn(min = 26.dp)
                        .clip(CircleShape)
                        .background(if (r.mine) colors.accentSoft else colors.bg.copy(alpha = 0.88f))
                        .border(1.dp, if (r.mine) colors.accent.copy(alpha = 0.45f) else colors.line2, CircleShape)
                        .then(
                            if (onReact != null) Modifier.clickable(role = Role.Button, onClickLabel = if (r.mine) "Remove your reaction" else "React with ${r.emoji}") { onReact(r.emoji) }
                            else Modifier,
                        )
                        .semantics(mergeDescendants = true) {
                            contentDescription = "${r.emoji}, ${r.who}"
                            selected = r.mine
                        }
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(r.emoji, fontSize = 15.sp)
                    if (r.count > 1) {
                        Text(
                            r.count.toString(),
                            style = AvailablissTheme.type.captionStrong.copy(fontSize = 12.sp),
                            color = if (r.mine) colors.accentInk else colors.muted,
                        )
                    }
                }
            }
        }
    }
}

/** The row of quick reactions at the top of a message's menu, with + for more. */
@Composable
fun QuickReactionRow(current: String?, onPick: (String) -> Unit, onMore: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AvailablissTheme.colors
    Row(modifier.padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        Reactions.quick.forEach { emoji -> EmojiButton(emoji, selected = emoji == current, size = 40, onClick = { onPick(emoji) }) }
        val other = current?.takeIf { it !in Reactions.quick }
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (other != null) colors.accentSoft else colors.surface)
                .clickable(role = Role.Button, onClickLabel = "More reactions", onClick = onMore)
                .semantics { contentDescription = "More reactions" },
            contentAlignment = Alignment.Center,
        ) {
            if (other != null) Text(other, fontSize = 20.sp)
            else Icon(AvIcons.Plus, null, tint = colors.muted, modifier = Modifier.size(18.dp))
        }
    }
}

/** "React": the quick reactions and more in a grid, and a field for any other emoji from the keyboard. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReactionPickerSheet(current: String?, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    val emoji = Reactions.single(typed)
    AvBottomSheet(title = "React", onDismissRequest = onDismiss) {
        AvTextField(
            value = typed,
            // Keep only the latest emoji typed.
            onValueChange = { value -> typed = lastCharacter(value) },
            placeholder = "Type any emoji",
            imeAction = ImeAction.Done,
            onImeAction = { emoji?.let(onPick) },
            trailing = emoji?.let { e -> { EmojiButton(e, selected = false, size = 40, onClick = { onPick(e) }) } },
        )
        FlowRow(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            maxItemsInEachRow = 8,
        ) {
            (Reactions.quick + Reactions.more).forEach { e -> EmojiButton(e, selected = e == current, size = 42, onClick = { onPick(e) }) }
        }
    }
}

/** A heart that pops over a bubble on a double tap; [trigger] changes to play it. */
@Composable
fun HeartBurst(trigger: Int, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        visible = true
        delay(550)
        visible = false
    }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy), initialScale = 0.4f) + fadeIn(),
        exit = fadeOut(tween(220)) + scaleOut(targetScale = 1.1f),
    ) {
        Text("❤️", fontSize = 40.sp, modifier = Modifier.semantics { contentDescription = "" })
    }
}

@Composable
private fun EmojiButton(emoji: String, selected: Boolean, size: Int, onClick: () -> Unit) {
    val colors = AvailablissTheme.colors
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(if (selected) colors.accentSoft else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = if (selected) "Remove your $emoji reaction" else "React with $emoji"
                if (selected) stateDescription = "Your reaction"
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji, fontSize = (size * 0.55f).sp)
    }
}

/** The last user-perceived character of [text] (a whole emoji, however many code points). */
private fun lastCharacter(text: String): String {
    if (text.isEmpty()) return ""
    val breaks = java.text.BreakIterator.getCharacterInstance()
    breaks.setText(text)
    val end = breaks.last()
    val start = breaks.previous()
    return if (start == java.text.BreakIterator.DONE) text else text.substring(start, end)
}
