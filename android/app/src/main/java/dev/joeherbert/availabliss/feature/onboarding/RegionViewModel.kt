package dev.joeherbert.availabliss.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.joeherbert.availabliss.core.common.UserMessage
import dev.joeherbert.availabliss.core.data.RegionsRepository
import dev.joeherbert.availabliss.core.data.UserRepository
import dev.joeherbert.availabliss.core.network.ApiResult
import dev.joeherbert.availabliss.core.network.getOrNull
import dev.joeherbert.availabliss.core.network.model.NamedCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId
import javax.inject.Inject

/** Which list the picker sheet is showing. */
enum class RegionPicker { Country, Subdivision, SchoolAuthority }

/** A pickable row; [code] null = "None". */
data class RegionOption(val code: String?, val name: String)

data class RegionUiState(
    val loading: Boolean = true,
    val countries: List<NamedCode> = emptyList(),
    val subdivisions: List<NamedCode> = emptyList(),
    val authorities: List<RegionOption> = emptyList(),
    val country: String? = null,
    val subdivision: String? = null,
    val schoolAuthority: String? = null,
    val timezone: String = ZoneId.systemDefault().id,
    val picker: RegionPicker? = null,
    val query: String = "",
    val saving: Boolean = false,
    /** True once a save succeeded; the UI moves on and calls [RegionViewModel.onSaveHandled]. */
    val saved: Boolean = false,
    val message: UserMessage? = null,
) {
    val countryName: String? get() = countries.firstOrNull { it.code == country }?.name ?: country
    val subdivisionName: String? get() = subdivisions.firstOrNull { it.code == subdivision }?.name ?: subdivision
    val schoolAuthorityName: String? get() = authorities.firstOrNull { it.code == schoolAuthority }?.name ?: schoolAuthority

    /** "United Kingdom · England" */
    val summaryTitle: String
        get() = listOfNotNull(countryName, subdivisionName).joinToString(" · ").ifEmpty { "Choose your region" }

    /** "School holidays: Bristol City Council" */
    val summarySubtitle: String
        get() = schoolAuthorityName?.let { "School holidays: $it" } ?: "No school holiday calendar"

    val canSave: Boolean get() = country != null && !saving

    /** Options for the open picker, filtered by [query]. */
    val pickerOptions: List<RegionOption>
        get() {
            val all = when (picker) {
                RegionPicker.Country -> countries.map { RegionOption(it.code, it.name) }
                RegionPicker.Subdivision -> listOf(RegionOption(null, "Whole country")) + subdivisions.map { RegionOption(it.code, it.name) }
                RegionPicker.SchoolAuthority -> listOf(RegionOption(null, "None")) + authorities
                null -> emptyList()
            }
            val q = query.trim()
            return if (q.isEmpty()) all else all.filter { it.name.contains(q, ignoreCase = true) }
        }
}

/**
 * Country, subdivision (bank holidays), school authority (term dates) and time zone.
 * Used by onboarding step 1 and the "Region & holidays" settings screen.
 */
@HiltViewModel
class RegionViewModel @Inject constructor(
    private val regions: RegionsRepository,
    private val users: UserRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(RegionUiState())
    val state: StateFlow<RegionUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val user = users.user.value
            var country = user?.country
            var subdivision = user?.subdivision
            var timezone = user?.timezone ?: ZoneId.systemDefault().id
            if (country == null) {
                regions.detect().getOrNull()?.let { detected ->
                    country = detected.country
                    subdivision = detected.subdivision
                    detected.timezone?.let { timezone = it }
                }
            }
            val countries = when (val result = regions.countries()) {
                is ApiResult.Success -> result.value
                is ApiResult.Failure -> {
                    _state.update { it.copy(message = UserMessage(result.error.message)) }
                    emptyList()
                }
            }
            _state.update {
                it.copy(
                    loading = false,
                    countries = countries,
                    country = country,
                    subdivision = subdivision,
                    schoolAuthority = user?.schoolAuthority,
                    timezone = timezone,
                )
            }
            country?.let { loadCountryDetails(it) }
        }
    }

    fun openPicker(picker: RegionPicker) = _state.update { it.copy(picker = picker, query = "") }

    fun closePicker() = _state.update { it.copy(picker = null, query = "") }

    fun onQueryChange(query: String) = _state.update { it.copy(query = query) }

    fun pick(option: RegionOption) {
        val picker = _state.value.picker ?: return
        when (picker) {
            RegionPicker.Country -> {
                val code = option.code ?: return
                val changed = code != _state.value.country
                _state.update {
                    it.copy(
                        country = code,
                        subdivision = if (changed) null else it.subdivision,
                        schoolAuthority = if (changed) null else it.schoolAuthority,
                        subdivisions = if (changed) emptyList() else it.subdivisions,
                        authorities = if (changed) emptyList() else it.authorities,
                    )
                }
                if (changed) viewModelScope.launch { loadCountryDetails(code) }
            }
            RegionPicker.Subdivision -> _state.update { it.copy(subdivision = option.code) }
            RegionPicker.SchoolAuthority -> _state.update { it.copy(schoolAuthority = option.code) }
        }
        closePicker()
    }

    fun useDeviceTimezone() = _state.update { it.copy(timezone = ZoneId.systemDefault().id) }

    fun save() {
        val s = _state.value
        val country = s.country ?: return
        if (s.saving) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            val result = users.update {
                set("country", country)
                set("subdivision", s.subdivision)
                set("schoolAuthority", s.schoolAuthority)
                set("timezone", s.timezone)
            }
            _state.update {
                when (result) {
                    is ApiResult.Success -> it.copy(saving = false, saved = true)
                    is ApiResult.Failure -> it.copy(saving = false, message = UserMessage(result.error.message))
                }
            }
        }
    }

    fun onSaveHandled() = _state.update { it.copy(saved = false) }

    fun onMessageShown(message: UserMessage) = _state.update { if (it.message?.id == message.id) it.copy(message = null) else it }

    private suspend fun loadCountryDetails(country: String) {
        val subdivisions = regions.subdivisions(country).getOrNull().orEmpty()
        val authorities = regions.schoolAuthorities(country).getOrNull().orEmpty().map { RegionOption(it.id, it.name) }
        _state.update { s ->
            if (s.country != country) s
            else s.copy(subdivisions = subdivisions, authorities = authorities)
        }
    }
}
