package dev.joeherbert.availabliss.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What a group is looking for (API.md "Requirements").
 *
 * No property has a default value on purpose: the server validates every field as
 * required (nullable fields must be sent as explicit `null`), and the shared [Json]
 * instance omits properties that equal their default.
 */
@Serializable
data class Requirement(
    val length: RequirementLength,
    val window: TimeWindow?,
    /** Allowed weekdays, 0 = Monday. Empty = any weekday (ignored for span). */
    val days: List<Int>,
    /** Bank holidays (from the group owner's region) count as allowed days. */
    val bankHolidays: Boolean,
    val schoolHolidaysOnly: Boolean,
    val schoolAuthority: String?,
    val customSchoolHolidays: List<DateRange>,
    val range: DateRange,
    val dates: List<DateRange>,
    val timezone: String,
)

/** The shape of a candidate slot, discriminated by `type`. */
@Serializable
sealed interface RequirementLength {
    /** e.g. 120 = 2 hours. Slots are reported as each maximal free stretch of at least [minutes]. */
    @Serializable
    @SerialName("duration")
    data class Duration(val minutes: Int) : RequirementLength

    /** Whole consecutive days; 7 = a week. */
    @Serializable
    @SerialName("days")
    data class Days(val count: Int) : RequirementLength

    /** e.g. Fri 18:00 → Sun 23:59. `weeksLater` 1 = "Tue → following Thu". */
    @Serializable
    @SerialName("span")
    data class Span(
        val startDay: Int,
        val startTime: String,
        val endDay: Int,
        val endTime: String,
        val weeksLater: Int,
    ) : RequirementLength
}
