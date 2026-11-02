package dev.joeherbert.availabliss.feature.timing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.joeherbert.availabliss.core.data.TemplatesRepository
import dev.joeherbert.availabliss.core.network.ApiError
import dev.joeherbert.availabliss.core.network.ApiResult
import dev.joeherbert.availabliss.core.network.model.Requirement
import dev.joeherbert.availabliss.core.network.model.RequirementTemplate
import dev.joeherbert.availabliss.designsystem.component.AvTextField
import dev.joeherbert.availabliss.designsystem.component.SecondaryButton
import dev.joeherbert.availabliss.designsystem.component.TextAction
import dev.joeherbert.availabliss.designsystem.icon.AvIcons
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import dev.joeherbert.availabliss.designsystem.theme.Shapes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** "Save as template" progress. [savedCount] goes up after each save, closing the name field. */
data class TemplateSaveState(val saving: Boolean = false, val savedCount: Int = 0)

/** Saves the current timing as a template; shared by the Timing tab and New group. */
class TemplateSaver(
    private val scope: CoroutineScope,
    private val templates: TemplatesRepository,
    /** Toasts: the saved confirmation or the error. */
    private val onMessage: (String) -> Unit,
    private val onSaved: (RequirementTemplate) -> Unit = {},
) {
    private val _state = MutableStateFlow(TemplateSaveState())
    val state: StateFlow<TemplateSaveState> = _state.asStateFlow()

    fun save(name: String, requirement: Requirement) {
        val trimmed = name.trim().take(Templates.MAX_NAME)
        if (trimmed.isEmpty() || _state.value.saving) return
        _state.update { it.copy(saving = true) }
        scope.launch {
            when (val result = templates.create(trimmed, requirement)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(saving = false, savedCount = it.savedCount + 1) }
                    onSaved(result.value)
                    onMessage(Templates.savedMessage(result.value.name))
                }
                is ApiResult.Failure -> {
                    _state.update { it.copy(saving = false) }
                    // e.g. the 50-template limit comes back as a validation message.
                    onMessage((result.error as? ApiError.Validation)?.issues?.firstOrNull()?.message ?: result.error.message)
                }
            }
        }
    }
}

/**
 * "Save this timing as a template": a quiet action that opens an inline name field.
 * [suggestion] prefills the name (e.g. the group's).
 */
@Composable
fun SaveTemplateRow(
    state: TemplateSaveState,
    onSave: (String) -> Unit,
    modifier: Modifier = Modifier,
    suggestion: String = "",
    /** Height of whatever floats over the bottom of the scroller (a save bar), kept clear when the form opens. */
    bottomClearance: Dp = 0.dp,
) {
    val colors = AvailablissTheme.colors
    var naming by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    var seenSaves by rememberSaveable { mutableStateOf(state.savedCount) }
    LaunchedEffect(state.savedCount) {
        if (state.savedCount != seenSaves) {
            seenSaves = state.savedCount
            naming = false
        }
    }
    if (!naming) {
        Row(
            modifier
                .clip(Shapes.controlSmall)
                .clickable(role = Role.Button) {
                    name = suggestion
                    naming = true
                }
                .padding(horizontal = 6.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(AvIcons.Plus, null, tint = colors.accentInk, modifier = Modifier.size(16.dp))
            Text("Save this timing as a template", style = AvailablissTheme.type.bodySmallStrong, color = colors.accentInk)
        }
        return
    }
    val bringIntoView = remember { BringIntoViewRequester() }
    val density = LocalDensity.current
    var size by remember { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    // Scroll the whole form (with its buttons) clear of the bar, again as the keyboard settles.
    val imeBottom = WindowInsets.ime.getBottom(density)
    LaunchedEffect(imeBottom) {
        delay(150)
        val extra = with(density) { bottomClearance.toPx() }
        bringIntoView.bringIntoView(Rect(0f, 0f, size.width.toFloat(), size.height + extra))
    }
    Column(
        modifier
            .onSizeChanged { size = it }
            .bringIntoViewRequester(bringIntoView)
            .fillMaxWidth()
            .clip(Shapes.card)
            .background(colors.surface)
            .border(1.dp, colors.glassBorder, Shapes.card)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AvTextField(
            value = name,
            onValueChange = { name = it.take(Templates.MAX_NAME) },
            label = "Template name",
            placeholder = "e.g. School holiday weekends",
            onImeAction = { onSave(name) },
            focusRequester = focus,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
            TextAction("Cancel", onClick = { naming = false }, color = colors.muted)
            SecondaryButton(
                text = if (state.saving) "Saving…" else "Save template",
                onClick = { onSave(name) },
                height = 40.dp,
                textStyle = AvailablissTheme.type.bodySmallStrong,
                enabled = name.isNotBlank() && !state.saving,
            )
        }
    }
}
