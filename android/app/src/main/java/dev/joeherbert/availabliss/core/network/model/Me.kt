package dev.joeherbert.availabliss.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class BusySource {
    @SerialName("calendar") Calendar,
    @SerialName("manual") Manual,
}

@Serializable
data class BusyInterval(
    val start: String,
    val end: String,
    val source: BusySource,
    val label: String? = null,
    val calendarId: String? = null,
    val blockId: String? = null,
    /** Calendar busy: the event's opaque key ('' when the calendar gives no ids). */
    val eventKey: String? = null,
    /** Calendar busy: the event repeats, so "not busy" can cover every occurrence. */
    val recurring: Boolean = false,
    /** Set when you've marked this event not busy (it doesn't count): the id to undo it with. */
    val ignoreId: String? = null,
) {
    val isIgnored: Boolean get() = ignoreId != null
}

@Serializable
data class BusyIgnoreRequest(val calendarId: String, val eventKey: String, val start: String, val end: String, val series: Boolean)

@Serializable
data class BusyIgnoreResponse(val id: String)

@Serializable
data class BusyResponse(val intervals: List<BusyInterval>)

/**
 * How a manual block repeats. Decoding never fails: a value this app doesn't know yet (from a
 * newer server) becomes [Unknown], which is never sent back.
 */
@Serializable(with = RepeatSerializer::class)
enum class Repeat(override val wire: String) : WireEnum {
    None("none"),
    Daily("daily"),
    Weekdays("weekdays"),
    Weekly("weekly"),
    Fortnightly("fortnightly"),
    /** Same date each month (skipped in months without it). */
    Monthly("monthly"),
    /** Same weekday of the month, e.g. the 2nd Tuesday (the last one if it starts in a month's last 7 days). */
    MonthlyWeekday("monthlyWeekday"),
    /** Same date each year (29 Feb: leap years only). */
    Yearly("yearly"),
    /** A repeat this version doesn't know. */
    Unknown("unknown"),
    ;

    companion object {
        /** The repeats that can be picked, in menu order. */
        val choices: List<Repeat> = entries - Unknown
    }
}

/** The API's name for this value, for JSON patches. */
val Repeat.wireName: String get() = wire

object RepeatSerializer : LenientEnumSerializer<Repeat>("Repeat", Repeat.entries.toTypedArray(), Repeat.Unknown)

@Serializable
data class ManualBlock(
    val id: String,
    val start: String,
    val end: String,
    val allDay: Boolean = false,
    val repeat: Repeat = Repeat.None,
    val until: String? = null,
    val note: String? = null,
)

/** `Omit<ManualBlock, 'id'>`. Nullable fields are sent explicitly. */
@Serializable
data class CreateBlockRequest(
    val start: String,
    val end: String,
    val allDay: Boolean,
    val repeat: Repeat,
    val until: String?,
    val note: String?,
)

@Serializable
data class BlocksResponse(val blocks: List<ManualBlock>)

@Serializable
data class NamedCode(val code: String, val name: String)

@Serializable
data class CountriesResponse(val countries: List<NamedCode>)

@Serializable
data class SubdivisionsResponse(val subdivisions: List<NamedCode>)

@Serializable
data class SchoolAuthority(val id: String, val name: String)

@Serializable
data class SchoolAuthoritiesResponse(val authorities: List<SchoolAuthority>)

@Serializable
data class DetectedRegion(
    val country: String? = null,
    val subdivision: String? = null,
    val timezone: String? = null,
)

@Serializable
data class Holiday(val date: String, val name: String)

@Serializable
data class HolidaysResponse(val holidays: List<Holiday>)
