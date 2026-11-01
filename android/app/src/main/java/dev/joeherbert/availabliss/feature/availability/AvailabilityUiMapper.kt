package dev.joeherbert.availabliss.feature.availability

import dev.joeherbert.availabliss.core.common.DateTimes
import dev.joeherbert.availabliss.core.common.firstName
import dev.joeherbert.availabliss.core.common.joinNames
import dev.joeherbert.availabliss.core.network.model.Availability
import dev.joeherbert.availabliss.core.network.model.AvailabilityDay
import dev.joeherbert.availabliss.core.network.model.DayAvailability
import dev.joeherbert.availabliss.core.network.model.DayStatus
import dev.joeherbert.availabliss.core.network.model.Group
import dev.joeherbert.availabliss.core.network.model.Slot
import dev.joeherbert.availabliss.core.network.model.SlotPosition
import dev.joeherbert.availabliss.designsystem.component.availability.BandPosition
import dev.joeherbert.availabliss.designsystem.component.availability.CellState
import dev.joeherbert.availabliss.designsystem.component.availability.EveryoneBandUi
import dev.joeherbert.availabliss.designsystem.component.availability.MonthCellUi
import dev.joeherbert.availabliss.designsystem.component.availability.TimelineBarUi
import dev.joeherbert.availabliss.designsystem.component.availability.TimelineRowUi
import dev.joeherbert.availabliss.designsystem.component.availability.WeekBlockUi
import dev.joeherbert.availabliss.designsystem.component.availability.WeekColumnUi
import dev.joeherbert.availabliss.designsystem.component.availability.timelineFraction
import dev.joeherbert.availabliss.feature.timing.slotNoun
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth

/** Sync banner model ("2 people still to sync"). */
data class SyncBannerUi(
    val synced: Int,
    val total: Int,
    val title: String,
    val subtitle: String,
    val canNudge: Boolean,
)

/** Detail card under the month for the selected day. */
data class DayDetailUi(
    val date: LocalDate,
    val title: String,
    val subtitle: String,
    val works: Boolean,
    /** "Propose this weekend"; null when the day isn't part of a slot. */
    val proposeLabel: String?,
    val slot: Slot?,
)

/**
 * Pure mapping from API models to the availability UI models. Kept out of the
 * composables (and testable) per the app's unidirectional data flow.
 */
object AvailabilityUiMapper {

    /** A slot's local start/end; all-day ranges are end-exclusive at midnight. */
    private data class LocalSlot(val slot: Slot, val start: LocalDateTime, val end: LocalDateTime) {
        val lastDate: LocalDate
            get() = if (end.toLocalTime() == LocalTime.MIDNIGHT && end.toLocalDate().isAfter(start.toLocalDate())) end.toLocalDate().minusDays(1) else end.toLocalDate()

        fun covers(date: LocalDate) = !date.isBefore(start.toLocalDate()) && !date.isAfter(lastDate)
    }

    private fun localSlots(availability: Availability): List<LocalSlot> = availability.slots.mapNotNull { slot ->
        val s = DateTimes.localDateTime(slot.start) ?: return@mapNotNull null
        val e = DateTimes.localDateTime(slot.end) ?: return@mapNotNull null
        LocalSlot(slot, s, e)
    }

    fun slotFor(availability: Availability, date: LocalDate): Slot? = localSlots(availability).firstOrNull { it.covers(date) }?.slot

    /** Visible weekday columns, Monday first. Falls back to all seven. */
    fun columns(availability: Availability?): List<Int> =
        availability?.visibleWeekdays?.filter { it in 0..6 }?.distinct()?.sorted()?.takeIf { it.isNotEmpty() } ?: (0..6).toList()

    fun columnLabels(columns: List<Int>): List<String> = columns.map { DateTimes.shortWeekday(it) }

    /** "Fri–Sun only" when some weekdays are dropped. */
    fun daysNote(columns: List<Int>): String? {
        if (columns.size == 7) return null
        val contiguous = columns.zipWithNext().all { (a, b) -> b == a + 1 }
        return if (contiguous && columns.size > 1) {
            "${DateTimes.shortWeekday(columns.first())}–${DateTimes.shortWeekday(columns.last())} only"
        } else {
            columns.joinToString(", ") { DateTimes.shortWeekday(it) } + " only"
        }
    }

    /** Week rows of the month, with only the visible columns; dates outside the month are null. */
    fun monthRows(
        month: YearMonth,
        availability: Availability?,
        group: Group?,
        meId: String?,
        selected: LocalDate?,
        today: LocalDate,
    ): List<List<MonthCellUi?>> {
        val columns = columns(availability)
        val days = availability?.days?.associateBy { it.date }.orEmpty()
        val slots = availability?.let(::localSlots).orEmpty()
        val first = month.atDay(1)
        var weekStart = DateTimes.startOfWeek(first)
        val rows = mutableListOf<List<MonthCellUi?>>()
        while (!weekStart.isAfter(month.atEndOfMonth())) {
            val row = columns.map { col ->
                val date = weekStart.plusDays(col.toLong())
                if (YearMonth.from(date) != month) null else cell(date, days[date.toString()], slots, group, meId, selected, today)
            }
            if (row.any { it != null }) rows += row
            weekStart = weekStart.plusWeeks(1)
        }
        return rows
    }

    private fun cell(
        date: LocalDate,
        day: AvailabilityDay?,
        slots: List<LocalSlot>,
        group: Group?,
        meId: String?,
        selected: LocalDate?,
        today: LocalDate,
    ): MonthCellUi {
        val state = when (day?.status) {
            DayStatus.Match -> CellState.Match
            DayStatus.Busy -> CellState.Busy
            DayStatus.Hidden -> CellState.Hidden
            DayStatus.Free, null -> CellState.Free
        }
        val band = when (day?.slotPosition) {
            SlotPosition.Start -> BandPosition.Start
            SlotPosition.Middle -> BandPosition.Middle
            SlotPosition.End -> BandPosition.End
            SlotPosition.Single -> BandPosition.Single
            null -> null
        }
        val slot = slots.firstOrNull { it.covers(date) }
        val busyNames = day?.busyMemberIds.orEmpty().map { name(group, it, meId) }
        val note = when {
            state == CellState.Match && slot != null && date == slot.start.toLocalDate() && slot.start.toLocalTime() != LocalTime.MIDNIGHT ->
                "from ${DateTimes.time(slot.start)}"
            state == CellState.Match -> "all free"
            state == CellState.Busy && busyNames.size == 1 -> "${busyNames.first()} busy"
            state == CellState.Busy && busyNames.size > 1 -> "${busyNames.size} busy"
            day?.holiday != null -> day.holiday
            date == today -> "Today"
            else -> ""
        }
        val dateLabel = "${DateTimes.longWeekday(DateTimes.apiWeekday(date))} ${DateTimes.dayMonth(date)}"
        val stateLabel = when (state) {
            CellState.Match -> "works for everyone"
            CellState.Busy -> if (busyNames.isEmpty()) "someone's busy" else "${busyNames.joinNames()} busy"
            CellState.Hidden -> "outside the timing"
            CellState.Free -> "free"
        }
        return MonthCellUi(
            date = date,
            state = state,
            band = band,
            note = note,
            selected = date == selected,
            accessibilityLabel = listOfNotNull(dateLabel, stateLabel, day?.holiday, if (date == today) "today" else null).joinToString(", "),
        )
    }

    fun detail(date: LocalDate, availability: Availability?, group: Group?, meId: String?, today: LocalDate): DayDetailUi {
        val day = availability?.days?.firstOrNull { it.date == date.toString() }
        val local = availability?.let(::localSlots)?.firstOrNull { it.covers(date) }
        val dayTitle = if (date == today) "Today · ${DateTimes.shortDay(date)}" else DateTimes.shortDay(date)
        val holiday = day?.holiday?.let { " · $it" }.orEmpty()
        if (local != null) {
            val from = if (local.start.toLocalTime() == LocalTime.MIDNIGHT) DateTimes.shortDay(local.start.toLocalDate())
            else "${DateTimes.shortWeekday(DateTimes.apiWeekday(local.start.toLocalDate()))} ${DateTimes.time(local.start)}"
            val noun = group?.requirement?.slotNoun() ?: "time"
            return DayDetailUi(
                date = date,
                title = local.slot.label.ifBlank { dayTitle },
                subtitle = "Everyone synced is free from $from$holiday",
                works = true,
                proposeLabel = "Propose this $noun",
                slot = local.slot,
            )
        }
        val busy = day?.busyMemberIds.orEmpty().map { name(group, it, meId) }
        val subtitle = when {
            day == null -> "No availability for this day yet"
            day.status == DayStatus.Hidden -> "Outside the group's timing"
            busy.size == 1 -> "${busy.first()} is busy"
            busy.isNotEmpty() -> "${busy.joinNames()} are busy"
            date.isBefore(today) -> "This day has already passed"
            else -> "Everyone's free, but not for a whole ${group?.requirement?.slotNoun() ?: "slot"}"
        }
        return DayDetailUi(date, dayTitle, subtitle + holiday, works = false, proposeLabel = null, slot = null)
    }

    fun syncBanner(availability: Availability?, group: Group?, isAdmin: Boolean): SyncBannerUi? {
        val pending = availability?.pendingMemberIds.orEmpty()
        if (availability == null || pending.isEmpty()) return null
        val names = pending.map { id -> group?.members?.firstOrNull { it.userId == id }?.name?.firstName() ?: "Someone" }
        val n = pending.size
        return SyncBannerUi(
            synced = availability.syncedCount,
            total = availability.memberCount,
            title = if (n == 1) "1 person still to sync" else "$n people still to sync",
            subtitle = "${names.joinNames()} ${if (n == 1) "hasn't" else "haven't"} added busy times",
            canNudge = isAdmin,
        )
    }

    /** Whether a slot should be proposed as all-day (midnight to midnight). */
    fun isAllDay(slot: Slot): Boolean {
        val s = DateTimes.localDateTime(slot.start) ?: return false
        val e = DateTimes.localDateTime(slot.end) ?: return false
        return s.toLocalTime() == LocalTime.MIDNIGHT && (e.toLocalTime() == LocalTime.MIDNIGHT || e.toLocalTime() >= LocalTime.of(23, 59))
    }

    // region Week / day
    private fun minutes(iso: String): Int? = DateTimes.localDateTime(iso)?.let { it.hour * 60 + it.minute }

    /** Clamped [start, end) minutes of an interval on [date]. */
    private fun clampToDate(start: String, end: String, date: LocalDate): Pair<Int, Int>? {
        val s = DateTimes.localDateTime(start) ?: return null
        val e = DateTimes.localDateTime(end) ?: return null
        val dayStart = date.atStartOfDay()
        val dayEnd = date.plusDays(1).atStartOfDay()
        if (!e.isAfter(dayStart) || !s.isBefore(dayEnd)) return null
        val sm = if (s.isBefore(dayStart)) 0 else s.hour * 60 + s.minute
        val em = if (!e.isBefore(dayEnd)) 24 * 60 else e.hour * 60 + e.minute
        return if (em > sm) sm to em else null
    }

    fun everyoneBands(day: DayAvailability): List<EveryoneBandUi> = day.everyoneFree.mapNotNull { interval ->
        val date = DateTimes.parseDate(day.date) ?: return@mapNotNull null
        val (s, e) = clampToDate(interval.start, interval.end, date) ?: return@mapNotNull null
        val from = "%02d:%02d".format(s / 60, s % 60)
        val to = if (e >= 23 * 60 + 59) "late" else "%02d:%02d".format(e / 60, e % 60)
        EveryoneBandUi(timelineFraction(s), timelineFraction(e), "$from – $to")
    }.filter { it.end > it.start }

    fun timelineRows(day: DayAvailability, group: Group?, meId: String?): List<TimelineRowUi> {
        val date = DateTimes.parseDate(day.date) ?: return emptyList()
        // Members in group order, current user first as in the concept.
        val order = group?.members?.map { it.userId }.orEmpty()
        val sorted = day.members.sortedWith(compareBy({ it.userId != meId }, { order.indexOf(it.userId).let { i -> if (i < 0) Int.MAX_VALUE else i } }))
        return sorted.map { member ->
            val bars = member.busy.mapNotNull { b ->
                val (s, e) = clampToDate(b.start, b.end, date) ?: return@mapNotNull null
                TimelineBarUi(timelineFraction(s), timelineFraction(e), b.manual)
            }.filter { it.end > it.start }
            val name = name(group, member.userId, meId, youSuffix = true)
            TimelineRowUi(
                name = name,
                pending = member.pending,
                bars = bars,
                accessibilityLabel = when {
                    member.pending -> "$name, not synced yet"
                    bars.isEmpty() -> "$name, free all day"
                    else -> "$name, busy ${bars.size} ${if (bars.size == 1) "time" else "times"}"
                },
            )
        }
    }

    fun dayNote(day: DayAvailability): String? {
        val window = day.window ?: return null
        val start = minutes(window.start) ?: return null
        return if (start == 0) null else "Timing starts %02d:%02d".format(start / 60, start % 60)
    }

    /** Week columns for the visible days: accent "everyone free" blocks and faded busy blocks. */
    fun weekColumns(days: List<DayAvailability>, group: Group?, meId: String?): List<WeekColumnUi> = days.mapNotNull { day ->
        val date = DateTimes.parseDate(day.date) ?: return@mapNotNull null
        val free = day.everyoneFree.mapNotNull { clampToDate(it.start, it.end, date) }
        val freeBlocks = free.map { (s, e) ->
            val label = when {
                s <= 8 * 60 && e >= 24 * 60 - 1 -> "Everyone free all day"
                s > 8 * 60 && e >= 24 * 60 - 1 -> "Free from %02d:%02d".format(s / 60, s % 60)
                else -> "Everyone free"
            }
            WeekBlockUi(timelineFraction(s), timelineFraction(e), label, free = true)
        }
        val busyBlocks = mergeBusy(day, date, group, meId).map { (range, names) ->
            WeekBlockUi(timelineFraction(range.first), timelineFraction(range.second), names, free = false)
        }
        val blocks = (busyBlocks + freeBlocks).filter { it.end > it.start }
        WeekColumnUi(
            label = DateTimes.weekdayAndDay(date),
            blocks = blocks,
            accessibilityLabel = DateTimes.shortDay(date) + ": " + (freeBlocks.joinToString { it.label }.ifEmpty { "no time when everyone's free" }),
        )
    }

    /** Union of synced members' busy time on [date], labelled with who's busy. */
    private fun mergeBusy(day: DayAvailability, date: LocalDate, group: Group?, meId: String?): List<Pair<Pair<Int, Int>, String>> {
        data class Piece(val s: Int, val e: Int, val who: String)
        val pieces = day.members.filterNot { it.pending }.flatMap { m ->
            m.busy.mapNotNull { b -> clampToDate(b.start, b.end, date)?.let { Piece(it.first, it.second, name(group, m.userId, meId, youSuffix = false)) } }
        }.sortedBy { it.s }
        val merged = mutableListOf<Triple<Int, Int, MutableSet<String>>>()
        pieces.forEach { p ->
            val last = merged.lastOrNull()
            if (last != null && p.s <= last.second) {
                merged[merged.lastIndex] = Triple(last.first, maxOf(last.second, p.e), last.third.apply { add(p.who) })
            } else {
                merged += Triple(p.s, p.e, mutableSetOf(p.who))
            }
        }
        return merged.map { (s, e, who) ->
            val label = if (who.size <= 2) who.joinToString(", ") else who.take(2).joinToString(", ") + " +${who.size - 2}"
            (s to e) to label
        }
    }
    // endregion

    fun name(group: Group?, userId: String, meId: String?, youSuffix: Boolean = false): String {
        val first = group?.members?.firstOrNull { it.userId == userId }?.name?.firstName() ?: "Someone"
        return if (userId == meId && youSuffix) "$first (you)" else first
    }
}
