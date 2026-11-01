package dev.joeherbert.availabliss.designsystem.component.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.joeherbert.availabliss.designsystem.component.AvatarPerson
import dev.joeherbert.availabliss.designsystem.component.AvatarStack
import dev.joeherbert.availabliss.designsystem.icon.AvIcons
import dev.joeherbert.availabliss.designsystem.theme.AvailablissPreviewTheme
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme

/**
 * Receipt line under your latest message: seen-by avatars, "Seen by 3 of 5" and a
 * double tick (accent when seen, muted single tick when only delivered).
 */
@Composable
fun ReadReceipt(
    label: String,
    seen: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    seenBy: List<AvatarPerson> = emptyList(),
) {
    val colors = AvailablissTheme.colors
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Row(
            Modifier
                .defaultMinSize(minHeight = 24.dp)
                .clickable(role = Role.Button, onClickLabel = "Message info", onClick = onClick)
                .semantics(mergeDescendants = true) { contentDescription = "$label. Tap for message info" }
                .padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (seenBy.isNotEmpty()) {
                AvatarStack(seenBy, size = 16.dp, overlap = 4.dp, border = colors.bg, modifier = Modifier.padding(end = 4.dp))
            }
            Text(label, style = AvailablissTheme.type.micro.copy(fontWeight = FontWeight.Medium), color = colors.muted)
            Icon(
                if (seen) AvIcons.DoubleCheck else AvIcons.SingleCheck,
                contentDescription = null,
                tint = if (seen) colors.accentInk else colors.muted,
            )
        }
    }
}

@Preview
@Composable
private fun ReadReceiptPreview() {
    AvailablissPreviewTheme {
        Box(Modifier.background(AvailablissTheme.colors.bg).padding(16.dp)) {
            ReadReceipt("Seen by 3 of 5", seen = true, onClick = {}, seenBy = listOf(AvatarPerson("a", "Ana"), AvatarPerson("p", "Priya"), AvatarPerson("t", "Tom")))
        }
    }
}
