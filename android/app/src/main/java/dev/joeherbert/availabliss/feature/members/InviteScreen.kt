package dev.joeherbert.availabliss.feature.members

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.joeherbert.availabliss.designsystem.component.AvailablissBackground
import dev.joeherbert.availabliss.designsystem.component.ErrorBanner
import dev.joeherbert.availabliss.designsystem.component.GlassIconButton
import dev.joeherbert.availabliss.designsystem.component.GlassPanel
import dev.joeherbert.availabliss.designsystem.component.LoadingIndicator
import dev.joeherbert.availabliss.designsystem.component.OrbLayout
import dev.joeherbert.availabliss.designsystem.component.PrimaryButton
import dev.joeherbert.availabliss.designsystem.component.TextAction
import dev.joeherbert.availabliss.designsystem.component.VennLogo
import dev.joeherbert.availabliss.designsystem.icon.AvIcons
import dev.joeherbert.availabliss.designsystem.theme.AvailablissPreviewTheme
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import dev.joeherbert.availabliss.designsystem.theme.Shapes
import dev.joeherbert.availabliss.designsystem.theme.Spacing

/** Accept an invite from `/invite/:token`. */
@Composable
fun InviteScreen(onBack: () -> Unit, onAccepted: (String) -> Unit, viewModel: InviteViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.joinedGroupId) { state.joinedGroupId?.let(onAccepted) }
    InviteContent(state, onBack = onBack, onJoin = viewModel::join, onRetry = viewModel::load)
}

@Composable
fun InviteContent(state: InviteUiState, onBack: () -> Unit, onJoin: () -> Unit, onRetry: () -> Unit) {
    val colors = AvailablissTheme.colors
    val type = AvailablissTheme.type
    AvailablissBackground(OrbLayout.SignIn) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            GlassIconButton(AvIcons.Back, "Back", onBack)
            Spacer(Modifier.weight(1f))
            VennLogo(72.dp, Modifier.align(Alignment.CenterHorizontally))
            when {
                state.loading -> LoadingIndicator()
                state.invalid -> GlassPanel(Modifier.fillMaxWidth(), shape = Shapes.cardLarge, contentPadding = PaddingValues(20.dp)) {
                    Text("This invite has expired", style = type.headlineSmall, color = colors.ink)
                    Text(
                        "Invite links stop working after a while or once they've been used. Ask whoever invited you for a fresh link.",
                        style = type.body,
                        color = colors.muted,
                    )
                    PrimaryButton("Back to Availabliss", onBack, height = 50.dp, shape = Shapes.control, modifier = Modifier.fillMaxWidth())
                }
                state.preview != null -> {
                    val p = state.preview
                    GlassPanel(Modifier.fillMaxWidth(), shape = Shapes.cardLarge, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
                        Text("${p.inviterName} invited you to", style = type.label, color = colors.muted)
                        Text(p.groupName, style = type.displaySmall, color = colors.ink)
                        Text(
                            listOf("${p.memberCount} ${if (p.memberCount == 1) "member" else "members"}", p.expiry)
                                .filter { it.isNotBlank() }.joinToString(" · "),
                            style = type.body,
                            color = colors.muted,
                        )
                        Text(
                            "Your group only ever sees when you're busy, never what you're doing.",
                            style = type.caption,
                            color = colors.muted,
                        )
                        Spacer(Modifier.height(2.dp))
                        PrimaryButton("Join group", onJoin, loading = state.joining, modifier = Modifier.fillMaxWidth())
                        TextAction("Not now", onBack, modifier = Modifier.align(Alignment.CenterHorizontally))
                    }
                }
            }
            state.error?.let { ErrorBanner(it, onRetry = if (state.preview == null) onRetry else null) }
            Spacer(Modifier.weight(1f))
        }
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun InvitePreview() {
    AvailablissPreviewTheme {
        InviteContent(
            InviteUiState(loading = false, preview = InvitePreviewUi("Lake District crew", "Ana", 6, "Expires 14 Nov")),
            onBack = {}, onJoin = {}, onRetry = {},
        )
    }
}
