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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.joeherbert.availabliss.designsystem.component.dashedBorder
import dev.joeherbert.availabliss.designsystem.component.glass
import dev.joeherbert.availabliss.designsystem.theme.AvailablissPreviewTheme
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import dev.joeherbert.availabliss.designsystem.theme.Shapes

/** A busy bar on a member's track (fractions of the 08–24 axis). */
data class TimelineBarUi(val start: Float, val end: Float)

data class TimelineRowUi(
    val name: String,
    val pending: Boolean,
    val bars: List<TimelineBarUi>,
    val accessibilityLabel: String = name,
)

/** Hour labels on the day timeline, which shows the whole day. */
val DayTimelineHours = listOf(0, 4, 8, 12, 16, 20, 24)

/** Fraction of the 00:00–24:00 day timeline for [minutesOfDay]. */
fun dayFraction(minutesOfDay: Int): Float = (minutesOfDay / (24f * 60)).coerceIn(0f, 1f)

/**
 * A band on the "Everyone" row: when everyone's free ([everyone] true, solid accent), or, with
 * a quorum or optional members, when most of the group is (drawn lighter).
 */
data class EveryoneBandUi(val start: Float, val end: Float, val label: String, val everyone: Boolean = true)

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
        DayTimelineRows(everyone, rows)
    }
}

/** The "Everyone" row, a row per member and the hour ticks, without a card around them. */
@Composable
fun DayTimelineRows(everyone: List<EveryoneBandUi>, rows: List<TimelineRowUi>, modifier: Modifier = Modifier) {
    val colors = AvailablissTheme.colors
    val type = AvailablissTheme.type
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // The track is the width less the 64dp name column and 8dp gap. A band's times go inside it
        // only if every band is wide enough (about 6.2dp a character at 11sp, plus padding);
        // otherwise they all go on a line under the row.
        val trackWidth = maxWidth - 72.dp
        val labelsInside = everyone.all { band -> trackWidth * (band.end - band.start) >= (band.label.length * 6.2f + 12).dp }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TimelineLine(label = "Everyone", labelColor = colors.accentInk, labelBold = true) {
                    BoxWithConstraints(
                        Modifier
                            .fillMaxWidth()
                            .height(30.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(colors.line)
                            .semantics {
                                val all = everyone.filter { it.everyone }.joinToString { it.label }.ifEmpty { "no shared time" }
                                val most = everyone.filterNot { it.everyone }.joinToString { it.label }
                                contentDescription = "Everyone free: $all" + if (most.isNotEmpty()) ". Most of us free: $most" else ""
                            },
                    ) {
                        val w = maxWidth
                        everyone.forEach { band ->
                            Box(
                                Modifier
                                    .offset(x = w * band.start)
                                    .width(w * (band.end - band.start))
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(if (band.everyone) colors.accent else colors.accentSoft)
                                    .then(if (band.everyone) Modifier else Modifier.border(1.dp, colors.accent, RoundedCornerShape(9.dp)))
                                    .padding(start = 8.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                if (labelsInside) {
                                    Text(band.label, style = type.micro, color = if (band.everyone) colors.onAccent else colors.accentInk, maxLines = 1, overflow = TextOverflow.Clip)
                                }
                            }
                        }
                    }
                }
                if (!labelsInside && everyone.isNotEmpty()) {
                    val all = everyone.filter { it.everyone }.joinToString(" · ") { it.label }
                    val most = everyone.filterNot { it.everyone }.joinToString(" · ") { it.label }
                    Row(Modifier.fillMaxWidth().clearAndSetSemantics {}) {
                        Spacer(Modifier.width(72.dp))
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(color = colors.accentInk)) { append(all) }
                                if (most.isNotEmpty()) withStyle(SpanStyle(color = colors.muted)) { append((if (all.isEmpty()) "" else "   ") + "Most of us $most") }
                            },
                            style = type.micro,
                            maxLines = 2,
                            modifier = Modifier.weight(1f).offset(y = (-4).dp),
                        )
                    }
                }
                rows.forEach { row -> MemberTrack(row) }
            }
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.width(72.dp))
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween) {
                    DayTimelineHours.forEach { Text("%02d".format(it), fontSize = 10.sp, color = colors.muted) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                LegendSwatch(colors.okBg, "Free")
                LegendSwatch(colors.busyFill, "Busy")
                LegendSwatch(colors.accent, "Everyone free")
                if (everyone.any { !it.everyone }) LegendSwatch(colors.accentSoft, "Most of us free")
            }
        }
    }
}

@Composable
private fun LegendSwatch(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(12.dp, 10.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Text(label, style = AvailablissTheme.type.micro.copy(fontWeight = FontWeight.Normal), color = AvailablissTheme.colors.muted)
    }
}

/** Solid fill for someone's busy time: clearly darker than the mint free track. */
private val dev.joeherbert.availabliss.designsystem.theme.AvailablissColors.busyFill
    get() = ink.copy(alpha = 0.32f)

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
                .then(if (row.pending) Modifier.dashedBorder(colors.line3, shape) else Modifier.clip(shape).background(colors.okBg))
                .semantics { contentDescription = row.accessibilityLabel },
        ) {
            val w = maxWidth
            row.bars.forEach { bar ->
                Box(
                    Modifier
                        .offset(x = w * bar.start)
                        .width((w * (bar.end - bar.start)).coerceAtLeast(2.dp))
                        .fillMaxHeight()
                        .clip(shape)
                        .background(colors.busyFill),
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
                    TimelineRowUi("Sam (you)", false, listOf(TimelineBarUi(f(9.0), f(17.5)))),
                    TimelineRowUi("Priya", false, listOf(TimelineBarUi(f(8.5), f(18.0)))),
                    TimelineRowUi("Leo", true, emptyList()),
                ),
            )
        }
    }
}
