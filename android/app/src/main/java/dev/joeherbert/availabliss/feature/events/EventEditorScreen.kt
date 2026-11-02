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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.joeherbert.availabliss.core.common.DateTimes
import dev.joeherbert.availabliss.designsystem.component.AvSnackbarHost
import dev.joeherbert.availabliss.designsystem.component.AvTextField
import dev.joeherbert.availabliss.designsystem.component.AvailablissBackground
import dev.joeherbert.availabliss.designsystem.component.GlassPanel
import dev.joeherbert.availabliss.designsystem.component.LoadingIndicator
import dev.joeherbert.availabliss.designsystem.component.OrbLayout
import dev.joeherbert.availabliss.designsystem.component.PrimaryButton
import dev.joeherbert.availabliss.designsystem.component.ScreenHeader
import dev.joeherbert.availabliss.designsystem.component.SectionLabel
import dev.joeherbert.availabliss.designsystem.component.TextAction
import dev.joeherbert.availabliss.designsystem.component.UserMessageEffect
import dev.joeherbert.availabliss.designsystem.component.rememberSnackbarHostState
import dev.joeherbert.availabliss.designsystem.icon.AvIcons
import dev.joeherbert.availabliss.designsystem.theme.AvailablissPreviewTheme
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import dev.joeherbert.availabliss.feature.common.AddToCalendarButton
import dev.joeherbert.availabliss.feature.common.AddToCalendarEffect

/** Adds a group event, or views / edits / deletes an existing one. */
@Composable
fun EventEditorScreen(onBack: () -> Unit, onSaved: () -> Unit, viewModel: EventEditorViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = rememberSnackbarHostState()
    UserMessageEffect(state.message, snackbar, viewModel::onMessageShown)
    AddToCalendarEffect(state.calendar, viewModel::onCalendarIntentHandled)
    LaunchedEffect(state.saved, state.deleted) { if (state.saved || state.deleted) onSaved() }
    EventEditorContent(
        state = state,
        actions = EventEditorActions(
            onBack = onBack,
            onTitleChange = viewModel::onTitleChange,
            onLocationChange = viewModel::onLocationChange,
            onNoteChange = viewModel::onNoteChange,
            onRangeChange = viewModel::onRangeChange,
            onSave = viewModel::save,
            onDelete = viewModel::delete,
            onAddToCalendar = viewModel::onAddToCalendar,
        ),
    )
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) { AvSnackbarHost(snackbar, Modifier.navigationBarsPadding()) }
}

data class EventEditorActions(
    val onBack: () -> Unit = {},
    val onTitleChange: (String) -> Unit = {},
    val onLocationChange: (String) -> Unit = {},
    val onNoteChange: (String) -> Unit = {},
    val onRangeChange: (TimeRangeDraft) -> Unit = {},
    val onSave: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onAddToCalendar: () -> Unit = {},
)

@Composable
private fun EventEditorContent(state: EventEditorUiState, actions: EventEditorActions) {
    var confirmDelete by remember { mutableStateOf(false) }
    val title = when {
        !state.isExisting -> "Add an event"
        state.canEdit -> "Edit event"
        else -> "Event"
    }
    AvailablissBackground(OrbLayout.Proposal) {
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
            ScreenHeader(
                title = title,
                subtitle = state.groupName.ifBlank { null },
                onBack = actions.onBack,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
            )
            if (state.loading) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { LoadingIndicator() }
                return@Column
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.canEdit) EditableFields(state, actions) else ReadOnlyDetails(state)
                if (state.isExisting) {
                    AddToCalendarButton(added = state.addedToCalendar, onClick = actions.onAddToCalendar, modifier = Modifier.fillMaxWidth())
                }
                if (state.isExisting && state.canEdit) {
                    TextAction(
                        "Delete event",
                        onClick = { confirmDelete = true },
                        color = AvailablissTheme.colors.warnInk,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 4.dp),
                    )
                }
            }
            if (state.canEdit) {
                PrimaryButton(
                    text = if (state.isExisting) "Save changes" else "Add to group",
                    onClick = actions.onSave,
                    icon = if (state.isExisting) AvIcons.Check else AvIcons.CalendarAdd,
                    enabled = state.canSave || state.title.isBlank(),
                    loading = state.saving,
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp).navigationBarsPadding(),
                )
            }
        }
    }
    if (confirmDelete) {
        DeleteEventDialog(state.title, onConfirm = { confirmDelete = false; actions.onDelete() }, onDismiss = { confirmDelete = false })
    }
}

@Composable
private fun EditableFields(state: EventEditorUiState, actions: EventEditorActions) {
    GlassPanel(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AvTextField(state.title, actions.onTitleChange, label = "What's happening?", placeholder = "e.g. Dinner at Hana's", error = state.titleError, imeAction = ImeAction.Next)
    }
    GlassPanel(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel("When")
        TimeRangeFields(state.range, actions.onRangeChange)
    }
    GlassPanel(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AvTextField(state.location, actions.onLocationChange, label = "Where (optional)", placeholder = "e.g. Keswick", imeAction = ImeAction.Next)
        AvTextField(state.note, actions.onNoteChange, label = "Note (optional)", placeholder = "Anything people should know", singleLine = false, height = 96.dp)
    }
}

/** For members who didn't create the event: the details, without editing controls. */
@Composable
private fun ReadOnlyDetails(state: EventEditorUiState) {
    val colors = AvailablissTheme.colors
    val muted = AvailablissTheme.type.caption.copy(fontWeight = FontWeight.Normal)
    GlassPanel(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(state.title, style = AvailablissTheme.type.headlineSmall, color = colors.ink)
        Text(
            DateTimes.rangeLabel(state.range.startIso(), state.range.endIso(), state.range.allDay),
            style = AvailablissTheme.type.bodyMedium,
            color = colors.ink,
        )
        if (state.location.isNotBlank()) Text(state.location, style = AvailablissTheme.type.body, color = colors.muted)
        if (state.note.isNotBlank()) Text(state.note, style = AvailablissTheme.type.body, color = colors.muted)
    }
    Text("Only the person who added it or a group admin can change this event.", style = muted, color = colors.muted)
}

@Preview
@Composable
private fun EventEditorPreview() {
    AvailablissPreviewTheme {
        EventEditorContent(EventEditorUiState(groupName = "Book club", title = "Dinner"), EventEditorActions())
    }
}

@Preview
@Composable
private fun EventReadOnlyPreview() {
    AvailablissPreviewTheme {
        EventEditorContent(
            EventEditorUiState(groupName = "Book club", eventId = "e1", canEdit = false, title = "Dinner at Hana's", location = "Hana's"),
            EventEditorActions(),
        )
    }
}
