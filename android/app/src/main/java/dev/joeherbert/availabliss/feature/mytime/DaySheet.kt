package dev.joeherbert.availabliss.feature.mytime

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.joeherbert.availabliss.core.common.DateTimes
import dev.joeherbert.availabliss.core.network.model.BusyInterval
import dev.joeherbert.availabliss.designsystem.component.AvBottomSheet
import dev.joeherbert.availabliss.designsystem.component.PlainIconButton
import dev.joeherbert.availabliss.designsystem.component.SheetHeader
import dev.joeherbert.availabliss.designsystem.component.swipeToStep
import dev.joeherbert.availabliss.designsystem.icon.AvIcons
import dev.joeherbert.availabliss.designsystem.theme.AvailablissPreviewTheme
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import java.time.LocalDate

/**
 * One day in full: the date as the title, previous / next arrows, all-day items as chips,
 * and a tall timeline. Tap empty time to block that hour; tap busy time to see or edit it.
 */
@Composable
fun DaySheet(
    day: LocalDate,
    today: LocalDate,
    row: DayRowUi?,
    allDay: List<BusyInterval>,
    placed: List<PlacedInterval>,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onDismiss: () -> Unit,
    onEmptyTap: (LocalDate, MinuteRange) -> Unit,
    onIntervalClick: (BusyInterval) -> Unit,
) {
    // Most of the screen: the sheet opens tall, with the timeline scrolling inside it.
    val timelineHeight = (LocalConfiguration.current.screenHeightDp.dp * 0.62f).coerceAtLeast(300.dp)
    AvBottomSheet(title = DateTimes.longDay(day), onDismissRequest = onDismiss, scrollable = false) {
        DaySheetContent(day, today, row, allDay, placed, timelineHeight, onPrevious, onNext, onEmptyTap, onIntervalClick)
    }
}

/** The sheet's body below its title (split out for previews). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DaySheetContent(
    day: LocalDate,
    today: LocalDate,
    row: DayRowUi?,
    allDay: List<BusyInterval>,
    placed: List<PlacedInterval>,
    timelineHeight: Dp,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onEmptyTap: (LocalDate, MinuteRange) -> Unit,
    onIntervalClick: (BusyInterval) -> Unit,
) {
    val colors = AvailablissTheme.colors
    val type = AvailablissTheme.type
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlainIconButton(
                AvIcons.Back,
                "Previous day",
                onPrevious,
                size = 40.dp,
                iconSize = 16.dp,
                background = colors.line,
                enabled = day.isAfter(today),
            )
            Text(
                when {
                    row == null -> "Loading…"
                    day == today -> "Today · ${row.summary.label}"
                    else -> row.summary.label
                },
                style = type.bodySmallStrong,
                color = colors.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
            )
            PlainIconButton(AvIcons.Forward, "Next day", onNext, size = 40.dp, iconSize = 16.dp, background = colors.line)
        }
        if (allDay.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                allDay.forEach { AllDayChip(it, day, onClick = onIntervalClick) }
            }
        }
        // Swipe sideways for the previous / next day; each day slides in from the side it's coming from.
        AnimatedContent(
            // The day with its own pieces, so the outgoing day keeps them while it slides out.
            targetState = day to placed,
            contentKey = { it.first },
            transitionSpec = {
                val sign = if (targetState.first.isAfter(initialState.first)) 1 else -1
                (slideInHorizontally { width -> sign * width / 3 } + fadeIn()) togetherWith
                    (slideOutHorizontally { width -> -sign * width / 3 } + fadeOut())
            },
            label = "day-page",
            modifier = Modifier.swipeToStep(onPrevious = { if (day.isAfter(today)) onPrevious() }, onNext = onNext),
        ) { (pageDay, pagePlaced) ->
            DayTimeline(
                day = pageDay,
                placed = pagePlaced,
                isToday = pageDay == today,
                scrollHeight = timelineHeight,
                onEmptyTap = onEmptyTap,
                onIntervalClick = onIntervalClick,
            )
        }
        Text("Tap a free time to block it", style = type.caption, color = colors.muted, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

@Preview(name = "Day sheet", widthDp = 390, heightDp = 760)
@Composable
private fun DaySheetPreview() {
    val state = MyTimeSamples.state()
    val day = state.today.plusDays(1)
    AvailablissPreviewTheme {
        Column(Modifier.background(AvailablissTheme.colors.bg).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SheetHeader(DateTimes.longDay(day), onClose = {})
            DaySheetContent(
                day = day,
                today = state.today,
                row = state.days.firstOrNull { it.date == day },
                allDay = state.layout.allDay[day].orEmpty(),
                placed = state.layout.timed[day].orEmpty(),
                timelineHeight = 520.dp,
                onPrevious = {},
                onNext = {},
                onEmptyTap = { _, _ -> },
                onIntervalClick = {},
            )
        }
    }
}

@Preview(name = "Day sheet · all day", widthDp = 390, heightDp = 700)
@Composable
private fun DaySheetAllDayPreview() {
    val state = MyTimeSamples.state()
    val day = state.today.plusDays(3)
    AvailablissPreviewTheme {
        Column(Modifier.background(AvailablissTheme.colors.bg).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SheetHeader(DateTimes.longDay(day), onClose = {})
            DaySheetContent(
                day = day,
                today = state.today,
                row = state.days.firstOrNull { it.date == day },
                allDay = state.layout.allDay[day].orEmpty(),
                placed = state.layout.timed[day].orEmpty(),
                timelineHeight = 460.dp,
                onPrevious = {},
                onNext = {},
                onEmptyTap = { _, _ -> },
                onIntervalClick = {},
            )
        }
    }
}
