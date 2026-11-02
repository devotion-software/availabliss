package dev.joeherbert.availabliss.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.joeherbert.availabliss.designsystem.icon.AvIcons
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme

/**
 * Screen header: optional glass back button, title (24 / 700) with optional
 * subtitle (12 muted), and trailing actions.
 */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    backDescription: String = "Back",
    titleStyle: TextStyle = AvailablissTheme.type.headlineMedium,
    subtitleMaxLines: Int = 1,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = AvailablissTheme.colors
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        onBack?.let { GlassIconButton(AvIcons.Back, backDescription, it) }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = titleStyle,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            subtitle?.let { Text(it, style = AvailablissTheme.type.caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal), color = colors.muted, maxLines = subtitleMaxLines, overflow = TextOverflow.Ellipsis) }
        }
        actions()
    }
}

/** Muted section label inside glass sections ("How long?", "Mode"). */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = AvailablissTheme.type.label, color = AvailablissTheme.colors.muted, modifier = modifier.semantics { heading() })
}

/** Display-face section title with an optional trailing action ("Your groups" + New group). */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text, style = AvailablissTheme.type.titleMedium, color = AvailablissTheme.colors.ink, modifier = Modifier.semantics { heading() })
        trailing()
    }
}
