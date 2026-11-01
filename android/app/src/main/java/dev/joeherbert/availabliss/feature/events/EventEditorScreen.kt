package dev.joeherbert.availabliss.feature.events

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.joeherbert.availabliss.designsystem.component.AvSnackbarHost
import dev.joeherbert.availabliss.designsystem.component.AvTextField
import dev.joeherbert.availabliss.designsystem.component.AvailablissBackground
import dev.joeherbert.availabliss.designsystem.component.GlassPanel
import dev.joeherbert.availabliss.designsystem.component.OrbLayout
import dev.joeherbert.availabliss.designsystem.component.PrimaryButton
import dev.joeherbert.availabliss.designsystem.component.ScreenHeader
import dev.joeherbert.availabliss.designsystem.component.SectionLabel
import dev.joeherbert.availabliss.designsystem.component.UserMessageEffect
import dev.joeherbert.availabliss.designsystem.component.rememberSnackbarHostState
import dev.joeherbert.availabliss.designsystem.icon.AvIcons
import dev.joeherbert.availabliss.designsystem.theme.AvailablissPreviewTheme

@Composable
fun EventEditorScreen(onBack: () -> Unit, onSaved: () -> Unit, viewModel: EventEditorViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = rememberSnackbarHostState()
    UserMessageEffect(state.message, snackbar, viewModel::onMessageShown)
    LaunchedEffect(state.saved) { if (state.saved) onSaved() }
    EventEditorContent(
        state = state,
        onBack = onBack,
        onTitleChange = viewModel::onTitleChange,
        onLocationChange = viewModel::onLocationChange,
        onNoteChange = viewModel::onNoteChange,
        onRangeChange = viewModel::onRangeChange,
        onSave = viewModel::save,
    )
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) { AvSnackbarHost(snackbar, Modifier.navigationBarsPadding()) }
}

@Composable
private fun EventEditorContent(
    state: EventEditorUiState,
    onBack: () -> Unit,
    onTitleChange: (String) -> Unit,
    onLocationChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onRangeChange: (TimeRangeDraft) -> Unit,
    onSave: () -> Unit,
) {
    AvailablissBackground(OrbLayout.Proposal) {
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
            ScreenHeader(
                title = "Add an event",
                subtitle = state.groupName.ifBlank { null },
                onBack = onBack,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
            )
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GlassPanel(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AvTextField(state.title, onTitleChange, label = "What's happening?", placeholder = "e.g. Dinner at Hana's", error = state.titleError, imeAction = ImeAction.Next)
                }
                GlassPanel(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionLabel("When")
                    TimeRangeFields(state.range, onRangeChange)
                }
                GlassPanel(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AvTextField(state.location, onLocationChange, label = "Where (optional)", placeholder = "e.g. Keswick", imeAction = ImeAction.Next)
                    AvTextField(state.note, onNoteChange, label = "Note (optional)", placeholder = "Anything people should know", singleLine = false, height = 96.dp)
                }
            }
            PrimaryButton(
                text = "Add to group",
                onClick = onSave,
                icon = AvIcons.CalendarAdd,
                enabled = state.canSave || state.title.isBlank(),
                loading = state.saving,
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp).navigationBarsPadding(),
            )
        }
    }
}

@Preview
@Composable
private fun EventEditorPreview() {
    AvailablissPreviewTheme {
        EventEditorContent(EventEditorUiState(groupName = "Book club", title = "Dinner"), {}, {}, {}, {}, {}, {})
    }
}
