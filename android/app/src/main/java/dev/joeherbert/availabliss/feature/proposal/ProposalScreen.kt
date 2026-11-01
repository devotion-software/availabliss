package dev.joeherbert.availabliss.feature.proposal

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.joeherbert.availabliss.core.network.model.ProposalStatus
import dev.joeherbert.availabliss.core.network.model.ResponseKind
import dev.joeherbert.availabliss.designsystem.component.Avatar
import dev.joeherbert.availabliss.designsystem.component.AvatarPerson
import dev.joeherbert.availabliss.designsystem.component.AvSnackbarHost
import dev.joeherbert.availabliss.designsystem.component.AvailablissBackground
import dev.joeherbert.availabliss.designsystem.component.ErrorBanner
import dev.joeherbert.availabliss.designsystem.component.GlassIconButton
import dev.joeherbert.availabliss.designsystem.component.GlassPanel
import dev.joeherbert.availabliss.designsystem.component.LoadingIndicator
import dev.joeherbert.availabliss.designsystem.component.OrbLayout
import dev.joeherbert.availabliss.designsystem.component.PillTone
import dev.joeherbert.availabliss.designsystem.component.PrimaryButton
import dev.joeherbert.availabliss.designsystem.component.RowDivider
import dev.joeherbert.availabliss.designsystem.component.ScreenHeader
import dev.joeherbert.availabliss.designsystem.component.SecondaryButton
import dev.joeherbert.availabliss.designsystem.component.StatusPill
import dev.joeherbert.availabliss.designsystem.component.SyncRing
import dev.joeherbert.availabliss.designsystem.component.UserMessageEffect
import dev.joeherbert.availabliss.designsystem.component.VennLogo
import dev.joeherbert.availabliss.designsystem.component.rememberSnackbarHostState
import dev.joeherbert.availabliss.designsystem.icon.AvIcons
import dev.joeherbert.availabliss.designsystem.theme.AvailablissPreviewTheme
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import dev.joeherbert.availabliss.designsystem.theme.Shapes
import dev.joeherbert.availabliss.feature.common.AddToCalendarButton
import dev.joeherbert.availabliss.feature.common.AddToCalendarEffect
import dev.joeherbert.availabliss.feature.common.CalendarPickerRow

/** Callbacks for [ProposalContent], bundled to keep signatures readable. */
data class ProposalActions(
    val onBack: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onRespond: (ResponseKind) -> Unit = {},
    val onOverride: (userId: String, ResponseKind) -> Unit = { _, _ -> },
    val onCancel: () -> Unit = {},
    val onAddToCalendar: () -> Unit = {},
    val onSelectCalendar: (Long) -> Unit = {},
)

@Composable
fun ProposalScreen(onBack: () -> Unit, viewModel: ProposalViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = rememberSnackbarHostState()
    UserMessageEffect(state.message, snackbar, viewModel::onMessageShown)
    AddToCalendarEffect(state.calendar, viewModel::onIntentHandled)
    LaunchedEffect(state.closed) { if (state.closed) onBack() }
    ProposalContent(
        state = state,
        actions = ProposalActions(
            onBack = onBack,
            onRetry = viewModel::refresh,
            onRespond = viewModel::respond,
            onOverride = viewModel::override,
            onCancel = viewModel::cancel,
            onAddToCalendar = viewModel::addToCalendar,
            onSelectCalendar = viewModel::onSelectCalendar,
        ),
    )
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) { AvSnackbarHost(snackbar, Modifier.navigationBarsPadding().padding(bottom = 120.dp)) }
}

@Composable
fun ProposalContent(state: ProposalUiState, actions: ProposalActions) {
    AvailablissBackground(OrbLayout.Proposal) {
        Crossfade(targetState = state.status == ProposalStatus.Confirmed, label = "proposal-state") { confirmed ->
            if (confirmed) AllInContent(state, actions) else PendingContent(state, actions)
        }
    }
}

@Composable
private fun PendingContent(state: ProposalUiState, actions: ProposalActions) {
    val colors = AvailablissTheme.colors
    Box(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 160.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ScreenHeader(
                title = "Proposal",
                subtitle = state.groupName.ifBlank { null },
                onBack = actions.onBack,
                titleStyle = AvailablissTheme.type.headlineSmall,
                actions = { if (state.canCancel) CancelMenu(actions.onCancel) },
            )
            state.error?.let { ErrorBanner(it, onRetry = actions.onRetry) }
            if (state.loading && state.range.isEmpty()) LoadingIndicator()
            if (state.range.isNotEmpty()) {
            GlassPanel(Modifier.fillMaxWidth(), shape = Shapes.cardLarge, contentPadding = PaddingValues(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(state.proposedBy, style = AvailablissTheme.type.captionStrong, color = colors.muted)
                        state.title?.let { Text(it, style = AvailablissTheme.type.bodyMedium, color = colors.ink) }
                        Text(state.range, style = AvailablissTheme.type.headlineLarge, color = colors.ink, modifier = Modifier.semantics { heading() })
                        if (state.detail.isNotEmpty()) Text(state.detail, style = AvailablissTheme.type.labelRegular.copy(fontWeight = FontWeight.Normal), color = colors.muted)
                    }
                    val total = state.total.coerceAtLeast(1)
                    SyncRing(
                        progress = state.accepted / total.toFloat(),
                        size = 76.dp,
                        strokeWidth = 7.dp,
                        color = colors.ok,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${state.accepted}/${state.total}", style = AvailablissTheme.type.titleMedium, color = colors.ink)
                            Text("accepted", style = AvailablissTheme.type.nano.copy(fontWeight = FontWeight.Normal), color = colors.muted)
                        }
                    }
                }
            }

            if (state.members.isNotEmpty()) {
                GlassPanel(
                    Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.Top,
                ) {
                    state.members.forEachIndexed { index, member ->
                        if (index > 0) RowDivider()
                        MemberRow(member, onOverride = { actions.onOverride(member.userId, it) })
                    }
                }
            }

            if (state.isAdmin) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(AvIcons.Shield, null, tint = colors.muted, modifier = Modifier.size(14.dp))
                    Text("As an admin you can set anyone's reply.", style = AvailablissTheme.type.caption.copy(fontWeight = FontWeight.Normal), color = colors.muted)
                }
            }
            }
        }

        if (state.status == ProposalStatus.Open && state.range.isNotEmpty()) {
            ReplyBar(state, actions.onRespond, Modifier.align(Alignment.BottomCenter))
        } else if (state.status == ProposalStatus.Cancelled) {
            GlassPanel(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 12.dp, end = 12.dp, bottom = 16.dp).fillMaxWidth(),
                shape = Shapes.card,
            ) {
                Text("This proposal was cancelled.", style = AvailablissTheme.type.label, color = colors.muted)
            }
        }
    }
}

@Composable
private fun MemberRow(member: ProposalMemberUi, onOverride: (ResponseKind) -> Unit) {
    val colors = AvailablissTheme.colors
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Avatar(member.person, size = 34.dp)
        Column(Modifier.weight(1f)) {
            Text(member.name, style = AvailablissTheme.type.bodyMedium, color = colors.ink)
            Text(member.note, style = AvailablissTheme.type.caption.copy(fontWeight = FontWeight.Normal), color = colors.muted)
        }
        if (member.canOverride) OverrideMenu(member.name, onOverride)
        val (label, tone) = when (member.response) {
            ResponseKind.Accepted -> "Accepted" to PillTone.Mint
            ResponseKind.Pending -> "Waiting" to PillTone.Apricot
            ResponseKind.Declined -> "Can't make it" to PillTone.Neutral
        }
        StatusPill(label, tone, padding = PaddingValues(horizontal = 9.dp, vertical = 4.dp))
    }
}

@Composable
private fun OverrideMenu(name: String, onOverride: (ResponseKind) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val colors = AvailablissTheme.colors
    Box {
        SecondaryButton(
            text = "Override",
            onClick = { open = true },
            height = 34.dp,
            shape = Shapes.inner,
            textStyle = AvailablissTheme.type.captionStrong,
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = colors.bg) {
            listOf(
                ResponseKind.Accepted to "Mark $name as accepted",
                ResponseKind.Declined to "Mark $name as can't make it",
                ResponseKind.Pending to "Reset $name's reply",
            ).forEach { (kind, label) ->
                DropdownMenuItem(
                    text = { Text(label, style = AvailablissTheme.type.bodySmallStrong, color = colors.ink) },
                    onClick = {
                        open = false
                        onOverride(kind)
                    },
                )
            }
        }
    }
}

@Composable
private fun CancelMenu(onCancel: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    val colors = AvailablissTheme.colors
    Box {
        GlassIconButton(AvIcons.More, "More options", { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = colors.bg) {
            DropdownMenuItem(
                text = { Text("Cancel proposal", style = AvailablissTheme.type.bodySmallStrong, color = colors.warnInk) },
                leadingIcon = { Icon(AvIcons.Trash, null, tint = colors.warnInk, modifier = Modifier.size(18.dp)) },
                onClick = {
                    open = false
                    onCancel()
                },
            )
        }
    }
}

@Composable
private fun ReplyBar(state: ProposalUiState, onRespond: (ResponseKind) -> Unit, modifier: Modifier) {
    val colors = AvailablissTheme.colors
    GlassPanel(
        modifier.navigationBarsPadding().padding(start = 12.dp, end = 12.dp, bottom = 16.dp).fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(26.dp),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val prompt = when (state.myResponse) {
            ResponseKind.Accepted -> "You're in. Changed your mind?"
            ResponseKind.Declined -> "You said you can't make it."
            else -> "Can you make it?"
        }
        Text(prompt, style = AvailablissTheme.type.label, color = colors.ink, modifier = Modifier.padding(start = 4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton(
                text = "Can't make it",
                onClick = { onRespond(ResponseKind.Declined) },
                enabled = !state.responding && state.myResponse != ResponseKind.Declined,
                modifier = Modifier.weight(1f),
                textStyle = AvailablissTheme.type.bodyMedium,
            )
            PrimaryButton(
                text = if (state.myResponse == ResponseKind.Accepted) "Accepted" else "Accept",
                onClick = { onRespond(ResponseKind.Accepted) },
                height = 50.dp,
                shape = Shapes.control,
                textStyle = AvailablissTheme.type.bodyMedium,
                enabled = state.myResponse != ResponseKind.Accepted,
                loading = state.responding,
                icon = if (state.myResponse == ResponseKind.Accepted) AvIcons.Check else null,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** The confirmed "Everyone's in." celebration. */
@Composable
private fun AllInContent(state: ProposalUiState, actions: ProposalActions) {
    val colors = AvailablissTheme.colors
    val bloom = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) { bloom.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) }
    val breathe by rememberInfiniteTransition(label = "venn").animateFloat(
        initialValue = 1f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(2200), RepeatMode.Reverse),
        label = "venn-breathe",
    )
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(start = 14.dp, end = 14.dp, top = 16.dp, bottom = 24.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(AvIcons.Back, "Back", actions.onBack)
        }
        GlassPanel(
            Modifier.fillMaxWidth().padding(top = 10.dp).semantics { liveRegion = LiveRegionMode.Polite },
            shape = Shapes.panel,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                VennLogo(34.dp, contentDescription = null)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        buildAnnotatedString {
                            append("Availabliss")
                            withStyle(SpanStyle(fontWeight = FontWeight.Medium, color = colors.muted)) { append(" · now") }
                        },
                        style = AvailablissTheme.type.label.copy(fontWeight = FontWeight.Bold),
                        color = colors.ink,
                    )
                    Text(
                        "Everyone accepted ${state.range}" + if (state.groupName.isNotBlank()) " for ${state.groupName}" else "",
                        style = AvailablissTheme.type.labelRegular.copy(fontWeight = FontWeight.Normal),
                        color = colors.ink,
                    )
                }
            }
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
        ) {
            VennLogo(
                132.dp,
                contentDescription = null,
                modifier = Modifier.scale(bloom.value * breathe).alpha(((bloom.value - 0.6f) / 0.4f).coerceIn(0f, 1f)),
            )
            Text("Everyone's in.", style = AvailablissTheme.type.displayLarge, color = colors.ink, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
            val where = state.location?.let { " in $it" }.orEmpty()
            Text(
                "All ${state.total} of you can make\n${state.range}$where.",
                style = AvailablissTheme.type.bodyLarge.copy(lineHeight = AvailablissTheme.type.bodyLarge.fontSize * 1.4f),
                color = colors.muted,
                textAlign = TextAlign.Center,
            )
        }
        GlassPanel(Modifier.fillMaxWidth(), shape = Shapes.cardLarge, contentPadding = PaddingValues(14.dp)) {
            CalendarPickerRow(state.calendar, actions.onSelectCalendar)
            AddToCalendarButton(added = state.addedToCalendar, onClick = actions.onAddToCalendar)
        }
    }
}

private val previewMembers = listOf(
    ProposalMemberUi("a", AvatarPerson("a", "Ana"), "Ana", "Proposed this", ResponseKind.Accepted, false),
    ProposalMemberUi("p", AvatarPerson("p", "Priya"), "Priya", "Synced", ResponseKind.Accepted, false),
    ProposalMemberUi("s", AvatarPerson("s", "Sam"), "Sam (you)", "Admin", ResponseKind.Pending, false),
    ProposalMemberUi("t", AvatarPerson("t", "Tom"), "Tom", "Hasn't replied", ResponseKind.Pending, true),
)

@Preview
@Composable
private fun ProposalPendingPreview() {
    AvailablissPreviewTheme {
        ProposalContent(
            ProposalUiState(
                loading = false, groupName = "Lake District crew", proposedBy = "Ana proposed · 2h ago",
                range = "Fri 13 – Sun 15 Nov", detail = "From Fri 18:00 · Keswick", accepted = 2, total = 4,
                members = previewMembers, isAdmin = true,
            ),
            ProposalActions(),
        )
    }
}

@Preview
@Composable
private fun ProposalConfirmedPreview() {
    AvailablissPreviewTheme(dark = true) {
        ProposalContent(
            ProposalUiState(loading = false, groupName = "Lake District crew", range = "Fri 13 – Sun 15 Nov", total = 6, location = "Keswick", status = ProposalStatus.Confirmed),
            ProposalActions(),
        )
    }
}
