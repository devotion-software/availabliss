package dev.joeherbert.availabliss.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import dev.joeherbert.availabliss.designsystem.theme.Shapes

enum class ButtonStyle { Solid, Accent, Success }

/**
 * Primary action. [ButtonStyle.Solid] = `solid` / `on-solid` (most CTAs),
 * [ButtonStyle.Accent] = `accent` / `on-accent` ("Block this time", "Propose"),
 * [ButtonStyle.Success] = `ok-bg` / `ok-ink` ("Added to your calendar").
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    style: ButtonStyle = ButtonStyle.Solid,
    height: Dp = 54.dp,
    shape: Shape = Shapes.controlLarge,
    textStyle: TextStyle = AvailablissTheme.type.bodyStrong,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val colors = AvailablissTheme.colors
    val (bg, fg) = when (style) {
        ButtonStyle.Solid -> colors.solid to colors.onSolid
        ButtonStyle.Accent -> colors.accent to colors.onAccent
        ButtonStyle.Success -> colors.okBg to colors.okInk
    }.let { (bg, fg) -> bg to if (enabled) fg else fg.copy(alpha = fg.alpha * 0.5f) }
    // Disabled fades the label rather than using a layer: an alpha layer here left the
    // background undrawn after the button switched from disabled to enabled.
    Row(
        modifier = modifier
            .height(height)
            .clip(shape)
            .background(bg)
            .clickable(enabled = enabled && !loading, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(color = fg, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
        } else {
            icon?.let { Icon(it, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp)) }
            Text(text, style = textStyle, color = fg, maxLines = 1)
        }
    }
}

/** Secondary action: `surface` fill with a `line-2` border. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    height: Dp = 50.dp,
    shape: Shape = Shapes.control,
    textStyle: TextStyle = AvailablissTheme.type.bodyMedium,
    borderColor: Color = AvailablissTheme.colors.line2,
    enabled: Boolean = true,
) {
    val colors = AvailablissTheme.colors
    // Disabled fades the label, not the whole button: see PrimaryButton.
    val ink = if (enabled) colors.ink else colors.ink.copy(alpha = colors.ink.alpha * 0.5f)
    Row(
        modifier = modifier
            .height(height)
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, borderColor, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { Icon(it, contentDescription = null, tint = ink, modifier = Modifier.size(18.dp)) }
        Text(text, style = textStyle, color = ink, maxLines = 1)
    }
}

/** Borderless text action in `accent-ink` ("Use a different email", "Change"). */
@Composable
fun TextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = AvailablissTheme.colors.accentInk,
    style: TextStyle = AvailablissTheme.type.bodySmallStrong,
    enabled: Boolean = true,
) {
    Box(
        modifier
            .defaultMinSize(minHeight = 44.dp)
            .clip(Shapes.inner)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = style, color = color.copy(alpha = if (enabled) 1f else 0.5f))
    }
}

/** 44dp circular glass icon button (back, invite, profile). */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 20.dp,
    shape: Shape = CircleShape,
    tint: Color = AvailablissTheme.colors.ink,
) {
    Box(
        modifier
            .size(size)
            .glass(shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** Flat circular icon button (sheet close, chat header actions). */
@Composable
fun PlainIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 20.dp,
    background: Color = Color.Transparent,
    tint: Color = AvailablissTheme.colors.ink,
    enabled: Boolean = true,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** Small glass pill button with an icon ("New group"). */
@Composable
fun GlassPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val colors = AvailablissTheme.colors
    Row(
        modifier
            .height(36.dp)
            .glass(Shapes.pill)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { Icon(it, contentDescription = null, tint = colors.ink, modifier = Modifier.size(14.dp)) }
        Text(text, style = AvailablissTheme.type.label, color = colors.ink)
    }
}
