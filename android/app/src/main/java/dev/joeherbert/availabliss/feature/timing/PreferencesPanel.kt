package dev.joeherbert.availabliss.feature.timing

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.joeherbert.availabliss.core.common.DateTimes
import dev.joeherbert.availabliss.core.data.GroupsRepository
import dev.joeherbert.availabliss.core.data.UserRepository
import dev.joeherbert.availabliss.core.live.LiveEvents
import dev.joeherbert.availabliss.core.live.LivePayload
import dev.joeherbert.availabliss.core.network.ApiResult
import dev.joeherbert.availabliss.core.network.getOrNull
import dev.joeherbert.availabliss.core.network.model.GroupPreference
import dev.joeherbert.availabliss.core.network.model.NewGroupPreference
import dev.joeherbert.availabliss.core.network.model.PreferenceWindow
import dev.joeherbert.availabliss.designsystem.component.AvBottomSheet
import dev.joeherbert.availabliss.designsystem.component.AvTextField
import dev.joeherbert.availabliss.designsystem.component.GlassPanel
import dev.joeherbert.availabliss.designsystem.component.PrimaryButton
import dev.joeherbert.availabliss.designsystem.component.Segment
import dev.joeherbert.availabliss.designsystem.component.SegmentedControl
import dev.joeherbert.availabliss.designsystem.component.TextAction
import dev.joeherbert.availabliss.designsystem.icon.AvIcons
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PreferencesUiState(
    val preferences: List<GroupPreference> = emptyList(),
    val meId: String? = null,
    val names: Map<String, String> = emptyMap(),
    val saving: Boolean = false,
    val error: String? = null,
) {
    val mine get() = preferences.filter { it.userId == meId }
    val others get() = preferences.filter { it.userId != meId }
}

/** A group's preferred times: everyone's, and adding / removing your own. */
@HiltViewModel
class PreferencesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val groups: GroupsRepository,
    private val users: UserRepository,
    liveEvents: LiveEvents,
) : ViewModel() {
    private val groupId = savedStateHandle.get<String>("groupId").orEmpty()
    private val _state = MutableStateFlow(PreferencesUiState())
    val state: StateFlow<PreferencesUiState> = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            liveEvents.payloadsFor(groupId).collect { if (it is LivePayload.AvailabilityChanged) load() }
        }
    }

    fun load() {
        viewModelScope.launch {
            val list = groups.preferences(groupId).getOrNull() ?: return@launch
            val group = groups.cachedGroup(groupId)
            _state.update {
                it.copy(
                    preferences = list,
                    meId = users.user.value?.id,
                    names = group?.members.orEmpty().associate { m -> m.userId to m.name.substringBefore(' ') },
                )
            }
        }
    }

    /** Saves a preference; returns via [onDone] when it worked. */
    fun add(body: NewGroupPreference, onDone: () -> Unit) {
        if (_state.value.saving) return
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            when (val result = groups.addPreference(groupId, body)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(saving = false, preferences = it.preferences + result.value) }
                    onDone()
                }
                is ApiResult.Failure -> _state.update { it.copy(saving = false, error = result.error.message) }
            }
        }
    }

    fun remove(pref: GroupPreference) {
        viewModelScope.launch {
            if (groups.removePreference(groupId, pref.id) is ApiResult.Success) {
                _state.update { it.copy(preferences = it.preferences.filterNot { p -> p.id == pref.id }) }
            }
        }
    }
}

/**
 * "Preferred times": say when you'd rather meet. Guidelines only: nothing is blocked; the
 * calendar marks the times that work and fit someone's preference with a heart.
 */
@Composable
fun PreferencesPanel(viewModel: PreferencesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AvailablissTheme.colors
    val type = AvailablissTheme.type
    var adding by remember { mutableStateOf(false) }
    GlassPanel(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(AvIcons.Heart, null, tint = colors.accentInk, modifier = Modifier.padding(end = 6.dp))
            Text("Preferred times", style = type.cardTitle, color = colors.ink, modifier = Modifier.weight(1f))
            TextAction("+ Add", { adding = true }, color = colors.accentInk)
        }
        Text(
            "Say when you'd rather meet. It never rules a date out: times that work and fit someone's preference are marked with a heart.",
            style = type.caption.copy(fontWeight = FontWeight.Normal),
            color = colors.muted,
        )
        if (state.mine.isNotEmpty()) {
            Text("Yours", style = type.label, color = colors.muted)
            state.mine.forEach { pref ->
                Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(pref.label, style = type.bodyMedium, color = colors.ink, modifier = Modifier.weight(1f))
                    TextAction("Remove", { viewModel.remove(pref) }, color = colors.warnInk)
                }
            }
        }
        if (state.others.isNotEmpty()) {
            Text("Everyone else", style = type.label, color = colors.muted)
            state.others.forEach { pref ->
                Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(pref.label, style = type.bodyMedium, color = colors.ink, modifier = Modifier.weight(1f))
                    Text(state.names[pref.userId] ?: "Someone", style = type.caption, color = colors.muted)
                }
            }
        }
    }
    if (adding) AddPreferenceSheet(state.saving, state.error, onDismiss = { adding = false }) { body -> viewModel.add(body) { adding = false } }
}

@Composable
private fun AddPreferenceSheet(saving: Boolean, error: String?, onDismiss: () -> Unit, onSave: (NewGroupPreference) -> Unit) {
    val colors = AvailablissTheme.colors
    var kind by remember { mutableStateOf("weekly") }
    var days by remember { mutableStateOf(setOf(5, 6)) }
    var timing by remember { mutableStateOf("any") }
    var from by remember { mutableStateOf("16:00") }
    var to by remember { mutableStateOf("22:00") }
    var fromDate by remember { mutableStateOf(DateTimes.today().toString()) }
    var toDate by remember { mutableStateOf(DateTimes.today().toString()) }
    AvBottomSheet(title = "Add a preferred time", onDismissRequest = onDismiss) {
        SegmentedControl(segments = listOf(Segment("weekly", "Repeating"), Segment("dates", "Specific dates")), selected = kind, onSelect = { kind = it })
        if (kind == "weekly") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { day, initial ->
                    val on = day in days
                    Box(
                        Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (on) colors.accent else colors.surface)
                            .clickable(role = Role.Checkbox) { days = if (on) days - day else days + day },
                        contentAlignment = Alignment.Center,
                    ) { Text(initial, color = if (on) colors.onAccent else colors.ink, fontWeight = FontWeight.Bold) }
                }
            }
            SegmentedControl(segments = listOf(Segment("any", "Any time"), Segment("after", "After"), Segment("between", "Between")), selected = timing, onSelect = { timing = it })
            if (timing != "any") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AvTextField(from, { from = it.take(5) }, label = if (timing == "after") "After (HH:MM)" else "From (HH:MM)", modifier = Modifier.weight(1f))
                    if (timing == "between") AvTextField(to, { to = it.take(5) }, label = "Until (HH:MM)", modifier = Modifier.weight(1f))
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AvTextField(fromDate, { fromDate = it.take(10) }, label = "From (YYYY-MM-DD)", modifier = Modifier.weight(1f))
                AvTextField(toDate, { toDate = it.take(10) }, label = "To (YYYY-MM-DD)", modifier = Modifier.weight(1f))
            }
        }
        error?.let { Text(it, color = colors.warnInk, style = AvailablissTheme.type.caption) }
        PrimaryButton(
            "Save preference",
            {
                onSave(
                    if (kind == "dates") NewGroupPreference(kind = "dates", from = fromDate, to = toDate)
                    else NewGroupPreference(
                        kind = "weekly",
                        days = days.sorted(),
                        window = when (timing) {
                            "after" -> PreferenceWindow(from, "23:59")
                            "between" -> PreferenceWindow(from, to)
                            else -> null
                        },
                    ),
                )
            },
            loading = saving,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
