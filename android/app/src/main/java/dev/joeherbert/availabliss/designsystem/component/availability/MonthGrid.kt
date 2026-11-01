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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
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
    val accessibilityLabel: String = "",
)

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
                        if (cell == null || cell.state == CellState.Hidden) {
                            Spacer(Modifier.weight(1f).height(54.dp))
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
    val (bg, fg, border) = when (cell.state) {
        CellState.Match -> Triple(colors.accent, colors.onAccent, Color.Transparent)
        CellState.Busy -> Triple(colors.busyBg, colors.ink, colors.glassBorder)
        else -> Triple(colors.surface, colors.ink, colors.glassBorder)
    }
    Column(
        modifier
            .height(54.dp)
            .then(if (cell.selected) Modifier.outlineOutside(shape, colors.ink) else Modifier)
            .then(if (cell.state == CellState.Match) Modifier.softShadow(shape, colors.accent, offsetY = 12.dp, blur = 24.dp, spread = (-14).dp) else Modifier)
            .alpha(if (cell.state == CellState.Busy) 0.5f else 1f)
            .clip(shape)
            .background(bg)
            .border(1.dp, border, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = cell.accessibilityLabel
                selected = cell.selected
            }
            .padding(horizontal = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        Text(cell.date.dayOfMonth.toString(), fontSize = 17.sp, fontWeight = AvWeights.Display650, lineHeight = 17.sp, color = fg)
        Text(cell.note, style = AvailablissTheme.type.micro, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Legend under the month: "Works for everyone", "Someone's busy", "Fri–Sun only". */
@Composable
fun MonthLegend(daysNote: String?, modifier: Modifier = Modifier) {
    val colors = AvailablissTheme.colors
    val type = AvailablissTheme.type
    Row(
        modifier.fillMaxWidth().padding(start = 2.dp, top = 4.dp, end = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendItem("Works for everyone") {
            Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(colors.accent))
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
                MonthCellUi(nov(13), CellState.Match, BandPosition.Start, "from 18:00", selected = true),
                MonthCellUi(nov(14), CellState.Match, BandPosition.Middle, "all free"),
                MonthCellUi(nov(15), CellState.Match, BandPosition.End, "all free"),
            ),
        )
        Column(Modifier.background(AvailablissTheme.colors.bg).padding(20.dp)) {
            MonthGrid(listOf("Fri", "Sat", "Sun"), rows, onSelect = {})
            MonthLegend("Fri–Sun only")
        }
    }
}
