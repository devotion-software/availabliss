package dev.joeherbert.availabliss.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Shared types from docs/API.md ("Shared types"). Property names and enum values
 * mirror the JSON contract exactly. Timestamps are ISO 8601 strings, dates are
 * YYYY-MM-DD and wall-clock times HH:MM; parsing happens at the edges
 * (see core/common/DateTimes.kt) so the wire models stay a faithful copy.
 */

@Serializable
enum class Role {
    @SerialName("owner") Owner,
    @SerialName("admin") Admin,
    @SerialName("member") Member;

    val isAdmin: Boolean get() = this == Owner || this == Admin
}

@Serializable
enum class ThemeMode {
    @SerialName("light") Light,
    @SerialName("dark") Dark,
    @SerialName("system") System,
}

@Serializable
enum class ColorTheme {
    @SerialName("dawn") Dawn,
    @SerialName("lagoon") Lagoon,
    @SerialName("sunset") Sunset,
    @SerialName("orchid") Orchid,
    @SerialName("grove") Grove,
}

/** Group tile colour token `t1` … `t5`. */
@Serializable
enum class TileColor {
    @SerialName("t1") T1,
    @SerialName("t2") T2,
    @SerialName("t3") T3,
    @SerialName("t4") T4,
    @SerialName("t5") T5,
}

/** Inclusive `YYYY-MM-DD` range. */
@Serializable
data class DateRange(val from: String, val to: String)

/** Daily wall-clock window, `HH:MM`. */
@Serializable
data class TimeWindow(val from: String, val to: String)

/** A start/end pair of ISO timestamps. */
@Serializable
data class Interval(val start: String, val end: String)

@Serializable
data class User(
    val id: String,
    val name: String,
    val email: String,
    val image: String? = null,
    val themeMode: ThemeMode = ThemeMode.System,
    val colorTheme: ColorTheme = ColorTheme.Dawn,
    val country: String? = null,
    val subdivision: String? = null,
    val schoolAuthority: String? = null,
    val timezone: String = "Europe/London",
    val onboarded: Boolean = false,
)

@Serializable
data class Member(
    val userId: String,
    val name: String,
    val image: String? = null,
    val role: Role,
    val synced: Boolean,
    val joinedAt: String,
)
