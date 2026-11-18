package dev.joeherbert.availabliss.designsystem.component.availability

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.joeherbert.availabliss.designsystem.component.softShadow
import dev.joeherbert.availabliss.designsystem.theme.AvWeights
import dev.joeherbert.availabliss.designsystem.theme.AvailablissPreviewTheme
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import java.time.LocalDate

/** Availability cell states (DESIGN.md "Availability states"). */
enum class CellState { Free, Busy, Match, Hidden }

/** Where a match cell sits inside a multi-day slot band. */
enum class BandPosition { Start, Middle, End, Single }

data class MonthCellUi(
    val date: LocalDate,
    val state: CellState,
    val band: BandPosition? = null,
    val note: String = "",
    val selected: Boolean = false,
    /** How many members' preferred times this day fits (shown as a heart on days that work). */
    val preferredCount: Int = 0,
    val accessibilityLabel: String = "",
    /**
     * Today: its number sits on an inverted pill on a day that works; on one that doesn't, the cell
     * is outlined in the accent with its number in accent (a pill would fade with the cell).
     */
    val today: Boolean = false,
    /**
     * A day that works only with someone missing (a quorum or optional members): drawn lighter,
     * a soft accent fill with an accent outline, and [note] says who ("2 missing").
     */
    val partial: Boolean = false,
)

/** How far cells that don't work fade; today's content doesn't, only its background. */
private const val BUSY_FADE = 0.5f
private const val HIDDEN_FADE = 0.35f

/** Today on a day that doesn't work: an accent outline rather than the cell's own border. */
@Composable
private fun Modifier.todayOutline(today: Boolean, shape: Shape, fallback: Color): Modifier =
    if (today) border(1.5.dp, AvailablissTheme.colors.accent, shape) else border(1.dp, fallback, shape)

/** A [width] ring [inset] inside the edge of [shape], drawn over the background. */
private fun Modifier.insetRing(shape: Shape, color: Color, width: Dp = 2.dp, inset: Dp = 2.dp): Modifier = drawBehind {
    val grow = inset.toPx() + width.toPx() / 2
    val outline = shape.createOutline(Size(size.width - grow * 2, size.height - grow * 2), layoutDirection, this)
    val path = Path().apply {
        addOutline(outline)
        translate(Offset(grow, grow))
    }
    drawPath(path, color, style = Stroke(width.toPx()))
}

/**
 * Picks out today's date with a pill behind it in [pill]. Drawn outside the layout, so cells keep
 * their size.
 */
fun Modifier.todayPill(today: Boolean, pill: Color): Modifier = if (!today) this else drawBehind {
    val h = 6.dp.toPx()
    val v = 3.dp.toPx()
    drawRoundRect(pill, topLeft = Offset(-h, -v), size = Size(size.width + 2 * h, size.height + 2 * v), cornerRadius = CornerRadius(size.height))
}

/** Shape of a cell: 16dp corners, 5dp on the inner edges of a joined band. */
fun bandShape(band: BandPosition?, outer: Dp = 16.dp, inner: Dp = 5.dp): Shape = when (band) {
    BandPosition.Start -> RoundedCornerShape(topStart = outer, topEnd = inner, bottomEnd = inner, bottomStart = outer)
    BandPosition.Middle -> RoundedCornerShape(inner)
    BandPosition.End -> RoundedCornerShape(topStart = inner, topEnd = outer, bottomEnd = outer, bottomStart = inner)
    BandPosition.Single, null -> RoundedCornerShape(outer)
}

/** 2dp outline drawn [offset] outside the shape, like CSS `outline-offset`. */
fun Modifier.outlineOutside(shape: Shape, color: Color, width: Dp = 2.dp, offset: Dp = 2.dp): Modifier = drawBehind {
    val grow = offset.toPx() + width.toPx() / 2
    val outline = shape.createOutline(Size(size.width + grow * 2, size.height + grow * 2), layoutDirection, this)
    val path = Path().apply {
        addOutline(outline)
        translate(Offset(-grow, -grow))
    }
    drawPath(path, color, style = Stroke(width.toPx()))
}

/**
 * Month view: only the weekday columns that can ever match are drawn. [rows] are
 * weeks; a null cell is a date outside the month (left blank).
 */
@Composable
fun MonthGrid(
    columnLabels: List<String>,
    rows: List<List<MonthCellUi?>>,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val type = AvailablissTheme.type
    val colors = AvailablissTheme.colors
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            columnLabels.forEach {
                Text(it, style = type.micro, color = colors.muted, modifier = Modifier.weight(1f).padding(start = 10.dp))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            rows.forEach { week ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    week.forEach { cell ->
                        if (cell == null) {
                            Spacer(Modifier.weight(1f).height(54.dp))
                        } else if (cell.state == CellState.Hidden) {
                            HiddenCell(cell, Modifier.weight(1f))
                        } else {
                            MonthCell(cell, onClick = { onSelect(cell.date) }, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthCell(cell: MonthCellUi, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AvailablissTheme.colors
    val shape = bandShape(if (cell.state == CellState.Match) cell.band else null)
    // Works for most of the group: lighter than a day that works for everyone.
    val partial = cell.state == CellState.Match && cell.partial
    val solid = cell.state == CellState.Match && !partial
    val (bg, fg, border) = when {
        partial -> Triple(colors.accentSoft, colors.accentInk, colors.accent)
        cell.state == CellState.Match -> Triple(colors.accent, colors.onAccent, Color.Transparent)
        cell.state == CellState.Busy -> Triple(colors.busyBg, colors.ink, colors.glassBorder)
        else -> Triple(colors.surface, colors.ink, colors.glassBorder)
    }
    // Today on a day that doesn't work keeps its content at full strength; only the cell fades.
    val plainToday = cell.today && cell.state != CellState.Match
    val fade = if (cell.state == CellState.Busy) BUSY_FADE else 1f
    Box(modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .height(54.dp)
                .then(if (solid) Modifier.softShadow(shape, colors.accent, offsetY = 12.dp, blur = 24.dp, spread = (-14).dp) else Modifier)
                .alpha(if (plainToday) 1f else fade)
                .clip(shape)
                .background(if (plainToday) bg.copy(alpha = bg.alpha * fade) else bg)
                // The selected day: a ring inside the cell, so it never reaches the neighbouring days. It takes over
                // from today's outline (only ever one ring); on a day that works, a light inner ring reads on the fill.
                .then(
                    when {
                        cell.selected && solid -> Modifier.insetRing(shape, colors.onAccent, inset = 2.dp)
                        cell.selected -> Modifier.border(2.dp, colors.ink, shape)
                        plainToday -> Modifier.todayOutline(true, shape, border)
                        partial -> Modifier.border(1.5.dp, border, shape)
                        else -> Modifier.border(1.dp, border, shape)
                    },
                )
                .clickable(role = Role.Button, onClick = onClick)
                .semantics {
                    contentDescription = cell.accessibilityLabel
                    selected = cell.selected
                }
                .padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
        ) {
            // Today's pill is inverted on a cell already filled with the accent.
            val onAccent = solid
            Text(
                cell.date.dayOfMonth.toString(),
                fontSize = 17.sp,
                fontWeight = AvWeights.Display650,
                lineHeight = 17.sp,
                // Today on a partial day sits on an accent pill (the cell's own fill is soft).
                color = when {
                    plainToday -> colors.accentInk
                    !cell.today -> fg
                    onAccent -> colors.accent
                    else -> colors.onAccent
                },
                modifier = Modifier.todayPill(cell.today && cell.state == CellState.Match, if (onAccent) colors.onAccent else colors.accent),
            )
            Text(cell.note, style = AvailablissTheme.type.micro, color = if (plainToday) colors.muted else fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (cell.state == CellState.Match && cell.preferredCount > 0) {
            Row(Modifier.align(Alignment.TopEnd).padding(top = 5.dp, end = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Icon(dev.joeherbert.availabliss.designsystem.icon.AvIcons.Heart, null, tint = fg, modifier = Modifier.size(9.dp))
                Text(cell.preferredCount.toString(), fontSize = 9.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = fg)
            }
        }
    }
}

/**
 * A day outside the group's timing, or already past: a quiet placeholder ("Past", "Today").
 * Today stays readable: outlined in the accent, its date in accent, only the cell faded.
 */
@Composable
private fun HiddenCell(cell: MonthCellUi, modifier: Modifier = Modifier) {
    val colors = AvailablissTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .height(54.dp)
            .alpha(if (cell.today) 1f else HIDDEN_FADE)
            .clip(shape)
            .background(if (cell.today) colors.surface.copy(alpha = colors.surface.alpha * HIDDEN_FADE) else colors.surface)
            .todayOutline(cell.today, shape, colors.glassBorder)
            .semantics { contentDescription = cell.accessibilityLabel }
            .padding(horizontal = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        Text(
            cell.date.dayOfMonth.toString(),
            fontSize = 17.sp,
            fontWeight = AvWeights.Display650,
            lineHeight = 17.sp,
            color = if (cell.today) colors.accentInk else colors.ink,
        )
        Text(cell.note, style = AvailablissTheme.type.micro, color = if (cell.today) colors.muted else colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Legend under the month: "Works for everyone", "Most of us free" (when [showPartial]: a day
 * on screen works only with someone missing), "Someone's busy", "Fri–Sun only".
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun MonthLegend(daysNote: String?, modifier: Modifier = Modifier, showPartial: Boolean = false) {
    val colors = AvailablissTheme.colors
    val type = AvailablissTheme.type
    // Wraps: with "Most of us free" as well, it can be wider than a phone.
    androidx.compose.foundation.layout.FlowRow(
        modifier.fillMaxWidth().padding(start = 2.dp, top = 4.dp, end = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        LegendItem("Works for everyone") {
            Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(colors.accent))
        }
        if (showPartial) {
            LegendItem("Most of us free") {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(colors.accentSoft).border(1.dp, colors.accent, RoundedCornerShape(3.dp)))
            }
        }
        LegendItem("Someone's busy") {
            Box(Modifier.size(10.dp).alpha(0.5f).clip(RoundedCornerShape(3.dp)).background(colors.busyBg).border(1.dp, colors.busyLine, RoundedCornerShape(3.dp)))
        }
        daysNote?.let { Text(it, style = type.micro.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal), color = colors.muted) }
    }
}

@Composable
internal fun LegendItem(label: String, swatch: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        swatch()
        Spacer(Modifier.width(5.dp))
        Text(label, style = AvailablissTheme.type.micro.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal), color = AvailablissTheme.colors.muted)
    }
}

@Preview(widthDp = 390)
@Composable
private fun MonthGridPreview() {
    AvailablissPreviewTheme {
        val nov = { d: Int -> LocalDate.of(2026, 11, d) }
        val rows = listOf(
            listOf(null, null, MonthCellUi(nov(1), CellState.Free, note = "Today")),
            listOf(MonthCellUi(nov(6), CellState.Free), MonthCellUi(nov(7), CellState.Busy, note = "Priya busy"), MonthCellUi(nov(8), CellState.Free)),
            listOf(
                MonthCellUi(nov(13), CellState.Match, BandPosition.Start, "18:00", selected = true),
                MonthCellUi(nov(14), CellState.Match, BandPosition.Middle, "all free"),
                MonthCellUi(nov(15), CellState.Match, BandPosition.End, "all free"),
            ),
            listOf(MonthCellUi(nov(20), CellState.Match, BandPosition.Single, "Tom missing", partial = true), null, null),
        )
        Column(Modifier.background(AvailablissTheme.colors.bg).padding(20.dp)) {
            MonthGrid(listOf("Fri", "Sat", "Sun"), rows, onSelect = {})
            MonthLegend("Fri–Sun only", showPartial = true)
        }
    }
}
