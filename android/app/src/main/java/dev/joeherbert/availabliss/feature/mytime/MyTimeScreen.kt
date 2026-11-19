package dev.joeherbert.availabliss.feature.mytime

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.joeherbert.availabliss.core.common.DateTimes
import dev.joeherbert.availabliss.core.network.model.BusyInterval
import dev.joeherbert.availabliss.core.network.model.BusySource
import dev.joeherbert.availabliss.core.network.model.Repeat
import dev.joeherbert.availabliss.designsystem.component.AvDatePickerDialog
import dev.joeherbert.availabliss.designsystem.component.AvDateRangePickerDialog
import dev.joeherbert.availabliss.designsystem.component.AvSnackbarHost
import dev.joeherbert.availabliss.designsystem.component.AvailablissBackground
import dev.joeherbert.availabliss.designsystem.component.ErrorBanner
import dev.joeherbert.availabliss.designsystem.component.GlassIconButton
import dev.joeherbert.availabliss.designsystem.component.GlassPanel
import dev.joeherbert.availabliss.designsystem.component.OrbLayout
import dev.joeherbert.availabliss.designsystem.component.PillTone
import dev.joeherbert.availabliss.designsystem.component.PlainIconButton
import dev.joeherbert.availabliss.designsystem.component.PrimaryButton
import dev.joeherbert.availabliss.designsystem.component.StatusPill
import dev.joeherbert.availabliss.designsystem.component.UserMessageEffect
import dev.joeherbert.availabliss.designsystem.component.glass
import dev.joeherbert.availabliss.designsystem.component.rememberSnackbarHostState
import dev.joeherbert.availabliss.designsystem.icon.AvIcons
import dev.joeherbert.availabliss.designsystem.theme.AvailablissPreviewTheme
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import dev.joeherbert.availabliss.designsystem.theme.Shapes
import dev.joeherbert.availabliss.designsystem.theme.Spacing
import java.time.LocalDate
import java.time.YearMonth

/**
 * "My time" tab: a month heat map of how free each day is (or an agenda list), opening a week
 * view with the day's timeline; repeating blocks and "Block time".
 */
@Composable
fun MyTimeScreen(onOpenCalendars: () -> Unit, viewModel: MyTimeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = rememberSnackbarHostState()
    UserMessageEffect(state.message, snackbar, viewModel::messageShown)
    BackHandler(enabled = state.openDay != null) { viewModel.closeDay() }

    val currentOnOpenCalendars by rememberUpdatedState(onOpenCalendars)
    // One instance for the screen's lifetime so unchanged parts skip recomposition.
    val actions = remember(viewModel) {
        MyTimeActions(
            onOpenCalendars = { currentOnOpenCalendars() },
            onSetMode = viewModel::setMode,
            onPreviousMonth = viewModel::previousMonth,
            onNextMonth = viewModel::nextMonth,
            onThisMonth = viewModel::thisMonth,
            onOpenDay = viewModel::openDay,
            onCloseDay = viewModel::closeDay,
            onStepDay = viewModel::stepDay,
            onStepWeek = viewModel::stepWeek,
            onNearEnd = viewModel::onNearEnd,
            onBlockSomethingElse = viewModel::openBlockSheet,
            onBlockOn = viewModel::openBlockSheetOn,
            onBlockFree = viewModel::blockFree,
            onEmptyTap = viewModel::onEmptyTapped,
            onIntervalClick = viewModel::onIntervalTapped,
            onOpenRecurring = viewModel::openEditor,
            onDeleteRecurring = viewModel::deleteBlock,
            onRefresh = viewModel::pullRefresh,
            onRetry = viewModel::refresh,
        )
    }
    MyTimeContent(state = state, snackbar = snackbar, actions = actions)

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
            onRange = viewModel::onDraftRange,
            onRepeat = viewModel::onDraftRepeat,
            onUntil = viewModel::onDraftUntil,
            onNote = viewModel::onDraftNote,
            onSave = viewModel::saveDraft,
            onDelete = viewModel::deleteEditingBlock,
        )
    }
}

/** Callbacks for [MyTimeContent]. */
data class MyTimeActions(
    val onOpenCalendars: () -> Unit = {},
    val onSetMode: (MyTimeMode) -> Unit = {},
    val onPreviousMonth: () -> Unit = {},
    val onNextMonth: () -> Unit = {},
    val onThisMonth: () -> Unit = {},
    /** Opens the week view on a day. */
    val onOpenDay: (LocalDate) -> Unit = {},
    val onCloseDay: () -> Unit = {},
    val onStepDay: (Long) -> Unit = {},
    val onStepWeek: (Long) -> Unit = {},
    /** The agenda is close to its last loaded day. */
    val onNearEnd: () -> Unit = {},
    val onBlockSomethingElse: () -> Unit = {},
    /** The block sheet on a given day (the week view's "+ Block"). */
    val onBlockOn: (LocalDate) -> Unit = {},
    /** The agenda's free row: block the day's longest free stretch. */
    val onBlockFree: (LocalDate, DaySummary) -> Unit = { _, _ -> },
    val onEmptyTap: (LocalDate, MinuteRange) -> Unit = { _, _ -> },
    val onIntervalClick: (BusyInterval) -> Unit = {},
    val onOpenRecurring: (String) -> Unit = {},
    val onDeleteRecurring: (String) -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onRetry: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyTimeContent(state: MyTimeUiState, snackbar: SnackbarHostState, actions: MyTimeActions) {
    // Kept here so the month and agenda keep their place while the week view is open.
    val agendaList = rememberLazyListState()
    val monthScroll = rememberScrollState()
    val top: @Composable () -> Unit = {
        TopCards(state, actions)
    }

    AvailablissBackground(OrbLayout.MyTime) {
        val day = state.openDay
        if (day != null) {
            WeekView(state, day, actions)
        } else {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                ScreenHeader(state.mode, actions)
                PullToRefreshBox(
                    isRefreshing = state.refreshing,
                    onRefresh = actions.onRefresh,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                ) {
                    when (state.mode) {
                        MyTimeMode.Month -> MonthView(state, monthScroll, actions, top)
                        MyTimeMode.Agenda -> AgendaView(state, agendaList, actions, top)
                    }
                }
            }
        }
        AvSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = Spacing.tabBarClearance))
    }
}

/** "My time" / "What groups see as busy", the month / agenda switch and "Connected calendars". */
@Composable
private fun ScreenHeader(mode: MyTimeMode, actions: MyTimeActions) {
    val colors = AvailablissTheme.colors
    val type = AvailablissTheme.type
    Row(
        Modifier.padding(start = Spacing.screen, end = Spacing.screen, top = Spacing.screen, bottom = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Column(Modifier.weight(1f)) {
            Text("My time", style = type.headlineMedium, color = colors.ink, modifier = Modifier.semantics { heading() })
            Text("What groups see as busy", style = type.caption.copy(fontWeight = FontWeight.Normal), color = colors.muted)
        }
        ViewSwitch(mode, onSwitch = { actions.onSetMode(if (mode == MyTimeMode.Month) MyTimeMode.Agenda else MyTimeMode.Month) })
        GlassIconButton(AvIcons.Settings, "Calendar settings", actions.onOpenCalendars)
    }
}

/** In the month: a list icon and "Agenda"; in the agenda: a calendar icon and "Month". */
@Composable
private fun ViewSwitch(mode: MyTimeMode, onSwitch: () -> Unit) {
    val colors = AvailablissTheme.colors
    val toAgenda = mode == MyTimeMode.Month
    val description = if (toAgenda) "Show as a list" else "Show as a month"
    Row(
        Modifier
            .height(44.dp)
            .glass(CircleShape)
            .clickable(role = Role.Button, onClick = onSwitch)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                onClick {
                    onSwitch()
                    true
                }
            }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(if (toAgenda) AvIcons.ListBullets else AvIcons.Calendar, null, tint = colors.ink, modifier = Modifier.size(18.dp))
        Text(if (toAgenda) "Agenda" else "Month", style = AvailablissTheme.type.label, color = colors.ink)
    }
}

/** "Block time", the connect-a-calendar hint and any error, above the month or agenda. */
@Composable
private fun TopCards(state: MyTimeUiState, actions: MyTimeActions) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        // Straight to the block sheet.
        PrimaryButton(
            text = "Block time",
            onClick = actions.onBlockSomethingElse,
            icon = AvIcons.Plus,
            modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.xs),
        )
        if (state.showCalendarHint) CalendarHintCard(actions.onOpenCalendars)
        state.error?.let { ErrorBanner(it, onRetry = actions.onRetry) }
    }
}

/** Bottom padding that clears the floating tab bar. */
@Composable
internal fun tabBarClearance(): Dp = Spacing.tabBarClearance + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

@Composable
internal fun SectionHeading(text: String) {
    Text(
        text,
        style = AvailablissTheme.type.label,
        color = AvailablissTheme.colors.muted,
        modifier = Modifier.padding(top = Spacing.l, bottom = Spacing.xxs, start = Spacing.xxs).semantics { heading() },
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

/** Glass row for a repeating block ("Choir · Every Tuesday · 19:00 – 21:30 · Private"). */
@Composable
internal fun RecurringBlockRow(block: RecurringBlockUi, onClick: () -> Unit, onDelete: () -> Unit) {
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
        val days = (0L until 45L).map(today::plusDays)
        val layout = layoutDays(days, intervals())
        val rows = days.map { dayRow(it, layout.allDay[it].orEmpty(), layout.timed[it].orEmpty(), today) }
        return MyTimeUiState(
            today = today,
            month = YearMonth.from(today),
            layout = layout,
            days = rows,
            rows = rows.associateBy { it.date },
            repeats = mapOf("b1" to Repeat.Weekly),
            recurring = listOf(RecurringBlockUi("b1", "Choir", "Every Tuesday · 19:00 – 21:30")),
            loading = false,
            showCalendarHint = true,
        )
    }
}

@Preview(name = "My time · month", widthDp = 390, heightDp = 844)
@Composable
private fun MyTimePreview() {
    AvailablissPreviewTheme {
        MyTimeContent(state = MyTimeSamples.state(), snackbar = SnackbarHostState(), actions = MyTimeActions())
    }
}

@Preview(name = "My time · agenda", widthDp = 390, heightDp = 844)
@Composable
private fun MyTimeAgendaPreview() {
    AvailablissPreviewTheme {
        MyTimeContent(state = MyTimeSamples.state().copy(mode = MyTimeMode.Agenda), snackbar = SnackbarHostState(), actions = MyTimeActions())
    }
}

@Preview(name = "My time · week", widthDp = 390, heightDp = 844)
@Composable
private fun MyTimeWeekPreview() {
    val state = MyTimeSamples.state()
    AvailablissPreviewTheme {
        MyTimeContent(state = state.copy(openDay = state.today.plusDays(1)), snackbar = SnackbarHostState(), actions = MyTimeActions())
    }
}

// endregion
