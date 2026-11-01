package dev.joeherbert.availabliss.core.data

import dev.joeherbert.availabliss.core.common.ApplicationScope
import dev.joeherbert.availabliss.core.network.ApiResult
import dev.joeherbert.availabliss.core.network.AvailablissApi
import dev.joeherbert.availabliss.core.network.JsonPatchBuilder
import dev.joeherbert.availabliss.core.network.jsonPatch
import dev.joeherbert.availabliss.core.network.onSuccess
import dev.joeherbert.availabliss.core.network.model.ColorTheme
import dev.joeherbert.availabliss.core.network.model.ThemeMode
import dev.joeherbert.availabliss.core.network.model.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The signed-in [User]. Cached in DataStore so the app can start offline and apply
 * the account's appearance immediately.
 */
@Singleton
class UserRepository @Inject constructor(
    private val api: AvailablissApi,
    private val call: ApiCaller,
    private val prefs: AppPreferences,
    private val json: Json,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user.asStateFlow()

    /** Loads the cached user (if any) into [user]. */
    suspend fun restore() {
        if (_user.value != null) return
        prefs.cachedUserJson.first()?.let { cached ->
            runCatching { json.decodeFromString(User.serializer(), cached) }.getOrNull()?.let(::publish)
        }
    }

    suspend fun refresh(): ApiResult<User> = call { api.getMe() }.onSuccess(::store)

    /** `PATCH /me` with only the fields set in [block]. */
    suspend fun update(block: JsonPatchBuilder.() -> Unit): ApiResult<User> =
        call { api.updateMe(jsonPatch(block)) }.onSuccess(::store)

    suspend fun setAppearance(mode: ThemeMode, theme: ColorTheme): ApiResult<User> {
        // Apply locally first so the change is instant, then persist to the account.
        prefs.setAppearance(Appearance(mode, theme))
        _user.value?.let { publish(it.copy(themeMode = mode, colorTheme = theme)) }
        return update {
            set("themeMode", mode.wire)
            set("colorTheme", theme.wire)
        }
    }

    suspend fun deleteAccount(): ApiResult<Unit> = call { api.deleteAccount() }

    fun store(user: User) {
        publish(user)
        scope.launch {
            prefs.setCachedUserJson(json.encodeToString(User.serializer(), user))
            prefs.setAppearance(Appearance(user.themeMode, user.colorTheme))
        }
    }

    suspend fun clear() {
        _user.value = null
        prefs.setCachedUserJson(null)
    }

    private fun publish(user: User) {
        _user.value = user
    }
}

/** Wire value of an enum annotated with `@SerialName`. */
val ThemeMode.wire: String get() = name.lowercase()
val ColorTheme.wire: String get() = name.lowercase()
