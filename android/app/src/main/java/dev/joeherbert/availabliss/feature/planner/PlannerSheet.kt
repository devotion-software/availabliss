package dev.joeherbert.availabliss.feature.planner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.joeherbert.availabliss.core.common.DateTimes
import dev.joeherbert.availabliss.core.network.NetworkModule
import dev.joeherbert.availabliss.core.network.model.PlannerResult
import dev.joeherbert.availabliss.core.network.model.Slot
import dev.joeherbert.availabliss.designsystem.component.AvBottomSheet
import dev.joeherbert.availabliss.designsystem.component.AvTextField
import dev.joeherbert.availabliss.designsystem.component.ButtonStyle
import dev.joeherbert.availabliss.designsystem.component.PrimaryButton
import dev.joeherbert.availabliss.designsystem.component.SecondaryButton
import dev.joeherbert.availabliss.designsystem.component.SectionLabel
import dev.joeherbert.availabliss.designsystem.component.TextAction
import dev.joeherbert.availabliss.designsystem.icon.AvIcons
import dev.joeherbert.availabliss.designsystem.theme.AvailablissPreviewTheme
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import dev.joeherbert.availabliss.designsystem.theme.Shapes

/** What "Propose this" hands to the create-proposal screen. */
data class PlannedProposal(val slot: Slot, val title: String, val note: String)

/**
 * "Plan with AI" (Pro, optional): notes, then Claude's pick of the times that already
 * work for everyone, alternatives, ideas and next steps. [onNeedsPro] fires on a 402.
 */
@Composable
fun PlannerSheet(
    onDismiss: () -> Unit,
    onPropose: (PlannedProposal) -> Unit,
    onNeedsPro: () -> Unit,
    viewModel: PlannerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DisposableEffect(Unit) { onDispose { viewModel.reset() } }
    LaunchedEffect(state.needsPro) {
        if (state.needsPro) {
            viewModel.needsProHandled()
            onDismiss()
            onNeedsPro()
        }
    }
    PlannerSheetContent(
        state = state,
        onDismiss = onDismiss,
        onNotesChange = viewModel::onNotesChange,
        onSuggest = viewModel::suggest,
        onStartOver = viewModel::startOver,
        onPropose = { slot ->
            val result = state.result ?: return@PlannerSheetContent
            onDismiss()
            onPropose(PlannedProposal(slot, result.proposalTitle, result.proposalNote))
        },
    )
}

@Composable
fun PlannerSheetContent(
    state: PlannerUiState,
    onDismiss: () -> Unit,
    onNotesChange: (String) -> Unit,
    onSuggest: () -> Unit,
    onStartOver: () -> Unit,
    onPropose: (Slot) -> Unit,
) {
    val colors = AvailablissTheme.colors
    val type = AvailablissTheme.type
    AvBottomSheet(title = "Plan with AI", onDismissRequest = onDismiss) {
        val result = state.result
        if (result == null) {
            Text("Picks the best of the times that work for everyone, with ideas and next steps.", style = type.body, color = colors.muted)
            AvTextField(
                value = state.notes,
                onValueChange = onNotesChange,
                label = "Anything to bear in mind? (optional)",
                placeholder = "e.g. We're around Manchester, two of us have kids, keep it cheap",
                singleLine = false,
                height = 104.dp,
                enabled = !state.thinking,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(AvIcons.Shield, null, tint = colors.muted, modifier = Modifier.padding(top = 2.dp).size(14.dp))
                Text(
                    "Sends this group's name, timing and free dates, plus your note, to Claude by Anthropic. Never anyone's calendar, busy time or names.",
                    style = type.caption.copy(fontWeight = FontWeight.Normal),
                    color = colors.muted,
                )
            }
            PrimaryButton(
                text = "Suggest a plan",
                onClick = onSuggest,
                style = ButtonStyle.Accent,
                icon = AvIcons.Sparkle,
                loading = state.thinking,
                height = 52.dp,
                shape = Shapes.control,
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.thinking) {
                Row(
                    Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(14.dp), color = colors.muted, strokeWidth = 2.dp)
                    Text("Weighing up the options. This can take a little while.", style = type.caption, color = colors.muted)
                }
            }
            state.error?.let { Text(it, style = type.label, color = colors.warnInk, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
        } else {
            PlannerResultContent(result, onPropose, onStartOver)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.PlannerResultContent(result: PlannerResult, onPropose: (Slot) -> Unit, onStartOver: () -> Unit) {
    val colors = AvailablissTheme.colors
    val type = AvailablissTheme.type
    Column(
        Modifier.fillMaxWidth().clip(Shapes.card).background(colors.accentSoft).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Best time", style = type.micro, color = colors.accentInk)
        Text(result.best.slot.displayLabel(), style = type.titleMedium, color = colors.ink, modifier = Modifier.semantics { heading() })
        Text(result.best.why, style = type.body, color = colors.ink)
        PrimaryButton(
            text = "Propose this",
            onClick = { onPropose(result.best.slot) },
            style = ButtonStyle.Accent,
            height = 46.dp,
            shape = Shapes.control,
            textStyle = type.bodySmallStrong,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
    if (result.alternatives.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel("Also works")
            result.alternatives.forEach { alt ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(alt.slot.displayLabel(), style = type.bodySmallStrong, color = colors.ink)
                        Text(alt.why, style = type.caption.copy(fontWeight = FontWeight.Normal), color = colors.muted)
                    }
                    SecondaryButton("Propose", { onPropose(alt.slot) }, height = 40.dp, textStyle = type.bodySmallStrong)
                }
            }
        }
    }
    if (result.ideas.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("Ideas")
            result.ideas.forEach { idea ->
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = colors.ink)) { append(idea.title) }
                        append(" ")
                        append(idea.description)
                    },
                    style = type.body,
                    color = colors.muted,
                )
            }
        }
    }
    if (result.checklist.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionLabel("Next steps")
            result.checklist.forEachIndexed { i, step ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${i + 1}.", style = type.bodySmallStrong, color = colors.accentInk, modifier = Modifier.width(20.dp))
                    Text(step, style = type.body, color = colors.ink)
                }
            }
        }
    }
    TextAction("Try again with different notes", onStartOver, modifier = Modifier.align(Alignment.CenterHorizontally))
}

/** The server's label ("Fri 27 – Sun 29 Nov"), or one made from the times. */
private fun Slot.displayLabel(): String = label.ifBlank { DateTimes.rangeLabel(start, end, allDay = false) }

@Preview(heightDp = 1400)
@Composable
private fun PlannerResultPreview() {
    val result = NetworkModule.provideJson().decodeFromString(PlannerResult.serializer(), PlannerFixtures.RESULT_JSON)
    AvailablissPreviewTheme {
        PlannerSheetContent(PlannerUiState(result = result), {}, {}, {}, {}, {})
    }
}

@Preview
@Composable
private fun PlannerAskPreview() {
    AvailablissPreviewTheme {
        PlannerSheetContent(PlannerUiState(notes = "Two of us have kids", thinking = true), {}, {}, {}, {}, {})
    }
}
