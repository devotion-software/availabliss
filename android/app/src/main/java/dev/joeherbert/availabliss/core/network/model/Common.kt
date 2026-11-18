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
    /** Lowercase, no "@". Every account gets one at sign-up; null only briefly for older accounts. */
    val username: String? = null,
    val image: String? = null,
    val themeMode: ThemeMode = ThemeMode.System,
    val colorTheme: ColorTheme = ColorTheme.Dawn,
    val country: String? = null,
    val subdivision: String? = null,
    val schoolAuthority: String? = null,
    val timezone: String = "Europe/London",
    val onboarded: Boolean = false,
    /** Minutes counted as busy before and after each busy time when finding free slots (0, 15, 30, 60, 120). */
    val busyBufferMinutes: Int = 0,
    /** Pushes before confirmed plans you're going to. Missing (older servers) means [EventReminders.Both]. */
    val eventReminders: EventReminders = EventReminders.Both,
) {
    /** The name other people see (outside a group that has its own name for them): their name, or their email. */
    val accountName: String get() = name.trim().ifEmpty { email }
}

/**
 * `User.eventReminders`: which reminders to push before a confirmed plan you're going to
 * (a day before, an hour before, both or neither). Values this build doesn't know decode as [Both].
 */
@Serializable(with = EventRemindersSerializer::class)
enum class EventReminders(override val wire: String) : WireEnum {
    Both("both"),
    DayBefore("day"),
    HourBefore("hour"),
    Off("off"),
}

object EventRemindersSerializer :
    LenientEnumSerializer<EventReminders>("EventReminders", EventReminders.entries.toTypedArray(), EventReminders.Both)

/** Someone found by username, to invite them. */
@Serializable
data class UserSummary(
    val id: String,
    val username: String,
    val name: String,
    val image: String? = null,
)

/** `GET /usernames/:username`. */
@Serializable
data class UsernameCheck(
    /** As the server normalized it. */
    val username: String,
    /** Why it can't be used as written ("At least 3 characters" …). */
    val problem: String? = null,
    /** Valid, and nobody else has it (true if it's already yours). */
    val available: Boolean = false,
    /** Who has it (you included). */
    val user: UserSummary? = null,
)

@Serializable
data class Member(
    val userId: String,
    /** The name they go by in this group: [nameInGroup] when they've set one, else their account name. */
    val name: String,
    /** The name they chose for this group, or null for their account name (older servers omit it). */
    val nameInGroup: String? = null,
    val image: String? = null,
    val role: Role,
    val synced: Boolean,
    val joinedAt: String,
)
