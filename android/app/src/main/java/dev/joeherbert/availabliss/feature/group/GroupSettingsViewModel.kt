package dev.joeherbert.availabliss.feature.group

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.joeherbert.availabliss.core.common.UserMessage
import dev.joeherbert.availabliss.core.data.GroupsRepository
import dev.joeherbert.availabliss.core.data.UserRepository
import dev.joeherbert.availabliss.core.live.LiveEvents
import dev.joeherbert.availabliss.core.live.LivePayload
import dev.joeherbert.availabliss.core.network.ApiResult
import dev.joeherbert.availabliss.core.network.model.Group
import dev.joeherbert.availabliss.core.network.model.Role
import dev.joeherbert.availabliss.core.network.model.TileColor
import dev.joeherbert.availabliss.navigation.GroupSettingsRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GroupSettingsUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val groupName: String = "",
    val monogram: String = "",
    val color: TileColor = TileColor.T1,
    val image: String? = null,
    val archived: Boolean = false,
    val role: Role? = null,
    /** The name being edited (starts as the group's name). */
    val nameDraft: String = "",
    val uploadingPhoto: Boolean = false,
    val savingName: Boolean = false,
    val archiving: Boolean = false,
    val confirmingDelete: Boolean = false,
    val deleting: Boolean = false,
    /** True once the group is deleted (navigate away). */
    val deleted: Boolean = false,
    val message: UserMessage? = null,
) {
    /** Owners and admins can change the photo, name and colour, and archive the group. */
    val canEdit: Boolean get() = role?.isAdmin == true
    /** Only the owner can delete the group. */
    val canDelete: Boolean get() = role == Role.Owner
    val nameChanged: Boolean get() = nameDraft.isNotBlank() && nameDraft.trim() != groupName
}

/**
 * Group settings: photo, name, colour, archive and delete. Reads the group from [GroupsRepository]'s
 * cache (kept live by the group's calendar underneath) and writes through it, so every screen of the
 * group shows a change straight away. Server rules: admins edit and archive, the owner deletes.
 */
@HiltViewModel
class GroupSettingsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val groups: GroupsRepository,
    private val users: UserRepository,
    liveEvents: LiveEvents,
) : ViewModel() {

    private val groupId = savedStateHandle.toRoute<GroupSettingsRoute>().groupId

    private val _state = MutableStateFlow(GroupSettingsUiState())
    val state: StateFlow<GroupSettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            groups.group(groupId).collect { group -> if (group != null) _state.update { it.withGroup(group) } }
        }
        refresh()
        viewModelScope.launch {
            liveEvents.payloadsFor(groupId).collect { payload ->
                if (payload is LivePayload.GroupUpdated) groups.applyLiveUpdate(payload.group, users.user.value?.id)
            }
        }
        viewModelScope.launch { liveEvents.reconnected.collect { refresh() } }
    }

    fun refresh() {
        viewModelScope.launch {
            when (val result = groups.refreshGroup(groupId)) {
                is ApiResult.Success -> _state.update { it.copy(loading = false, error = null) }
                is ApiResult.Failure -> _state.update { it.copy(loading = false, error = result.error.message) }
            }
        }
    }

    fun onNameChange(name: String) = _state.update { it.copy(nameDraft = name) }

    fun saveName() {
        val s = _state.value
        if (!s.canEdit || !s.nameChanged || s.savingName) return
        _state.update { it.copy(savingName = true) }
        viewModelScope.launch {
            val result = groups.update(groupId, name = s.nameDraft.trim())
            _state.update { it.copy(savingName = false, message = UserMessage(result.messageOr("Name updated"))) }
        }
    }

    fun setColor(color: TileColor) {
        val s = _state.value
        if (!s.canEdit || color == s.color) return
        // Shows the new colour straight away; the saved group (or the old colour, on failure) follows.
        _state.update { it.copy(color = color) }
        viewModelScope.launch {
            val result = groups.update(groupId, color = color)
            if (result is ApiResult.Failure) _state.update { it.copy(color = s.color, message = UserMessage(result.error.message)) }
        }
    }

    fun toggleArchived() {
        val s = _state.value
        if (!s.canEdit || s.archiving) return
        val archive = !s.archived
        _state.update { it.copy(archiving = true) }
        viewModelScope.launch {
            val result = groups.update(groupId, archived = archive)
            _state.update { it.copy(archiving = false, message = UserMessage(result.messageOr(if (archive) "Group archived" else "Group unarchived"))) }
        }
    }

    /** Uploads a picked group photo (already a JPEG), or removes it with null. */
    fun setPhoto(jpeg: ByteArray?) {
        if (!_state.value.canEdit || _state.value.uploadingPhoto) return
        _state.update { it.copy(uploadingPhoto = true) }
        viewModelScope.launch {
            val result = groups.setImage(groupId, jpeg)
            _state.update {
                it.copy(uploadingPhoto = false, message = UserMessage(result.messageOr(if (jpeg == null) "Group photo removed" else "Group photo updated")))
            }
        }
    }

    fun photoUnreadable() = _state.update { it.copy(message = UserMessage("Couldn't read that photo.")) }

    fun requestDelete() = _state.update { it.copy(confirmingDelete = true) }

    fun dismissDelete() = _state.update { if (it.deleting) it else it.copy(confirmingDelete = false) }

    fun delete() {
        if (!_state.value.canDelete || _state.value.deleting) return
        _state.update { it.copy(deleting = true) }
        viewModelScope.launch {
            when (val result = groups.delete(groupId)) {
                is ApiResult.Success -> _state.update { it.copy(deleting = false, confirmingDelete = false, deleted = true) }
                is ApiResult.Failure -> _state.update {
                    it.copy(deleting = false, confirmingDelete = false, message = UserMessage(result.error.message))
                }
            }
        }
    }

    fun messageShown(message: UserMessage) = _state.update { if (it.message?.id == message.id) it.copy(message = null) else it }

    private fun ApiResult<*>.messageOr(success: String) = when (this) {
        is ApiResult.Success -> success
        is ApiResult.Failure -> error.message
    }

    private fun GroupSettingsUiState.withGroup(group: Group): GroupSettingsUiState {
        val me = users.user.value?.id
        // Keep what's being typed; follow renames (here or elsewhere) when the field still shows the old name.
        val draft = if (nameDraft.isEmpty() || nameDraft.trim() == groupName) group.name else nameDraft
        return copy(
            loading = false,
            groupName = group.name,
            monogram = group.monogram,
            color = group.color,
            image = group.image,
            archived = group.archived,
            role = group.members.firstOrNull { it.userId == me }?.role ?: group.role,
            nameDraft = draft,
        )
    }
}
