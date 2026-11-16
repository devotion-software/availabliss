package dev.joeherbert.availabliss.feature.mytime

import dev.joeherbert.availabliss.core.common.DateTimes
import dev.joeherbert.availabliss.core.network.model.Repeat
import java.time.LocalDate

/*
 * How a block's repeat reads, built from its first date. Pure, unit-tested.
 */

/** "1st", "2nd", "3rd", "4th", "11th", "22nd". */
internal fun ordinal(n: Int): String {
    val suffix = when {
        n % 100 in 11..13 -> "th"
        n % 10 == 1 -> "st"
        n % 10 == 2 -> "nd"
        n % 10 == 3 -> "rd"
        else -> "th"
    }
    return "$n$suffix"
}

/** The date falls in its month's last 7 days, so `monthlyWeekday` repeats on the last such weekday. */
internal fun isLastWeekdayOfMonth(date: LocalDate): Boolean = date.dayOfMonth > date.lengthOfMonth() - 7

/** "3rd Monday", or "last Monday" in a month's last 7 days. */
internal fun weekdayOfMonth(date: LocalDate): String {
    val weekday = DateTimes.longWeekday(DateTimes.apiWeekday(date))
    val which = if (isLastWeekdayOfMonth(date)) "last" else ordinal((date.dayOfMonth - 1) / 7 + 1)
    return "$which $weekday"
}

/** The Repeat menu's option for [repeat] starting on [start]: "Every month on the 3rd Monday". */
fun repeatOptionLabel(repeat: Repeat, start: LocalDate): String {
    val weekday = DateTimes.longWeekday(DateTimes.apiWeekday(start))
    return when (repeat) {
        Repeat.None -> "Never"
        Repeat.Daily -> "Every day"
        Repeat.Weekdays -> "Every weekday (Mon–Fri)"
        Repeat.Weekly -> "Every week on $weekday"
        Repeat.Fortnightly -> "Every 2 weeks on $weekday"
        Repeat.Monthly -> "Every month on the ${ordinal(start.dayOfMonth)}"
        Repeat.MonthlyWeekday -> "Every month on the ${weekdayOfMonth(start)}"
        Repeat.Yearly -> "Every year on ${DateTimes.dayMonthLong(start)}"
        Repeat.Unknown -> "Repeats"
    }
}

/** The day part of a block's schedule line: "Every Tuesday", "Monthly on the 16th", "Sat 21 Nov". */
fun repeatScheduleLabel(repeat: Repeat, start: LocalDate): String {
    val weekday = DateTimes.longWeekday(DateTimes.apiWeekday(start))
    return when (repeat) {
        Repeat.None -> DateTimes.shortDay(start)
        Repeat.Daily -> "Every day"
        Repeat.Weekdays -> "Weekdays"
        Repeat.Weekly -> "Every $weekday"
        Repeat.Fortnightly -> "Every other $weekday"
        Repeat.Monthly -> "Monthly on the ${ordinal(start.dayOfMonth)}"
        Repeat.MonthlyWeekday -> "Monthly on the ${weekdayOfMonth(start)}"
        Repeat.Yearly -> "Yearly on ${DateTimes.dayMonth(start)}"
        Repeat.Unknown -> "Repeats"
    }
}
