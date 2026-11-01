package dev.joeherbert.availabliss.feature.mytime

import dev.joeherbert.availabliss.core.common.DateTimes
import dev.joeherbert.availabliss.core.network.model.BusyInterval
import dev.joeherbert.availabliss.core.network.model.BusySource
import dev.joeherbert.availabliss.core.network.model.ManualBlock
import dev.joeherbert.availabliss.core.network.model.Repeat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Prefix for optimistic, not-yet-saved block ids. */
internal const val LOCAL_BLOCK_PREFIX = "local-"

private data class ParsedInterval(val start: Instant, val end: Instant, val source: BusyInterval)

/**
 * Builds the 7 × 3 grid for the week starting [weekStart] from the merged busy
 * [intervals]. Calendar busy time wins over manual blocks in the same slot (it can't
 * be toggled here). Pure, so it is unit-testable.
 */
internal fun buildWeek(
    weekStart: LocalDate,
    intervals: List<BusyInterval>,
    blocks: List<ManualBlock>,
    calendarColors: Map<String, String>,
    zone: ZoneId = DateTimes.zone,
): List<DayRowUi> {
    val parsed = intervals.mapNotNull { interval ->
        val s = DateTimes.parseInstant(interval.start) ?: return@mapNotNull null
        val e = DateTimes.parseInstant(interval.end) ?: return@mapNotNull null
        ParsedInterval(s, e, interval)
    }
    val blocksById = blocks.associateBy { it.id }
    return (0L until 7L).map { offset ->
        val date = weekStart.plusDays(offset)
        DayRowUi(
            date = date,
            dayLabel = DateTimes.shortWeekday(DateTimes.apiWeekday(date)),
            dateLabel = date.dayOfMonth.toString(),
            slots = DayPart.entries.map { part ->
                val slotStart = part.startOn(date).atZone(zone).toInstant()
                val slotEnd = part.endOn(date).atZone(zone).toInstant()
                val overlapping = parsed.filter { it.start < slotEnd && it.end > slotStart }
                val calendar = overlapping.firstOrNull { it.source.source == BusySource.Calendar }
                val manual = overlapping.firstOrNull { it.source.source == BusySource.Manual }
                val state = when {
                    calendar != null -> SlotState.Calendar(
                        label = calendar.source.label?.takeIf { it.isNotBlank() } ?: "Busy",
                        dotColor = calendar.source.calendarId?.let(calendarColors::get),
                    )
                    manual != null -> {
                        val id = manual.source.blockId
                        SlotState.Manual(
                            blockId = id,
                            label = id?.let(blocksById::get)?.note?.takeIf { it.isNotBlank() }
                                ?: manual.source.label?.takeIf { it.isNotBlank() }
                                ?: "Busy",
                            pending = id?.startsWith(LOCAL_BLOCK_PREFIX) == true,
                        )
                    }
                    else -> SlotState.Free
                }
                SlotUi(date, part, state)
            },
        )
    }
}

/** True when [block] is exactly the slot [part] on [date] (so tapping it again removes it). */
internal fun ManualBlock.coversExactly(date: LocalDate, part: DayPart, zone: ZoneId = DateTimes.zone): Boolean {
    val s = DateTimes.parseInstant(start) ?: return false
    val e = DateTimes.parseInstant(end) ?: return false
    return s == part.startOn(date).atZone(zone).toInstant() && e == part.endOn(date).atZone(zone).toInstant()
}

/** "Every Tuesday · 19:00 – 21:30", "Every other Friday · All day", "Sat 21 Nov · All day". */
internal fun ManualBlock.scheduleLabel(zone: ZoneId = DateTimes.zone): String {
    val s = DateTimes.parseInstant(start)?.atZone(zone)?.toLocalDateTime() ?: return ""
    val e = DateTimes.parseInstant(end)?.atZone(zone)?.toLocalDateTime() ?: return ""
    val weekday = DateTimes.longWeekday(DateTimes.apiWeekday(s.toLocalDate()))
    val day = when (repeat) {
        Repeat.Weekly -> "Every $weekday"
        Repeat.Fortnightly -> "Every other $weekday"
        Repeat.None -> DateTimes.shortDay(s.toLocalDate())
    }
    val time = if (allDay || (s.toLocalTime() == LocalTime.MIDNIGHT && e.toLocalTime() == LocalTime.MIDNIGHT)) {
        "All day"
    } else {
        "${DateTimes.time(s)} – ${DateTimes.time(e)}"
    }
    val untilLabel = until?.let(DateTimes::parseDate)?.takeIf { repeat != Repeat.None }?.let { " · until ${DateTimes.dayMonth(it)}" }.orEmpty()
    return "$day · $time$untilLabel"
}

internal fun ManualBlock.title(): String = note?.takeIf { it.isNotBlank() } ?: "Busy"
