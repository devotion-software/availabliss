package dev.joeherbert.availabliss.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.joeherbert.availabliss.designsystem.icon.AvIcons
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import dev.joeherbert.availabliss.designsystem.theme.Shapes

@Composable
fun LoadingIndicator(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = AvailablissTheme.colors.accent, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
    }
}

/** Inline glass banner for a failed load or action, with optional retry. */
@Composable
fun ErrorBanner(message: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    val colors = AvailablissTheme.colors
    Row(
        modifier.fillMaxWidth().glass(Shapes.row).padding(horizontal = 14.dp, vertical = 10.dp).semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(AvIcons.Info, null, tint = colors.warnInk, modifier = Modifier.size(18.dp))
        Text(message, style = AvailablissTheme.type.labelRegular, color = colors.ink, modifier = Modifier.weight(1f))
        onRetry?.let { TextAction("Retry", it) }
    }
}

/** Friendly empty state inside a glass panel. */
@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = AvailablissTheme.colors
    GlassPanel(modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            icon?.let { Icon(it, null, tint = colors.accentInk, modifier = Modifier.size(28.dp)) }
            Text(title, style = AvailablissTheme.type.cardTitle, color = colors.ink, textAlign = TextAlign.Center)
            Text(body, style = AvailablissTheme.type.body, color = colors.muted, textAlign = TextAlign.Center)
            if (actionLabel != null && onAction != null) {
                PrimaryButton(actionLabel, onAction, height = 44.dp, shape = Shapes.controlSmall, textStyle = AvailablissTheme.type.bodySmallStrong, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}
