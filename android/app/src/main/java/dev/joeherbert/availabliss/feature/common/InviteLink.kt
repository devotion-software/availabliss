package dev.joeherbert.availabliss.feature.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.joeherbert.availabliss.core.network.model.LinkLimitChoices
import dev.joeherbert.availabliss.core.network.model.linkLimitLabel
import dev.joeherbert.availabliss.designsystem.component.Chip
import dev.joeherbert.availabliss.designsystem.component.PrimaryButton
import dev.joeherbert.availabliss.designsystem.component.TextAction
import dev.joeherbert.availabliss.designsystem.icon.AvIcons
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import dev.joeherbert.availabliss.designsystem.theme.Shapes

/**
 * "Share a link" (Members and the last onboarding step): choose how many people can join with it
 * (1, 5, 10, 25 or Unlimited), then Share invite link makes it and opens the share sheet. Once there's
 * a link ([linkUrl], described by [linkSummary]) Share sends that one, and New link starts another.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InviteLinkSection(
    linkUrl: String?,
    linkSummary: String?,
    creating: Boolean,
    onShare: () -> Unit,
    onCreate: (maxUses: Int?) -> Unit,
    modifier: Modifier = Modifier,
    buttonHeight: Dp = 50.dp,
) {
    val colors = AvailablissTheme.colors
    val type = AvailablissTheme.type
    var makingAnother by rememberSaveable { mutableStateOf(false) }
    var limit by rememberSaveable { mutableStateOf<Int?>(1) }
    // A new link arrived: back to sharing it, and the next one starts from one person again.
    LaunchedEffect(linkUrl) {
        makingAnother = false
        limit = 1
    }
    val choosing = linkUrl == null || makingAnother

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Share a link", style = type.bodySmallStrong, color = colors.ink)
            Text(
                if (choosing) "Choose how many people can join with it." else linkSummary.orEmpty(),
                style = type.caption.copy(fontWeight = FontWeight.Normal),
                color = colors.muted,
            )
        }
        if (choosing) {
            FlowRow(
                Modifier.semantics { contentDescription = "People who can join with the link" },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LinkLimitChoices.forEach { choice ->
                    Chip(linkLimitLabel(choice), selected = limit == choice, onClick = { limit = choice })
                }
            }
            PrimaryButton(
                text = "Share invite link",
                onClick = { onCreate(limit) },
                icon = AvIcons.Share,
                height = buttonHeight,
                shape = Shapes.control,
                loading = creating,
                modifier = Modifier.fillMaxWidth(),
            )
            if (makingAnother) {
                TextAction("Cancel", onClick = { makingAnother = false }, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        } else {
            PrimaryButton(
                text = "Share invite link",
                onClick = onShare,
                icon = AvIcons.Share,
                height = buttonHeight,
                shape = Shapes.control,
                modifier = Modifier.fillMaxWidth(),
            )
            TextAction("New link", onClick = { makingAnother = true }, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}
