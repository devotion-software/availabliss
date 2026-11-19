package dev.joeherbert.availabliss.feature.myplans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.joeherbert.availabliss.core.common.UserMessage
import dev.joeherbert.availabliss.core.data.EventsRepository
import dev.joeherbert.availabliss.core.live.LiveEvents
import dev.joeherbert.availabliss.core.network.ApiResult
import dev.joeherbert.availabliss.core.network.model.MyPlansResponse
import dev.joeherbert.availabliss.feature.common.AddAllResult
import dev.joeherbert.availabliss.feature.common.AddToCalendarDelegate
import dev.joeherbert.availabliss.feature.common.AddToCalendarState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MyPlansUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val sections: MyPlanSections = MyPlanSections(),
    /** Plans you're going to that aren't in a calendar yet ("Add all to calendar" while there are any). */
    val toAddCount: Int = 0,
    val adding: Boolean = false,
    val calendar: AddToCalendarState = AddToCalendarState(),
    val message: UserMessage? = null,
)

private data class MyPlansData(
    val plans: MyPlansResponse? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val adding: Boolean = false,
    val message: UserMessage? = null,
)

/**
 * My plans (from Activity): every plan across your groups, as `GET /me/plans` sorts them, marked
 * with whether each is in your calendar, and "Add all to calendar" for the ones you're going to.
 */
@HiltViewModel
class MyPlansViewModel @Inject constructor(
    private val events: EventsRepository,
    private val addToCalendar: AddToCalendarDelegate,
    liveEvents: LiveEvents,
) : ViewModel() {

    private val data = MutableStateFlow(MyPlansData())

    val state: StateFlow<MyPlansUiState> = combine(data, addToCalendar.state) { d, cal ->
        val keys = cal.inCalendar.keys
        MyPlansUiState(
            loading = d.loading,
            error = d.error,
            sections = d.plans?.let { MyPlans.sections(it, keys) } ?: MyPlanSections(),
            toAddCount = d.plans?.let { MyPlans.toAdd(it, keys).size } ?: 0,
            adding = d.adding,
            calendar = cal,
            message = d.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MyPlansUiState())

    init {
        refresh()
        viewModelScope.launch { addToCalendar.loadCalendars() }
        // Plans already in a calendar (e.g. from before a reinstall) count as added.
        viewModelScope.launch {
            data.map { it.plans }.distinctUntilChanged().collectLatest { p ->
                if (p != null) addToCalendar.findExistingEvents((p.going + p.notGoing).map { it.event })
            }
        }
        viewModelScope.launch { liveEvents.reconnected.collect { refresh() } }
    }

    fun refresh() {
        viewModelScope.launch {
            data.update { it.copy(loading = true, error = null) }
            when (val result = events.myPlans()) {
                is ApiResult.Success -> data.update { it.copy(loading = false, plans = result.value) }
                is ApiResult.Failure -> data.update { it.copy(loading = false, error = result.error.message) }
            }
        }
    }

    /** Adds every plan you're going to that isn't in a calendar yet (asking which calendar once). */
    fun addAll() {
        val plans = data.value.plans ?: return
        if (data.value.adding) return
        val todo = MyPlans.toAdd(plans, addToCalendar.state.value.inCalendar.keys)
        if (todo.isEmpty()) return
        data.update { it.copy(adding = true) }
        viewModelScope.launch {
            val result = addToCalendar.addAll(todo)
            data.update { it.copy(adding = false) }
            result?.let(::report)
        }
    }

    fun chooseCalendar(id: Long) {
        viewModelScope.launch { addToCalendar.choose(id)?.let(::report) }
    }

    fun cancelCalendarChoice() = addToCalendar.cancelChoice()

    fun onIntentHandled() = addToCalendar.onIntentHandled()

    fun messageShown(message: UserMessage) = data.update { if (it.message?.id == message.id) it.copy(message = null) else it }

    private fun report(result: AddAllResult) {
        if (result.added > 0) data.update { it.copy(message = UserMessage(result.message)) }
    }
}
