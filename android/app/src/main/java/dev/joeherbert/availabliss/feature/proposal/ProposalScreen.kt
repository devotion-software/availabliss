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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.joeherbert.availabliss.core.network.model.ProposalStatus
import dev.joeherbert.availabliss.core.network.model.ResponseKind
import dev.joeherbert.availabliss.designsystem.component.AvSnackbarHost
import dev.joeherbert.availabliss.designsystem.component.AvailablissBackground
import dev.joeherbert.availabliss.designsystem.component.Avatar
import dev.joeherbert.availabliss.designsystem.component.AvatarPerson
import dev.joeherbert.availabliss.designsystem.component.ConfirmOption
import dev.joeherbert.availabliss.designsystem.component.ConfirmSheet
import dev.joeherbert.availabliss.designsystem.component.CoverPhoto
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

/** Callbacks for [ProposalContent], bundled to keep signatures readable. */
data class ProposalActions(
    val onBack: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onRespond: (ResponseKind) -> Unit = {},
    val onOverride: (userId: String, ResponseKind) -> Unit = { _, _ -> },
    val onCancel: () -> Unit = {},
    val onConfirmAnyway: () -> Unit = {},
    val onKeepPlan: () -> Unit = {},
    val onCancelPlan: () -> Unit = {},
    val onAddToCalendar: () -> Unit = {},
    val onOpenChat: (groupId: String) -> Unit = {},
)

@Composable
fun ProposalScreen(
    onBack: () -> Unit,
    onOpenChat: (groupId: String) -> Unit,
    onOpenGroupCalendar: (groupId: String) -> Unit = {},
    viewModel: ProposalViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = rememberSnackbarHostState()
    UserMessageEffect(state.message, snackbar, viewModel::onMessageShown)
    AddToCalendarEffect(state.calendar, viewModel::onIntentHandled, viewModel::chooseCalendar, viewModel::cancelCalendarChoice)
    LaunchedEffect(state.closed) { if (state.closed) onBack() }
    LaunchedEffect(state.findAnotherDate) {
        if (state.findAnotherDate) {
            viewModel.onFoundAnotherDate()
            onOpenGroupCalendar(state.groupId)
        }
    }
    val currentOnBack by rememberUpdatedState(onBack)
    val currentOnOpenChat by rememberUpdatedState(onOpenChat)
    // One instance for the screen's lifetime so unchanged parts skip recomposition.
    val actions = remember(viewModel) {
        ProposalActions(
            onBack = { currentOnBack() },
            onRetry = viewModel::refresh,
            onRespond = viewModel::respond,
            onOverride = viewModel::override,
            onCancel = viewModel::cancel,
            onConfirmAnyway = viewModel::confirmAnyway,
            onKeepPlan = viewModel::keepPlan,
            onCancelPlan = viewModel::cancelPlan,
            onAddToCalendar = viewModel::addToCalendar,
            onOpenChat = { currentOnOpenChat(it) },
        )
    }
    ProposalContent(state = state, actions = actions)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) { AvSnackbarHost(snackbar, Modifier.navigationBarsPadding().padding(bottom = 180.dp)) }
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
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 220.dp),
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
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                            // Maybes are counted apart from the firm yeses in the ring.
                            if (state.maybe > 0) StatusPill("${state.maybe} maybe", PillTone.Indigo)
                        }
                    }
                }

                state.clash?.let { ClashPanel(it, state.clashDetail) }

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

                if (state.canConfirmAnyway) {
                    var confirming by remember { mutableStateOf(false) }
                    SecondaryButton(
                        text = "Confirm anyway",
                        onClick = { confirming = true },
                        icon = AvIcons.Check,
                        enabled = !state.responding,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "Not everyone can make it? Go ahead with who can.",
                        style = AvailablissTheme.type.caption.copy(fontWeight = FontWeight.Normal),
                        color = colors.muted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (confirming) {
                        ConfirmSheet(
                            title = "Confirm anyway?",
                            message = state.confirmAnywayMessage,
                            options = listOf(
                                ConfirmOption("Confirm the plan", onClick = {
                                    confirming = false
                                    actions.onConfirmAnyway()
                                }, icon = AvIcons.Check),
                            ),
                            onDismiss = { confirming = false },
                        )
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
            ResponseKind.Maybe -> "Maybe" to PillTone.Indigo
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
                ResponseKind.Maybe to "Mark $name as maybe",
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
            ResponseKind.Maybe -> "You said maybe. Can you say for sure?"
            ResponseKind.Declined -> "You said you can't make it."
            else -> if (state.iAmBusy) "You're busy then. Can you still make it?" else "Can you make it?"
        }
        Text(prompt, style = AvailablissTheme.type.label, color = colors.ink, modifier = Modifier.padding(start = 4.dp))
        // Can't make it and Maybe side by side, then Accept full width: three in a row don't fit on a phone.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton(
                text = "Can't make it",
                onClick = { onRespond(ResponseKind.Declined) },
                enabled = !state.responding && state.myResponse != ResponseKind.Declined,
                modifier = Modifier.weight(1f),
                textStyle = AvailablissTheme.type.bodyMedium,
            )
            val maybe = state.myResponse == ResponseKind.Maybe
            SecondaryButton(
                text = "Maybe",
                onClick = { onRespond(ResponseKind.Maybe) },
                enabled = !state.responding && !maybe,
                modifier = Modifier.weight(1f),
                icon = if (maybe) AvIcons.Check else null,
                textStyle = AvailablissTheme.type.bodyMedium,
                borderColor = if (maybe) colors.accent else colors.line2,
            )
        }
        PrimaryButton(
            text = if (state.myResponse == ResponseKind.Accepted) "Accepted" else "Accept",
            onClick = { onRespond(ResponseKind.Accepted) },
            height = 50.dp,
            shape = Shapes.control,
            textStyle = AvailablissTheme.type.bodyMedium,
            enabled = state.myResponse != ResponseKind.Accepted,
            loading = state.responding,
            icon = if (state.myResponse == ResponseKind.Accepted) AvIcons.Check else null,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The confirmed "Everyone's in." celebration ("It's on." when confirmed before everyone accepted). */
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
            Spacer(Modifier.weight(1f))
            if (state.groupId.isNotEmpty()) GlassIconButton(AvIcons.Chat, "Go to chat", { actions.onOpenChat(state.groupId) })
        }
        ReviewPrompt(state, actions)
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
                        (if (state.everyoneIn) "Everyone accepted ${state.range}" else "It's on: ${state.range}") + if (state.groupName.isNotBlank()) " for ${state.groupName}" else "",
                        style = AvailablissTheme.type.labelRegular.copy(fontWeight = FontWeight.Normal),
                        color = colors.ink,
                    )
                }
            }
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 24.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            VennLogo(
                132.dp,
                contentDescription = null,
                modifier = Modifier.scale(bloom.value * breathe).alpha(((bloom.value - 0.6f) / 0.4f).coerceIn(0f, 1f)),
            )
            Text(if (state.everyoneIn) "Everyone's in." else "It's on.", style = AvailablissTheme.type.displayLarge, color = colors.ink, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
            // "All 4 of you can make" (or "3 of 4 can make"), then the date and time large on their own lines, then the place.
            Column(
                Modifier.padding(horizontal = 10.dp).semantics(mergeDescendants = true) {},
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    if (state.everyoneIn) "All ${state.coming.size.takeIf { it > 0 } ?: state.total} of you can make"
                    else "${state.coming.size} of ${state.invited} can make",
                    style = AvailablissTheme.type.bodyLarge,
                    color = colors.muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
                Text(state.rangeDate.ifEmpty { state.range }, style = AvailablissTheme.type.displaySmall, color = colors.ink, textAlign = TextAlign.Center)
                state.rangeTime?.let {
                    Text(it, style = AvailablissTheme.type.titleLarge, color = colors.ink, textAlign = TextAlign.Center)
                }
                state.location?.let {
                    Text(
                        "in $it",
                        style = AvailablissTheme.type.bodyLarge,
                        color = colors.muted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            // The first card, above Who's coming: the cover photo (if any) and Add to calendar (or which
            // calendar it's in). Changing your reply sits apart, below it.
            GlassPanel(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                shape = Shapes.panel,
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                state.coverImage?.let { CoverPhoto(it, aspectRatio = 2.4f) }
                AddToCalendarButton(state.calendarStatus, onClick = actions.onAddToCalendar)
            }
            if (state.canChangeReply) ChangeReply(state, actions.onRespond)
            if (state.coming.isNotEmpty()) {
                PeoplePanel("Who's coming", state.coming.size.toString(), state.coming)
            }
            if (state.maybes.isNotEmpty()) {
                PeoplePanel("Maybe", state.maybes.size.toString(), state.maybes)
            }
            if (state.cantMakeIt.isNotEmpty()) {
                PeoplePanel("Can't make it", state.cantMakeIt.size.toString(), state.cantMakeIt, quiet = true)
            }
            if (state.notReplied.isNotEmpty()) {
                PeoplePanel("Not replied", state.notReplied.size.toString(), state.notReplied, quiet = true)
            }
            if (state.joinedLater.isNotEmpty()) {
                PeoplePanel("Joined since", null, state.joinedLater, quiet = true)
            }
        }
    }
}

/**
 * For the proposer or an admin after someone who was coming dropped out: a panel that stays
 * until they choose, and a sheet (opened by itself once per visit) to keep the plan or cancel it.
 */
@Composable
private fun ReviewPrompt(state: ProposalUiState, actions: ProposalActions) {
    if (!state.needsReview) return
    var open by remember { mutableStateOf(false) }
    var prompted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!prompted) {
            prompted = true
            kotlinx.coroutines.delay(600)
            open = true
        }
    }
    Column(Modifier.fillMaxWidth().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ClashPanel(state.reviewHeadline, "Keep the plan with who can make it, or cancel it and find another date.")
        SecondaryButton(
            text = "Keep or cancel…",
            onClick = { open = true },
            enabled = !state.responding,
            height = 44.dp,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (open) {
        ConfirmSheet(
            title = state.reviewHeadline,
            message = "The plan's still on. Keep it with who can make it, or cancel it and find another date.",
            options = listOf(
                ConfirmOption("Keep the plan", onClick = {
                    open = false
                    actions.onKeepPlan()
                }, icon = AvIcons.Check),
                ConfirmOption("Cancel and find another date", onClick = {
                    open = false
                    actions.onCancelPlan()
                }, icon = AvIcons.Calendar, kind = ConfirmOption.Kind.Destructive),
            ),
            onDismiss = { open = false },
            cancelText = "Decide later",
        )
    }
}

/**
 * "Can't make it any more?" (or "Can you say for sure?", "Can make it after all?") under the
 * plan, confirmed in a sheet offering the other replies: I can make it, Maybe, I can't make it.
 */
@Composable
private fun ChangeReply(state: ProposalUiState, onRespond: (ResponseKind) -> Unit) {
    val colors = AvailablissTheme.colors
    var open by remember { mutableStateOf(false) }
    val label = when (state.myResponse) {
        ResponseKind.Accepted -> "Can't make it any more?"
        ResponseKind.Maybe -> "Can you say for sure?"
        ResponseKind.Declined -> "Can make it after all?"
        else -> "Can you make it?"
    }
    Text(
        label,
        style = AvailablissTheme.type.label,
        color = colors.muted,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
            .clickable(enabled = !state.responding, role = Role.Button) { open = true }
            .padding(vertical = 10.dp),
    )
    if (!open) return
    val iCant = ConfirmOption("I can't make it", onClick = {
        open = false
        onRespond(ResponseKind.Declined)
    }, kind = if (state.myResponse == ResponseKind.Accepted) ConfirmOption.Kind.Destructive else ConfirmOption.Kind.Secondary)
    val iCan = ConfirmOption("I can make it", onClick = {
        open = false
        onRespond(ResponseKind.Accepted)
    }, icon = AvIcons.Check)
    val maybe = ConfirmOption("Maybe", onClick = {
        open = false
        onRespond(ResponseKind.Maybe)
    }, kind = ConfirmOption.Kind.Secondary)
    ConfirmSheet(
        title = when (state.myResponse) {
            ResponseKind.Accepted -> "Can't make it any more?"
            ResponseKind.Maybe -> "Can you say for sure?"
            else -> "Can you make it?"
        },
        message = if (state.myResponse == ResponseKind.Accepted) {
            "The plan stays on. If you can't make it, whoever proposed it and the admins will be told, so they can keep it or find another date."
        } else {
            "Let everyone know whether you're coming."
        },
        options = when (state.myResponse) {
            ResponseKind.Accepted -> listOf(maybe, iCant)
            ResponseKind.Maybe -> listOf(iCan, iCant)
            ResponseKind.Declined -> listOf(iCan, maybe)
            else -> listOf(iCan, maybe, iCant)
        },
        onDismiss = { open = false },
    )
}

/** Who's coming or said maybe (or, [quiet], who can't, never replied or joined the group since): a titled glass list of people. */
@Composable
private fun PeoplePanel(title: String, trailing: String?, people: List<ProposalMemberUi>, modifier: Modifier = Modifier, quiet: Boolean = false) {
    val colors = AvailablissTheme.colors
    GlassPanel(
        modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp).semantics { heading() }) {
            Text(title, style = AvailablissTheme.type.captionStrong, color = colors.muted, modifier = Modifier.weight(1f))
            trailing?.let { Text(it, style = AvailablissTheme.type.captionStrong, color = colors.muted) }
        }
        people.forEach { person ->
            RowDivider()
            Row(
                Modifier.fillMaxWidth().defaultMinSize(minHeight = if (quiet) 48.dp else 52.dp).alpha(if (quiet) 0.65f else 1f)
                    .semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Avatar(person.person, size = if (quiet) 28.dp else 34.dp)
                Column(Modifier.weight(1f)) {
                    Text(person.name, style = AvailablissTheme.type.bodyMedium, color = colors.ink, maxLines = 1)
                    if (person.note.isNotEmpty()) {
                        Text(person.note, style = AvailablissTheme.type.caption.copy(fontWeight = FontWeight.Normal), color = colors.muted)
                    }
                }
            }
        }
    }
}

private val previewMembers = listOf(
    ProposalMemberUi("a", AvatarPerson("a", "Ana"), "Ana", "Proposed this", ResponseKind.Accepted, false),
    ProposalMemberUi("p", AvatarPerson("p", "Priya"), "Priya", "Synced", ResponseKind.Accepted, false),
    ProposalMemberUi("s", AvatarPerson("s", "Sam"), "Sam (you)", "Said maybe", ResponseKind.Maybe, false),
    ProposalMemberUi("t", AvatarPerson("t", "Tom"), "Tom", "Hasn't replied", ResponseKind.Pending, true),
)

@Preview
@Composable
private fun ProposalPendingPreview() {
    AvailablissPreviewTheme {
        ProposalContent(
            ProposalUiState(
                loading = false, groupName = "Lake District crew", proposedBy = "Ana proposed · 2h ago",
                range = "Fri 13 – Sun 15 Nov", detail = "From Fri 18:00 · Keswick", accepted = 2, maybe = 1, total = 4,
                members = previewMembers, isAdmin = true, myResponse = ResponseKind.Maybe,
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
            ProposalUiState(
                loading = false, groupName = "Lake District crew", range = "Sat 14 Nov · 18:30 – 21:00", rangeDate = "Sat 14 Nov", rangeTime = "18:30 – 21:00", total = 4, location = "Keswick", status = ProposalStatus.Confirmed,
                coming = previewMembers.take(2), maybes = previewMembers.slice(2..2), joinedLater = previewMembers.drop(3), everyoneIn = false, invited = 3
            ),
            ProposalActions(),
        )
    }
}
