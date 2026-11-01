package dev.joeherbert.availabliss.feature.newgroup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.hilt.navigation.compose.hiltViewModel
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

/** Create a group: name + tile colour. Timing is set on the next screen. */
@Composable
fun NewGroupScreen(
    onBack: () -> Unit,
    onCreated: (groupId: String) -> Unit,
    viewModel: NewGroupViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.createdGroupId) {
        val id = state.createdGroupId ?: return@LaunchedEffect
        viewModel.createdHandled()
        onCreated(id)
    }
    NewGroupContent(state, onBack, viewModel::setName, viewModel::setColor, viewModel::create)
}

@Composable
fun NewGroupContent(
    state: NewGroupUiState,
    onBack: () -> Unit,
    onNameChange: (String) -> Unit,
    onColorChange: (TileColor) -> Unit,
    onCreate: () -> Unit,
) {
    val colors = AvailablissTheme.colors
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
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
            ScreenHeader(title = "New group", subtitle = "You'll be its owner", onBack = onBack)
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
                        onImeAction = onCreate,
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
                Text(
                    "Next you'll choose what you're looking for: an evening, a weekend, a week away…",
                    style = AvailablissTheme.type.labelRegular,
                    color = colors.muted,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            Spacer(Modifier)
            PrimaryButton(
                text = "Create group",
                onClick = onCreate,
                enabled = state.canCreate,
                loading = state.creating,
                modifier = Modifier.fillMaxWidth(),
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
