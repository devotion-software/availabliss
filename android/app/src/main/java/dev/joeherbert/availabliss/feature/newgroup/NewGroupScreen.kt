package dev.joeherbert.availabliss.feature.newgroup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import dev.joeherbert.availabliss.designsystem.component.AvSnackbarHost
import dev.joeherbert.availabliss.designsystem.component.ButtonStyle
import dev.joeherbert.availabliss.designsystem.component.Chip
import dev.joeherbert.availabliss.designsystem.component.SecondaryButton
import dev.joeherbert.availabliss.designsystem.component.UserMessageEffect
import dev.joeherbert.availabliss.designsystem.component.glass
import dev.joeherbert.availabliss.designsystem.component.rememberSnackbarHostState
import dev.joeherbert.availabliss.designsystem.icon.AvIcons
import dev.joeherbert.availabliss.designsystem.theme.Shapes
import dev.joeherbert.availabliss.core.network.model.RequirementTemplate
import dev.joeherbert.availabliss.feature.timing.FitSummary
import dev.joeherbert.availabliss.feature.timing.SaveTemplateRow
import dev.joeherbert.availabliss.feature.timing.TimingActions
import dev.joeherbert.availabliss.feature.timing.TimingEditorSections
import dev.joeherbert.availabliss.feature.timing.TimingEditorUiState
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.heading
import dev.joeherbert.availabliss.feature.plan.UpgradeSheet
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.joeherbert.availabliss.core.network.model.TileColor
import dev.joeherbert.availabliss.designsystem.component.AvTextField
import dev.joeherbert.availabliss.designsystem.component.AvailablissBackground
import dev.joeherbert.availabliss.designsystem.component.GlassPanel
import dev.joeherbert.availabliss.designsystem.component.GroupTile
import dev.joeherbert.availabliss.designsystem.component.OrbLayout
import dev.joeherbert.availabliss.designsystem.component.PrimaryButton
import dev.joeherbert.availabliss.designsystem.component.ScreenHeader
import dev.joeherbert.availabliss.designsystem.component.SectionLabel
import dev.joeherbert.availabliss.designsystem.component.availability.outlineOutside
import dev.joeherbert.availabliss.designsystem.theme.AvailablissPreviewTheme
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import dev.joeherbert.availabliss.designsystem.theme.Spacing

/** Create a group in two steps: name, colour and roughly what you're planning; then the full timing. */
@Composable
fun NewGroupScreen(
    onBack: () -> Unit,
    onCreated: (groupId: String) -> Unit,
    viewModel: NewGroupViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = rememberSnackbarHostState()
    UserMessageEffect(state.message, snackbar, viewModel::messageShown)
    LaunchedEffect(state.createdGroupId) {
        val id = state.createdGroupId ?: return@LaunchedEffect
        viewModel.createdHandled()
        onCreated(id)
    }
    var upgrading by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = state.step == NewGroupStep.Timing && !state.atLimit, onBack = viewModel::back)
    when {
        state.atLimit -> GroupLimitContent(state.groupLimit, onBack, onSeePro = { upgrading = true })
        state.step == NewGroupStep.Details -> NewGroupContent(state, onBack, viewModel::setName, viewModel::setColor, viewModel::next, viewModel::selectTemplate)
        else -> {
            val editor = state.editor
            if (editor != null) {
                NewGroupTimingContent(
                    state,
                    editor,
                    viewModel.editor.actions(onBack = viewModel::back).copy(onSaveTemplate = viewModel::saveTemplate),
                    viewModel::create,
                )
            }
        }
    }
    if (upgrading) {
        UpgradeSheet(
            onDismiss = { upgrading = false },
            onUpgraded = viewModel::upgraded,
            reason = "You've used your ${state.groupLimit} free groups. Pro gives you as many as you like.",
        )
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AvSnackbarHost(snackbar, Modifier.navigationBarsPadding().padding(bottom = if (state.step == NewGroupStep.Timing) 150.dp else 90.dp))
    }
}

/** Shown instead of the form when a free account already owns its groups. */
@Composable
fun GroupLimitContent(limit: Int, onBack: () -> Unit, onSeePro: () -> Unit) {
    val colors = AvailablissTheme.colors
    AvailablissBackground(OrbLayout.Main) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(start = Spacing.screen, end = Spacing.screen, top = Spacing.screen, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ScreenHeader(title = "New group", onBack = onBack)
            GlassPanel(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)).background(colors.accentSoft), contentAlignment = Alignment.Center) {
                    Icon(AvIcons.Groups, null, tint = colors.accentInk, modifier = Modifier.size(24.dp))
                }
                Text("You've made $limit groups", style = AvailablissTheme.type.titleMedium, color = colors.ink, modifier = Modifier.semantics { heading() })
                Text(
                    "Free accounts can create $limit. Upgrade to Pro for unlimited groups, or delete one you no longer need. " +
                        "You can still join groups other people make.",
                    style = AvailablissTheme.type.body,
                    color = colors.muted,
                )
                PrimaryButton(
                    text = "See Pro",
                    onClick = onSeePro,
                    style = ButtonStyle.Accent,
                    icon = AvIcons.Sparkle,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
        }
    }
}

/** Step 1: name, colour and (if you have any) a saved template to start from. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewGroupContent(
    state: NewGroupUiState,
    onBack: () -> Unit,
    onNameChange: (String) -> Unit,
    onColorChange: (TileColor) -> Unit,
    onNext: () -> Unit,
    onTemplate: (RequirementTemplate) -> Unit = {},
) {
    val colors = AvailablissTheme.colors
    val focus = remember { FocusRequester() }
    // Only for a fresh form: coming back from step 2 shouldn't pop the keyboard up.
    LaunchedEffect(Unit) { if (state.name.isEmpty()) runCatching { focus.requestFocus() } }
    AvailablissBackground(OrbLayout.Main) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(start = Spacing.screen, end = Spacing.screen, top = Spacing.screen, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ScreenHeader(title = "New group", subtitle = "Name it and pick a colour. Timing comes next.", onBack = onBack)
            StepDots(total = 2, step = 1)
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.l),
            ) {
                GlassPanel(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        GroupTile(state.monogramPreview, colors.tile(state.color), size = 56.dp, radius = 18.dp)
                        Column(Modifier.weight(1f)) {
                            Text(
                                state.name.ifBlank { "Your group" },
                                style = AvailablissTheme.type.cardTitle,
                                color = if (state.name.isBlank()) colors.muted else colors.ink,
                                maxLines = 1,
                            )
                            Text("Invite people once it's created", style = AvailablissTheme.type.caption, color = colors.muted)
                        }
                    }
                    AvTextField(
                        value = state.name,
                        onValueChange = onNameChange,
                        label = "Name",
                        placeholder = "e.g. Lake District crew",
                        error = state.error,
                        imeAction = ImeAction.Done,
                        onImeAction = onNext,
                        focusRequester = focus,
                    )
                }
                GlassPanel(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionLabel("Colour")
                    Row(
                        Modifier.fillMaxWidth().selectableGroup(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        TileColor.entries.forEachIndexed { index, tile ->
                            val selected = tile == state.color
                            Box(
                                Modifier
                                    .then(if (selected) Modifier.outlineOutside(RoundedCornerShape(16.dp), colors.ink, width = 2.5.dp, offset = 3.dp) else Modifier)
                                    .selectable(selected = selected, role = Role.RadioButton, onClick = { onColorChange(tile) })
                                    .semantics { contentDescription = "Colour ${index + 1}" },
                            ) {
                                GroupTile(state.monogramPreview, colors.tile(tile), size = 48.dp, radius = 16.dp)
                            }
                        }
                    }
                }
                if (state.templates.isNotEmpty()) {
                    GlassPanel(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionLabel("Start from a template")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.templates.forEach { t ->
                                Chip(t.name, selected = state.templateId == t.id, onClick = { onTemplate(t) })
                            }
                        }
                    }
                }
            }
            PrimaryButton(
                text = "Next: timing",
                onClick = onNext,
                style = ButtonStyle.Accent,
                icon = AvIcons.ArrowRight,
                enabled = state.canContinue,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Step 2: the full timing editor, with the live fit count, Back and Create group pinned at the bottom. */
@Composable
fun NewGroupTimingContent(state: NewGroupUiState, editor: TimingEditorUiState, actions: TimingActions, onCreate: () -> Unit) {
    AvailablissBackground(OrbLayout.Timing) {
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
            ScreenHeader(
                title = "When are you looking for?",
                subtitle = "Fine-tune the timing for ${state.name.trim()}. You can change it later.",
                onBack = actions.onBack,
                titleStyle = AvailablissTheme.type.headlineSmall,
                subtitleMaxLines = 2,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
            )
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 180.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(Spacing.l),
            ) {
                StepDots(total = 2, step = 2)
                TimingEditorSections(editor, canEdit = true, actions = actions)
                SaveTemplateRow(state.templateSave, actions.onSaveTemplate, bottomClearance = 170.dp)
            }
        }
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 12.dp, end = 12.dp, bottom = 16.dp)
                .fillMaxWidth()
                // Opaque glass: the editor scrolls underneath and would show through.
                .glass(RoundedCornerShape(26.dp), AvailablissTheme.colors, fill = AvailablissTheme.colors.glass.compositeOver(AvailablissTheme.colors.bg))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FitSummary(editor, Modifier.padding(horizontal = 4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("Back", onClick = actions.onBack, icon = AvIcons.Back, height = 48.dp, modifier = Modifier.weight(1f))
                PrimaryButton(
                    text = "Create group",
                    onClick = onCreate,
                    style = ButtonStyle.Accent,
                    height = 48.dp,
                    shape = Shapes.control,
                    textStyle = AvailablissTheme.type.bodyMedium,
                    enabled = state.canCreate,
                    loading = state.creating,
                    modifier = Modifier.weight(1.4f),
                )
            }
        }
    }
}

/** "Step n of total" dots. */
@Composable
private fun StepDots(total: Int, step: Int) {
    val colors = AvailablissTheme.colors
    Row(
        Modifier.semantics(mergeDescendants = true) { contentDescription = "Step $step of $total" },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        (1..total).forEach { i ->
            Box(
                Modifier
                    .height(6.dp)
                    .width(if (i == step) 22.dp else 6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (i <= step) colors.accent else colors.line2),
            )
        }
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun NewGroupPreview() {
    AvailablissPreviewTheme {
        NewGroupContent(NewGroupUiState(name = "Lake District crew", color = TileColor.T2), {}, {}, {}, {})
    }
}
