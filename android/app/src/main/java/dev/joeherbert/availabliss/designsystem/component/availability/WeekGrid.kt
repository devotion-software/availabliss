package dev.joeherbert.availabliss.designsystem.component.availability

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.Role
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
    /** A free block when most of the group is free (a quorum or optional members): drawn lighter. */
    val most: Boolean = false,
)

data class WeekColumnUi(
    val label: String,
    val blocks: List<WeekBlockUi>,
    val accessibilityLabel: String = label,
    val selected: Boolean = false,
    /** Today: its label sits on an accent pill. */
    val today: Boolean = false,
    /** How its date looks, and how far the day steps back, as its month cell. */
    val look: DayLook = DayLook.Free,
    /** A bank holiday: a small flag after the label. */
    val bankHoliday: Boolean = false,
)

/**
 * How a day's date looks in the week, as its month cell: accent when it works (soft when only most
 * of the group can), dark when someone's busy, plain when free, dashed when past or outside the timing.
 */
enum class DayLook(val fade: Float) {
    Match(1f),
    Partial(1f),
    Busy(0.5f),
    Free(1f),
    Out(0.35f),
}

/** A date drawn on its month-cell look (the text colour to use comes from [dayLookInk]). */
@Composable
private fun Modifier.dayLook(look: DayLook, shape: RoundedCornerShape): Modifier {
    val colors = AvailablissTheme.colors
    return when (look) {
        DayLook.Match -> clip(shape).background(colors.accent)
        DayLook.Partial -> clip(shape).background(colors.accentSoft).border(1.5.dp, colors.accent, shape)
        DayLook.Busy -> clip(shape).background(colors.busyBg).border(1.dp, colors.glassBorder, shape)
        DayLook.Free -> clip(shape).background(colors.surface).border(1.dp, colors.glassBorder, shape)
        DayLook.Out -> drawBehind {
            val stroke = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 1.dp.toPx(),
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())),
            )
            val radius = 12.dp.toPx()
            drawRoundRect(colors.line, cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius), style = stroke)
        }
    }
}

@Composable
private fun dayLookInk(look: DayLook) = AvailablissTheme.colors.let {
    when (look) {
        DayLook.Match -> it.onAccent
        DayLook.Partial -> it.accentInk
        DayLook.Out -> it.muted
        else -> it.ink
    }
}

/** A small flag after a bank holiday's date. */
@Composable
private fun BankHolidayFlag(tint: androidx.compose.ui.graphics.Color) {
    androidx.compose.material3.Icon(
        dev.joeherbert.availabliss.designsystem.icon.AvIcons.Flag,
        null,
        tint = tint.copy(alpha = 0.8f),
        modifier = Modifier.padding(start = 3.dp).size(9.dp),
    )
}

/** Faded as its month cell; today's date always stays at full strength. */
private fun WeekColumnUi.dateAlpha() = if (today) 1f else look.fade

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
                val ink = dayLookInk(it.look)
                Row(
                    Modifier.weight(1f).heightIn(min = 28.dp).alpha(it.dateAlpha()).dayLook(it.look, RoundedCornerShape(10.dp)),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        it.label,
                        style = type.micro,
                        color = if (it.today) (if (it.look == DayLook.Match) colors.accent else colors.onAccent) else ink,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.todayPill(it.today, if (it.look == DayLook.Match) colors.onAccent else colors.accent),
                    )
                    if (it.bankHoliday) BankHolidayFlag(ink)
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
                        .alpha(column.look.fade)
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
                                .background(if (block.most) colors.accentSoft else if (block.free) colors.accent else colors.busyBg)
                                .then(
                                    when {
                                        block.most -> Modifier.border(1.dp, colors.accent, shape)
                                        block.free -> Modifier
                                        else -> Modifier.border(1.dp, colors.busyLine, shape)
                                    },
                                )
                                .padding(6.dp),
                        ) {
                            Text(
                                block.label,
                                fontSize = 10.sp,
                                lineHeight = 12.5.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                color = if (block.most) colors.accentInk else if (block.free) colors.onAccent else colors.ink,
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
            // Everyone free first in the headline; most of us free only when nothing works for everyone.
            val free = column.blocks.filter { it.free }.sortedBy { it.most }
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
                val ink = dayLookInk(column.look)
                Row(
                    Modifier.width(56.dp).heightIn(min = 44.dp).alpha(column.dateAlpha()).dayLook(column.look, RoundedCornerShape(12.dp)),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Text(
                        column.label,
                        style = type.bodySmallStrong,
                        color = if (column.today) (if (column.look == DayLook.Match) colors.accent else colors.onAccent) else ink,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.todayPill(column.today, if (column.look == DayLook.Match) colors.onAccent else colors.accent),
                    )
                    if (column.bankHoliday) BankHolidayFlag(ink)
                }
                Column(Modifier.weight(1f).alpha(column.look.fade), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                        (busy + free.sortedBy { !it.most }).forEach { block ->
                            Box(
                                Modifier
                                    .offset(x = w * block.start)
                                    .width((w * (block.end - block.start)).coerceAtLeast(3.dp))
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (block.most) colors.accent.copy(alpha = 0.4f) else if (block.free) colors.accent else colors.ink.copy(alpha = 0.32f)),
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
