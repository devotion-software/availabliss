package dev.joeherbert.availabliss.feature.members

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.joeherbert.availabliss.core.common.DateTimes
import dev.joeherbert.availabliss.core.data.GroupsRepository
import dev.joeherbert.availabliss.core.network.ApiError
import dev.joeherbert.availabliss.core.network.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class InvitePreviewUi(
    val groupName: String,
    val inviterName: String,
    val memberCount: Int,
    /** "Expires 14 Nov" */
    val expiry: String,
)

data class InviteUiState(
    val loading: Boolean = true,
    val preview: InvitePreviewUi? = null,
    /** Invite can't be used (expired, already used, not found). */
    val invalid: Boolean = false,
    val error: String? = null,
    val joining: Boolean = false,
    /** Set once joined; the screen navigates to the group. */
    val joinedGroupId: String? = null,
)

@HiltViewModel
class InviteViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val groups: GroupsRepository,
) : ViewModel() {

    private val token: String = savedStateHandle.get<String>("token").orEmpty()

    private val _state = MutableStateFlow(InviteUiState())
    val state: StateFlow<InviteUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val result = groups.invite(token)) {
                is ApiResult.Success -> {
                    val p = result.value
                    val expires = DateTimes.parseInstant(p.expiresAt)
                    val expired = expires != null && expires.isBefore(Instant.now())
                    _state.update {
                        it.copy(
                            loading = false,
                            invalid = expired,
                            preview = InvitePreviewUi(
                                groupName = p.groupName,
                                inviterName = p.inviterName,
                                memberCount = p.memberCount,
                                expiry = expires?.let { e -> "Expires ${DateTimes.dayMonth(e.atZone(DateTimes.zone).toLocalDate())}" }.orEmpty(),
                            ),
                        )
                    }
                }
                is ApiResult.Failure -> _state.update {
                    when (result.error) {
                        is ApiError.NotFound, is ApiError.Forbidden, is ApiError.Conflict -> it.copy(loading = false, invalid = true)
                        else -> it.copy(loading = false, error = result.error.message)
                    }
                }
            }
        }
    }

    fun join() {
        if (_state.value.joining) return
        _state.update { it.copy(joining = true, error = null) }
        viewModelScope.launch {
            when (val result = groups.acceptInvite(token)) {
                is ApiResult.Success -> {
                    groups.refreshGroups()
                    _state.update { it.copy(joining = false, joinedGroupId = result.value.id) }
                }
                is ApiResult.Failure -> _state.update {
                    when (result.error) {
                        is ApiError.NotFound -> it.copy(joining = false, invalid = true)
                        else -> it.copy(joining = false, error = result.error.message)
                    }
                }
            }
        }
    }
}
