package dev.joeherbert.availabliss.feature.events

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.joeherbert.availabliss.core.calendar.CalendarEventDraft
import dev.joeherbert.availabliss.core.common.DateTimes
import dev.joeherbert.availabliss.core.common.UserMessage
import dev.joeherbert.availabliss.core.data.EventsRepository
import dev.joeherbert.availabliss.core.data.GroupsRepository
import dev.joeherbert.availabliss.core.data.UserRepository
import dev.joeherbert.availabliss.core.live.LiveEvents
import dev.joeherbert.availabliss.core.live.LivePayload
import dev.joeherbert.availabliss.core.network.ApiResult
import dev.joeherbert.availabliss.core.network.model.GroupEvent
import dev.joeherbert.availabliss.core.network.model.Role
import dev.joeherbert.availabliss.feature.common.AddToCalendarDelegate
import dev.joeherbert.availabliss.feature.common.AddToCalendarState
import dev.joeherbert.availabliss.navigation.EventsRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class EventRowUi(
    val id: String,
    val title: String,
    val timeLabel: String,
    val location: String?,
    val month: String,
    val day: String,
    val canDelete: Boolean,
    val added: Boolean,
)

data class EventsUiState(
    val groupName: String = "",
    val loading: Boolean = true,
    val error: String? = null,
    val upcoming: List<EventRowUi> = emptyList(),
    val past: List<EventRowUi> = emptyList(),
    val calendar: AddToCalendarState = AddToCalendarState(),
    val message: UserMessage? = null,
)

private data class EventsData(
    val events: List<GroupEvent> = emptyList(),
    val groupName: String = "",
    val role: Role? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val message: UserMessage? = null,
)

@HiltViewModel
class EventsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val events: EventsRepository,
    private val groups: GroupsRepository,
    private val users: UserRepository,
    private val addToCalendar: AddToCalendarDelegate,
    liveEvents: LiveEvents,
) : ViewModel() {

    private val groupId = savedStateHandle.toRoute<EventsRoute>().groupId
    private val data = MutableStateFlow(EventsData())

    val state: StateFlow<EventsUiState> = combine(data, addToCalendar.state) { d, cal -> d.toUi(cal) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EventsUiState())

    init {
        refresh()
        viewModelScope.launch { addToCalendar.loadCalendars() }
        viewModelScope.launch {
            liveEvents.payloadsFor(groupId).collect { payload ->
                when (payload) {
                    is LivePayload.EventChanged -> data.update { d ->
                        val others = d.events.filterNot { it.id == payload.event.id }
                        d.copy(events = others + payload.event)
                    }
                    is LivePayload.EventDeleted -> data.update { d -> d.copy(events = d.events.filterNot { it.id == payload.id }) }
                    is LivePayload.ProposalConfirmed -> data.update { d ->
                        d.copy(events = d.events.filterNot { it.id == payload.event.id } + payload.event)
                    }
                    else -> Unit
                }
            }
        }
        viewModelScope.launch { liveEvents.reconnected.collect { refresh() } }
    }

    fun refresh() {
        viewModelScope.launch {
            data.update { it.copy(loading = true, error = null) }
            val group = groups.cachedGroup(groupId) ?: (groups.refreshGroup(groupId) as? ApiResult.Success)?.value
            when (val result = events.list(groupId)) {
                is ApiResult.Success -> data.update {
                    it.copy(events = result.value, loading = false, groupName = group?.name.orEmpty(), role = group?.role)
                }
                is ApiResult.Failure -> data.update {
                    it.copy(loading = false, error = result.error.message, groupName = group?.name.orEmpty(), role = group?.role)
                }
            }
        }
    }

    fun onAddToCalendar(eventId: String) {
        val event = data.value.events.firstOrNull { it.id == eventId } ?: return
        val draft = CalendarEventDraft.from(event) ?: return
        viewModelScope.launch { addToCalendar.add(eventId, draft) }
    }

    fun onSelectCalendar(id: Long) = addToCalendar.select(id)

    fun onIntentHandled() = addToCalendar.onIntentHandled()

    fun onDelete(eventId: String) {
        viewModelScope.launch {
            when (val result = events.delete(eventId)) {
                is ApiResult.Success -> data.update { d -> d.copy(events = d.events.filterNot { it.id == eventId }, message = UserMessage("Event deleted")) }
                is ApiResult.Failure -> data.update { it.copy(message = UserMessage(result.error.message)) }
            }
        }
    }

    fun onMessageShown(message: UserMessage) = data.update { if (it.message?.id == message.id) it.copy(message = null) else it }

    private fun EventsData.toUi(cal: AddToCalendarState): EventsUiState {
        val meId = users.user.value?.id
        val now = Instant.now()
        val rows = events
            .sortedBy { it.start }
            .map { e ->
                val ends = DateTimes.parseInstant(e.end)
                val date = DateTimes.localDate(e.start)
                (ends != null && ends.isBefore(now)) to EventRowUi(
                    id = e.id,
                    title = e.title,
                    timeLabel = DateTimes.rangeLabel(e.start, e.end, e.allDay),
                    location = e.location,
                    month = date?.let(DateTimes::monthAbbrev).orEmpty(),
                    day = date?.dayOfMonth?.toString().orEmpty(),
                    canDelete = e.createdBy == meId || role?.isAdmin == true,
                    added = e.id in cal.added,
                )
            }
        return EventsUiState(
            groupName = groupName,
            loading = loading,
            error = error,
            upcoming = rows.filterNot { it.first }.map { it.second },
            past = rows.filter { it.first }.map { it.second }.reversed(),
            calendar = cal,
            message = message,
        )
    }
}
