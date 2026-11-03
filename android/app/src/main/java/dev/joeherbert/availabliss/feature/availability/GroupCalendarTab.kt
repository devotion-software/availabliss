package dev.joeherbert.availabliss.feature.availability

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.joeherbert.availabliss.core.network.model.Slot
import dev.joeherbert.availabliss.designsystem.component.AvSnackbarHost
import dev.joeherbert.availabliss.designsystem.component.AvailablissBackground
import dev.joeherbert.availabliss.designsystem.component.ButtonStyle
import dev.joeherbert.availabliss.designsystem.component.ErrorBanner
import dev.joeherbert.availabliss.designsystem.component.GlassIconButton
import dev.joeherbert.availabliss.designsystem.component.LoadingIndicator
import dev.joeherbert.availabliss.designsystem.component.OrbLayout
import dev.joeherbert.availabliss.designsystem.component.PillTone
import dev.joeherbert.availabliss.designsystem.component.PlainIconButton
import dev.joeherbert.availabliss.designsystem.component.PrimaryButton
import dev.joeherbert.availabliss.designsystem.component.SecondaryButton
import dev.joeherbert.availabliss.designsystem.component.Segment
import dev.joeherbert.availabliss.designsystem.component.SegmentedControl
import dev.joeherbert.availabliss.designsystem.component.StatusPill
import dev.joeherbert.availabliss.designsystem.component.SyncRing
import dev.joeherbert.availabliss.designsystem.component.UserMessageEffect
import dev.joeherbert.availabliss.designsystem.component.availability.DayTimeline
import dev.joeherbert.availabliss.designsystem.component.availability.MonthGrid
import dev.joeherbert.availabliss.designsystem.component.availability.MonthLegend
import dev.joeherbert.availabliss.designsystem.component.availability.WeekGrid
import dev.joeherbert.availabliss.designsystem.component.glass
import dev.joeherbert.availabliss.designsystem.component.rememberSnackbarHostState
import dev.joeherbert.availabliss.designsystem.icon.AvIcons
import dev.joeherbert.availabliss.designsystem.theme.AvailablissPreviewTheme
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import dev.joeherbert.availabliss.designsystem.theme.Shapes
import dev.joeherbert.availabliss.designsystem.theme.Spacing
import dev.joeherbert.availabliss.feature.plan.UpgradeSheet
import dev.joeherbert.availabliss.feature.planner.PlannerSheet
import dev.joeherbert.availabliss.feature.proposal.ProposalPrefill
import java.time.LocalDate
import kotlinx.coroutines.launch

/** Why the upgrade sheet opens from "Plan with AI". */
private const val PLANNER_UPGRADE_REASON = "AI planning is part of Pro: it picks the best time, suggests things to do and drafts the plan."

/** Group availability: month / week / day (GroupCalendar.dc.html). */
@Composable
fun GroupCalendarTab(
    onBack: () -> Unit,
    onOpenMembers: () -> Unit,
    onOpenTiming: () -> Unit,
    onCreateProposal: (ProposalPrefill) -> Unit,
    viewModel: GroupCalendarViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = rememberSnackbarHostState()
    UserMessageEffect(state.message, snackbar, viewModel::messageShown)
    var planning by rememberSaveable { mutableStateOf(false) }
    var upgradeReason by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    if (planning) {
        PlannerSheet(
            onDismiss = { planning = false },
            onPropose = { p ->
                val (start, end, allDay) = viewModel.proposalFor(p.slot)
                onCreateProposal(ProposalPrefill(start, end, allDay, p.title.ifBlank { null }, p.note.ifBlank { null }))
            },
            onNeedsPro = { upgradeReason = PLANNER_UPGRADE_REASON },
        )
    }
    upgradeReason?.let { reason ->
        UpgradeSheet(
            onDismiss = { upgradeReason = null },
            onUpgraded = { message -> scope.launch { snackbar.showSnackbar(message) } },
            reason = reason,
        )
    }
    GroupCalendarContent(
        state = state,
        onBack = onBack,
        onOpenMembers = onOpenMembers,
        onOpenTiming = onOpenTiming,
        onNudge = viewModel::nudge,
        onSelectView = viewModel::selectView,
        onSelectDate = viewModel::selectDate,
        onPrevious = viewModel::previous,
        onNext = viewModel::next,
        onRetry = viewModel::refresh,
        onPropose = { slot ->
            val (start, end, allDay) = viewModel.proposalFor(slot)
            onCreateProposal(ProposalPrefill(start, end, allDay))
        },
        onProposeDay = { date ->
            val (start, end) = viewModel.eveningOn(date)
            onCreateProposal(ProposalPrefill(start, end, false))
        },
        // Offered only when the server has the AI planner set up; without Pro it explains Pro first.
        onPlanWithAi = if (state.aiPlanner.available && state.hasSlots) {
            { if (state.aiPlanner.included) planning = true else upgradeReason = PLANNER_UPGRADE_REASON }
        } else {
            null
        },
        onSeeWhosFree = viewModel::showDay,
        snackbar = { AvSnackbarHost(snackbar) },
    )
}

@Composable
fun GroupCalendarContent(
    state: GroupCalendarUiState,
    onBack: () -> Unit,
    onOpenMembers: () -> Unit,
    onOpenTiming: () -> Unit,
    onNudge: () -> Unit,
    onSelectView: (CalendarView) -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onRetry: () -> Unit,
    onPropose: (Slot) -> Unit,
    onSeeWhosFree: () -> Unit,
    snackbar: @Composable () -> Unit = {},
    /** "Propose anyway" on a day that doesn't work for everyone. */
    onProposeDay: (LocalDate) -> Unit = {},
    /** "Plan with AI"; null hides it (not set up on this server, or nothing works yet). */
    onPlanWithAi: (() -> Unit)? = null,
) {
    AvailablissBackground(OrbLayout.GroupCalendar) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(start = Spacing.screen, end = Spacing.screen, top = Spacing.screen, bottom = Spacing.tabBarClearance),
                verticalArrangement = Arrangement.spacedBy(Spacing.l),
            ) {
                GroupHeader(state, onBack, onOpenMembers)
                state.syncBanner?.let { SyncBanner(it, state.nudging, onNudge) }
                if (state.requirementSummary.isNotBlank()) TimingChip(state.requirementSummary, state.isAdmin, onOpenTiming)
                HeadingRow(state, onSelectView, onPrevious, onNext)
                state.coverageNote?.let { CoverageNote(it) }
                state.error?.let { ErrorBanner(it, onRetry = onRetry) }
                AnimatedContent(targetState = state.view, label = "calendar-view") { view ->
                    when (view) {
                        CalendarView.Month -> MonthSection(state, onSelectDate, onPropose, onProposeDay, onSeeWhosFree, onPlanWithAi)
                        CalendarView.Week -> if (state.weekLoading && state.weekColumns.isEmpty()) LoadingIndicator() else WeekGrid(state.weekColumns)
                        CalendarView.Day -> if (state.dayLoading && state.timelineRows.isEmpty()) LoadingIndicator()
                        else DayTimeline(state.dayTitle, state.dayNote, state.everyone, state.timelineRows)
                    }
                }
            }
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = Spacing.tabBarClearance)) { snackbar() }
        }
    }
}

@Composable
private fun GroupHeader(state: GroupCalendarUiState, onBack: () -> Unit, onOpenMembers: () -> Unit) {
    val colors = AvailablissTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        GlassIconButton(AvIcons.Back, "Back to groups", onBack)
        Column(Modifier.weight(1f)) {
            Text(state.groupName, style = AvailablissTheme.type.headlineSmall, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(state.membersLine, style = AvailablissTheme.type.caption.copy(fontWeight = FontWeight.Normal), color = colors.muted)
        }
        GlassIconButton(AvIcons.AddPerson, "Invite people", onOpenMembers)
    }
}

@Composable
private fun SyncBanner(banner: SyncBannerUi, nudging: Boolean, onNudge: () -> Unit) {
    val colors = AvailablissTheme.colors
    Row(
        Modifier.fillMaxWidth().glass(Shapes.row).padding(start = 12.dp, top = 10.dp, end = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SyncRing(
            progress = if (banner.total == 0) 0f else banner.synced.toFloat() / banner.total,
            size = 38.dp,
            strokeWidth = 4.dp,
            color = colors.warn,
            modifier = Modifier.semantics { contentDescription = "${banner.synced} of ${banner.total} synced" },
        ) {
            Text("${banner.synced}/${banner.total}", style = AvailablissTheme.type.micro.copy(fontWeight = FontWeight.Bold), color = colors.ink)
        }
        Column(Modifier.weight(1f)) {
            Text(banner.title, style = AvailablissTheme.type.bodySmallStrong, color = colors.ink)
            Text(banner.subtitle, style = AvailablissTheme.type.caption.copy(fontWeight = FontWeight.Normal), color = colors.muted, maxLines = 2)
        }
        if (banner.canNudge) {
            Box(
                Modifier
                    .height(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.warnBg)
                    .clickable(enabled = !nudging, role = Role.Button, onClickLabel = "Remind people to add busy times", onClick = onNudge)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (nudging) "Nudging…" else "Nudge", style = AvailablissTheme.type.label, color = colors.warnInk)
            }
        }
    }
}

@Composable
private fun CoverageNote(text: String) {
    val colors = AvailablissTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.warnBg)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(AvIcons.Info, null, tint = colors.warnInk, modifier = Modifier.padding(top = 2.dp).size(15.dp))
        Text(text, style = AvailablissTheme.type.caption.copy(fontWeight = FontWeight.Normal), color = colors.warnInk)
    }
}

@Composable
private fun TimingChip(summary: String, editable: Boolean, onClick: () -> Unit) {
    val colors = AvailablissTheme.colors
    val lead = summary.substringBefore(" · ")
    val rest = summary.removePrefix(lead)
    Row(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .glass(Shapes.controlSmall)
            .clickable(role = Role.Button, onClickLabel = if (editable) "Edit timing" else "View timing", onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(AvIcons.Clock, null, tint = colors.ink, modifier = Modifier.size(16.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(lead) }
                append(rest)
            },
            style = AvailablissTheme.type.labelRegular.copy(fontWeight = FontWeight.Normal),
            color = colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (editable) Icon(AvIcons.Pencil, null, tint = colors.muted, modifier = Modifier.size(15.dp))
    }
}

@Composable
private fun HeadingRow(state: GroupCalendarUiState, onSelectView: (CalendarView) -> Unit, onPrevious: () -> Unit, onNext: () -> Unit) {
    val colors = AvailablissTheme.colors
    val unit = when (state.view) {
        CalendarView.Month -> "month"
        CalendarView.Week -> "week"
        CalendarView.Day -> "day"
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        // "‹ Nov 2026 ›" keeps together and takes whatever the view switcher leaves.
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            PlainIconButton(AvIcons.Back, "Previous $unit", onPrevious, size = 32.dp, iconSize = 16.dp)
            Text(state.heading, style = AvailablissTheme.type.titleSmall, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            PlainIconButton(AvIcons.Forward, "Next $unit", onNext, size = 32.dp, iconSize = 16.dp)
        }
        SegmentedControl(
            segments = CalendarView.entries.map { Segment(it, it.label) },
            selected = state.view,
            onSelect = onSelectView,
        )
    }
}

@Composable
private fun MonthSection(
    state: GroupCalendarUiState,
    onSelectDate: (LocalDate) -> Unit,
    onPropose: (Slot) -> Unit,
    onProposeDay: (LocalDate) -> Unit,
    onSeeWhosFree: () -> Unit,
    onPlanWithAi: (() -> Unit)?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
        if (state.loading && state.monthRows.all { row -> row.all { it == null } }) {
            LoadingIndicator()
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MonthGrid(state.columnLabels, state.monthRows, onSelectDate)
                MonthLegend(state.daysNote)
            }
        }
        onPlanWithAi?.let { PlanWithAiRow(it) }
        state.detail?.let { DetailCard(it, onPropose, onProposeDay, onSeeWhosFree) }
    }
}

/** "Best options · Plan with AI" above the selected day (mirrors web's Best options header). */
@Composable
private fun PlanWithAiRow(onClick: () -> Unit) {
    val colors = AvailablissTheme.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Best options", style = AvailablissTheme.type.cardTitle, color = colors.ink, modifier = Modifier.weight(1f).semantics { heading() })
        Row(
            Modifier
                .clip(Shapes.pill)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(AvIcons.Sparkle, null, tint = colors.accentInk, modifier = Modifier.size(16.dp))
            Text("Plan with AI", style = AvailablissTheme.type.bodySmallStrong, color = colors.accentInk)
        }
    }
}

@Composable
private fun DetailCard(detail: DayDetailUi, onPropose: (Slot) -> Unit, onProposeDay: (LocalDate) -> Unit, onSeeWhosFree: () -> Unit) {
    val colors = AvailablissTheme.colors
    Column(
        Modifier.fillMaxWidth().glass(Shapes.panel).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(detail.title, style = AvailablissTheme.type.cardTitle, color = colors.ink)
                Text(detail.subtitle, style = AvailablissTheme.type.caption.copy(fontWeight = FontWeight.Normal), color = colors.muted)
            }
            StatusPill(if (detail.works) "Works" else "Doesn’t fit", if (detail.works) PillTone.Mint else PillTone.Neutral)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val slot = detail.slot
            if (slot != null && detail.proposeLabel != null) {
                PrimaryButton(
                    text = detail.proposeLabel,
                    onClick = { onPropose(slot) },
                    style = ButtonStyle.Accent,
                    height = 44.dp,
                    shape = Shapes.controlSmall,
                    textStyle = AvailablissTheme.type.bodySmallStrong,
                    // The primary action gets more room so "Propose this weekend" fits on one line.
                    modifier = Modifier.weight(1.4f),
                )
            } else if (detail.canProposeAnyway) {
                SecondaryButton(
                    text = "Propose anyway",
                    onClick = { onProposeDay(detail.date) },
                    height = 44.dp,
                    shape = Shapes.controlSmall,
                    textStyle = AvailablissTheme.type.bodySmallStrong,
                    modifier = Modifier.weight(1f),
                )
            }
            SecondaryButton(
                text = "See who's free",
                onClick = onSeeWhosFree,
                height = 44.dp,
                shape = Shapes.controlSmall,
                textStyle = AvailablissTheme.type.bodySmallStrong,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun GroupCalendarPreview() {
    AvailablissPreviewTheme {
        GroupCalendarContent(
            state = GroupCalendarUiState(
                loading = false,
                groupName = "Lake District crew",
                membersLine = "6 members · you're an admin",
                requirementSummary = "A weekend · Fri from 18:00 → Sun · Nov – Jan",
                isAdmin = true,
                heading = "November 2026",
                syncBanner = SyncBannerUi(4, 6, "2 people still to sync", "Leo and Jade haven't added busy times", true),
                detail = DayDetailUi(LocalDate.of(2026, 11, 13), "Fri 13 – Sun 15 Nov", "Everyone synced is free from Fri 18:00", true, "Propose this weekend", Slot("", "", "")),
            ),
            onBack = {}, onOpenMembers = {}, onOpenTiming = {}, onNudge = {}, onSelectView = {}, onSelectDate = {},
            onPrevious = {}, onNext = {}, onRetry = {}, onPropose = {}, onSeeWhosFree = {},
        )
    }
}
