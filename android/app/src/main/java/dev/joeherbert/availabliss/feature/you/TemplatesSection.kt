package dev.joeherbert.availabliss.feature.you

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.joeherbert.availabliss.core.network.model.RequirementTemplate
import dev.joeherbert.availabliss.designsystem.component.AvTextField
import dev.joeherbert.availabliss.designsystem.component.GlassPanel
import dev.joeherbert.availabliss.designsystem.component.PlainIconButton
import dev.joeherbert.availabliss.designsystem.component.RowDivider
import dev.joeherbert.availabliss.designsystem.component.SectionLabel
import dev.joeherbert.availabliss.designsystem.component.TextAction
import dev.joeherbert.availabliss.designsystem.icon.AvIcons
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import dev.joeherbert.availabliss.feature.timing.RequirementPresets
import dev.joeherbert.availabliss.feature.timing.TimingLabels

/** One saved timing on the You screen: "School weekends" / "A weekend · Fri from 18:00 → Sun · Nov – Feb". */
data class TemplateRowUi(val id: String, val name: String, val summary: String) {
    companion object {
        fun from(template: RequirementTemplate) = TemplateRowUi(
            id = template.id,
            name = template.name,
            summary = TimingLabels.summary(RequirementPresets.fromRequirement(template.requirement)),
        )
    }
}

/** Callbacks for [TemplatesSection]. */
data class TemplateActions(
    val onRename: (String) -> Unit = {},
    val onNameChange: (String) -> Unit = {},
    val onSaveName: () -> Unit = {},
    val onCancelRename: () -> Unit = {},
    val onDelete: (String) -> Unit = {},
)

/** "Timing templates": rename or delete them. New ones are saved from a group's timing. */
@Composable
fun TemplatesSection(state: YouUiState, actions: TemplateActions) {
    val colors = AvailablissTheme.colors
    val templates = state.templates ?: return
    GlassPanel(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 6.dp, top = 14.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        SectionLabel("Timing templates")
        if (templates.isEmpty()) {
            Text(
                "Save a group's timing as a template (from New group or a group's Timing) to reuse it for new groups.",
                style = AvailablissTheme.type.caption.copy(fontWeight = FontWeight.Normal),
                color = colors.muted,
                modifier = Modifier.padding(top = 6.dp, end = 10.dp, bottom = 10.dp),
            )
        }
        templates.forEachIndexed { index, t ->
            if (index > 0) RowDivider()
            if (state.renamingTemplateId == t.id) {
                val focus = remember { FocusRequester() }
                LaunchedEffect(t.id) { runCatching { focus.requestFocus() } }
                Column(Modifier.padding(top = 8.dp, end = 10.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    AvTextField(
                        state.templateNameDraft,
                        actions.onNameChange,
                        label = "Name",
                        onImeAction = actions.onSaveName,
                        height = 46.dp,
                        focusRequester = focus,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.End)) {
                        TextAction("Cancel", actions.onCancelRename, color = colors.muted)
                        TextAction("Save", actions.onSaveName, enabled = state.templateNameDraft.isNotBlank())
                    }
                }
            } else {
                Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(t.name, style = AvailablissTheme.type.bodySmallStrong, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            t.summary,
                            style = AvailablissTheme.type.caption.copy(fontWeight = FontWeight.Normal),
                            color = colors.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    PlainIconButton(AvIcons.Pencil, "Rename ${t.name}", { actions.onRename(t.id) }, size = 40.dp, iconSize = 16.dp, tint = colors.muted)
                    PlainIconButton(AvIcons.Trash, "Delete ${t.name}", { actions.onDelete(t.id) }, size = 40.dp, iconSize = 16.dp, tint = colors.muted)
                }
            }
        }
    }
}
