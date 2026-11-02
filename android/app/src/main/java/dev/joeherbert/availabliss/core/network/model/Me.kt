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
)

@Serializable
data class BusyResponse(val intervals: List<BusyInterval>)

@Serializable
enum class Repeat {
    @SerialName("none") None,
    @SerialName("weekly") Weekly,
    @SerialName("fortnightly") Fortnightly,
}

/** The API's name for this value (its @SerialName), for JSON patches. */
val Repeat.wireName: String get() = Repeat.serializer().descriptor.getElementName(ordinal)

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
