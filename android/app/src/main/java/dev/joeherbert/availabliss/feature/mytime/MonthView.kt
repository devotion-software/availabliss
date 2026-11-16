package dev.joeherbert.availabliss.feature.mytime

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.joeherbert.availabliss.core.common.DateTimes
import dev.joeherbert.availabliss.designsystem.component.PlainIconButton
import dev.joeherbert.availabliss.designsystem.component.availability.todayPill
import dev.joeherbert.availabliss.designsystem.component.swipeToStep
import dev.joeherbert.availabliss.designsystem.icon.AvIcons
import dev.joeherbert.availabliss.designsystem.theme.AvWeights
import dev.joeherbert.availabliss.designsystem.theme.AvailablissColors
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import dev.joeherbert.availabliss.designsystem.theme.Spacing
import java.time.LocalDate
import java.time.YearMonth

private val CellHeight = 46.dp
private val CellShape = RoundedCornerShape(12.dp)

/** The month heat map: how free each day is, a tap opening the week view; "Every week" below. */
@Composable
internal fun MonthView(state: MyTimeUiState, scroll: ScrollState, actions: MyTimeActions, top: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(start = Spacing.screen, end = Spacing.screen, top = Spacing.xxs, bottom = tabBarClearance()),
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        top()
        // Which way the next month slides in: forward (later) comes from the right.
        var forward by remember { mutableStateOf(true) }
        val previous = { forward = false; actions.onPreviousMonth() }
        val next = { forward = true; actions.onNextMonth() }
        val thisMonth = YearMonth.from(state.today)
        MonthHeading(
            month = state.month,
            canGoBack = state.month > state.months.start,
            canGoOn = state.month < state.months.endInclusive,
            awayFromToday = state.month != thisMonth,
            onPrevious = previous,
            onNext = next,
            onToday = { forward = thisMonth > state.month; actions.onThisMonth() },
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            WeekdayHeader()
            AnimatedContent(
                targetState = state.month,
                transitionSpec = {
                    val sign = if (forward) 1 else -1
                    (slideInHorizontally { width -> sign * width / 3 } + fadeIn()) togetherWith
                        (slideOutHorizontally { width -> -sign * width / 3 } + fadeOut())
                },
                label = "month-page",
                // Swipe sideways for the previous / next month.
                modifier = Modifier.swipeToStep(onPrevious = previous, onNext = next),
            ) { month ->
                val weeks = remember(month, state.rows, state.today) { monthWeeks(month, state.rows, state.today) }
                HeatGrid(weeks, onSelect = actions.onOpenDay)
            }
            HeatLegend(Modifier.padding(top = Spacing.xxs))
        }
        if (state.recurring.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                SectionHeading("Every week")
                state.recurring.forEach { block ->
                    RecurringBlockRow(block, onClick = { actions.onOpenRecurring(block.id) }, onDelete = { actions.onDeleteRecurring(block.id) })
                }
            }
        }
    }
}

/** "‹ November 2026 ›" and, away from this month, the "Today" pill (as in the group calendar). */
@Composable
private fun MonthHeading(
    month: YearMonth,
    canGoBack: Boolean,
    canGoOn: Boolean,
    awayFromToday: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    val colors = AvailablissTheme.colors
    Row(Modifier.fillMaxWidth().padding(top = Spacing.s), verticalAlignment = Alignment.CenterVertically) {
        PlainIconButton(AvIcons.Back, "Previous month", onPrevious, size = 32.dp, iconSize = 16.dp, enabled = canGoBack)
        Text(
            DateTimes.monthYear(month.atDay(1)),
            style = AvailablissTheme.type.titleSmall,
            color = colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false).semantics { heading() },
        )
        PlainIconButton(AvIcons.Forward, "Next month", onNext, size = 32.dp, iconSize = 16.dp, enabled = canGoOn)
        if (awayFromToday) TodayChip("Go back to this month", onToday)
    }
}

/** The group calendar's "Today" pill. */
@Composable
internal fun TodayChip(clickLabel: String, onClick: () -> Unit) {
    val colors = AvailablissTheme.colors
    Text(
        "Today",
        style = AvailablissTheme.type.label,
        color = colors.accentInk,
        maxLines = 1,
        modifier = Modifier
            .padding(start = 2.dp)
            .clip(CircleShape)
            .background(colors.accentSoft)
            .clickable(role = Role.Button, onClickLabel = clickLabel, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
private fun WeekdayHeader() {
    val colors = AvailablissTheme.colors
    Row(Modifier.fillMaxWidth().clearAndSetSemantics { }, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (d in 0..6) {
            Text(
                DateTimes.shortWeekday(d),
                style = AvailablissTheme.type.micro,
                color = colors.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun HeatGrid(weeks: List<List<MonthDayUi?>>, onSelect: (LocalDate) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        weeks.forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { cell ->
                    if (cell == null) Spacer(Modifier.weight(1f).height(CellHeight))
                    else HeatCell(cell, onClick = { onSelect(cell.date) }, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** A level's fill over the surface (null = plain surface with a border) and its number colour. */
private fun levelColors(level: Int, colors: AvailablissColors): Pair<Color?, Color> = when (level) {
    3 -> colors.accent to colors.onAccent
    2 -> colors.accent.copy(alpha = colors.accent.alpha * 0.5f) to colors.onAccent
    1 -> colors.accent.copy(alpha = colors.accent.alpha * 0.2f) to colors.ink
    else -> null to colors.muted
}

@Composable
private fun HeatCell(cell: MonthDayUi, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AvailablissTheme.colors
    val level = if (cell.past) 0 else cell.level
    val (fill, fg) = levelColors(level, colors)
    // On a filled cell, today's pill is inverted.
    val strong = level >= 2
    Column(
        modifier
            .height(CellHeight)
            .alpha(if (cell.past) 0.4f else 1f)
            .clip(CellShape)
            .background(colors.surface)
            .then(if (fill != null) Modifier.background(fill) else Modifier.border(1.dp, colors.glassBorder, CellShape))
            .then(if (cell.past) Modifier else Modifier.clickable(role = Role.Button, onClick = onClick))
            .clearAndSetSemantics {
                contentDescription = cell.accessibility
                if (!cell.past) {
                    role = Role.Button
                    onClick(label = "See the day") { onClick(); true }
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            cell.date.dayOfMonth.toString(),
            fontSize = 15.sp,
            lineHeight = 15.sp,
            fontWeight = AvWeights.Display650,
            color = when {
                !cell.today -> fg
                strong -> colors.accentInk
                else -> colors.onAccent
            },
            modifier = Modifier.todayPill(cell.today, if (strong) colors.onAccent else colors.accent),
        )
        // Your own block that day.
        Box(
            Modifier
                .padding(top = 5.dp)
                .size(4.dp)
                .clip(CircleShape)
                .background(if (!cell.hasBlock) Color.Transparent else if (strong) colors.onAccent else colors.accentInk),
        )
    }
}

/** "Less free" → the four fills → "More free". */
@Composable
private fun HeatLegend(modifier: Modifier = Modifier) {
    val colors = AvailablissTheme.colors
    val style = AvailablissTheme.type.micro.copy(fontWeight = FontWeight.Normal)
    val shape = RoundedCornerShape(3.dp)
    Row(
        modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = "Colour shows how free each day is, from less free to more free" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
    ) {
        Text("Less free", style = style, color = colors.muted, modifier = Modifier.padding(end = 2.dp))
        for (level in 0..3) {
            val (fill, _) = levelColors(level, colors)
            Box(
                Modifier
                    .size(12.dp)
                    .clip(shape)
                    .background(colors.surface)
                    .then(if (fill != null) Modifier.background(fill) else Modifier.border(1.dp, colors.glassBorder, shape)),
            )
        }
        Text("More free", style = style, color = colors.muted, modifier = Modifier.padding(start = 2.dp))
    }
}
