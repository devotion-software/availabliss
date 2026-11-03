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
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    /**
     * Bumped by [clear]. A response for a session that has since been cleared is
     * dropped instead of re-publishing (and re-caching) the signed-out user.
     */
    @Volatile private var session = 0
    private val storeLock = Mutex()

    /** Loads the cached user (if any) into [user]. */
    suspend fun restore() {
        if (_user.value != null) return
        val started = session
        prefs.cachedUserJson.first()?.let { cached ->
            val user = runCatching { json.decodeFromString(User.serializer(), cached) }.getOrNull() ?: return
            storeLock.withLock { if (session == started && _user.value == null) publish(user) }
        }
    }

    private var inFlight: Deferred<ApiResult<User>>? = null

    /**
     * `GET /me`. Concurrent callers share one request: on a cold start with nothing
     * cached, SessionManager and the root screen both ask at once.
     */
    suspend fun refresh(): ApiResult<User> {
        val request = synchronized(this) {
            inFlight?.takeIf { it.isActive } ?: run {
                val started = session
                scope.async { call { api.getMe() }.onSuccess { store(it, started) } }
                    .also { inFlight = it }
            }
        }
        return request.await()
    }

    /** `PATCH /me` with only the fields set in [block]. */
    suspend fun update(block: JsonPatchBuilder.() -> Unit): ApiResult<User> {
        val started = session
        return call { api.updateMe(jsonPatch(block)) }.onSuccess { store(it, started) }
    }

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

    suspend fun store(user: User) = store(user, session)

    /** Publishes and caches [user], unless the session changed since [started]. */
    private suspend fun store(user: User, started: Int) = storeLock.withLock {
        if (session != started) return@withLock
        publish(user)
        prefs.setCachedUserJson(json.encodeToString(User.serializer(), user))
        prefs.setAppearance(Appearance(user.themeMode, user.colorTheme))
    }

    suspend fun clear() = storeLock.withLock {
        // Together, so a refresh() starting now can't join the old session's request.
        synchronized(this) {
            session++
            inFlight = null
        }
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
