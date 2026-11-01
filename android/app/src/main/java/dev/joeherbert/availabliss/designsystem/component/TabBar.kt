package dev.joeherbert.availabliss.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import dev.joeherbert.availabliss.designsystem.theme.BrandColors
import dev.joeherbert.availabliss.designsystem.theme.Shapes

/** A tab bar destination. [badgeCount] > 0 shows a count; [dot] shows a plain dot. */
data class TabItem<T>(
    val key: T,
    val label: String,
    val icon: ImageVector,
    val badgeCount: Int = 0,
    val dot: Boolean = false,
)

/**
 * Floating glass tab bar: 66dp tall, radius 24, inset 20dp from the sides and 22dp
 * from the bottom (above the navigation bar).
 */
@Composable
fun <T> TabBar(
    items: List<TabItem<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AvailablissTheme.colors
    Box(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 20.dp, end = 20.dp, bottom = 22.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(66.dp)
                .glass(Shapes.card)
                .padding(horizontal = 6.dp)
                .selectableGroup(),
        ) {
            items.forEach { item ->
                val on = item.key == selected
                val tint = if (on) colors.accentInk else colors.muted
                val badgeDescription = when {
                    item.badgeCount > 0 -> ", ${item.badgeCount} unread"
                    item.dot -> ", new activity"
                    else -> ""
                }
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(Shapes.control)
                        .selectable(selected = on, role = Role.Tab, onClick = { onSelect(item.key) })
                        .semantics { contentDescription = item.label + badgeDescription },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
                ) {
                    Box {
                        Icon(item.icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                        when {
                            item.badgeCount > 0 -> Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 10.dp, y = (-4).dp)
                                    .defaultMinSize(minWidth = 16.dp)
                                    .height(16.dp)
                                    .clip(Shapes.pill)
                                    .background(colors.warn)
                                    .padding(horizontal = 4.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    if (item.badgeCount > 99) "99+" else item.badgeCount.toString(),
                                    style = AvailablissTheme.type.nano.copy(fontWeight = FontWeight.Bold),
                                    color = BrandColors.BadgeInk,
                                )
                            }
                            item.dot -> BadgeDot(Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-1).dp))
                        }
                    }
                    Text(
                        item.label,
                        style = AvailablissTheme.type.micro.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium),
                        color = tint,
                    )
                }
            }
        }
    }
}
