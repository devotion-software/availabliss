package dev.joeherbert.availabliss.designsystem.component.availability

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.joeherbert.availabliss.designsystem.component.glass
import dev.joeherbert.availabliss.designsystem.theme.AvailablissPreviewTheme
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import dev.joeherbert.availabliss.designsystem.theme.Shapes

/** A block in the week grid. Fractions are positions within the 08:00–24:00 axis. */
data class WeekBlockUi(
    val start: Float,
    val end: Float,
    val label: String,
    /** true = everyone free (accent), false = someone busy (faded). */
    val free: Boolean,
)

data class WeekColumnUi(
    val label: String,
    val blocks: List<WeekBlockUi>,
    val accessibilityLabel: String = label,
    val selected: Boolean = false,
    /** Today: its label sits on an accent pill. */
    val today: Boolean = false,
    /** Works for everyone (or not known yet). Days that don't are faded so the ones that do stand out. */
    val works: Boolean = true,
)

/** How far a week day that doesn't work for everyone fades. */
private const val WEEK_FADE = 0.5f

/** Faded unless the day works; today's date always stays at full strength. */
private fun WeekColumnUi.dateAlpha() = if (works || today) 1f else WEEK_FADE

/** Hour labels on the shared 08–24 axis. */
val TimelineHours = listOf(8, 12, 16, 20, 24)

/** Fraction of the 08:00–24:00 axis for [minutesOfDay]. */
fun timelineFraction(minutesOfDay: Int): Float = ((minutesOfDay - 8 * 60) / (16f * 60)).coerceIn(0f, 1f)

/** Week view: a 320dp time grid with one column per visible day. */
@Composable
fun WeekGrid(columns: List<WeekColumnUi>, modifier: Modifier = Modifier, onSelectColumn: ((Int) -> Unit)? = null) {
    val colors = AvailablissTheme.colors
    val type = AvailablissTheme.type
    Column(
        modifier.fillMaxWidth().glass(Shapes.panel).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Spacer(Modifier.width(34.dp))
            columns.forEach {
                Box(Modifier.weight(1f).alpha(it.dateAlpha()), contentAlignment = Alignment.Center) {
                    Text(
                        it.label, style = type.micro, color = if (it.today) colors.onAccent else colors.muted, textAlign = TextAlign.Center,
                        modifier = Modifier.todayPill(it.today, colors.accent),
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(320.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BoxWithConstraints(Modifier.width(34.dp).fillMaxHeight()) {
                val h = maxHeight
                TimelineHours.forEach { hour ->
                    val y = h * timelineFraction(hour * 60)
                    Text(
                        "%02d".format(hour),
                        fontSize = 10.sp,
                        color = colors.muted,
                        // Centre the label on its line (CSS translateY(-50%)).
                        modifier = Modifier.offset(y = (y - 6.dp).coerceIn(0.dp, h - 12.dp)),
                    )
                }
            }
            columns.forEachIndexed { index, column ->
                BoxWithConstraints(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .then(if (column.selected) Modifier.border(2.dp, colors.ink, RoundedCornerShape(12.dp)) else Modifier)
                        // After the selection border, so only the day fades, not the ring.
                        .alpha(if (column.works) 1f else WEEK_FADE)
                        .clip(RoundedCornerShape(12.dp))
                        .then(if (onSelectColumn != null) Modifier.clickable(role = Role.Button, onClickLabel = "Show who's free") { onSelectColumn(index) } else Modifier)
                        .background(colors.line)
                        .semantics { contentDescription = column.accessibilityLabel },
                ) {
                    val h = maxHeight
                    column.blocks.forEach { block ->
                        val top = h * block.start
                        val height = (h * (block.end - block.start)).coerceAtLeast(14.dp)
                        val shape = RoundedCornerShape(10.dp)
                        Box(
                            Modifier
                                .offset(y = top)
                                .padding(horizontal = 3.dp)
                                .fillMaxWidth()
                                .height(height)
                                .then(if (block.free) Modifier else Modifier.alpha(0.5f))
                                .clip(shape)
                                .background(if (block.free) colors.accent else colors.busyBg)
                                .then(if (block.free) Modifier else Modifier.border(1.dp, colors.busyLine, shape))
                                .padding(6.dp),
                        ) {
                            Text(
                                block.label,
                                fontSize = 10.sp,
                                lineHeight = 12.5.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                color = if (block.free) colors.onAccent else colors.ink,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Week view in portrait: a card per day saying when everyone's free, with a strip of the
 * day (everyone free in accent, someone busy in grey). The time grid needs landscape's width.
 */
@Composable
fun WeekCards(columns: List<WeekColumnUi>, modifier: Modifier = Modifier, onSelectColumn: ((Int) -> Unit)? = null) {
    val colors = AvailablissTheme.colors
    val type = AvailablissTheme.type
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        columns.forEachIndexed { index, column ->
            val free = column.blocks.filter { it.free }
            val busy = column.blocks.filterNot { it.free }
            val shape = RoundedCornerShape(18.dp)
            Row(
                Modifier
                    .fillMaxWidth()
                    .then(if (column.selected) Modifier.border(2.dp, colors.ink, shape) else Modifier)
                    .glass(shape, shadow = false)
                    .clip(shape)
                    .then(if (onSelectColumn != null) Modifier.clickable(role = Role.Button, onClickLabel = "Show who's free") { onSelectColumn(index) } else Modifier)
                    .semantics { contentDescription = column.accessibilityLabel }
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Box(Modifier.width(56.dp).alpha(column.dateAlpha())) {
                    Text(
                        column.label, style = type.bodySmallStrong, color = if (column.today) colors.onAccent else colors.ink,
                        modifier = Modifier.padding(start = if (column.today) 6.dp else 0.dp).todayPill(column.today, colors.accent),
                    )
                }
                Column(Modifier.weight(1f).alpha(if (column.works) 1f else WEEK_FADE), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        free.firstOrNull()?.label ?: "No time when everyone's free",
                        style = type.bodySmallStrong,
                        color = if (free.isEmpty()) colors.ink else colors.accentInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    busy.firstOrNull()?.let { Text("${it.label} busy", style = type.caption, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    BoxWithConstraints(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(colors.line)) {
                        val w = maxWidth
                        (busy + free).forEach { block ->
                            Box(
                                Modifier
                                    .offset(x = w * block.start)
                                    .width((w * (block.end - block.start)).coerceAtLeast(3.dp))
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (block.free) colors.accent else colors.ink.copy(alpha = 0.32f)),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(widthDp = 390)
@Composable
private fun WeekGridPreview() {
    AvailablissPreviewTheme {
        Box(Modifier.background(AvailablissTheme.colors.bg).padding(20.dp).fillMaxSize()) {
            WeekGrid(
                listOf(
                    WeekColumnUi("Fri 13", listOf(WeekBlockUi(timelineFraction(18 * 60), 1f, "Free from 18:00", true))),
                    WeekColumnUi("Sat 14", listOf(WeekBlockUi(0f, 1f, "Everyone free all day", true))),
                    WeekColumnUi("Sun 15", listOf(WeekBlockUi(0f, timelineFraction(22 * 60), "Everyone free", true), WeekBlockUi(timelineFraction(22 * 60), 1f, "Tom", false))),
                ),
            )
        }
    }
}
