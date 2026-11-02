package dev.joeherbert.availabliss.feature.mytime

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.joeherbert.availabliss.core.common.DateTimes
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
import dev.joeherbert.availabliss.feature.common.parseHex
import java.time.LocalDate

/** "My time" tab: the week grid of busy times, recurring blocks and "Block a specific time". */
@Composable
fun MyTimeScreen(onOpenCalendars: () -> Unit, viewModel: MyTimeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = rememberSnackbarHostState()
    UserMessageEffect(state.message, snackbar, viewModel::messageShown)

    MyTimeContent(
        state = state,
        snackbar = snackbar,
        actions = MyTimeActions(
            onOpenCalendars = onOpenCalendars,
            onPreviousWeek = viewModel::previousWeek,
            onNextWeek = viewModel::nextWeek,
            onSlotTapped = viewModel::onSlotTapped,
            onOpenRecurring = viewModel::openEditor,
            onDeleteRecurring = viewModel::deleteBlock,
            onBlockSpecificTime = viewModel::openBlockSheet,
            onRetry = viewModel::refresh,
        ),
    )

    state.draft?.let { draft ->
        BlockTimeSheet(
            draft = draft,
            saving = state.savingDraft,
            onDismiss = viewModel::closeBlockSheet,
            onDate = viewModel::onDraftDate,
            onWhen = viewModel::onDraftWhen,
            onCustomStart = viewModel::onDraftCustomStart,
            onCustomEnd = viewModel::onDraftCustomEnd,
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
    val onSlotTapped: (SlotUi) -> Unit = {},
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
                    Text("Tap to block time you're busy", style = type.caption.copy(fontWeight = FontWeight.Normal), color = colors.muted)
                }
                GlassIconButton(AvIcons.CalendarSync, "Connected calendars", actions.onOpenCalendars)
            }

            // Week switcher
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                GlassIconButton(AvIcons.Back, "Previous week", actions.onPreviousWeek, size = 40.dp, iconSize = 16.dp, shape = Shapes.inner)
                Text(state.weekLabel, style = type.titleXSmall, color = colors.ink, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                GlassIconButton(AvIcons.Forward, "Next week", actions.onNextWeek, size = 40.dp, iconSize = 16.dp, shape = Shapes.inner)
            }

            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.xl),
            ) {
                state.error?.let { ErrorBanner(it, onRetry = actions.onRetry) }
                WeekGridCard(state, actions.onSlotTapped)
                state.recurring.forEach { block ->
                    RecurringBlockRow(block, onClick = { actions.onOpenRecurring(block.id) }, onDelete = { actions.onDeleteRecurring(block.id) })
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeekGridCard(state: MyTimeUiState, onSlotTapped: (SlotUi) -> Unit) {
    val colors = AvailablissTheme.colors
    val type = AvailablissTheme.type
    GlassPanel(
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Busy times this week" },
        contentPadding = PaddingValues(Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Spacer(Modifier.width(50.dp))
            DayPart.entries.forEach { part ->
                Text(part.label, style = type.micro, color = colors.muted, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        state.days.forEach { day ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.width(50.dp)) {
                    Text(day.dayLabel, style = type.captionStrong, color = colors.ink)
                    Text(day.dateLabel, style = type.micro.copy(fontWeight = FontWeight.Normal), color = colors.muted)
                }
                day.slots.forEach { slot ->
                    SlotButton(slot, onClick = { onSlotTapped(slot) }, modifier = Modifier.weight(1f))
                }
            }
        }
        FlowRow(
            modifier = Modifier.padding(top = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            LegendItem("Blocked by you") {
                Box(Modifier.size(12.dp, 10.dp).clip(RoundedCornerShape(3.dp)).background(colors.accentSoft).stripes(colors.accent, 2.dp, 5.dp))
            }
            LegendItem("From a calendar") {
                Box(Modifier.size(12.dp, 10.dp).alpha(0.5f).clip(RoundedCornerShape(3.dp)).background(colors.busyBg).border(1.dp, colors.busyLine, RoundedCornerShape(3.dp)))
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

/** One 46dp grid cell in its free / calendar-busy / blocked-by-hand look. */
@Composable
private fun SlotButton(slot: SlotUi, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AvailablissTheme.colors
    val shape = Shapes.inner
    val labelStyle = AvailablissTheme.type.micro
    val base = modifier.height(46.dp)
    when (val s = slot.state) {
        SlotState.Free -> Box(
            base
                .clip(shape)
                .background(colors.surface)
                .border(1.dp, colors.glassBorder, shape)
                .clickable(role = Role.Button, onClickLabel = "Block", onClick = onClick)
                .clearAndSetSemantics { contentDescription = slot.accessibilityLabel },
        )
        is SlotState.Calendar -> Row(
            base
                .alpha(0.5f)
                .clip(shape)
                .background(colors.busyBg)
                .border(1.dp, colors.busyLine, shape)
                .padding(horizontal = 6.dp)
                .clearAndSetSemantics { contentDescription = slot.accessibilityLabel },
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(6.dp).clip(Shapes.pill).background(s.dotColor?.let(::parseHex) ?: colors.warn))
            Text(s.label, style = labelStyle, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        is SlotState.Manual -> Box(
            base
                .alpha(if (s.pending) 0.7f else 1f)
                .clip(shape)
                .background(colors.surface)
                .stripes(colors.accentSoft, 6.dp, 12.dp)
                .border(1.5.dp, colors.accent, shape)
                .clickable(role = Role.Button, onClickLabel = "Unblock or edit", onClick = onClick)
                .padding(horizontal = 6.dp)
                .clearAndSetSemantics { contentDescription = slot.accessibilityLabel },
            contentAlignment = Alignment.Center,
        ) {
            Text(s.label, style = labelStyle, color = colors.accentInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
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
    val days = (0L until 7L).map { i ->
        val date = start.plusDays(i)
        DayRowUi(
            date = date,
            dayLabel = DateTimes.shortWeekday(i.toInt()),
            dateLabel = date.dayOfMonth.toString(),
            slots = DayPart.entries.mapIndexed { p, part ->
                val state = when {
                    i < 5 && p < 2 -> SlotState.Calendar("Work", "#5B8DEF")
                    i == 1L && p == 2 -> SlotState.Manual("b1", "Choir")
                    i == 6L && p == 0 -> SlotState.Calendar("Parkrun", null)
                    else -> SlotState.Free
                }
                SlotUi(date, part, state)
            },
        )
    }
    AvailablissPreviewTheme {
        MyTimeContent(
            state = MyTimeUiState(
                weekStart = start,
                weekLabel = "2 – 8 November",
                days = days,
                recurring = listOf(RecurringBlockUi("b1", "Choir", "Every Tuesday · 19:00 – 21:30")),
                blockedCount = 1,
                loading = false,
            ),
            snackbar = SnackbarHostState(),
            actions = MyTimeActions(),
        )
    }
}
