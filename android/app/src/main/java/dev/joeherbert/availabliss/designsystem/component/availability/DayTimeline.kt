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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.joeherbert.availabliss.designsystem.component.dashedBorder
import dev.joeherbert.availabliss.designsystem.component.glass
import dev.joeherbert.availabliss.designsystem.component.stripes
import dev.joeherbert.availabliss.designsystem.theme.AvailablissPreviewTheme
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import dev.joeherbert.availabliss.designsystem.theme.Shapes

/** A busy bar on a member's track (fractions of the 08–24 axis). */
data class TimelineBarUi(val start: Float, val end: Float, val manual: Boolean)

data class TimelineRowUi(
    val name: String,
    val pending: Boolean,
    val bars: List<TimelineBarUi>,
    val accessibilityLabel: String = name,
)

/** An "Everyone" free band. */
data class EveryoneBandUi(val start: Float, val end: Float, val label: String)

/** Day view: "Everyone" row plus one timeline per member, 08–24. */
@Composable
fun DayTimeline(
    title: String,
    note: String?,
    everyone: List<EveryoneBandUi>,
    rows: List<TimelineRowUi>,
    modifier: Modifier = Modifier,
) {
    val colors = AvailablissTheme.colors
    val type = AvailablissTheme.type
    Column(
        modifier.fillMaxWidth().glass(Shapes.panel).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text(title, style = type.bodySmallStrong, color = colors.ink)
            note?.let { Text(it, style = type.caption.copy(fontWeight = FontWeight.Normal), color = colors.muted) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TimelineLine(label = "Everyone", labelColor = colors.accentInk, labelBold = true) {
                BoxWithConstraints(
                    Modifier
                        .fillMaxWidth()
                        .height(30.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(colors.line)
                        .semantics { contentDescription = "Everyone free: " + everyone.joinToString { it.label }.ifEmpty { "no shared time" } },
                ) {
                    val w = maxWidth
                    everyone.forEach { band ->
                        Box(
                            Modifier
                                .offset(x = w * band.start)
                                .width(w * (band.end - band.start))
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(9.dp))
                                .background(colors.accent)
                                .padding(start = 8.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text(band.label, style = type.micro, color = colors.onAccent, maxLines = 1, overflow = TextOverflow.Clip)
                        }
                    }
                }
            }
            rows.forEach { row -> MemberTrack(row) }
        }
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(72.dp))
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween) {
                TimelineHours.forEach { Text("%02d".format(it), fontSize = 10.sp, color = colors.muted) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(14.dp, 10.dp).clip(RoundedCornerShape(3.dp)).stripes(colors.line3, 2.dp, 5.dp))
            Text("Striped = blocked by hand", style = type.micro.copy(fontWeight = FontWeight.Normal), color = colors.muted)
        }
    }
}

@Composable
private fun TimelineLine(label: String, labelColor: androidx.compose.ui.graphics.Color, labelBold: Boolean, track: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = if (labelBold) FontWeight.Bold else FontWeight.Medium,
            color = labelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(64.dp),
        )
        Box(Modifier.weight(1f)) { track() }
    }
}

@Composable
private fun MemberTrack(row: TimelineRowUi) {
    val colors = AvailablissTheme.colors
    val shape = RoundedCornerShape(7.dp)
    TimelineLine(label = row.name, labelColor = colors.ink, labelBold = false) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(22.dp)
                .then(if (row.pending) Modifier.dashedBorder(colors.line3, shape) else Modifier.clip(shape).background(colors.accentSoft))
                .semantics { contentDescription = row.accessibilityLabel },
        ) {
            val w = maxWidth
            row.bars.forEach { bar ->
                Box(
                    Modifier
                        .offset(x = w * bar.start)
                        .width((w * (bar.end - bar.start)).coerceAtLeast(2.dp))
                        .fillMaxHeight()
                        .alpha(0.5f)
                        .clip(shape)
                        .background(colors.busyBg)
                        .then(if (bar.manual) Modifier.stripes(colors.line3, 2.dp, 5.dp) else Modifier)
                        .border(1.dp, colors.busyLine, shape),
                )
            }
            if (row.pending) {
                Text(
                    "Not synced yet",
                    fontSize = 10.sp,
                    color = colors.muted,
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp),
                )
            }
        }
    }
}

@Preview(widthDp = 390)
@Composable
private fun DayTimelinePreview() {
    val f = { h: Double -> timelineFraction((h * 60).toInt()) }
    AvailablissPreviewTheme {
        Box(Modifier.background(AvailablissTheme.colors.bg).padding(20.dp).fillMaxSize()) {
            DayTimeline(
                title = "Fri 13 Nov",
                note = "Timing starts 18:00",
                everyone = listOf(EveryoneBandUi(f(18.0), 1f, "18:00 – late")),
                rows = listOf(
                    TimelineRowUi("Sam (you)", false, listOf(TimelineBarUi(f(9.0), f(17.5), false))),
                    TimelineRowUi("Priya", false, listOf(TimelineBarUi(f(8.5), f(17.0), false), TimelineBarUi(f(17.0), f(18.0), true))),
                    TimelineRowUi("Leo", true, emptyList()),
                ),
            )
        }
    }
}
