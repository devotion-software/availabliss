package dev.joeherbert.availabliss.feature.timing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.joeherbert.availabliss.core.common.DateTimes
import dev.joeherbert.availabliss.core.network.model.DateRange
import dev.joeherbert.availabliss.core.network.model.RequirementLength
import dev.joeherbert.availabliss.designsystem.component.AvBottomSheet
import dev.joeherbert.availabliss.designsystem.component.AvDatePickerDialog
import dev.joeherbert.availabliss.designsystem.component.AvDateRangePickerDialog
import dev.joeherbert.availabliss.designsystem.component.AvSnackbarHost
import dev.joeherbert.availabliss.designsystem.component.AvTimePickerDialog
import dev.joeherbert.availabliss.designsystem.component.AvailablissBackground
import dev.joeherbert.availabliss.designsystem.component.Chip
import dev.joeherbert.availabliss.designsystem.component.DashedChip
import dev.joeherbert.availabliss.designsystem.component.ErrorBanner
import dev.joeherbert.availabliss.designsystem.component.FieldTile
import dev.joeherbert.availabliss.designsystem.component.LoadingIndicator
import dev.joeherbert.availabliss.designsystem.component.OrbLayout
import dev.joeherbert.availabliss.designsystem.component.PillTone
import dev.joeherbert.availabliss.designsystem.component.PlainIconButton
import dev.joeherbert.availabliss.designsystem.component.PrimaryButton
import dev.joeherbert.availabliss.designsystem.component.RemovableChip
import dev.joeherbert.availabliss.designsystem.component.RowDivider
import dev.joeherbert.availabliss.designsystem.component.ScreenHeader
import dev.joeherbert.availabliss.designsystem.component.SectionLabel
import dev.joeherbert.availabliss.designsystem.component.Segment
import dev.joeherbert.availabliss.designsystem.component.SegmentedControl
import dev.joeherbert.availabliss.designsystem.component.SegmentedStyle
import dev.joeherbert.availabliss.designsystem.component.StatusPill
import dev.joeherbert.availabliss.designsystem.component.ToggleRow
import dev.joeherbert.availabliss.designsystem.component.UserMessageEffect
import dev.joeherbert.availabliss.designsystem.component.ValueBox
import dev.joeherbert.availabliss.designsystem.component.glass
import dev.joeherbert.availabliss.designsystem.component.rememberSnackbarHostState
import dev.joeherbert.availabliss.designsystem.icon.AvIcons
import dev.joeherbert.availabliss.designsystem.theme.AvailablissPreviewTheme
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import dev.joeherbert.availabliss.designsystem.theme.Shapes
import dev.joeherbert.availabliss.designsystem.theme.Spacing
import java.time.LocalDate

/** Which picker is open; UI-only state. */
private enum class TimingPicker { None, StartTime, EndTime, SpanStart, SpanEnd, RangeFrom, RangeTo, Dates, SchoolHolidays }

/** Group timing editor (Requirements.dc.html). Read-only for non-admins. */
@Composable
fun GroupTimingTab(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: GroupTimingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = rememberSnackbarHostState()
    UserMessageEffect(state.message, snackbar, viewModel::messageShown)
    LaunchedEffect(state.saved) {
        if (state.saved) {
            viewModel.savedHandled()
            onSaved()
        }
    }
    GroupTimingContent(
        state = state,
        actions = viewModel.editor.actions(onBack = onBack, onRetry = viewModel::load, onSave = viewModel::save),
        snackbar = { AvSnackbarHost(snackbar) },
    )
}

/** All callbacks of the timing editor, bundled to keep signatures readable. */
data class TimingActions(
    val onBack: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onPreset: (TimingPreset) -> Unit = {},
    val onCustomKind: (CustomKind) -> Unit = {},
    val onDuration: (Int) -> Unit = {},
    val onDayCount: (Int) -> Unit = {},
    val onWeeksLater: (Boolean) -> Unit = {},
    val onToggleDay: (Int) -> Unit = {},
    val onBankHolidays: (Boolean) -> Unit = {},
    val onSchoolHolidays: (Boolean) -> Unit = {},
    val onStartTime: (java.time.LocalTime) -> Unit = {},
    val onEndTime: (java.time.LocalTime) -> Unit = {},
    val onSpanStart: (SpanEnd) -> Unit = {},
    val onSpanEnd: (SpanEnd) -> Unit = {},
    val onRangeFrom: (LocalDate) -> Unit = {},
    val onRangeTo: (LocalDate) -> Unit = {},
    val onAddRange: (RangeList, LocalDate, LocalDate) -> Unit = { _, _, _ -> },
    val onRemoveRange: (RangeList, DateRange) -> Unit = { _, _ -> },
    val onSave: () -> Unit = {},
)

@Composable
fun GroupTimingContent(state: GroupTimingUiState, actions: TimingActions, snackbar: @Composable () -> Unit = {}) {
    AvailablissBackground(OrbLayout.Timing) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            ScreenHeader(
                title = "Timing",
                subtitle = state.groupName,
                onBack = actions.onBack,
                backDescription = "Back to calendar",
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
            ) {
                StatusPill(
                    "Admins only",
                    PillTone.Indigo,
                    icon = AvIcons.Shield,
                    padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 9.dp, vertical = 5.dp),
                )
            }
            val editor = state.editor
            when {
                state.loading -> LoadingIndicator()
                editor == null -> ErrorBanner(state.error ?: "Couldn't load the timing.", Modifier.padding(20.dp), onRetry = actions.onRetry)
                else -> Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 120.dp)
                        // The floating save bar sits above the navigation bar, so clear that too.
                        .navigationBarsPadding()
                        .alpha(if (state.canEdit) 1f else 0.85f),
                    verticalArrangement = Arrangement.spacedBy(Spacing.l),
                ) {
                    if (!state.canEdit) {
                        Text(
                            "Only admins can change the timing.",
                            style = AvailablissTheme.type.caption,
                            color = AvailablissTheme.colors.muted,
                        )
                    }
                    TimingEditorSections(editor, state.canEdit, actions)
                }
            }
        }
        state.editor?.let { editor ->
            SaveBar(state, editor, actions.onSave, Modifier.align(Alignment.BottomCenter))
        }
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 110.dp)) { snackbar() }
    }
}

/**
 * The timing editor's sections (length, starts and ends, holidays, search range) and
 * their pickers. Shared by the group Timing tab and New group's second step.
 */
@Composable
fun TimingEditorSections(state: TimingEditorUiState, canEdit: Boolean, actions: TimingActions) {
    var picker by rememberSaveable { mutableStateOf(TimingPicker.None) }
    val draft = state.draft
    LengthSection(draft, canEdit, actions)
    StartsEndsSection(state, canEdit, draft, actions, openPicker = { picker = it })
    ExtrasSection(state, canEdit, draft, actions, openPicker = { picker = it })
    RangeSection(state, canEdit, draft, actions, openPicker = { picker = it })
    TimingPickers(state, picker, actions, close = { picker = TimingPicker.None })
}

/** "13 weekends / fit this timing between 2 Nov and 1 Feb", with a spinner while counting. */
@Composable
fun FitSummary(state: TimingEditorUiState, modifier: Modifier = Modifier) {
    val colors = AvailablissTheme.colors
    Row(modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f, fill = false)) {
            Text(state.summaryTitle, style = AvailablissTheme.type.titleMedium.copy(fontWeight = FontWeight.Bold), color = colors.ink, maxLines = 1)
            Text(state.summarySubtitle, style = AvailablissTheme.type.caption.copy(fontWeight = FontWeight.Normal), color = colors.muted, maxLines = 1)
        }
        if (state.counting) {
            CircularProgressIndicator(Modifier.padding(start = 8.dp).size(14.dp), color = colors.muted, strokeWidth = 2.dp)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LengthSection(draft: TimingDraft, enabled: Boolean, actions: TimingActions) {
    Section {
        SectionLabel("How long?")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TimingPreset.entries.forEach { preset ->
                Chip(preset.label, selected = draft.preset == preset, onClick = { if (enabled) actions.onPreset(preset) }, height = 40.dp)
            }
        }
        if (draft.preset == TimingPreset.Custom) {
            SegmentedControl(
                segments = CustomKind.entries.map { Segment(it, it.label) },
                selected = draft.customKind,
                onSelect = { if (enabled) actions.onCustomKind(it) },
                style = SegmentedStyle.Inset,
                modifier = Modifier.fillMaxWidth(),
            )
            when (val l = draft.length) {
                is RequirementLength.Duration -> Stepper(TimingLabels.duration(l.minutes), "duration", enabled, onMinus = { actions.onDuration(-30) }, onPlus = { actions.onDuration(30) })
                is RequirementLength.Days -> Stepper(TimingLabels.days(l.count), "number of days", enabled, onMinus = { actions.onDayCount(-1) }, onPlus = { actions.onDayCount(1) })
                is RequirementLength.Span -> ToggleRow(
                    title = "Ends the following week",
                    subtitle = "e.g. Tue → the Thursday after next",
                    checked = l.weeksLater == 1,
                    onCheckedChange = { if (enabled) actions.onWeeksLater(it) },
                )
            }
        }
    }
}

@Composable
private fun Stepper(value: String, what: String, enabled: Boolean, onMinus: () -> Unit, onPlus: () -> Unit) {
    val colors = AvailablissTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(Shapes.control).background(colors.surface).border(1.dp, colors.glassBorder, Shapes.control).padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlainIconButton(AvIcons.Back, "Decrease $what", onMinus, enabled = enabled, size = 44.dp, iconSize = 16.dp)
        Text(value, style = AvailablissTheme.type.bodyMedium, color = colors.ink, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        PlainIconButton(AvIcons.Forward, "Increase $what", onPlus, enabled = enabled, size = 44.dp, iconSize = 16.dp)
    }
}

@Composable
private fun StartsEndsSection(state: TimingEditorUiState, canEdit: Boolean, draft: TimingDraft, actions: TimingActions, openPicker: (TimingPicker) -> Unit) {
    val colors = AvailablissTheme.colors
    Section {
        SectionLabel("Starts and ends")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldTile(
                "Starts",
                state.startsLabel,
                onClick = { if (canEdit) openPicker(if (draft.isSpan) TimingPicker.SpanStart else TimingPicker.StartTime) },
                modifier = Modifier.weight(1f),
            )
            FieldTile(
                "Ends",
                state.endsLabel,
                onClick = { if (canEdit) openPicker(if (draft.isSpan) TimingPicker.SpanEnd else TimingPicker.EndTime) },
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            Modifier.fillMaxWidth().semantics { contentDescription = "Days it can fall on" },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            (0..6).forEach { day ->
                val on = day in state.highlightedDays
                val toggleable = canEdit && !draft.isSpan
                Box(
                    Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(Shapes.controlSmall)
                        .background(if (on) colors.accent else colors.surface)
                        .border(1.dp, if (on) colors.accent else colors.glassBorder, Shapes.controlSmall)
                        .toggleable(value = on, enabled = toggleable, role = Role.Checkbox, onValueChange = { actions.onToggleDay(day) })
                        .semantics { contentDescription = DateTimes.longWeekday(day) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        DateTimes.narrowWeekday(day),
                        style = AvailablissTheme.type.bodySmallStrong.copy(fontWeight = FontWeight.Bold),
                        color = if (on) colors.onAccent else colors.ink,
                    )
                }
            }
        }
        val hint = when {
            draft.isSpan -> "Set by when it starts and ends"
            draft.days.isEmpty() -> "Any day of the week"
            else -> null
        }
        hint?.let { Text(it, style = AvailablissTheme.type.caption.copy(fontWeight = FontWeight.Normal), color = colors.muted) }
    }
}

@Composable
private fun ExtrasSection(state: TimingEditorUiState, canEdit: Boolean, draft: TimingDraft, actions: TimingActions, openPicker: (TimingPicker) -> Unit) {
    val colors = AvailablissTheme.colors
    Column(Modifier.fillMaxWidth().glass(Shapes.card).padding(horizontal = 16.dp, vertical = 4.dp)) {
        ToggleRow(
            title = "Bank holidays",
            subtitle = state.bankSubtitle,
            checked = draft.bankHolidays,
            onCheckedChange = { if (canEdit) actions.onBankHolidays(it) },
        )
        RowDivider()
        ToggleRow(
            title = "School holidays only",
            subtitle = state.schoolSubtitle,
            checked = draft.schoolHolidaysOnly,
            onCheckedChange = { if (canEdit) actions.onSchoolHolidays(it) },
        )
        if (draft.schoolHolidaysOnly) {
            // The group's own term breaks, for authorities we don't have dates for (or extra days off).
            Column(Modifier.padding(bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Add your own holiday dates if your school's aren't listed.",
                    style = AvailablissTheme.type.caption.copy(fontWeight = FontWeight.Normal),
                    color = colors.muted,
                )
                RangeChips(
                    ranges = draft.customSchoolHolidays,
                    canEdit = canEdit,
                    addLabel = "+ Add school holiday",
                    onAdd = { openPicker(TimingPicker.SchoolHolidays) },
                    onRemove = { actions.onRemoveRange(RangeList.SchoolHolidays, it) },
                )
            }
        }
    }
}

/** Removable chips for a list of date ranges, plus an add chip for editors. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RangeChips(ranges: List<DateRange>, canEdit: Boolean, addLabel: String, onAdd: () -> Unit, onRemove: (DateRange) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ranges.forEach { range ->
            val label = TimingLabels.dateRange(range.from, range.to)
            RemovableChip(label, "Remove $label", onRemove = { if (canEdit) onRemove(range) })
        }
        if (canEdit) DashedChip(addLabel, onClick = onAdd)
    }
}

@Composable
private fun RangeSection(state: TimingEditorUiState, canEdit: Boolean, draft: TimingDraft, actions: TimingActions, openPicker: (TimingPicker) -> Unit) {
    val colors = AvailablissTheme.colors
    Section {
        SectionLabel("Search between")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ValueBox(
                DateTimes.dayMonthYear(state.rangeFrom),
                onClick = { if (canEdit) openPicker(TimingPicker.RangeFrom) },
                contentDescription = "From ${DateTimes.dayMonthYear(state.rangeFrom)}",
                modifier = Modifier.weight(1f),
            )
            Icon(AvIcons.ArrowRight, null, tint = colors.muted, modifier = Modifier.size(16.dp))
            ValueBox(
                DateTimes.dayMonthYear(state.rangeTo),
                onClick = { if (canEdit) openPicker(TimingPicker.RangeTo) },
                contentDescription = "To ${DateTimes.dayMonthYear(state.rangeTo)}",
                modifier = Modifier.weight(1f),
            )
        }
        SectionLabel("Or only these dates", Modifier.padding(top = 4.dp))
        RangeChips(
            ranges = draft.dates,
            canEdit = canEdit,
            addLabel = "+ Add date range",
            onAdd = { openPicker(TimingPicker.Dates) },
            onRemove = { actions.onRemoveRange(RangeList.Dates, it) },
        )
    }
}

@Composable
private fun SaveBar(state: GroupTimingUiState, editor: TimingEditorUiState, onSave: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AvailablissTheme.colors
    Row(
        modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 12.dp, end = 12.dp, bottom = 16.dp)
            .fillMaxWidth()
            .glass(androidx.compose.foundation.shape.RoundedCornerShape(26.dp))
            .padding(start = 16.dp, top = 12.dp, end = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FitSummary(editor, Modifier.weight(1f))
        if (state.canEdit) {
            PrimaryButton(
                text = "Save timing",
                onClick = onSave,
                height = 48.dp,
                shape = Shapes.control,
                textStyle = AvailablissTheme.type.bodyMedium,
                loading = state.saving,
                enabled = state.dirty,
            )
        }
    }
}

@Composable
private fun Section(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().glass(Shapes.card).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun TimingPickers(state: TimingEditorUiState, picker: TimingPicker, actions: TimingActions, close: () -> Unit) {
    when (picker) {
        TimingPicker.None -> Unit
        TimingPicker.StartTime -> AvTimePickerDialog("Starts", state.startTime, actions.onStartTime, close)
        TimingPicker.EndTime -> AvTimePickerDialog("Ends", state.endTime, actions.onEndTime, close)
        TimingPicker.SpanStart -> SpanEndSheet("Starts", state.spanStart, onDone = { actions.onSpanStart(it); close() }, onDismiss = close)
        TimingPicker.SpanEnd -> SpanEndSheet("Ends", state.spanEnd, onDone = { actions.onSpanEnd(it); close() }, onDismiss = close)
        TimingPicker.RangeFrom -> AvDatePickerDialog(state.rangeFrom, actions.onRangeFrom, close)
        TimingPicker.RangeTo -> AvDatePickerDialog(state.rangeTo, actions.onRangeTo, close, minDate = state.rangeFrom)
        TimingPicker.Dates -> AvDateRangePickerDialog(null, null, { from, to -> actions.onAddRange(RangeList.Dates, from, to) }, close)
        TimingPicker.SchoolHolidays -> AvDateRangePickerDialog(null, null, { from, to -> actions.onAddRange(RangeList.SchoolHolidays, from, to) }, close)
    }
}

/** Picks the weekday and time for one end of a span. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpanEndSheet(title: String, initial: SpanEnd, onDone: (SpanEnd) -> Unit, onDismiss: () -> Unit) {
    var day by remember { mutableStateOf(initial.day) }
    var time by remember { mutableStateOf(initial.time) }
    var pickingTime by remember { mutableStateOf(false) }
    AvBottomSheet(title = title, onDismissRequest = onDismiss) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            (0..6).forEach { d -> Chip(DateTimes.shortWeekday(d), selected = d == day, onClick = { day = d }) }
        }
        FieldTile("Time", DateTimes.time(time), onClick = { pickingTime = true }, modifier = Modifier.fillMaxWidth())
        PrimaryButton("Done", onClick = { onDone(SpanEnd(day, time)) }, height = 52.dp, shape = Shapes.control, modifier = Modifier.fillMaxWidth())
    }
    if (pickingTime) AvTimePickerDialog(title, time, { time = it }, { pickingTime = false })
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun GroupTimingPreview() {
    val draft = RequirementPresets.applyPreset(RequirementPresets.defaultDraft(LocalDate.of(2026, 11, 1), "Europe/London"), TimingPreset.Weekend)
    AvailablissPreviewTheme {
        GroupTimingContent(
            state = GroupTimingUiState(
                loading = false,
                groupName = "Lake District crew",
                canEdit = true,
                editor = TimingEditorUiState(
                    draft = draft,
                    startsLabel = TimingLabels.starts(draft),
                    endsLabel = TimingLabels.ends(draft),
                    summaryTitle = TimingLabels.title(draft),
                    summarySubtitle = TimingLabels.searchRange(draft),
                    highlightedDays = setOf(4, 5, 6),
                ),
                dirty = true,
            ),
            actions = TimingActions(),
        )
    }
}
