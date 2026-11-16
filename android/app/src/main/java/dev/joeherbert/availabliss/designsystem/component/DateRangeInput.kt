package dev.joeherbert.availabliss.designsystem.component

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DatePicker
import androidx.compose.material3.Icon
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import dev.joeherbert.availabliss.core.common.DateTimes
import dev.joeherbert.availabliss.core.common.TimeRangeDraft
import dev.joeherbert.availabliss.designsystem.icon.AvIcons
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import dev.joeherbert.availabliss.designsystem.theme.Shapes
import kotlinx.coroutines.flow.drop
import java.time.LocalDate
import java.time.LocalTime

/** Which chip's picker is open under a [DateRangeInput]. */
private enum class RangePart { StartDate, StartTime, EndDate, EndTime }

/**
 * The app's one date/time range input: an "All day" switch, then the start (date, time
 * underneath) → the end. Each date and time is a chip; tapping one opens its picker inline
 * under the row (tap it again to close). Moving the start keeps the length; the end can't go
 * before the start (see [TimeRangeDraft]). Every change is reported through [onChange].
 *
 * Draws no card: put it in a [GlassPanel] (screens) or [SurfacePanel] (sheets).
 */
@Composable
fun DateRangeInput(
    draft: TimeRangeDraft,
    onChange: (TimeRangeDraft) -> Unit,
    modifier: Modifier = Modifier,
    /** The earliest start date the picker offers (null: any). */
    minDate: LocalDate? = null,
) {
    val colors = AvailablissTheme.colors
    val type = AvailablissTheme.type
    var open by rememberSaveable { mutableStateOf<RangePart?>(null) }
    // Time pickers have nothing to show once all day is on.
    val shown = open?.takeUnless { draft.allDay && (it == RangePart.StartTime || it == RangePart.EndTime) }
    val current by rememberUpdatedState(draft)
    val change by rememberUpdatedState(onChange)
    fun toggle(part: RangePart) { open = if (shown == part) null else part }

    Column(modifier.fillMaxWidth().animateContentSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ToggleRow(title = "All day", checked = draft.allDay, onCheckedChange = { onChange(draft.withAllDay(it)) }, minHeight = 44.dp)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            RangeSide(
                date = draft.startDate,
                time = draft.startTime.takeUnless { draft.allDay },
                dateOpen = shown == RangePart.StartDate,
                timeOpen = shown == RangePart.StartTime,
                dateLabel = "Starts",
                timeLabel = "Start time",
                onDate = { toggle(RangePart.StartDate) },
                onTime = { toggle(RangePart.StartTime) },
                modifier = Modifier.weight(1f),
            )
            Icon(AvIcons.ArrowRight, contentDescription = null, tint = colors.muted, modifier = Modifier.size(16.dp))
            RangeSide(
                date = draft.endDate,
                time = draft.endTime.takeUnless { draft.allDay },
                dateOpen = shown == RangePart.EndDate,
                timeOpen = shown == RangePart.EndTime,
                dateLabel = "Ends",
                timeLabel = "End time",
                onDate = { toggle(RangePart.EndDate) },
                onTime = { toggle(RangePart.EndTime) },
                modifier = Modifier.weight(1f).padding(start = 6.dp),
            )
        }
        if (!draft.isValid) {
            Text("Ends before it starts", style = type.caption, color = colors.warnInk)
        }
        when (shown) {
            RangePart.StartDate -> key(RangePart.StartDate) {
                InlineDatePicker(draft.startDate, minDate) { date ->
                    change(current.withStart(date))
                    open = null
                }
            }
            RangePart.EndDate -> key(RangePart.EndDate) {
                InlineDatePicker(draft.endDate, draft.startDate) { date ->
                    change(current.withEnd(date))
                    open = null
                }
            }
            RangePart.StartTime -> key(RangePart.StartTime) {
                InlineTimePicker(draft.startTime) { time ->
                    current.withStart(current.startDate, time).also(change).startTime
                }
            }
            RangePart.EndTime -> key(RangePart.EndTime) {
                InlineTimePicker(draft.endTime) { time ->
                    current.withEnd(current.endDate, time).also(change).endTime
                }
            }
            null -> Unit
        }
    }
}

/** One end of the range: the date chip with the time chip underneath (no time when all day). */
@Composable
private fun RangeSide(
    date: LocalDate,
    time: LocalTime?,
    dateOpen: Boolean,
    timeOpen: Boolean,
    dateLabel: String,
    timeLabel: String,
    onDate: () -> Unit,
    onTime: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val type = AvailablissTheme.type
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        RangeChip(
            text = DateTimes.shortDay(date),
            style = type.bodyMedium,
            open = dateOpen,
            description = "$dateLabel, ${DateTimes.longDay(date)}",
            onClick = onDate,
        )
        if (time != null) {
            RangeChip(
                text = DateTimes.time(time),
                style = type.titleLarge.copy(fontFeatureSettings = "tnum"),
                open = timeOpen,
                description = "$timeLabel, ${DateTimes.time(time)}",
                onClick = onTime,
            )
        }
    }
}

/** A tappable date or time: accent text while its picker is open. Min 44dp tall. */
@Composable
private fun RangeChip(text: String, style: TextStyle, open: Boolean, description: String, onClick: () -> Unit) {
    val colors = AvailablissTheme.colors
    Box(
        Modifier
            .defaultMinSize(minHeight = 44.dp, minWidth = 44.dp)
            .clip(Shapes.pill)
            .clickable(role = Role.Button, onClickLabel = if (open) "Close picker" else "Change", onClick = onClick)
            .semantics {
                contentDescription = description
                if (open) stateDescription = "Picker open"
            }
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = style, color = if (open) colors.accentInk else colors.ink, maxLines = 1)
    }
}

/** An inline Material 3 calendar; [onPick] fires when a day is tapped. */
@Composable
private fun InlineDatePicker(value: LocalDate, minDate: LocalDate?, onPick: (LocalDate) -> Unit) {
    val pick by rememberUpdatedState(onPick)
    val state = rememberDatePickerState(
        initialSelectedDateMillis = value.utcMillis(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = minDate == null || !utcTimeMillis.utcDate().isBefore(minDate)
        },
    )
    LaunchedEffect(state) {
        snapshotFlow { state.selectedDateMillis }.drop(1).collect { millis -> millis?.let { pick(it.utcDate()) } }
    }
    val colors = pickerColors().copy(containerColor = Color.Transparent)
    DatePicker(state = state, colors = colors, showModeToggle = false, title = null, headline = null, modifier = Modifier.fillMaxWidth())
}

/**
 * An inline 24-hour clock. [onPick] applies the time and returns the one the range actually
 * took; when that differs (an end before the start snaps back), the clock shows it.
 */
@Composable
private fun InlineTimePicker(value: LocalTime, onPick: (LocalTime) -> LocalTime) {
    val pick by rememberUpdatedState(onPick)
    var sync by remember { mutableStateOf(0 to value) }
    key(sync) {
        val state = rememberTimePickerState(initialHour = sync.second.hour, initialMinute = sync.second.minute, is24Hour = true)
        LaunchedEffect(state) {
            snapshotFlow { state.hour * 60 + state.minute }.drop(1).collect { minutes ->
                val picked = LocalTime.of(minutes / 60, minutes % 60)
                val applied = pick(picked)
                if (applied != picked) sync = (sync.first + 1) to applied
            }
        }
        Box(Modifier.fillMaxWidth().padding(top = 6.dp), contentAlignment = Alignment.Center) {
            TimePicker(state = state, colors = timePickerColors().copy(containerColor = Color.Transparent))
        }
    }
}
