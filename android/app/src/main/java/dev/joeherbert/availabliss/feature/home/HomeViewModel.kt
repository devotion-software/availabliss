package dev.joeherbert.availabliss.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.joeherbert.availabliss.core.calendar.CalendarEventDraft
import dev.joeherbert.availabliss.core.common.DateTimes
import dev.joeherbert.availabliss.core.common.UserMessage
import dev.joeherbert.availabliss.core.common.firstName
import dev.joeherbert.availabliss.core.common.initial
import dev.joeherbert.availabliss.core.data.EventsRepository
import dev.joeherbert.availabliss.core.data.GroupsRepository
import dev.joeherbert.availabliss.core.data.UserRepository
import dev.joeherbert.availabliss.core.live.LiveEvents
import dev.joeherbert.availabliss.core.live.LivePayload
import dev.joeherbert.availabliss.core.network.ApiResult
import dev.joeherbert.availabliss.core.network.model.GroupEvent
import dev.joeherbert.availabliss.core.network.model.GroupStatus
import dev.joeherbert.availabliss.core.network.model.GroupSummary
import dev.joeherbert.availabliss.core.network.model.TileColor
import dev.joeherbert.availabliss.designsystem.component.AvatarPerson
import dev.joeherbert.availabliss.designsystem.component.PillTone
import dev.joeherbert.availabliss.feature.common.AddToCalendarDelegate
import dev.joeherbert.availabliss.feature.common.AddToCalendarState
import dev.joeherbert.availabliss.feature.common.CalendarStatus
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One row in "Your groups". */
data class GroupTileUi(
    val id: String,
    val name: String,
    val monogram: String,
    val color: TileColor,
    val badge: String,
    val tone: PillTone,
    val timing: String,
    val people: List<AvatarPerson>,
    val syncProgress: Float,
    val syncLabel: String,
    val unread: Int,
    /** The group's photo, instead of the monogram. */
    val image: String? = null,
    /** Listed in the collapsed Archived section. */
    val archived: Boolean = false,
)

/** The "Up next" card. */
data class UpNextUi(
    val key: String,
    val groupId: String,
    /** The confirmed proposal it came from; null for an event added directly. */
    val proposalId: String?,
    val groupName: String,
    val title: String,
    val whenLabel: String,
    val detail: String,
    val calendar: CalendarStatus,
    /** The event's cover photo. */
    val image: String? = null,
    /** Its group's tile beside the name: its photo, or the monogram on its colour (no monogram until the groups list has it). */
    val groupMonogram: String = "",
    val groupColor: TileColor = TileColor.T1,
    val groupImage: String? = null,
)

data class HomeUiState(
    val loading: Boolean = true,
    val dateLabel: String = "",
    val greeting: String = "Hi",
    val profileInitial: String = "",
    val profileImage: String? = null,
    /** The next confirmed plans, soonest first (a few at most), for the Up next pager. */
    val upNext: List<UpNextUi> = emptyList(),
    val groups: List<GroupTileUi> = emptyList(),
    val calendar: AddToCalendarState = AddToCalendarState(),
    val error: String? = null,
    val message: UserMessage? = null,
)

private data class HomeRaw(
    val loading: Boolean = true,
    val upcoming: List<GroupEvent> = emptyList(),
    val error: String? = null,
    val message: UserMessage? = null,
)

/** How many plans Up next swipes through. */
private const val UP_NEXT_LIMIT = 5

@OptIn(FlowPreview::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val groups: GroupsRepository,
    private val events: EventsRepository,
    users: UserRepository,
    liveEvents: LiveEvents,
    private val addToCalendar: AddToCalendarDelegate,
) : ViewModel() {

    private val raw = MutableStateFlow(HomeRaw())
    private val refreshRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val uiState: StateFlow<HomeUiState> =
        combine(raw, groups.groups, users.user, addToCalendar.state) { r, list, user, calendar ->
            HomeUiState(
                loading = r.loading && list == null,
                dateLabel = DateTimes.longDay(DateTimes.today()),
                greeting = user?.name?.firstName()?.takeIf { it.isNotBlank() }?.let { "Hi, $it" } ?: "Hi",
                profileInitial = user?.name?.initial().orEmpty(),
                profileImage = user?.image,
                upNext = r.upcoming.take(UP_NEXT_LIMIT).map { toUpNext(it, list.orEmpty(), calendar.status(it.id)) },
                groups = list.orEmpty().map(::toTile),
                calendar = calendar,
                error = r.error,
                message = r.message,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        refresh()
        viewModelScope.launch { addToCalendar.loadCalendars() }
        // Plans already in a calendar (e.g. from before a reinstall) count as added.
        viewModelScope.launch { raw.map { it.upcoming.take(UP_NEXT_LIMIT) }.distinctUntilChanged().collectLatest { addToCalendar.findExistingEvents(it) } }
        viewModelScope.launch {
            liveEvents.payloads.collect { payload ->
                when (payload) {
                    is LivePayload.GroupUpdated, is LivePayload.MemberChanged, is LivePayload.MemberRemoved,
                    is LivePayload.ProposalChanged, is LivePayload.ProposalConfirmed, is LivePayload.AvailabilityChanged,
                    is LivePayload.EventChanged, is LivePayload.EventDeleted, is LivePayload.MessageCreated,
                    -> refreshRequests.tryEmit(Unit)
                    else -> Unit
                }
            }
        }
        viewModelScope.launch { liveEvents.reconnected.collect { refreshRequests.tryEmit(Unit) } }
        viewModelScope.launch {
            // Live events arrive in bursts; coalesce them into one refresh.
            refreshRequests.debounce(500).collect { refresh(silent = true) }
        }
    }

    fun refresh(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) raw.update { it.copy(loading = true, error = null) }
            val groupsResult = groups.refreshGroups()
            val upcoming = events.upcoming()
            raw.update {
                it.copy(
                    loading = false,
                    upcoming = (upcoming as? ApiResult.Success)?.value?.sortedBy { e -> e.start } ?: it.upcoming,
                    error = (groupsResult as? ApiResult.Failure)?.error?.message,
                )
            }
        }
    }

    fun addToCalendar(eventId: String) {
        val event = raw.value.upcoming.firstOrNull { it.id == eventId } ?: return
        val draft = CalendarEventDraft.from(event) ?: return
        viewModelScope.launch { addToCalendar.add(event.id, draft) }
    }

    fun chooseCalendar(id: Long) {
        viewModelScope.launch { addToCalendar.choose(id) }
    }

    fun cancelCalendarChoice() = addToCalendar.cancelChoice()

    fun calendarIntentHandled() = addToCalendar.onIntentHandled()

    fun messageShown(message: UserMessage) = raw.update { if (it.message?.id == message.id) it.copy(message = null) else it }

    private fun toUpNext(event: GroupEvent, list: List<GroupSummary>, calendar: CalendarStatus): UpNextUi {
        val summary = list.firstOrNull { it.id == event.groupId }
        val going = summary?.memberCount?.let { "$it going" }
        return UpNextUi(
            key = event.id,
            groupId = event.groupId,
            proposalId = event.proposalId,
            groupName = event.groupName ?: summary?.name.orEmpty(),
            groupMonogram = summary?.monogram.orEmpty(),
            groupColor = summary?.color ?: TileColor.T1,
            groupImage = summary?.image,
            title = event.title,
            whenLabel = DateTimes.dayAndTime(event.start, event.allDay),
            detail = listOfNotNull(event.location?.takeIf { it.isNotBlank() }?.let { "At $it" }, going).joinToString(" · "),
            calendar = calendar,
            image = event.image,
        )
    }

    private fun toTile(g: GroupSummary): GroupTileUi {
        val (badge, tone) = when (val s = g.status) {
            is GroupStatus.Matches -> s.label to PillTone.Indigo
            is GroupStatus.NeedsReply -> "Needs your reply" to PillTone.Apricot
            is GroupStatus.Confirmed -> (DateTimes.localDate(s.start)?.let(DateTimes::shortDay) ?: "Confirmed") to PillTone.Mint
            GroupStatus.Searching -> "Finding times" to PillTone.Neutral
        }
        val left = (g.memberCount - g.syncedCount).coerceAtLeast(0)
        return GroupTileUi(
            id = g.id,
            name = g.name,
            monogram = g.monogram,
            color = g.color,
            badge = badge,
            tone = tone,
            timing = g.requirementSummary,
            people = g.memberPreview.map { AvatarPerson(it.userId, it.name, it.image) },
            syncProgress = if (g.memberCount == 0) 0f else g.syncedCount.toFloat() / g.memberCount,
            syncLabel = if (left == 0) "All synced" else "$left of ${g.memberCount} to sync",
            unread = g.unreadCount,
            image = g.image,
            archived = g.archived,
        )
    }
}
