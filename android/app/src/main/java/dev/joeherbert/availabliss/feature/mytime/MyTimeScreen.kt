package dev.joeherbert.availabliss.feature.mytime

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.joeherbert.availabliss.core.common.DateTimes
import dev.joeherbert.availabliss.core.network.model.BusyInterval
import dev.joeherbert.availabliss.core.network.model.BusySource
import dev.joeherbert.availabliss.designsystem.component.AvDatePickerDialog
import dev.joeherbert.availabliss.designsystem.component.AvDateRangePickerDialog
import dev.joeherbert.availabliss.designsystem.component.AvSnackbarHost
import dev.joeherbert.availabliss.designsystem.component.AvailablissBackground
import dev.joeherbert.availabliss.designsystem.component.ErrorBanner
import dev.joeherbert.availabliss.designsystem.component.GlassIconButton
import dev.joeherbert.availabliss.designsystem.component.GlassPanel
import dev.joeherbert.availabliss.designsystem.component.LoadingIndicator
import dev.joeherbert.availabliss.designsystem.component.OrbLayout
import dev.joeherbert.availabliss.designsystem.component.PillTone
import dev.joeherbert.availabliss.designsystem.component.PlainIconButton
import dev.joeherbert.availabliss.designsystem.component.PrimaryButton
import dev.joeherbert.availabliss.designsystem.component.StatusPill
import dev.joeherbert.availabliss.designsystem.component.UserMessageEffect
import dev.joeherbert.availabliss.designsystem.component.glass
import dev.joeherbert.availabliss.designsystem.component.rememberSnackbarHostState
import dev.joeherbert.availabliss.designsystem.icon.AvIcons
import dev.joeherbert.availabliss.designsystem.theme.AvWeights
import dev.joeherbert.availabliss.designsystem.theme.AvailablissPreviewTheme
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import dev.joeherbert.availabliss.designsystem.theme.DisplayFontFamily
import dev.joeherbert.availabliss.designsystem.theme.Shapes
import dev.joeherbert.availabliss.designsystem.theme.Spacing
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import java.time.LocalDate

/** "My time" tab: a list of days from today, a day sheet per day, repeating blocks and "Block time". */
@Composable
fun MyTimeScreen(onOpenCalendars: () -> Unit, viewModel: MyTimeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = rememberSnackbarHostState()
    UserMessageEffect(state.message, snackbar, viewModel::messageShown)

    val currentOnOpenCalendars by rememberUpdatedState(onOpenCalendars)
    // One instance for the screen's lifetime so unchanged parts skip recomposition.
    val actions = remember(viewModel) {
        MyTimeActions(
            onOpenCalendars = { currentOnOpenCalendars() },
            onJumpTo = viewModel::jumpTo,
            onScrollHandled = viewModel::scrollHandled,
            onOpenDay = viewModel::openDay,
            onNearEnd = viewModel::onNearEnd,
            onBlockEvening = viewModel::blockEvening,
            onBlockAllDay = viewModel::blockAllDay,
            onBlockAway = viewModel::blockAway,
            onBlockSomethingElse = viewModel::openBlockSheet,
            onOpenRecurring = viewModel::openEditor,
            onDeleteRecurring = viewModel::deleteBlock,
            onRefresh = viewModel::pullRefresh,
            onRetry = viewModel::refresh,
        )
    }
    MyTimeContent(state = state, snackbar = snackbar, actions = actions)

    state.openDay?.let { day ->
        DaySheet(
            day = day,
            today = state.today,
            row = state.days.firstOrNull { it.date == day },
            allDay = state.layout.allDay[day].orEmpty(),
            placed = state.layout.timed[day].orEmpty(),
            onPrevious = { viewModel.stepDay(-1) },
            onNext = { viewModel.stepDay(1) },
            onDismiss = viewModel::closeDay,
            onEmptyTap = viewModel::onEmptyTapped,
            onIntervalClick = viewModel::onIntervalTapped,
        )
    }

    state.calendarBusy?.let { info ->
        CalendarBusySheet(
            info,
            onDismiss = viewModel::closeCalendarBusy,
            onOpenCalendars = onOpenCalendars,
            onIgnore = viewModel::ignoreCalendarBusy,
            onUnignore = viewModel::unignoreCalendarBusy,
        )
    }

    state.draft?.let { draft ->
        BlockTimeSheet(
            draft = draft,
            saving = state.savingDraft,
            onDismiss = viewModel::closeBlockSheet,
            onDate = viewModel::onDraftDate,
            onWhen = viewModel::onDraftWhen,
            onStart = viewModel::onDraftStart,
            onEnd = viewModel::onDraftEnd,
            onRepeat = viewModel::onDraftRepeat,
            onUntil = viewModel::onDraftUntil,
            onNote = viewModel::onDraftNote,
            onSave = viewModel::saveDraft,
            onDelete = viewModel::deleteEditingBlock,
            onDates = viewModel::onDraftDates,
        )
    }
}

/** Callbacks for [MyTimeContent]. */
data class MyTimeActions(
    val onOpenCalendars: () -> Unit = {},
    val onJumpTo: (LocalDate) -> Unit = {},
    val onScrollHandled: () -> Unit = {},
    val onOpenDay: (LocalDate) -> Unit = {},
    /** The list is close to its last loaded day. */
    val onNearEnd: () -> Unit = {},
    val onBlockEvening: () -> Unit = {},
    val onBlockAllDay: (LocalDate) -> Unit = {},
    val onBlockAway: (LocalDate, LocalDate) -> Unit = { _, _ -> },
    val onBlockSomethingElse: () -> Unit = {},
    val onOpenRecurring: (String) -> Unit = {},
    val onDeleteRecurring: (String) -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onRetry: () -> Unit = {},
)

/** What the list shows, in order. */
private sealed interface Entry {
    val key: String

    data object BlockTime : Entry {
        override val key = "block"
    }

    data object CalendarHint : Entry {
        override val key = "hint"
    }

    data class Error(val message: String) : Entry {
        override val key = "error"
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
    add(Entry.BlockTime)
    if (state.showCalendarHint) add(Entry.CalendarHint)
    state.error?.let { add(Entry.Error(it)) }
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

private enum class DatePick { AllDay, Away, Jump }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyTimeContent(state: MyTimeUiState, snackbar: SnackbarHostState, actions: MyTimeActions) {
    val colors = AvailablissTheme.colors
    val type = AvailablissTheme.type
    val list = rememberLazyListState()
    val entries = remember(state.days, state.recurring, state.error, state.showCalendarHint, state.loading, state.loadingMore, state.today) {
        entries(state)
    }
    var pick by rememberSaveable { mutableStateOf<DatePick?>(null) }

    // Load further weeks as the end of the list comes into view.
    val nearEnd by rememberUpdatedState(actions.onNearEnd)
    LaunchedEffect(list) {
        snapshotFlow {
            val info = list.layoutInfo
            info.totalItemsCount > 0 && (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 8
        }
            .distinctUntilChanged()
            .filter { it }
            .collect { nearEnd() }
    }

    // "Jump to date": scroll once the day is in the list (it may need loading first).
    LaunchedEffect(state.scrollTo, entries) {
        val target = state.scrollTo ?: return@LaunchedEffect
        val index = entries.indexOfFirst { it is Entry.Day && it.row.date == target }
        if (index >= 0) {
            // Show the week's heading too when it's the first day of a section.
            val heading = if (index > 0 && entries[index - 1] is Entry.Heading) index - 1 else index
            list.animateScrollToItem(heading)
            actions.onScrollHandled()
        }
    }

    AvailablissBackground(OrbLayout.MyTime) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.padding(start = Spacing.screen, end = Spacing.screen, top = Spacing.screen, bottom = Spacing.m),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("My time", style = type.headlineMedium, color = colors.ink, modifier = Modifier.semantics { heading() })
                    Text("What groups see as busy", style = type.caption.copy(fontWeight = FontWeight.Normal), color = colors.muted)
                }
                GlassIconButton(AvIcons.Calendar, "Jump to date", { pick = DatePick.Jump })
                GlassIconButton(AvIcons.CalendarSync, "Connected calendars", actions.onOpenCalendars)
            }

            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = actions.onRefresh,
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                LazyColumn(
                    state = list,
                    modifier = Modifier.fillMaxSize(),
                    // Clear the floating tab bar.
                    contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = Spacing.xxs, bottom = Spacing.tabBarClearance + navBar),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    items(entries, key = { it.key }, contentType = { it::class }) { entry ->
                        when (entry) {
                            Entry.BlockTime -> BlockTimeButton(
                                onEvening = actions.onBlockEvening,
                                onAllDay = { pick = DatePick.AllDay },
                                onAway = { pick = DatePick.Away },
                                onSomethingElse = actions.onBlockSomethingElse,
                                modifier = Modifier.padding(bottom = Spacing.xs),
                            )
                            Entry.CalendarHint -> CalendarHintCard(actions.onOpenCalendars)
                            is Entry.Error -> ErrorBanner(entry.message, onRetry = actions.onRetry)
                            Entry.Loading -> LoadingIndicator()
                            is Entry.Heading -> SectionHeading(entry.text)
                            is Entry.Day -> DayRow(entry.row, isToday = entry.row.date == state.today, onClick = { actions.onOpenDay(entry.row.date) })
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
        }
        AvSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = Spacing.tabBarClearance))
    }

    when (pick) {
        DatePick.AllDay -> AvDatePickerDialog(
            initial = state.today,
            onPick = actions.onBlockAllDay,
            onDismiss = { pick = null },
            minDate = state.today,
        )
        DatePick.Away -> AvDateRangePickerDialog(
            initialStart = null,
            initialEnd = null,
            onPick = actions.onBlockAway,
            onDismiss = { pick = null },
        )
        DatePick.Jump -> AvDatePickerDialog(
            initial = state.today,
            onPick = actions.onJumpTo,
            onDismiss = { pick = null },
            minDate = state.today,
        )
        null -> Unit
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text,
        style = AvailablissTheme.type.label,
        color = AvailablissTheme.colors.muted,
        modifier = Modifier.padding(top = Spacing.l, bottom = Spacing.xxs, start = Spacing.xxs).semantics { heading() },
    )
}

/** "Block time", with quick presets before the full sheet. */
@Composable
private fun BlockTimeButton(
    onEvening: () -> Unit,
    onAllDay: () -> Unit,
    onAway: () -> Unit,
    onSomethingElse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val colors = AvailablissTheme.colors
    Box(modifier) {
        PrimaryButton(text = "Block time", onClick = { open = true }, icon = AvIcons.Plus, modifier = Modifier.fillMaxWidth())
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = colors.bg) {
            val eveningLabel = remember(open) { BlockDraft.eveningLabel(DateTimes.now().toLocalDateTime()) }
            listOf(
                Triple(eveningLabel, AvIcons.Moon, onEvening),
                Triple("All day…", AvIcons.Sun, onAllDay),
                Triple("Away for a few days…", AvIcons.Calendar, onAway),
                Triple("Something else…", AvIcons.Pencil, onSomethingElse),
            ).forEach { (label, icon, onClick) -> PresetItem(label, icon) { open = false; onClick() } }
        }
    }
}

@Composable
private fun PresetItem(label: String, icon: ImageVector, onClick: () -> Unit) {
    val colors = AvailablissTheme.colors
    DropdownMenuItem(
        text = { Text(label, style = AvailablissTheme.type.bodySmallStrong, color = colors.ink) },
        leadingIcon = { Icon(icon, null, tint = colors.ink, modifier = Modifier.size(18.dp)) },
        onClick = onClick,
        modifier = Modifier.semantics { role = Role.Button },
    )
}

@Composable
private fun CalendarHintCard(onOpenCalendars: () -> Unit) {
    val colors = AvailablissTheme.colors
    GlassPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = Shapes.row,
        contentPadding = PaddingValues(horizontal = Spacing.l, vertical = Spacing.l),
        onClick = onOpenCalendars,
        onClickLabel = "Calendar settings",
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.l)) {
            Icon(AvIcons.CalendarSync, null, tint = colors.accentInk, modifier = Modifier.size(20.dp))
            Text(
                "Connect a calendar so your busy time fills in by itself",
                style = AvailablissTheme.type.labelRegular,
                color = colors.ink,
                modifier = Modifier.weight(1f),
            )
            Icon(AvIcons.Forward, null, tint = colors.muted, modifier = Modifier.size(14.dp))
        }
    }
}

/** One day: date on the left, the 06:00–24:00 strip and a one-line summary on the right. Whole row tappable. */
@Composable
fun DayRow(row: DayRowUi, isToday: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AvailablissTheme.colors
    val type = AvailablissTheme.type
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .glass(Shapes.controlLarge, shadow = false)
            .clickable(onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = row.accessibility
                onClick(label = "See the day") { onClick(); true }
            }
            .padding(start = Spacing.m, end = Spacing.l, top = Spacing.s, bottom = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        Column(Modifier.width(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                DateTimes.shortWeekday(DateTimes.apiWeekday(row.date)),
                style = type.micro,
                color = if (isToday) colors.accentInk else colors.muted,
            )
            Box(
                Modifier.size(30.dp).clip(CircleShape).then(if (isToday) Modifier.background(colors.accent) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    row.date.dayOfMonth.toString(),
                    fontFamily = DisplayFontFamily,
                    fontSize = 17.sp,
                    lineHeight = 17.sp,
                    fontWeight = AvWeights.Display650,
                    color = if (isToday) colors.onAccent else colors.ink,
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(
                buildAnnotatedString {
                    append(row.summary.label)
                    row.names?.let { names ->
                        withStyle(SpanStyle(color = colors.muted, fontWeight = FontWeight.Normal)) { append(" · $names") }
                    }
                },
                style = type.bodySmallStrong,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            BusyStrip(row)
        }
        Icon(AvIcons.Forward, null, tint = colors.muted, modifier = Modifier.size(12.dp))
    }
}

/** Busy time from 06:00 to 24:00: calendar time faded, your blocks in the accent; ticks at 12:00 and 18:00. */
@Composable
private fun BusyStrip(row: DayRowUi) {
    val colors = AvailablissTheme.colors
    Box(
        Modifier
            .fillMaxWidth()
            .height(10.dp)
            .drawBehind {
                val span = (DAY_MINUTES - STRIP_FROM).toFloat()
                fun x(minutes: Int) = ((minutes - STRIP_FROM).coerceIn(0, DAY_MINUTES - STRIP_FROM) / span) * size.width
                val radius = CornerRadius(size.height / 2)
                drawRoundRect(colors.line2, cornerRadius = radius)
                fun draw(spans: List<BusySpan>, color: androidx.compose.ui.graphics.Color) = spans.forEach { s ->
                    val left = x(s.from)
                    val right = x(s.to)
                    if (right > left) drawRoundRect(color, Offset(left, 0f), Size(maxOf(right - left, 2.dp.toPx()), size.height), radius)
                }
                draw(row.calendarSpans, colors.busyLine)
                draw(row.manualSpans, colors.accent)
                listOf(12 * 60, 18 * 60).forEach { tick ->
                    drawRect(colors.line3, Offset(x(tick) - 0.5.dp.toPx(), -2.dp.toPx()), Size(1.dp.toPx(), size.height + 4.dp.toPx()))
                }
            },
    )
}

/** Glass row for a repeating block ("Choir · Every Tuesday · 19:00 – 21:30 · Private"). */
@Composable
private fun RecurringBlockRow(block: RecurringBlockUi, onClick: () -> Unit, onDelete: () -> Unit) {
    val colors = AvailablissTheme.colors
    GlassPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = Shapes.row,
        contentPadding = PaddingValues(start = Spacing.l, end = Spacing.xxs, top = Spacing.m, bottom = Spacing.m),
        onClick = onClick,
        onClickLabel = "Edit ${block.title}",
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.l)) {
            Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(colors.accentSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AvIcons.Repeat, null, tint = colors.accentInk, modifier = Modifier.size(17.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(block.title, style = AvailablissTheme.type.bodySmallStrong, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(block.schedule, style = AvailablissTheme.type.caption.copy(fontWeight = FontWeight.Normal), color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            StatusPill("Private", PillTone.Neutral)
            PlainIconButton(AvIcons.Trash, "Delete ${block.title}", onDelete, size = 40.dp, iconSize = 16.dp, tint = colors.muted)
        }
    }
}

// region Previews
internal object MyTimeSamples {
    val today: LocalDate = LocalDate.of(2026, 11, 17)

    fun intervals(): List<BusyInterval> {
        val zone = DateTimes.zone
        fun at(day: Long, h: Int, m: Int = 0) = DateTimes.toIso(today.plusDays(day).atTime(h, m), zone)
        return listOf(
            BusyInterval(at(0, 9), at(0, 17, 30), BusySource.Calendar, "Work"),
            BusyInterval(at(0, 19), at(0, 21, 30), BusySource.Manual, "Choir", blockId = "b1"),
            BusyInterval(at(1, 10), at(1, 11), BusySource.Calendar, "Work"),
            BusyInterval(at(1, 10, 30), at(1, 12), BusySource.Manual, "Dentist", blockId = "b2"),
            BusyInterval(at(1, 14), at(1, 23), BusySource.Calendar, "Work"),
            BusyInterval(at(3, 0), at(4, 0), BusySource.Manual, "Away", blockId = "b3"),
            BusyInterval(at(4, 22), at(5, 2), BusySource.Calendar, "Night out"),
        )
    }

    fun state(): MyTimeUiState {
        val days = (0L until 13L).map(today::plusDays)
        val layout = layoutDays(days, intervals())
        return MyTimeUiState(
            today = today,
            layout = layout,
            days = days.map { dayRow(it, layout.allDay[it].orEmpty(), layout.timed[it].orEmpty(), today) },
            recurring = listOf(RecurringBlockUi("b1", "Choir", "Every Tuesday · 19:00 – 21:30")),
            loading = false,
            showCalendarHint = true,
        )
    }
}

@Preview(name = "My time", widthDp = 390, heightDp = 844)
@Composable
private fun MyTimePreview() {
    AvailablissPreviewTheme {
        MyTimeContent(state = MyTimeSamples.state(), snackbar = SnackbarHostState(), actions = MyTimeActions())
    }
}

@Preview(name = "Day rows", widthDp = 390)
@Composable
private fun DayRowPreview() {
    val state = MyTimeSamples.state()
    AvailablissPreviewTheme {
        Column(Modifier.background(AvailablissTheme.colors.bg).padding(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            state.days.take(6).forEach { DayRow(it, isToday = it.date == state.today, onClick = {}) }
        }
    }
}
// endregion
