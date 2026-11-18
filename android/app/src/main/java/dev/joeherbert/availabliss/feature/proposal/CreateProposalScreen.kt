package dev.joeherbert.availabliss.feature.proposal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import dev.joeherbert.availabliss.designsystem.component.PillTone
import dev.joeherbert.availabliss.designsystem.component.StatusPill
import dev.joeherbert.availabliss.designsystem.icon.AvIcons
import dev.joeherbert.availabliss.feature.plan.UpgradeSheet
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.joeherbert.availabliss.designsystem.component.AvSnackbarHost
import dev.joeherbert.availabliss.designsystem.component.AvTextField
import dev.joeherbert.availabliss.designsystem.component.AvailablissBackground
import dev.joeherbert.availabliss.designsystem.component.ButtonStyle
import dev.joeherbert.availabliss.designsystem.component.GlassPanel
import dev.joeherbert.availabliss.designsystem.component.OrbLayout
import dev.joeherbert.availabliss.designsystem.component.PrimaryButton
import dev.joeherbert.availabliss.designsystem.component.ScreenHeader
import dev.joeherbert.availabliss.designsystem.component.SectionLabel
import dev.joeherbert.availabliss.designsystem.component.UserMessageEffect
import dev.joeherbert.availabliss.designsystem.component.rememberSnackbarHostState
import dev.joeherbert.availabliss.designsystem.theme.AvailablissPreviewTheme
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import dev.joeherbert.availabliss.designsystem.theme.Shapes
import dev.joeherbert.availabliss.core.common.TimeRangeDraft
import dev.joeherbert.availabliss.designsystem.component.DateRangeInput

@Composable
fun CreateProposalScreen(
    onBack: () -> Unit,
    onCreated: (proposalId: String) -> Unit,
    viewModel: CreateProposalViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = rememberSnackbarHostState()
    UserMessageEffect(state.message, snackbar, viewModel::onMessageShown)
    LaunchedEffect(state.createdId) { state.createdId?.let(onCreated) }
    val scope = rememberCoroutineScope()
    if (state.needsPro) {
        UpgradeSheet(
            onDismiss = {
                viewModel.needsProHandled()
                viewModel.refreshPlan()
            },
            onUpgraded = { message -> scope.launch { snackbar.showSnackbar(message) } },
            reason = AI_UPGRADE_REASON,
        )
    }
    CreateProposalContent(
        state = state,
        onBack = onBack,
        onRangeChange = viewModel::onRangeChange,
        onTitleChange = viewModel::onTitleChange,
        onLocationChange = viewModel::onLocationChange,
        onNoteChange = viewModel::onNoteChange,
        onSubmit = viewModel::submit,
        onDraftWithAi = viewModel::draftWithAi,
    )
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) { AvSnackbarHost(snackbar, Modifier.navigationBarsPadding().padding(bottom = 90.dp)) }
}

@Composable
private fun CreateProposalContent(
    state: CreateProposalUiState,
    onBack: () -> Unit,
    onRangeChange: (TimeRangeDraft) -> Unit,
    onTitleChange: (String) -> Unit,
    onLocationChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDraftWithAi: () -> Unit = {},
) {
    val colors = AvailablissTheme.colors
    AvailablissBackground(OrbLayout.Proposal) {
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
            ScreenHeader(
                title = "Propose a time",
                subtitle = state.groupName.ifBlank { null },
                onBack = onBack,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
            )
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GlassPanel(Modifier.fillMaxWidth(), shape = Shapes.cardLarge, contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (state.clash == null) {
                        Text("Everyone synced is free", style = AvailablissTheme.type.captionStrong, color = colors.okInk)
                    } else {
                        Text("Not everyone is free", style = AvailablissTheme.type.captionStrong, color = colors.warnInk)
                    }
                    Text(
                        if (state.range.isValid) state.range.label else "Pick a start and end",
                        style = AvailablissTheme.type.headlineLarge,
                        color = colors.ink,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        "You'll be marked as accepted. Everyone else gets a notification to reply.",
                        style = AvailablissTheme.type.labelRegular.copy(fontWeight = FontWeight.Normal),
                        color = colors.muted,
                    )
                }
                GlassPanel(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionLabel("When")
                    DateRangeInput(state.range, onRangeChange)
                }
                state.clash?.let { ClashPanel(it, "You can still propose it; everyone will see the clash before they reply.") }
                GlassPanel(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (state.aiPlanner.available) DraftWithAiButton(state, onDraftWithAi)
                    AvTextField(state.title, onTitleChange, label = "What's the plan? (optional)", placeholder = "e.g. Lakes weekend", imeAction = ImeAction.Next)
                    AvTextField(state.location, onLocationChange, label = "Where (optional)", placeholder = "e.g. Keswick", imeAction = ImeAction.Next)
                    AvTextField(state.note, onNoteChange, label = "Note (optional)", placeholder = "Anything people should know", singleLine = false, height = 96.dp)
                }
            }
            PrimaryButton(
                text = if (state.clash != null) "Propose anyway" else "Propose this time",
                onClick = onSubmit,
                style = ButtonStyle.Accent,
                enabled = state.canSubmit,
                loading = state.saving,
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp).navigationBarsPadding(),
            )
        }
    }
}

/**
 * "Draft with AI": writes the title and note for the time picked. Like "Plan with AI", it shows
 * whenever the server has AI set up, marked Pro (and opening the upgrade) for those without it.
 */
@Composable
private fun DraftWithAiButton(state: CreateProposalUiState, onClick: () -> Unit) {
    val colors = AvailablissTheme.colors
    val enabled = !state.drafting && state.range.isValid
    Row(
        Modifier
            .heightIn(min = 44.dp)
            .alpha(if (enabled || state.drafting) 1f else 0.5f)
            .clip(Shapes.pill)
            .background(colors.accentSoft)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = "Write a title and note for this time", onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.drafting) {
            CircularProgressIndicator(Modifier.size(14.dp), color = colors.accentInk, strokeWidth = 2.dp)
        } else {
            Icon(AvIcons.Sparkle, null, tint = colors.accentInk, modifier = Modifier.size(15.dp))
        }
        Text(if (state.drafting) "Drafting\u2026" else "Draft with AI", style = AvailablissTheme.type.bodySmallStrong, color = colors.accentInk)
        if (!state.aiPlanner.included) StatusPill("Pro", PillTone.Indigo)
    }
}

/** Why the upgrade sheet opens from "Draft with AI" (as from "Plan with AI"). */
private const val AI_UPGRADE_REASON = "AI planning is part of Pro: it picks the best time, suggests things to do and drafts the plan."

@Preview
@Composable
private fun CreateProposalPreview() {
    AvailablissPreviewTheme {
        CreateProposalContent(
            CreateProposalUiState(groupName = "Lake District crew", aiPlanner = dev.joeherbert.availabliss.core.network.model.AiPlannerAccess(available = true)),
            {}, {}, {}, {}, {}, {},
        )
    }
}
