package dev.joeherbert.availabliss.feature.mytime

import dev.joeherbert.availabliss.core.common.DateTimes
import dev.joeherbert.availabliss.core.network.model.BusyInterval
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.floor

/*
 * "My time" layout: every busy interval placed at its exact start and end on
 * each day's 24-hour axis. Overlapping intervals share the day side by side;
 * intervals covering whole days are kept apart as all-day items. Pure, so it is
 * unit-testable (mirrors web `mytime/parts.ts`).
 */

const val DAY_MINUTES = 24 * 60

/** Tapping an empty slot suggests an hour. */
const val DEFAULT_BLOCK_MINUTES = 60

/** A timed interval (or the piece of it on one day), positioned on the 24-hour axis. */
data class PlacedInterval(
    val interval: BusyInterval,
    val day: LocalDate,
    /** Minutes from the start of [day] on the 24-hour axis, clamped to the day. */
    val from: Float,
    val to: Float,
    /** Side-by-side position among overlapping intervals. */
    val lane: Int,
    val lanes: Int,
    /** The interval carries on from the previous day / into the next. */
    val continuesBefore: Boolean,
    val continuesAfter: Boolean,
) {
    val minutes: Float get() = to - from
}

data class DaysLayout(
    val days: List<LocalDate>,
    /** Intervals that cover whole days, per day. */
    val allDay: Map<LocalDate, List<BusyInterval>>,
    /** Timed intervals per day, with lanes. */
    val timed: Map<LocalDate, List<PlacedInterval>>,
) {
    val hasAllDay: Boolean get() = allDay.values.any { it.isNotEmpty() }

    companion object {
        val Empty = DaysLayout(emptyList(), emptyMap(), emptyMap())
    }
}

/** A minutes-since-midnight range on one day; [to] may be 1440 (24:00). */
data class MinuteRange(val from: Int, val to: Int)

/** An item with its side-by-side lane among overlapping items. */
data class Laned<T>(val item: T, val lane: Int, val lanes: Int)

/** Minutes from local midnight starting [day] to [instant]; negative before the day. */
internal fun minutesIntoDay(instant: Instant, day: LocalDate, zone: ZoneId): Float =
    (instant.epochSecond - day.atStartOfDay(zone).toEpochSecond()) / 60f

/** Length of [day] in minutes (1380 / 1500 on DST changes). */
fun dayLength(day: LocalDate, zone: ZoneId): Int =
    ((day.plusDays(1).atStartOfDay(zone).toEpochSecond() - day.atStartOfDay(zone).toEpochSecond()) / 60).toInt()

/** Assigns lanes so overlapping items sit side by side; each overlap cluster shares a lane count. */
fun <T> assignLanes(items: List<T>, from: (T) -> Float, to: (T) -> Float): List<Laned<T>> {
    val sorted = items.sortedWith(compareBy<T> { from(it) }.thenByDescending { to(it) })
    val out = ArrayList<Laned<T>>(items.size)
    val cluster = ArrayList<Pair<T, Int>>()
    val laneEnds = ArrayList<Float>()
    var clusterEnd = Float.NEGATIVE_INFINITY

    fun flush() {
        cluster.forEach { (item, lane) -> out += Laned(item, lane, laneEnds.size) }
        cluster.clear()
        laneEnds.clear()
    }

    for (item in sorted) {
        val start = from(item)
        val end = to(item)
        if (start >= clusterEnd) flush()
        var lane = laneEnds.indexOfFirst { it <= start }
        if (lane == -1) {
            laneEnds += end
            lane = laneEnds.lastIndex
        } else {
            laneEnds[lane] = end
        }
        cluster += item to lane
        clusterEnd = maxOf(clusterEnd, end)
    }
    flush()
    return out
}

private data class Piece(
    val interval: BusyInterval,
    val from: Float,
    val to: Float,
    val continuesBefore: Boolean,
    val continuesAfter: Boolean,
)

/** Lays out [intervals] over [days] in [zone]. Unparseable intervals are skipped. */
fun layoutDays(days: List<LocalDate>, intervals: List<BusyInterval>, zone: ZoneId = DateTimes.zone): DaysLayout {
    val parsed = intervals.mapNotNull { interval ->
        val start = DateTimes.parseInstant(interval.start) ?: return@mapNotNull null
        val end = DateTimes.parseInstant(interval.end) ?: return@mapNotNull null
        Triple(interval, start, end)
    }
    val allDay = days.associateWith { mutableListOf<BusyInterval>() }
    val timed = days.associateWith { day ->
        val length = dayLength(day, zone)
        // Scale DST days onto the 24-hour axis so 23:00 still lines up with 23:00.
        val scale = DAY_MINUTES.toFloat() / length
        val pieces = parsed.mapNotNull { (interval, startInstant, endInstant) ->
            val start = minutesIntoDay(startInstant, day, zone)
            val end = minutesIntoDay(endInstant, day, zone)
            when {
                end <= 0 || start >= length -> null
                // Ending at 23:59 or later reaches midnight (some calendars end all-day events at 23:59:59).
                start <= 0 && end >= length - 1 -> {
                    allDay.getValue(day) += interval
                    null
                }
                else -> Piece(
                    interval = interval,
                    from = maxOf(0f, start) * scale,
                    to = minOf(length.toFloat(), end) * scale,
                    continuesBefore = start < 0,
                    continuesAfter = end > length,
                )
            }
        }
        assignLanes(pieces, { it.from }, { it.to }).map { (p, lane, lanes) ->
            PlacedInterval(p.interval, day, p.from, p.to, lane, lanes, p.continuesBefore, p.continuesAfter)
        }
    }
    return DaysLayout(days, allDay, timed)
}

/** The range a tap on an empty slot suggests: the hour it falls in. */
fun tapRange(at: Float): MinuteRange {
    val from = minOf(floor(at / 60).toInt() * 60, DAY_MINUTES - DEFAULT_BLOCK_MINUTES).coerceAtLeast(0)
    return MinuteRange(from, from + DEFAULT_BLOCK_MINUTES)
}

/** "09:15" for minutes since midnight; 1440 is "24:00". */
fun clock(minutes: Float): String {
    val m = Math.round(minutes)
    return String.format(Locale.ROOT, "%02d:%02d", m / 60, m % 60)
}
