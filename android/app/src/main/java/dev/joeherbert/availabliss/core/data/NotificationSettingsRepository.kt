package dev.joeherbert.availabliss.core.data

import dev.joeherbert.availabliss.core.common.DateTimes
import dev.joeherbert.availabliss.core.network.ApiResult
import dev.joeherbert.availabliss.core.network.AvailablissApi
import dev.joeherbert.availabliss.core.network.JsonPatchBuilder
import dev.joeherbert.availabliss.core.network.jsonPatch
import dev.joeherbert.availabliss.core.network.model.MuteGroupRequest
import dev.joeherbert.availabliss.core.network.model.NotificationSettings
import dev.joeherbert.availabliss.core.network.model.QuietHours
import dev.joeherbert.availabliss.core.network.onSuccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/** How long "Mute notifications" lasts for a group. */
enum class MuteDuration(val label: String) {
    EightHours("For 8 hours"),
    Week("For a week"),
    UntilUnmuted("Until I turn it back on"),
    ;

    /** When the mute runs out if started at [now] (to the second), or null for [UntilUnmuted]. */
    fun until(now: Instant): Instant? = when (this) {
        EightHours -> now.plus(Duration.ofHours(8))
        Week -> now.plus(Duration.ofDays(7))
        UntilUnmuted -> null
    }?.truncatedTo(ChronoUnit.SECONDS)

    /** The snackbar once it's muted. */
    val confirmation: String
        get() = when (this) {
            EightHours -> "Muted for 8 hours"
            Week -> "Muted for a week"
            UntilUnmuted -> "Muted until you turn it back on"
        }
}

/**
 * Which pushes you get (`/me/notification-settings`): categories turned off, quiet hours and
 * muted groups. Every call returns the whole settings, which become [settings].
 */
@Singleton
class NotificationSettingsRepository @Inject constructor(
    private val api: AvailablissApi,
    private val call: ApiCaller,
) {
    private val _settings = MutableStateFlow<NotificationSettings?>(null)

    /** The last settings the server sent; null until the first [refresh]. */
    val settings: StateFlow<NotificationSettings?> = _settings.asStateFlow()

    suspend fun refresh(): ApiResult<NotificationSettings> =
        call { api.getNotificationSettings() }.onSuccess { _settings.value = it }

    /** Replaces the categories turned off (wire values). */
    suspend fun setOff(off: List<String>): ApiResult<NotificationSettings> =
        update { set("off", JsonArray(off.map(::JsonPrimitive))) }

    /** Sets quiet hours, or with null turns them off. */
    suspend fun setQuietHours(quietHours: QuietHours?): ApiResult<NotificationSettings> =
        update {
            set(
                "quietHours",
                quietHours?.let { buildJsonObject { put("from", JsonPrimitive(it.from)); put("to", JsonPrimitive(it.to)) } } ?: JsonNull,
            )
        }

    /** Mutes [groupId] for [duration], counted from [now]. */
    suspend fun mute(groupId: String, duration: MuteDuration, now: Instant = Instant.now()): ApiResult<NotificationSettings> =
        call { api.muteGroup(groupId, MuteGroupRequest(duration.until(now)?.let(DateTimes::toIso))) }.onSuccess { _settings.value = it }

    suspend fun unmute(groupId: String): ApiResult<NotificationSettings> =
        call { api.unmuteGroup(groupId) }.onSuccess { _settings.value = it }

    fun clear() {
        _settings.value = null
    }

    private suspend fun update(block: JsonPatchBuilder.() -> Unit): ApiResult<NotificationSettings> =
        call { api.updateNotificationSettings(jsonPatch(block)) }.onSuccess { _settings.value = it }
}
