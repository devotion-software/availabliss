package dev.joeherbert.availabliss.feature.mytime

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.joeherbert.availabliss.core.common.DateTimes
import dev.joeherbert.availabliss.designsystem.component.glass
import dev.joeherbert.availabliss.designsystem.component.swipeToStep
import dev.joeherbert.availabliss.designsystem.icon.AvIcons
import dev.joeherbert.availabliss.designsystem.theme.AvWeights
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import dev.joeherbert.availabliss.designsystem.theme.Shapes
import dev.joeherbert.availabliss.designsystem.theme.Spacing
import java.time.LocalDate

/**
 * The week view, opened from a day in the month or agenda: a back button, the week strip (swipe
 * for the previous / next week), the selected day's summary and all-day items, and its timeline
 * filling the rest (swipe for the previous / next day; tap free time to block it).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WeekView(state: MyTimeUiState, day: LocalDate, actions: MyTimeActions) {
    val colors = AvailablissTheme.colors
    val type = AvailablissTheme.type
    val weekStart = DateTimes.startOfWeek(day)
    val row = state.rows[day]
    val allDay = state.layout.allDay[day].orEmpty()
    val placed = state.layout.timed[day].orEmpty()
    val bottom = tabBarClearance()

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = Spacing.m, end = Spacing.screen, top = Spacing.screen, bottom = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            BackChip(if (state.mode == MyTimeMode.Month) "Month" else "Agenda", actions.onCloseDay)
            Text(
                DateTimes.weekRange(weekStart, weekStart.plusDays(6)),
                style = type.titleSmall,
                color = colors.ink,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            BlockChip(onClick = { actions.onBlockOn(day) })
        }

        // The strip slides a week at a time.
        AnimatedContent(
            targetState = weekStart,
            transitionSpec = {
                val sign = if (targetState.isAfter(initialState)) 1 else -1
                (slideInHorizontally { width -> sign * width / 3 } + fadeIn()) togetherWith
                    (slideOutHorizontally { width -> -sign * width / 3 } + fadeOut())
            },
            label = "week-strip",
            modifier = Modifier
                .padding(horizontal = Spacing.screen)
                .swipeToStep(onPrevious = { actions.onStepWeek(-1) }, onNext = { actions.onStepWeek(1) }),
        ) { start ->
            WeekStrip(start, selected = day, state = state, onSelect = actions.onOpenDay)
        }

        Column(
            Modifier.fillMaxWidth().padding(start = Spacing.screen, end = Spacing.screen, top = Spacing.l, bottom = Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Text(
                weekSummaryLine(row, day == state.today),
                style = type.bodySmallStrong,
                color = colors.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            if (allDay.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    allDay.forEach { AllDayChip(it, day, onClick = actions.onIntervalClick) }
                }
            }
        }

        // The timeline fills the rest of the screen, above the floating tab bar.
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).padding(horizontal = Spacing.l)) {
            val height = (maxHeight - bottom + Spacing.s).coerceAtLeast(240.dp)
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
                modifier = Modifier
                    .glass(Shapes.panel, shadow = false)
                    .swipeToStep(onPrevious = { actions.onStepDay(-1) }, onNext = { actions.onStepDay(1) }),
            ) { (pageDay, pagePlaced) ->
                DayTimeline(
                    day = pageDay,
                    placed = pagePlaced,
                    isToday = pageDay == state.today,
                    scrollHeight = height,
                    onEmptyTap = actions.onEmptyTap,
                    onIntervalClick = actions.onIntervalClick,
                    modifier = Modifier.padding(end = Spacing.s),
                )
            }
        }
    }
}

/** "‹ Month" / "‹ Agenda": back to where the week view was opened from. */
@Composable
private fun BackChip(label: String, onClick: () -> Unit) {
    val colors = AvailablissTheme.colors
    Row(
        Modifier
            .height(40.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = "Back to ${label.lowercase()}"
                role = Role.Button
                onClick { onClick(); true }
            }
            .padding(start = Spacing.xs, end = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(AvIcons.Back, null, tint = colors.accentInk, modifier = Modifier.size(18.dp))
        Text(label, style = AvailablissTheme.type.bodySmallStrong, color = colors.accentInk)
    }
}

/** "+ Block": the block sheet on the selected day. */
@Composable
private fun BlockChip(onClick: () -> Unit) {
    val colors = AvailablissTheme.colors
    Row(
        Modifier
            .height(36.dp)
            .clip(CircleShape)
            .background(colors.accentSoft)
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = "Block time on this day"
                role = Role.Button
                onClick { onClick(); true }
            }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(AvIcons.Plus, null, tint = colors.accentInk, modifier = Modifier.size(14.dp))
        Text("Block", style = AvailablissTheme.type.label, color = colors.accentInk)
    }
}

/** Mon … Sun of the week starting [start]: tap a day (today or later) to select it. */
@Composable
private fun WeekStrip(start: LocalDate, selected: LocalDate, state: MyTimeUiState, onSelect: (LocalDate) -> Unit) {
    Row(
        Modifier.fillMaxWidth().glass(Shapes.panel).padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for (i in 0L..6L) {
            val date = start.plusDays(i)
            StripDay(
                date = date,
                row = state.rows[date],
                selected = date == selected,
                today = date == state.today,
                enabled = !date.isBefore(state.today) && !date.isAfter(state.lastDay),
                onClick = { onSelect(date) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StripDay(date: LocalDate, row: DayRowUi?, selected: Boolean, today: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AvailablissTheme.colors
    val label = listOfNotNull(if (today) "Today" else null, DateTimes.longDay(date), row?.summary?.label?.replaceFirstChar { it.lowercase() })
        .joinToString(", ")
    Column(
        modifier
            .alpha(if (enabled) 1f else 0.4f)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) colors.solid else Color.Transparent)
            .then(if (enabled) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .clearAndSetSemantics {
                contentDescription = label
                this.selected = selected
                if (enabled) onClick { onClick(); true }
            }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            DateTimes.shortWeekday(DateTimes.apiWeekday(date)),
            style = AvailablissTheme.type.micro,
            color = if (selected) colors.onSolid.copy(alpha = 0.75f) else colors.muted,
        )
        Text(
            date.dayOfMonth.toString(),
            fontSize = 17.sp,
            lineHeight = 18.sp,
            fontWeight = AvWeights.Display700,
            color = when {
                selected -> colors.onSolid
                today -> colors.accentInk
                else -> colors.ink
            },
        )
        Row(Modifier.height(5.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            if (row?.hasBlock == true) Dot(colors.accent)
            if (row?.hasCalendarBusy == true) Dot(if (selected) colors.onSolid.copy(alpha = 0.55f) else colors.muted.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun Dot(color: Color) {
    Column(Modifier.size(5.dp).clip(CircleShape).background(color)) {}
}
