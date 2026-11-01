package dev.joeherbert.availabliss.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.joeherbert.availabliss.designsystem.component.Avatar
import dev.joeherbert.availabliss.designsystem.component.AvBottomSheet
import dev.joeherbert.availabliss.designsystem.component.AvTextField
import dev.joeherbert.availabliss.designsystem.component.ButtonStyle
import dev.joeherbert.availabliss.designsystem.component.DashedChip
import dev.joeherbert.availabliss.designsystem.component.LoadingIndicator
import dev.joeherbert.availabliss.designsystem.component.PlainIconButton
import dev.joeherbert.availabliss.designsystem.component.PrimaryButton
import dev.joeherbert.availabliss.designsystem.component.RowDivider
import dev.joeherbert.availabliss.designsystem.component.SectionLabel
import dev.joeherbert.availabliss.designsystem.component.SurfacePanel
import dev.joeherbert.availabliss.designsystem.component.ToggleRow
import dev.joeherbert.availabliss.designsystem.component.chat.OwnBubbleShape
import dev.joeherbert.availabliss.designsystem.icon.AvIcons
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme

/** "Message info": who has seen, received, or not yet received your message. */
@Composable
fun MessageInfoSheet(info: MessageInfoUi, onDismiss: () -> Unit) {
    val colors = AvailablissTheme.colors
    AvBottomSheet(title = "Message info", onDismissRequest = onDismiss) {
        if (info.text.isNotBlank()) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Text(
                    info.text,
                    style = AvailablissTheme.type.body,
                    color = colors.onSolid,
                    modifier = Modifier
                        .widthIn(max = 270.dp)
                        .clip(OwnBubbleShape)
                        .background(colors.solid)
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                )
            }
        }
        Text(
            info.sentLabel,
            style = AvailablissTheme.type.micro.copy(fontWeight = FontWeight.Normal),
            color = colors.muted,
            modifier = Modifier.align(Alignment.End).offset(y = (-8).dp),
        )
        when {
            info.loading -> LoadingIndicator()
            info.error != null -> Text(info.error, style = AvailablissTheme.type.labelRegular, color = colors.warnInk)
            else -> {
                ReceiptSection("Seen by", info.seen, AvIcons.DoubleCheck, colors.accentInk)
                ReceiptSection("Delivered to, not seen yet", info.delivered, AvIcons.DoubleCheck, colors.muted)
                ReceiptSection("Not delivered yet", info.pending, AvIcons.SingleCheck, colors.muted)
            }
        }
    }
}

@Composable
private fun ReceiptSection(title: String, people: List<InfoPerson>, icon: ImageVector, tint: Color) {
    if (people.isEmpty()) return
    val colors = AvailablissTheme.colors
    SurfacePanel(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, null, tint = tint)
            Text(title, style = AvailablissTheme.type.captionStrong, color = colors.muted, modifier = Modifier.weight(1f))
            Text(people.size.toString(), style = AvailablissTheme.type.captionStrong, color = colors.muted)
        }
        people.forEach { p ->
            RowDivider()
            Row(
                Modifier.fillMaxWidth().defaultMinSize(minHeight = 50.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Avatar(p.person, size = 32.dp)
                Text(p.name, style = AvailablissTheme.type.bodyMedium, color = colors.ink, modifier = Modifier.weight(1f))
                Text(p.time, style = AvailablissTheme.type.caption.copy(fontWeight = FontWeight.Normal), color = colors.muted)
            }
        }
    }
}

/** Callbacks for the poll creation sheet. */
data class PollSheetActions(
    val onQuestion: (String) -> Unit,
    val onOption: (Int, String) -> Unit,
    val onAddOption: () -> Unit,
    val onRemoveOption: (Int) -> Unit,
    val onMulti: (Boolean) -> Unit,
    val onSubmit: () -> Unit,
    val onDismiss: () -> Unit,
)

/** "New poll": question, 2–10 options, allow multiple answers. */
@Composable
fun PollComposerSheet(draft: PollDraft, actions: PollSheetActions) {
    AvBottomSheet(title = "New poll", onDismissRequest = actions.onDismiss) {
        AvTextField(
            value = draft.question,
            onValueChange = actions.onQuestion,
            label = "Question",
            placeholder = "e.g. Where should we stay?",
            imeAction = ImeAction.Next,
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("Options")
            draft.options.forEachIndexed { index, option ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AvTextField(
                        value = option,
                        onValueChange = { actions.onOption(index, it) },
                        placeholder = "Option ${index + 1}",
                        height = 48.dp,
                        imeAction = ImeAction.Next,
                        modifier = Modifier.weight(1f),
                    )
                    if (draft.options.size > 2) {
                        PlainIconButton(AvIcons.Close, "Remove option ${index + 1}", { actions.onRemoveOption(index) }, size = 40.dp, iconSize = 14.dp)
                    }
                }
            }
            if (draft.canAddOption) DashedChip("+ Add option", actions.onAddOption)
        }
        ToggleRow(title = "Allow more than one answer", checked = draft.multi, onCheckedChange = actions.onMulti)
        PrimaryButton(
            text = "Post poll",
            onClick = actions.onSubmit,
            style = ButtonStyle.Accent,
            height = 52.dp,
            enabled = draft.canSubmit,
            loading = draft.sending,
            icon = AvIcons.Poll,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
