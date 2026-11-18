package dev.joeherbert.availabliss.feature.proposal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.joeherbert.availabliss.designsystem.component.GlassPanel
import dev.joeherbert.availabliss.designsystem.component.TextAction
import dev.joeherbert.availabliss.designsystem.component.chat.DateTile
import dev.joeherbert.availabliss.designsystem.component.chat.ProposalCardTone
import dev.joeherbert.availabliss.designsystem.component.chat.ProposalCardUi
import dev.joeherbert.availabliss.designsystem.icon.AvIcons
import dev.joeherbert.availabliss.designsystem.theme.AvailablissPreviewTheme
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import dev.joeherbert.availabliss.designsystem.theme.Shapes

/**
 * The calendar's compact "Plans" card: up to two open proposals / the next confirmed plan, and
 * "See all" for the Plans screen. Renders nothing when there's nothing coming up.
 */
@Composable
fun PlansCard(
    onOpenProposal: (String) -> Unit,
    onSeeAll: () -> Unit,
    viewModel: GroupProposalsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PlansCardContent(state.highlights, onOpenProposal, onSeeAll)
}

@Composable
private fun PlansCardContent(items: List<ProposalListItem>, onOpenProposal: (String) -> Unit, onSeeAll: () -> Unit) {
    if (items.isEmpty()) return
    val colors = AvailablissTheme.colors
    val type = AvailablissTheme.type
    GlassPanel(
        Modifier.fillMaxWidth(),
        shape = Shapes.row,
        contentPadding = PaddingValues(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Plans", style = type.bodySmallStrong, color = colors.ink, modifier = Modifier.weight(1f).semantics { heading() })
            TextAction("See all", onSeeAll, style = type.captionStrong)
        }
        items.forEach { item ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(end = 8.dp)
                    .clip(Shapes.inner)
                    .clickable(role = Role.Button, onClick = { onOpenProposal(item.id) })
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DateTile(item.card.month, item.card.day, muted = item.card.tone == ProposalCardTone.Muted)
                Column(Modifier.weight(1f)) {
                    Text(item.headline, style = type.bodySmallStrong, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        item.detail,
                        style = type.caption.copy(fontWeight = FontWeight.Normal),
                        color = colors.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(AvIcons.Forward, null, tint = colors.muted, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Preview
@Composable
private fun PlansCardPreview() {
    AvailablissPreviewTheme {
        PlansCardContent(
            listOf(
                ProposalListItem(
                    "1",
                    ProposalCardUi("NOV", "13", "Ana proposed a time", "Fri 13 – Sun 15 Nov", "4 of 6 accepted"),
                    headline = "Pub quiz",
                    detail = "Fri 13 – Sun 15 Nov · 4 of 6 in · you're in",
                ),
                ProposalListItem(
                    "2",
                    ProposalCardUi("DEC", "4", "Tom proposed a time", "Fri 4 – Sun 6 Dec", "Everyone's in", ProposalCardTone.Confirmed),
                    detail = "Confirmed",
                ),
            ),
            {},
            {},
        )
    }
}
