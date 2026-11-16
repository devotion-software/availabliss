package dev.joeherbert.availabliss.feature.mytime

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.joeherbert.availabliss.core.common.DateTimes
import dev.joeherbert.availabliss.designsystem.component.LoadingIndicator
import dev.joeherbert.availabliss.designsystem.component.availability.todayPill
import dev.joeherbert.availabliss.designsystem.component.dashedBorder
import dev.joeherbert.availabliss.designsystem.theme.AvWeights
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import dev.joeherbert.availabliss.designsystem.theme.Shapes
import dev.joeherbert.availabliss.designsystem.theme.Spacing
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

private val Tabular = TextStyle(fontFeatureSettings = "tnum")

/** What the agenda shows, in order. */
private sealed interface Entry {
    val key: String

    data object Top : Entry {
        override val key = "top"
    }

    data object Loading : Entry {
        override val key = "loading"
    }

    data class Heading(val text: String, override val key: String) : Entry

    data class Day(val row: DayRowUi) : Entry {
        override val key get() = "d${row.date}"
    }

    data class Recurring(val block: RecurringBlockUi) : Entry {
        override val key get() = "r${block.id}"
    }

    data object LoadingMore : Entry {
        override val key = "more"
    }
}

/** Week sections of days, with "Every week" after the first one. */
private fun entries(state: MyTimeUiState): List<Entry> = buildList {
    add(Entry.Top)
    if (state.loading && state.days.isEmpty()) {
        add(Entry.Loading)
        return@buildList
    }
    val weeks = state.days.groupBy { DateTimes.startOfWeek(it.date) }.entries.toList()
    weeks.forEachIndexed { i, (weekStart, days) ->
        add(Entry.Heading(weekHeading(weekStart, state.today), "w$weekStart"))
        days.forEach { add(Entry.Day(it)) }
        if (i == 0 && state.recurring.isNotEmpty()) {
            add(Entry.Heading("Every week", "every-week"))
            state.recurring.forEach { add(Entry.Recurring(it)) }
        }
    }
    if (state.loadingMore) add(Entry.LoadingMore)
}

/** The agenda: days from today with what's busy on each and the longest free stretch. */
@Composable
internal fun AgendaView(state: MyTimeUiState, list: LazyListState, actions: MyTimeActions, top: @Composable () -> Unit) {
    val entries = remember(state.days, state.recurring, state.loading, state.loadingMore, state.today) { entries(state) }

    // Load further weeks as the end of the list comes into view.
    val nearEnd by rememberUpdatedState(actions.onNearEnd)
    LaunchedEffect(list) {
        snapshotFlow {
            val info = list.layoutInfo
            info.totalItemsCount > 0 && (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 6
        }
            .distinctUntilChanged()
            .filter { it }
            .collect { nearEnd() }
    }

    LazyColumn(
        state = list,
        modifier = Modifier.fillMaxSize(),
        // Clear the floating tab bar.
        contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = Spacing.xxs, bottom = tabBarClearance()),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        items(entries, key = { it.key }, contentType = { it::class }) { entry ->
            when (entry) {
                Entry.Top -> top()
                Entry.Loading -> LoadingIndicator()
                is Entry.Heading -> SectionHeading(entry.text)
                is Entry.Day -> {
                    val date = entry.row.date
                    val allDay = state.layout.allDay[date].orEmpty()
                    val timed = state.layout.timed[date].orEmpty()
                    val items = remember(allDay, timed, state.repeats) { agendaItems(allDay, timed, state.repeats) }
                    AgendaDay(entry.row, items, isToday = date == state.today, actions)
                }
                is Entry.Recurring -> RecurringBlockRow(
                    entry.block,
                    onClick = { actions.onOpenRecurring(entry.block.id) },
                    onDelete = { actions.onDeleteRecurring(entry.block.id) },
                )
                Entry.LoadingMore -> LoadingIndicator()
            }
        }
    }
}

/** "Mon 16 · Free from 17:30", the day's busy items, then its longest free stretch. */
@Composable
private fun AgendaDay(row: DayRowUi, items: List<AgendaItem>, isToday: Boolean, actions: MyTimeActions) {
    val colors = AvailablissTheme.colors
    val type = AvailablissTheme.type
    val open = { actions.onOpenDay(row.date) }
    Column(Modifier.fillMaxWidth().padding(bottom = Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 36.dp)
                .clip(Shapes.controlSmall)
                .clickable(role = Role.Button, onClick = open)
                .clearAndSetSemantics {
                    contentDescription = row.accessibility
                    role = Role.Button
                    onClick(label = "See the day") { open(); true }
                }
                .padding(horizontal = Spacing.xxs, vertical = Spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(DateTimes.shortWeekday(DateTimes.apiWeekday(row.date)), style = type.bodySmallStrong, color = colors.ink)
            Spacer(Modifier.width(if (isToday) 11.dp else 5.dp))
            Text(
                row.date.dayOfMonth.toString(),
                fontSize = 15.sp,
                lineHeight = 15.sp,
                fontWeight = AvWeights.Display650,
                color = if (isToday) colors.onAccent else colors.ink,
                modifier = Modifier.todayPill(isToday, colors.accent),
            )
            if (isToday) {
                Spacer(Modifier.width(14.dp))
                Text("Today", style = type.label, color = colors.accentInk)
            }
            Spacer(Modifier.weight(1f).width(Spacing.s))
            Text(row.summary.label, style = type.labelRegular, color = colors.muted, maxLines = 1)
        }
        items.forEach { item -> AgendaItemRow(item, onClick = { actions.onIntervalClick(item.interval) }) }
        if (row.summary != DaySummary.BusyAllDay) {
            FreeRow(row.summary.label, onClick = { actions.onBlockFree(row.date, row.summary) })
        }
    }
}

/** One busy item: times, title, where it's from. Your blocks in the accent tint. */
@Composable
private fun AgendaItemRow(item: AgendaItem, onClick: () -> Unit) {
    val colors = AvailablissTheme.colors
    val type = AvailablissTheme.type
    val shape = Shapes.controlSmall
    val ink = if (item.manual) colors.accentInk else colors.ink
    val spoken = if (item.allDay) "all day" else "${clock(item.from.toFloat())} to ${clock(item.to.toFloat())}"
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(if (item.manual) colors.accentSoft else colors.surface)
            .border(1.dp, if (item.manual) Color.Transparent else colors.glassBorder, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = "${item.title}, $spoken, ${item.tag.label}"
                role = Role.Button
                onClick(label = if (item.manual) "Edit" else "Details") { onClick(); true }
            }
            .padding(horizontal = Spacing.l, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        Column(Modifier.width(40.dp)) {
            if (item.allDay) {
                Text("All day", style = type.caption, color = ink)
            } else {
                Text(clock(item.from.toFloat()), style = type.caption.merge(Tabular), color = ink)
                Text(clock(item.to.toFloat()), style = type.caption.merge(Tabular).copy(fontWeight = FontWeight.Normal), color = colors.muted)
            }
        }
        Text(item.title, style = type.bodySmallStrong, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(item.tag.label, style = type.micro.copy(fontWeight = FontWeight.Medium), color = colors.muted, maxLines = 1)
    }
}

/** The day's longest free stretch as a dashed row; tap to block it. */
@Composable
private fun FreeRow(label: String, onClick: () -> Unit) {
    val colors = AvailablissTheme.colors
    val type = AvailablissTheme.type
    val shape = Shapes.controlSmall
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(shape)
            .dashedBorder(colors.line3, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = label
                role = Role.Button
                onClick(label = "Block this time") { onClick(); true }
            }
            .padding(horizontal = Spacing.l, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = type.labelRegular, color = colors.muted, modifier = Modifier.weight(1f))
        Text("+ Block", style = type.label, color = colors.accentInk)
    }
}
