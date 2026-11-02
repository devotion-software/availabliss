package dev.joeherbert.availabliss.designsystem.component.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.joeherbert.availabliss.designsystem.component.GlassPanel
import dev.joeherbert.availabliss.designsystem.theme.AvailablissPreviewTheme
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import dev.joeherbert.availabliss.designsystem.theme.Shapes

/** Visual state of a proposal / event card. */
enum class ProposalCardTone { Open, Confirmed, Muted }

@Immutable
data class ProposalCardUi(
    /** "NOV" */
    val month: String,
    /** "13" */
    val day: String,
    /** "Ana proposed a time" */
    val caption: String,
    /** "Fri 13 – Sun 15 Nov" */
    val title: String,
    /** "4 of 6 accepted" */
    val status: String,
    val tone: ProposalCardTone = ProposalCardTone.Open,
    /** Appended to [status] in warn ink: "Priya busy then". */
    val clash: String? = null,
)

/** Accent date tile (46×50, radius 12): "NOV" over "13". */
@Composable
fun DateTile(month: String, day: String, modifier: Modifier = Modifier, muted: Boolean = false) {
    val colors = AvailablissTheme.colors
    Column(
        modifier
            .size(46.dp, 50.dp)
            .clip(Shapes.inner)
            .background(if (muted) colors.line2 else colors.accent)
            .clearAndSetSemantics { },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val fg = if (muted) colors.ink else colors.onAccent
        Text(month, style = AvailablissTheme.type.nano, color = fg)
        Text(day, style = AvailablissTheme.type.titleSmall, color = fg)
    }
}

/** Proposal / event card used in chat and the proposals list. */
@Composable
fun ProposalCard(
    ui: ProposalCardUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    actionLabel: String? = "Reply",
) {
    val colors = AvailablissTheme.colors
    GlassPanel(
        modifier = modifier.fillMaxWidth(),
        shape = Shapes.row,
        contentPadding = PaddingValues(12.dp),
        onClick = onClick,
        onClickLabel = actionLabel ?: "Open",
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DateTile(ui.month, ui.day, muted = ui.tone == ProposalCardTone.Muted)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(ui.caption, style = AvailablissTheme.type.caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal), color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(ui.title, style = AvailablissTheme.type.bodyMedium, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    buildAnnotatedString {
                        append(ui.status)
                        ui.clash?.let { withStyle(SpanStyle(color = colors.warnInk)) { append(" · $it") } }
                    },
                    style = AvailablissTheme.type.captionStrong,
                    color = when (ui.tone) {
                        ProposalCardTone.Muted -> colors.muted
                        else -> colors.okInk
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            actionLabel?.let {
                Box(Modifier.padding(start = 4.dp)) {
                    Text(it, style = AvailablissTheme.type.label, color = colors.accentInk)
                }
            }
        }
    }
}

@Preview
@Composable
private fun ProposalCardPreview() {
    AvailablissPreviewTheme {
        Box(Modifier.background(AvailablissTheme.colors.bg).padding(16.dp)) {
            ProposalCard(ProposalCardUi("NOV", "13", "Ana proposed a time", "Fri 13 – Sun 15 Nov", "4 of 6 accepted", clash = "Priya busy then"), onClick = {})
        }
    }
}
