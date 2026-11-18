package dev.joeherbert.availabliss.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import dev.joeherbert.availabliss.designsystem.theme.Shapes

/** One button in a [ConfirmSheet]. */
data class ConfirmOption(
    val text: String,
    val onClick: () -> Unit,
    val icon: ImageVector? = null,
    val kind: Kind = Kind.Primary,
    val loading: Boolean = false,
) {
    enum class Kind { Primary, Secondary, Destructive }
}

/**
 * Asks to confirm something, or to choose between a few options, in a bottom sheet: title,
 * a short explanation, the options as full-width buttons (destructive ones in `warn-bg` /
 * `warn-ink`), then Cancel. Closing it (Cancel, ✕, back or a swipe down) calls [onDismiss].
 */
@Composable
fun ConfirmSheet(
    title: String,
    message: String?,
    options: List<ConfirmOption>,
    onDismiss: () -> Unit,
    cancelText: String = "Cancel",
) {
    val colors = AvailablissTheme.colors
    val working = options.any { it.loading }
    AvBottomSheet(title = title, onDismissRequest = onDismiss) {
        if (!message.isNullOrBlank()) {
            Text(message, style = AvailablissTheme.type.body, color = colors.muted)
        }
        options.forEach { option ->
            when (option.kind) {
                ConfirmOption.Kind.Primary -> PrimaryButton(
                    text = option.text,
                    onClick = option.onClick,
                    icon = option.icon,
                    height = 52.dp,
                    shape = Shapes.control,
                    loading = option.loading,
                    enabled = !working,
                    modifier = Modifier.fillMaxWidth(),
                )
                ConfirmOption.Kind.Secondary -> SecondaryButton(
                    text = option.text,
                    onClick = option.onClick,
                    icon = option.icon,
                    height = 52.dp,
                    enabled = !working,
                    modifier = Modifier.fillMaxWidth(),
                )
                ConfirmOption.Kind.Destructive -> DestructiveButton(option, enabled = !working)
            }
        }
        SecondaryButton(cancelText, onClick = onDismiss, height = 52.dp, enabled = !working, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun DestructiveButton(option: ConfirmOption, enabled: Boolean) {
    val colors = AvailablissTheme.colors
    Box(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(Shapes.control)
            .background(colors.warnBg)
            .alpha(if (enabled || option.loading) 1f else 0.5f)
            .clickable(enabled = enabled && !option.loading, role = Role.Button, onClick = option.onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (option.loading) {
            CircularProgressIndicator(Modifier.size(20.dp), color = colors.warnInk, strokeWidth = 2.dp)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                option.icon?.let { Icon(it, null, tint = colors.warnInk, modifier = Modifier.size(18.dp)) }
                Text(option.text, style = AvailablissTheme.type.bodyStrong, color = colors.warnInk)
            }
        }
    }
}
