package dev.joeherbert.availabliss.feature.mytime

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.joeherbert.availabliss.designsystem.component.dashedBorder
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.joeherbert.availabliss.core.common.DateTimes
import dev.joeherbert.availabliss.core.network.model.BusyInterval
import dev.joeherbert.availabliss.core.network.model.BusySource
import dev.joeherbert.availabliss.designsystem.component.stripes
import dev.joeherbert.availabliss.designsystem.theme.AvWeights
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import dev.joeherbert.availabliss.designsystem.theme.DisplayFontFamily
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate

/** Swallows what the hour scroller leaves over, so reaching its top or bottom never scrolls the page. */
private val ContainScroll = object : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset = available.copy(x = 0f)

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available.copy(x = 0f)
}

private val HourHeight = 40.dp
private val GutterWidth = 44.dp
private val DayHeight = HourHeight * 24

/** Boxes narrower than this stack their start and end times. */
private val CompactBoxWidth = 72.dp

/** Boxes narrower than this (e.g. overlapping lanes) are just colour, no text. */
private val TextlessBoxWidth = 28.dp
private val Tabular = TextStyle(fontFeatureSettings = "tnum")

/**
 * One day's busy time on a tall 24-hour timeline (the "My time" week view). Tapping
 * empty time calls [onEmptyTap] with the hour tapped; [onIntervalClick] makes busy
 * boxes tappable (null = display only). Opens scrolled so [initialHour] is at the top.
 */
@Composable
fun DayTimeline(
    day: LocalDate,
    placed: List<PlacedInterval>,
    isToday: Boolean,
    scrollHeight: Dp,
    onEmptyTap: (LocalDate, MinuteRange) -> Unit,
    modifier: Modifier = Modifier,
    onIntervalClick: ((BusyInterval) -> Unit)? = null,
    initialHour: Int = 8,
) {
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    // Back to the start of the working day whenever the day changes.
    LaunchedEffect(day) {
        scroll.scrollTo(with(density) { (HourHeight * initialHour).roundToPx() })
    }

    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            now = Instant.now()
        }
    }

    Box(
        modifier
            .fillMaxWidth()
            .height(scrollHeight)
            // Like web's overscroll-behavior: contain. Reaching the top doesn't drag the sheet away.
            .nestedScroll(ContainScroll)
            .verticalScroll(scroll),
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp).height(DayHeight)) {
            HourGutter()
            val nowMinutes = if (isToday) minutesIntoDay(now, day, DateTimes.zone) * DAY_MINUTES / dayLength(day, DateTimes.zone) else null
            DayColumn(
                day = day,
                placed = placed,
                isToday = isToday,
                nowMinutes = nowMinutes?.takeIf { it in 0f..DAY_MINUTES.toFloat() },
                onEmptyTap = onEmptyTap,
                onIntervalClick = onIntervalClick,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** An all-day item as a chip above the timeline. */
@Composable
fun AllDayChip(interval: BusyInterval, day: LocalDate, modifier: Modifier = Modifier, onClick: ((BusyInterval) -> Unit)? = null) {
    val colors = AvailablissTheme.colors
    val shape = RoundedCornerShape(10.dp)
    Text(
        interval.displayLabel + " · all day",
        style = AvailablissTheme.type.label,
        color = if (interval.source == BusySource.Manual) colors.accentInk else colors.ink,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .defaultMinSize(minHeight = 36.dp)
            .intervalStyle(interval.source, shape, interval.isIgnored)
            .tapOnly(onClick?.let { { it(interval) } })
            .padding(horizontal = 12.dp, vertical = 9.dp)
            .clearAndSetSemantics {
                contentDescription = "${interval.describe()}, all day ${DateTimes.longDay(day)}"
                onClick?.let { open -> onClick(label = interval.clickLabel) { open(interval); true } }
            },
    )
}

@Composable
private fun HourGutter() {
    val colors = AvailablissTheme.colors
    Box(Modifier.width(GutterWidth).fillMaxHeight().clearAndSetSemantics { }) {
        for (hour in 1 until 24) {
            Text(
                "${hour.toString().padStart(2, '0')}:00",
                fontSize = 11.sp,
                lineHeight = 12.sp,
                style = Tabular,
                color = colors.muted,
                textAlign = TextAlign.End,
                // Centre the label on its hour line.
                modifier = Modifier
                    .offset(y = HourHeight * hour - 6.dp)
                    .width(GutterWidth - 6.dp),
            )
        }
    }
}

@Composable
private fun DayColumn(
    day: LocalDate,
    placed: List<PlacedInterval>,
    isToday: Boolean,
    nowMinutes: Float?,
    onEmptyTap: (LocalDate, MinuteRange) -> Unit,
    onIntervalClick: ((BusyInterval) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = AvailablissTheme.colors
    val dayName = DateTimes.longDay(day)
    val onTap by rememberUpdatedState(onEmptyTap)
    BoxWithConstraints(
        modifier
            .fillMaxHeight()
            .drawBehind {
                if (isToday) drawRect(colors.accentSoft.copy(alpha = colors.accentSoft.alpha * 0.35f))
                val hour = size.height / 24
                val half = colors.line.copy(alpha = colors.line.alpha * 0.5f)
                for (h in 0 until 24) {
                    drawRect(colors.line, Offset(0f, h * hour), Size(size.width, 1.dp.toPx()))
                    drawRect(half, Offset(0f, h * hour + hour / 2), Size(size.width, 1.dp.toPx()))
                }
                drawRect(colors.line, Offset.Zero, Size(1.dp.toPx(), size.height))
            }
            .drawWithContent {
                drawContent()
                if (nowMinutes != null) {
                    val y = size.height * nowMinutes / DAY_MINUTES
                    drawRect(colors.warn, Offset(0f, y - 1.dp.toPx()), Size(size.width, 2.dp.toPx()))
                    drawCircle(colors.warn, radius = 4.dp.toPx(), center = Offset(0f, y))
                }
            }
            .pointerInput(day) {
                detectTapGestures { offset ->
                    onTap(day, tapRange(offset.y / size.height * DAY_MINUTES))
                }
            }
            .semantics {
                contentDescription = dayName
                onClick(label = "Block a time on $dayName") {
                    onTap(day, tapRange(9f * 60))
                    true
                }
            },
    ) {
        val laneWidth = maxWidth
        placed.forEach { p ->
            val boxWidth = laneWidth / p.lanes - 4.dp
            IntervalBox(
                p = p,
                compact = boxWidth < CompactBoxWidth,
                showText = boxWidth >= TextlessBoxWidth,
                modifier = Modifier
                    .offset(x = laneWidth * p.lane / p.lanes + 2.dp, y = DayHeight * (p.from / DAY_MINUTES))
                    .width(boxWidth)
                    .height((DayHeight * (p.minutes / DAY_MINUTES)).coerceAtLeast(14.dp)),
                onClick = onIntervalClick?.let { { it(p.interval) } },
            )
        }
    }
}

/** One busy box. Taps on it never fall through to the empty-time tap underneath. */
@Composable
private fun IntervalBox(p: PlacedInterval, compact: Boolean, showText: Boolean, modifier: Modifier, onClick: (() -> Unit)?) {
    val colors = AvailablissTheme.colors
    val manual = p.interval.source == BusySource.Manual
    val ink = if (manual) colors.accentInk else colors.ink
    val round = 8.dp
    val square = 2.dp
    val shape = RoundedCornerShape(
        topStart = if (p.continuesBefore) square else round,
        topEnd = if (p.continuesBefore) square else round,
        bottomStart = if (p.continuesAfter) square else round,
        bottomEnd = if (p.continuesAfter) square else round,
    )
    Column(
        modifier
            .intervalStyle(p.interval.source, shape, p.interval.isIgnored)
            .tapOnly(onClick)
            .padding(start = if (manual) 5.dp else 6.dp, end = 4.dp, top = 3.dp, bottom = 3.dp)
            .clearAndSetSemantics {
                contentDescription = "${p.interval.describe()}, ${DateTimes.longDay(p.day)} ${clock(p.from)} to ${clock(p.to)}"
                if (onClick != null) onClick(label = p.interval.clickLabel) { onClick(); true }
            },
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        // Too narrow to read anything: the box's colour (and its accessibility label) say it all.
        if (!showText) return@Column
        Text(
            p.interval.displayLabel,
            fontSize = 13.sp,
            lineHeight = 15.sp,
            fontWeight = AvWeights.Display650,
            color = ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (p.minutes >= 60) {
            Text(
                // Too narrow for "19:00 – 21:30" on one line: stack the start and end instead.
                if (compact) "${clock(p.from)}\n${clock(p.to)}" else "${clock(p.from)} – ${clock(p.to)}",
                fontSize = 12.sp,
                lineHeight = 14.sp,
                style = Tabular,
                color = ink.copy(alpha = ink.alpha * 0.8f),
                maxLines = if (compact && p.minutes >= 90) 2 else 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Blocked by you: accent stripes over surface. From a calendar: busy fill with a busy-line left edge. */
@Composable
private fun Modifier.intervalStyle(source: BusySource, shape: RoundedCornerShape, ignored: Boolean = false): Modifier {
    val colors = AvailablissTheme.colors
    return if (ignored) {
        // Marked not busy: a faded dashed outline.
        alpha(0.6f).dashedBorder(colors.busyLine, shape)
    } else if (source == BusySource.Manual) {
        clip(shape)
            .background(colors.surface)
            .stripes(colors.accentSoft, 6.dp, 12.dp)
            .border(1.5.dp, colors.accent, shape)
    } else {
        clip(shape)
            .background(colors.busyBg)
            .drawBehind { drawRect(colors.busyLine, size = Size(3.dp.toPx(), size.height)) }
            .border(1.dp, colors.busyLine, shape)
    }
}

/**
 * Handles taps on a busy box or chip. Always consumes them (even with no [onClick]) so a tap on
 * busy time never falls through to "block this time" underneath.
 */
@Composable
private fun Modifier.tapOnly(onClick: (() -> Unit)?): Modifier {
    val current by rememberUpdatedState(onClick)
    return pointerInput(Unit) { detectTapGestures { current?.invoke() } }
}

/** Accessibility action label: edit your own block, or see what calendar busy time is. */
private val BusyInterval.clickLabel: String
    get() = if (source == BusySource.Calendar) "Details" else "Edit $displayLabel"

private val BusyInterval.displayLabel: String get() = label?.takeIf { it.isNotBlank() } ?: "Busy"

private fun BusyInterval.describe(): String = when (source) {
    BusySource.Calendar -> "${if (isIgnored) "Marked not busy, from" else "Busy from"} ${label?.takeIf { it.isNotBlank() } ?: "a calendar"}"
    else -> "Blocked by you" + (label?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: "")
}
