package dev.joeherbert.availabliss.core.data

import dev.joeherbert.availabliss.core.network.model.MessageLocation
import dev.joeherbert.availabliss.designsystem.component.chat.OsmUserAgent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** A place found by searching: what to show in the list and to send. */
data class FoundPlace(val name: String, val address: String?, val lat: Double, val lng: Double) {
    val location: MessageLocation get() = MessageLocation(lat, lng, name = name, address = address)
}

/**
 * Finds places by name with OpenStreetMap's Nominatim (no API key). Only searched when you
 * press Search (its usage policy rules out search-as-you-type), and with its own client, so
 * nothing of the app's session goes with it.
 */
@Singleton
class PlaceSearch @Inject constructor() {
    private val client = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Result(
        val lat: String,
        val lon: String,
        val name: String? = null,
        @SerialName("display_name") val displayName: String = "",
    )

    /** Up to six places for [query], nearer [near] (lat, lng) first when given. Throws on network errors. */
    suspend fun search(query: String, near: Pair<Double, Double>? = null): List<FoundPlace> = withContext(Dispatchers.IO) {
        val url = "https://nominatim.openstreetmap.org/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("format", "jsonv2")
            .addQueryParameter("limit", "6")
            .apply {
                // Roughly where you are (to about 10 km), to rank nearby places first.
                near?.let { (lat, lng) ->
                    fun r(v: Double) = "%.1f".format(Locale.ROOT, v)
                    addQueryParameter("viewbox", "${r(lng - 0.5)},${r(lat + 0.5)},${r(lng + 0.5)},${r(lat - 0.5)}")
                }
            }
            .build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", OsmUserAgent)
            .header("Accept-Language", Locale.getDefault().toLanguageTag())
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Search failed (${response.code})")
            json.decodeFromString<List<Result>>(response.body.string()).mapNotNull { it.toPlace() }
        }
    }

    private fun Result.toPlace(): FoundPlace? {
        val lat = lat.toDoubleOrNull() ?: return null
        val lng = lon.toDoubleOrNull() ?: return null
        val parts = displayName.split(", ").filter { it.isNotBlank() }
        val title = name?.takeIf { it.isNotBlank() } ?: parts.firstOrNull() ?: return null
        // The address: the rest of the name, without the country, at most four parts.
        var rest = parts.dropWhile { it == title }.dropLast(1)
        // "7, Boundary Street" reads as "7 Boundary Street".
        if (rest.size >= 2 && rest[0].first().isDigit() && rest[0].length <= 6) rest = listOf("${rest[0]} ${rest[1]}") + rest.drop(2)
        rest = rest.take(4)
        return FoundPlace(title, rest.joinToString(", ").ifBlank { null }, lat, lng)
    }
}
