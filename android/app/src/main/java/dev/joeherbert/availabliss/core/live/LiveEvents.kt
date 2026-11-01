package dev.joeherbert.availabliss.core.live

import android.util.Log
import dev.joeherbert.availabliss.core.auth.TokenStore
import dev.joeherbert.availabliss.core.common.ApplicationScope
import dev.joeherbert.availabliss.core.network.AvailablissApi
import dev.joeherbert.availabliss.core.network.StreamingClient
import dev.joeherbert.availabliss.core.network.model.LiveEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

/**
 * The `GET /api/v1/live` Server-Sent Events stream. [start] while the app is in the
 * foreground (see [LiveConnectionObserver]); screens subscribe to [events] or
 * [eventsFor] and refresh what they show. Reconnects with exponential backoff.
 */
@Singleton
class LiveEvents @Inject constructor(
    @StreamingClient private val client: OkHttpClient,
    @Named("baseUrl") private val baseUrl: HttpUrl,
    private val json: Json,
    private val tokenStore: TokenStore,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val _events = MutableSharedFlow<LiveEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<LiveEvent> = _events.asSharedFlow()

    private val _connected = MutableStateFlow(false)
    /** True while the stream is open. Screens may refresh when it flips back to true. */
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private var job: Job? = null

    fun eventsFor(groupId: String): Flow<LiveEvent> = events.filter { it.groupId == groupId }

    /** Typed payloads for one group (see [LivePayload]). */
    fun payloadsFor(groupId: String): Flow<LivePayload> = eventsFor(groupId).map { it.decode(json) }

    /** Typed payloads for every group. */
    val payloads: Flow<LivePayload> get() = events.map { it.decode(json) }

    @Synchronized
    fun start() {
        if (job?.isActive == true || tokenStore.token() == null) return
        job = scope.launch { connectLoop() }
    }

    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
        _connected.value = false
    }

    private suspend fun connectLoop() {
        var backoff = INITIAL_BACKOFF
        while (scope.isActive) {
            if (tokenStore.token() == null) return
            try {
                stream().collect { event ->
                    backoff = INITIAL_BACKOFF
                    _events.emit(event)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.d(TAG, "Live stream dropped: ${e.message}")
            }
            _connected.value = false
            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(MAX_BACKOFF)
        }
    }

    private fun stream(): Flow<LiveEvent> = callbackFlow {
        val request = Request.Builder()
            .url(baseUrl.resolve(AvailablissApi.LIVE_PATH) ?: error("Bad live URL"))
            .header("Accept", "text/event-stream")
            .build()
        val listener = object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: Response) {
                _connected.value = true
            }

            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                runCatching { json.decodeFromString(LiveEvent.serializer(), data) }
                    .onSuccess { trySend(it) }
            }

            override fun onClosed(eventSource: EventSource) {
                close()
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                close(t ?: IOException("Live stream failed (${response?.code})"))
            }
        }
        val source = EventSources.createFactory(client).newEventSource(request, listener)
        awaitClose { source.cancel() }
    }

    private companion object {
        const val TAG = "LiveEvents"
        val INITIAL_BACKOFF = 1.seconds
        val MAX_BACKOFF = 30.seconds
    }
}
