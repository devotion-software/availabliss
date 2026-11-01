package dev.joeherbert.availabliss.designsystem.component.chat

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.joeherbert.availabliss.designsystem.component.TextAction
import dev.joeherbert.availabliss.designsystem.component.glass
import dev.joeherbert.availabliss.designsystem.icon.AvIcons
import dev.joeherbert.availabliss.designsystem.theme.AvailablissPreviewTheme
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import dev.joeherbert.availabliss.designsystem.theme.Shapes

@Immutable
data class PollOptionUi(
    val id: String,
    val label: String,
    val count: Int,
    val mine: Boolean,
    /** Has the most votes (bar drawn in accent-soft). */
    val leading: Boolean,
)

@Immutable
data class PollUi(
    val id: String,
    val question: String,
    val totalVotes: Int,
    val multi: Boolean,
    val closed: Boolean,
    val options: List<PollOptionUi>,
    val canClose: Boolean = false,
)

/** Poll card with live result bars; tap an option to vote or unvote. */
@Composable
fun PollCard(
    poll: PollUi,
    onToggle: (optionId: String) -> Unit,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
) {
    val colors = AvailablissTheme.colors
    Column(
        modifier
            .glass(RoundedCornerShape(22.dp))
            .padding(14.dp)
            .semantics(mergeDescendants = false) { contentDescription = "Poll: ${poll.question}" },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(AvIcons.Poll, null, tint = colors.accentInk, modifier = Modifier.size(16.dp))
            val votes = if (poll.totalVotes == 1) "1 vote" else "${poll.totalVotes} votes"
            Text(
                "Poll · $votes" + if (poll.closed) " · Closed" else if (poll.multi) " · Pick any" else "",
                style = AvailablissTheme.type.captionStrong,
                color = colors.accentInk,
                modifier = Modifier.weight(1f),
            )
        }
        Text(poll.question, style = AvailablissTheme.type.titleXSmall, color = colors.ink, modifier = Modifier.semantics { heading() })
        poll.options.forEach { option ->
            PollOptionRow(option, poll.totalVotes, poll.multi, enabled = !poll.closed, onClick = { onToggle(option.id) })
        }
        if (poll.canClose && !poll.closed && onClose != null) {
            TextAction("Close poll", onClose, color = colors.muted, style = AvailablissTheme.type.label, modifier = Modifier.align(Alignment.End))
        }
    }
}

@Composable
private fun PollOptionRow(option: PollOptionUi, total: Int, multi: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = AvailablissTheme.colors
    val fraction by animateFloatAsState(if (total == 0) 0f else option.count / total.toFloat(), tween(400), label = "poll-bar")
    val shape = Shapes.controlSmall
    Box(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 46.dp)
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, if (option.mine) colors.accent else colors.glassHi, shape)
            .toggleable(value = option.mine, enabled = enabled, role = if (multi) Role.Checkbox else Role.RadioButton, onValueChange = { onClick() })
            .semantics { contentDescription = "${option.label}, ${option.count} votes" + if (option.mine) ", your vote" else "" },
    ) {
        Box(
            Modifier
                .matchParentSize()
                .padding(0.dp),
        ) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(if (option.leading) colors.accentSoft else colors.line))
        }
        Row(
            Modifier.fillMaxWidth().defaultMinSize(minHeight = 46.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val markShape = if (multi) RoundedCornerShape(5.dp) else CircleShape
            Box(
                Modifier
                    .size(18.dp)
                    .clip(markShape)
                    .then(if (option.mine) Modifier.background(colors.accent) else Modifier.border(2.dp, colors.line3, markShape)),
                contentAlignment = Alignment.Center,
            ) {
                if (option.mine) Icon(AvIcons.Check, null, tint = Color.White, modifier = Modifier.size(11.dp))
            }
            Text(option.label, style = AvailablissTheme.type.bodySmallStrong, color = colors.ink, modifier = Modifier.weight(1f))
            Text(option.count.toString(), style = AvailablissTheme.type.label, color = colors.neutralInk)
        }
    }
}

@Preview
@Composable
private fun PollCardPreview() {
    AvailablissPreviewTheme {
        Box(Modifier.background(AvailablissTheme.colors.bg).padding(16.dp)) {
            PollCard(
                PollUi(
                    id = "p", question = "Where should we stay?", totalVotes = 5, multi = false, closed = false,
                    options = listOf(
                        PollOptionUi("a", "Keswick cottage", 3, mine = true, leading = true),
                        PollOptionUi("b", "Ambleside YHA", 1, mine = false, leading = false),
                        PollOptionUi("c", "Camping at Wasdale", 1, mine = false, leading = false),
                    ),
                ),
                onToggle = {},
            )
        }
    }
}
