package dev.joeherbert.availabliss.feature.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.joeherbert.availabliss.core.network.model.MAX_LINK_USES
import dev.joeherbert.availabliss.core.network.model.linkLimitFrom
import dev.joeherbert.availabliss.core.network.model.linkLimitInput
import dev.joeherbert.availabliss.designsystem.component.AvTextField
import dev.joeherbert.availabliss.designsystem.component.Chip
import dev.joeherbert.availabliss.designsystem.component.PrimaryButton
import dev.joeherbert.availabliss.designsystem.component.TextAction
import dev.joeherbert.availabliss.designsystem.icon.AvIcons
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import dev.joeherbert.availabliss.designsystem.theme.Shapes

/**
 * "Share a link" (Members and the last onboarding step): type how many people can join with it
 * (1 to [MAX_LINK_USES], one to start) or choose Unlimited, then Share invite link makes it and opens the share sheet. Once there's
 * a link ([linkUrl], described by [linkSummary]) Share sends that one, and New link starts another.
 */
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
    var usesText by rememberSaveable { mutableStateOf("1") }
    var unlimited by rememberSaveable { mutableStateOf(false) }
    // A new link arrived: back to sharing it, and the next one starts from one person again.
    LaunchedEffect(linkUrl) {
        makingAnother = false
        usesText = "1"
        unlimited = false
    }
    // Shows the number that will be used (an empty or out-of-range entry, clamped).
    val settle = { usesText = linkLimitFrom(usesText).toString() }
    val create = {
        if (!unlimited) settle()
        onCreate(if (unlimited) null else linkLimitFrom(usesText))
    }
    val choosing = linkUrl == null || makingAnother

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Share a link", style = type.bodySmallStrong, color = colors.ink)
            Text(
                if (choosing) "How many people can join with it?" else linkSummary.orEmpty(),
                style = type.caption.copy(fontWeight = FontWeight.Normal),
                color = colors.muted,
            )
        }
        if (choosing) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AvTextField(
                    value = if (unlimited) "" else usesText,
                    onValueChange = { usesText = linkLimitInput(it) },
                    placeholder = if (unlimited) "Anyone" else "1",
                    height = 44.dp,
                    shape = Shapes.controlSmall,
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                    onImeAction = settle,
                    enabled = !unlimited,
                    modifier = Modifier.weight(1f).semantics { contentDescription = "People who can join with the link, 1 to $MAX_LINK_USES" },
                )
                Chip("Unlimited", selected = unlimited, onClick = {
                    if (!unlimited) settle()
                    unlimited = !unlimited
                }, height = 44.dp)
            }
            PrimaryButton(
                text = "Share invite link",
                onClick = create,
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
