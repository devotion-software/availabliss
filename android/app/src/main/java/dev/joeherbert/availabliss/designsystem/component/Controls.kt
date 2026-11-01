package dev.joeherbert.availabliss.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.joeherbert.availabliss.designsystem.icon.AvIcons
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import dev.joeherbert.availabliss.designsystem.theme.Shapes

/** One option in a [SegmentedControl]. */
data class Segment<T>(val value: T, val label: String, val icon: ImageVector? = null)

enum class SegmentedStyle {
    /** Glass container, `solid` selected segment (calendar Month / Week / Day). */
    Glass,
    /** `line` well, raised `surface` selected segment (Repeat, Appearance mode). */
    Inset,
}

@Composable
fun <T> SegmentedControl(
    segments: List<Segment<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    style: SegmentedStyle = SegmentedStyle.Glass,
    segmentHeight: Dp = if (style == SegmentedStyle.Glass) 32.dp else 36.dp,
    fillWidth: Boolean = style == SegmentedStyle.Inset,
) {
    val colors = AvailablissTheme.colors
    val container = when (style) {
        SegmentedStyle.Glass -> Modifier.glass(RoundedCornerShape(13.dp)).padding(3.dp)
        SegmentedStyle.Inset -> Modifier.clip(Shapes.controlSmall).background(colors.line).padding(4.dp)
    }
    Row(
        modifier.then(container).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(if (style == SegmentedStyle.Glass) 2.dp else 6.dp),
    ) {
        segments.forEach { segment ->
            val on = segment.value == selected
            val segShape = RoundedCornerShape(if (style == SegmentedStyle.Glass) 10.dp else 10.dp)
            val bg by animateColorAsState(
                when {
                    !on -> Color.Transparent
                    style == SegmentedStyle.Glass -> colors.solid
                    else -> colors.surface
                },
                label = "segment-bg",
            )
            val fg = when {
                style == SegmentedStyle.Glass && on -> colors.onSolid
                style == SegmentedStyle.Glass -> colors.ink
                on -> colors.ink
                else -> colors.muted
            }
            Row(
                Modifier
                    .then(if (fillWidth) Modifier.weight(1f) else Modifier.defaultMinSize(minWidth = 52.dp))
                    .height(segmentHeight)
                    .then(if (on && style == SegmentedStyle.Inset) Modifier.shadow(4.dp, segShape, spotColor = colors.shadow, ambientColor = Color.Transparent) else Modifier)
                    .clip(segShape)
                    .background(bg)
                    .selectable(selected = on, role = Role.Tab, onClick = { onSelect(segment.value) })
                    .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                segment.icon?.let { Icon(it, null, tint = fg, modifier = Modifier.size(16.dp)) }
                Text(segment.label, style = AvailablissTheme.type.label, color = fg, maxLines = 1)
            }
        }
    }
}

/** Pill chip: `solid` when selected, `surface` + `glass-border` otherwise. */
@Composable
fun Chip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 38.dp,
) {
    val colors = AvailablissTheme.colors
    val bg by animateColorAsState(if (selected) colors.solid else colors.surface, label = "chip-bg")
    Box(
        modifier
            .height(height)
            .clip(Shapes.pill)
            .background(bg)
            .border(1.dp, if (selected) colors.solid else colors.glassBorder, Shapes.pill)
            .selectable(selected = selected, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = AvailablissTheme.type.label, color = if (selected) colors.onSolid else colors.ink, maxLines = 1)
    }
}

/** Removable `accent-soft` chip ("27 Dec – 2 Jan ×"). */
@Composable
fun RemovableChip(label: String, removeDescription: String, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AvailablissTheme.colors
    Row(
        modifier.height(36.dp).clip(Shapes.pill).background(colors.accentSoft).padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, style = AvailablissTheme.type.label, color = colors.accentInk)
        PlainIconButton(AvIcons.Close, removeDescription, onRemove, size = 28.dp, iconSize = 12.dp, tint = colors.accentInk)
    }
}

/** Dashed "+ Add …" pill. */
@Composable
fun DashedChip(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AvailablissTheme.colors
    Box(
        modifier
            .height(36.dp)
            .clip(Shapes.pill)
            .dashedBorder(colors.line3, Shapes.pill)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = AvailablissTheme.type.label, color = colors.ink)
    }
}

/** 52×32 switch from the concept. Make the containing row toggleable rather than this alone where possible. */
@Composable
fun Toggle(checked: Boolean, modifier: Modifier = Modifier) {
    val colors = AvailablissTheme.colors
    val track by animateColorAsState(if (checked) colors.accent else colors.line2, label = "toggle-track")
    val knobOffset by animateDpAsState(if (checked) 20.dp else 0.dp, label = "toggle-knob")
    Box(
        modifier.size(52.dp, 32.dp).clip(RoundedCornerShape(16.dp)).background(track).padding(3.dp),
    ) {
        Box(
            Modifier
                .offset(x = knobOffset)
                .size(26.dp)
                .shadow(3.dp, CircleShape, ambientColor = Color(0x40141440), spotColor = Color(0x40141440))
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

/** A list row whose whole area toggles a [Toggle] (min height 50–60). */
@Composable
fun ToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable RowScope.() -> Unit)? = null,
    accessibilityLabel: String = title,
    minHeight: Dp = if (subtitle != null) 60.dp else 50.dp,
) {
    val colors = AvailablissTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = minHeight)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .semantics { contentDescription = accessibilityLabel },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leading?.invoke(this)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, style = if (subtitle != null) AvailablissTheme.type.bodyMedium else AvailablissTheme.type.bodyMediumRegular, color = colors.ink)
            subtitle?.let { Text(it, style = AvailablissTheme.type.caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal), color = colors.muted) }
        }
        Toggle(checked)
    }
}

/** 1px `line` divider used between rows inside glass sections. */
@Composable
fun RowDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(AvailablissTheme.colors.line))
}

/** Square colour swatch (calendar colour dots: 12dp, radius 4). */
@Composable
fun ColorSwatch(color: Color, modifier: Modifier = Modifier, size: Dp = 12.dp, radius: Dp = 4.dp) {
    Box(modifier.size(size).clip(RoundedCornerShape(radius)).background(color))
}

/** Onboarding progress: inactive steps 18dp `line-3`, current 30dp `ink`. */
@Composable
fun StepIndicator(current: Int, total: Int, modifier: Modifier = Modifier) {
    val colors = AvailablissTheme.colors
    Row(
        modifier.semantics { contentDescription = "Step $current of $total" },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(total) { i ->
            val active = i + 1 == current
            val w by animateDpAsState(if (active) 30.dp else 18.dp, label = "step")
            Box(Modifier.width(w).height(6.dp).clip(RoundedCornerShape(3.dp)).background(if (active) colors.ink else colors.line3))
        }
    }
}

/** Horizontal flexible spacer for rows. */
@Composable
fun RowScope.Fill() = Spacer(Modifier.weight(1f))

/** Shape-aware dashed border (pending members, add-range chip). */
fun Modifier.dashedBorder(color: Color, shape: Shape, width: Dp = 1.dp, dash: Dp = 4.dp, gap: Dp = 3.dp): Modifier =
    this.then(
        Modifier.drawWithCacheDashed(color, shape, width, dash, gap),
    )
