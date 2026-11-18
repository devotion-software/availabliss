package dev.joeherbert.availabliss.feature.chat

import dev.joeherbert.availabliss.core.common.UserMessage
import dev.joeherbert.availabliss.core.data.FoundPlace
import dev.joeherbert.availabliss.core.data.PlaceSearch
import dev.joeherbert.availabliss.core.network.ApiResult
import dev.joeherbert.availabliss.core.network.model.MessageLocation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The Share a location sheet: searching for a place and sending it. */
internal class ChatLocation(
    private val session: ChatSession,
    private val places: PlaceSearch,
) {
    private val data get() = session.data

    fun openLocationSheet() = data.update { it.copy(locationSheet = LocationSheetUi(), menuOpen = false) }
    fun closeLocationSheet() = data.update { it.copy(locationSheet = null) }

    fun onPlaceQuery(value: String) = updateLocation { it.copy(query = value.take(200), noResults = false) }

    fun clearPlaceSearch() = updateLocation { LocationSheetUi() }

    /** Searches for [LocationSheetUi.query], nearer [near] (lat, lng) first. */
    fun searchPlaces(near: Pair<Double, Double>?) {
        val query = data.value.locationSheet?.query?.trim().orEmpty()
        if (query.isEmpty() || data.value.locationSheet?.searching == true) return
        updateLocation { it.copy(searching = true, picked = null, noResults = false) }
        session.scope.launch {
            val found = try {
                places.search(query, near)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateLocation { it.copy(searching = false) }
                data.update { it.copy(message = UserMessage("Couldn't search for places. Check your connection and try again.")) }
                return@launch
            }
            updateLocation { it.copy(searching = false, results = found, noResults = found.isEmpty(), picked = found.singleOrNull()) }
        }
    }

    fun pickPlace(place: FoundPlace) = updateLocation { it.copy(picked = place) }

    /** Shares [location] (as a reply, if one is being written), then closes the sheet. */
    fun sendLocation(location: MessageLocation) {
        if (data.value.locationSheet?.sending == true) return
        val replyTo = data.value.replyingToId
        updateLocation { it.copy(sending = true) }
        session.scope.launch {
            when (val result = session.chat.sendLocation(session.groupId, location, replyTo)) {
                is ApiResult.Success -> {
                    session.appendMessage(result.value)
                    data.update { it.copy(locationSheet = null, replyingToId = if (it.replyingToId == replyTo) null else it.replyingToId) }
                }
                is ApiResult.Failure -> {
                    updateLocation { it.copy(sending = false) }
                    data.update { it.copy(message = UserMessage(result.error.message)) }
                }
            }
        }
    }

    private fun updateLocation(transform: (LocationSheetUi) -> LocationSheetUi) =
        data.update { d -> d.copy(locationSheet = d.locationSheet?.let(transform)) }
}
