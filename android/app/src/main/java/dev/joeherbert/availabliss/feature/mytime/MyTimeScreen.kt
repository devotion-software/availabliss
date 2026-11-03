package dev.joeherbert.availabliss.feature.mytime

import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.joeherbert.availabliss.core.common.DateTimes
import dev.joeherbert.availabliss.core.network.model.BusyInterval
import dev.joeherbert.availabliss.core.network.model.BusySource
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
import dev.joeherbert.availabliss.designsystem.component.rememberSnackbarHostState
import dev.joeherbert.availabliss.designsystem.component.stripes
import dev.joeherbert.availabliss.designsystem.icon.AvIcons
import dev.joeherbert.availabliss.designsystem.theme.AvailablissPreviewTheme
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import dev.joeherbert.availabliss.designsystem.theme.Shapes
import dev.joeherbert.availabliss.designsystem.theme.Spacing
import java.time.LocalDate

/** "My time" tab: the week grid of busy times, recurring blocks and "Block a specific time". */
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
            onPreviousWeek = viewModel::previousWeek,
            onNextWeek = viewModel::nextWeek,
            onEmptyTapped = viewModel::onEmptyTapped,
            onIntervalTapped = viewModel::onIntervalTapped,
            onOpenRecurring = viewModel::openEditor,
            onDeleteRecurring = viewModel::deleteBlock,
            onBlockSpecificTime = viewModel::openBlockSheet,
            onRetry = viewModel::refresh,
        )
    }
    MyTimeContent(state = state, snackbar = snackbar, actions = actions)

    state.calendarBusy?.let { info ->
        CalendarBusySheet(info, onDismiss = viewModel::closeCalendarBusy, onOpenCalendars = onOpenCalendars)
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
        )
    }
}

/** Callbacks for [MyTimeContent]. */
data class MyTimeActions(
    val onOpenCalendars: () -> Unit = {},
    val onPreviousWeek: () -> Unit = {},
    val onNextWeek: () -> Unit = {},
    /** A tap on empty time in the week grid: that day and the hour tapped. */
    val onEmptyTapped: (LocalDate, MinuteRange) -> Unit = { _, _ -> },
    /** A tap on a busy box or all-day chip. */
    val onIntervalTapped: (BusyInterval) -> Unit = {},
    val onOpenRecurring: (String) -> Unit = {},
    val onDeleteRecurring: (String) -> Unit = {},
    val onBlockSpecificTime: () -> Unit = {},
    val onRetry: () -> Unit = {},
)

@Composable
fun MyTimeContent(state: MyTimeUiState, snackbar: SnackbarHostState, actions: MyTimeActions) {
    val colors = AvailablissTheme.colors
    val type = AvailablissTheme.type
    AvailablissBackground(OrbLayout.MyTime) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(start = Spacing.screen, end = Spacing.screen, top = Spacing.screen, bottom = Spacing.tabBarClearance - 10.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Column(Modifier.weight(1f)) {
                    Text("My time", style = type.headlineMedium, color = colors.ink)
                    Text("Tap to block time or edit a block", style = type.caption.copy(fontWeight = FontWeight.Normal), color = colors.muted)
                }
                GlassIconButton(AvIcons.CalendarSync, "Connected calendars", actions.onOpenCalendars)
            }

            // Week switcher
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                GlassIconButton(AvIcons.Back, "Previous week", actions.onPreviousWeek, size = 40.dp, iconSize = 16.dp, shape = Shapes.inner)
                Text(state.weekLabel, style = type.titleXSmall, color = colors.ink, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                GlassIconButton(AvIcons.Forward, "Next week", actions.onNextWeek, size = 40.dp, iconSize = 16.dp, shape = Shapes.inner)
            }

            BoxWithConstraints(Modifier.weight(1f)) {
                // The grid card takes just over half the screen (never more than the viewport), so the hours
                // scroll inside it with the day header in view and the page still reaches the blocks below.
                val screenHeight = LocalConfiguration.current.screenHeightDp.dp
                val gridHeight = (minOf(screenHeight * 0.52f, maxHeight) - GridCardChrome).coerceIn(200.dp, 640.dp)
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xl),
                ) {
                    state.error?.let { ErrorBanner(it, onRetry = actions.onRetry) }
                    WeekGridCard(state, gridHeight, actions.onEmptyTapped, actions.onIntervalTapped)
                    state.recurring.forEach { block ->
                        RecurringBlockRow(block, onClick = { actions.onOpenRecurring(block.id) }, onDelete = { actions.onDeleteRecurring(block.id) })
                    }
                }
            }

            PrimaryButton(
                text = "Block a specific time",
                onClick = actions.onBlockSpecificTime,
                icon = AvIcons.Plus,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        AvSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = Spacing.tabBarClearance))
    }
}

/** Header, legend and padding around the grid's scroller (plus the all-day row when shown). */
private val GridCardChrome = 112.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeekGridCard(
    state: MyTimeUiState,
    scrollHeight: Dp,
    onEmptyTapped: (LocalDate, MinuteRange) -> Unit,
    onIntervalTapped: (BusyInterval) -> Unit,
) {
    val colors = AvailablissTheme.colors
    val type = AvailablissTheme.type
    GlassPanel(
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Busy times this week" },
        contentPadding = PaddingValues(Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        WeekTimeGrid(
            layout = state.layout,
            today = state.today,
            scrollHeight = if (state.layout.hasAllDay) scrollHeight - 32.dp else scrollHeight,
            onEmptyTap = onEmptyTapped,
            ready = !state.loading,
            onIntervalClick = onIntervalTapped,
        )
        FlowRow(
            modifier = Modifier.padding(top = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            LegendItem("Blocked by you") {
                Box(Modifier.size(12.dp, 10.dp).clip(RoundedCornerShape(3.dp)).background(colors.surface).stripes(colors.accentSoft, 2.dp, 4.dp).border(1.dp, colors.accent, RoundedCornerShape(3.dp)))
            }
            LegendItem("From a calendar") {
                Box(Modifier.size(12.dp, 10.dp).clip(RoundedCornerShape(3.dp)).background(colors.busyBg).border(1.dp, colors.busyLine, RoundedCornerShape(3.dp)))
            }
            Text("${state.blockedCount} blocked this week", style = type.micro.copy(fontWeight = FontWeight.Normal), color = colors.muted)
        }
    }
}

@Composable
private fun LegendItem(label: String, swatch: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        swatch()
        Text(label, style = AvailablissTheme.type.micro.copy(fontWeight = FontWeight.Normal), color = AvailablissTheme.colors.muted)
    }
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

@Preview(name = "My time", widthDp = 390, heightDp = 844)
@Composable
private fun MyTimePreview() {
    val start = LocalDate.of(2026, 11, 2)
    val zone = DateTimes.zone
    fun at(day: Long, h: Int, m: Int = 0) = DateTimes.toIso(start.plusDays(day).atTime(h, m), zone)
    val intervals = listOf(
        BusyInterval(at(0, 9), at(0, 17, 30), BusySource.Manual, "Work", blockId = "b0"),
        BusyInterval(at(1, 19), at(1, 21, 30), BusySource.Manual, "Choir", blockId = "b1"),
        BusyInterval(at(2, 10), at(2, 11), BusySource.Calendar, "Dentist"),
        BusyInterval(at(2, 10, 30), at(2, 12), BusySource.Calendar, "Standup"),
        BusyInterval(at(4, 22), at(5, 2), BusySource.Calendar, "Night out"),
    )
    AvailablissPreviewTheme {
        MyTimeContent(
            state = MyTimeUiState(
                weekStart = start,
                weekLabel = "2 – 8 November",
                today = start.plusDays(1),
                layout = layoutWeek(start, intervals, zone),
                recurring = listOf(RecurringBlockUi("b1", "Choir", "Every Tuesday · 19:00 – 21:30")),
                blockedCount = 2,
                loading = false,
            ),
            snackbar = SnackbarHostState(),
            actions = MyTimeActions(),
        )
    }
}
