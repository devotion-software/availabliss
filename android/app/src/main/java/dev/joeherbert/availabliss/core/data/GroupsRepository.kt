package dev.joeherbert.availabliss.core.data

import dev.joeherbert.availabliss.core.network.ApiResult
import dev.joeherbert.availabliss.core.network.AvailablissApi
import dev.joeherbert.availabliss.core.network.map
import dev.joeherbert.availabliss.core.network.model.Availability
import dev.joeherbert.availabliss.core.network.model.CreateGroupRequest
import dev.joeherbert.availabliss.core.network.model.CreateInviteRequest
import dev.joeherbert.availabliss.core.network.model.DayAvailability
import dev.joeherbert.availabliss.core.network.model.Group
import dev.joeherbert.availabliss.core.network.model.GroupSummary
import dev.joeherbert.availabliss.core.network.model.InviteLink
import dev.joeherbert.availabliss.core.network.model.InvitePreview
import dev.joeherbert.availabliss.core.network.model.Member
import dev.joeherbert.availabliss.core.network.model.MemberRoleRequest
import dev.joeherbert.availabliss.core.network.model.Requirement
import dev.joeherbert.availabliss.core.network.model.RequirementPreview
import dev.joeherbert.availabliss.core.network.model.RequirementPreviewRequest
import dev.joeherbert.availabliss.core.network.model.Role
import dev.joeherbert.availabliss.core.network.model.TileColor
import dev.joeherbert.availabliss.core.network.model.UpdateGroupRequest
import dev.joeherbert.availabliss.core.network.onSuccess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Groups, members, invites and availability. Keeps an in-memory cache of the groups
 * list and of each loaded [Group] so screens can render instantly and update live.
 */
@Singleton
class GroupsRepository @Inject constructor(
    private val api: AvailablissApi,
    private val call: ApiCaller,
) {
    private val _groups = MutableStateFlow<List<GroupSummary>?>(null)
    /** `null` until first loaded. */
    val groups: StateFlow<List<GroupSummary>?> = _groups.asStateFlow()

    private val details = MutableStateFlow<Map<String, Group>>(emptyMap())

    fun group(id: String): Flow<Group?> = details.map { it[id] }

    fun cachedGroup(id: String): Group? = details.value[id]

    suspend fun refreshGroups(): ApiResult<List<GroupSummary>> =
        call { api.getGroups() }.map { it.groups }.onSuccess { _groups.value = it }

    suspend fun refreshGroup(id: String): ApiResult<Group> = call { api.getGroup(id) }.onSuccess(::cache)

    suspend fun create(name: String, color: TileColor?, requirement: Requirement?): ApiResult<Group> =
        call { api.createGroup(CreateGroupRequest(name.trim(), color, requirement)) }.onSuccess(::cache)

    suspend fun update(
        id: String,
        name: String? = null,
        color: TileColor? = null,
        requirement: Requirement? = null,
    ): ApiResult<Group> = call { api.updateGroup(id, UpdateGroupRequest(name, color, requirement)) }.onSuccess(::cache)

    suspend fun delete(id: String): ApiResult<Unit> = call { api.deleteGroup(id) }.onSuccess { forget(id) }

    suspend fun setRole(groupId: String, userId: String, role: Role): ApiResult<Member> =
        call { api.setMemberRole(groupId, userId, MemberRoleRequest(role)) }.onSuccess { member ->
            details.update { map ->
                val group = map[groupId] ?: return@update map
                map + (groupId to group.copy(members = group.members.map { if (it.userId == userId) member else it }))
            }
        }

    /** Removes [userId]; when it is the caller this is "leave group". */
    suspend fun removeMember(groupId: String, userId: String, isSelf: Boolean): ApiResult<Unit> =
        call { api.removeMember(groupId, userId) }.onSuccess {
            if (isSelf) {
                forget(groupId)
            } else {
                details.update { map ->
                    val group = map[groupId] ?: return@update map
                    val members = group.members.filterNot { it.userId == userId }
                    map + (groupId to group.copy(members = members, memberCount = members.size))
                }
            }
        }

    suspend fun nudge(groupId: String): ApiResult<Int> = call { api.nudge(groupId) }.map { it.nudged }

    suspend fun createInvite(groupId: String, email: String? = null): ApiResult<InviteLink> =
        call { api.createInvite(groupId, CreateInviteRequest(email?.takeIf { it.isNotBlank() })) }

    suspend fun invite(token: String): ApiResult<InvitePreview> = call { api.getInvite(token) }

    suspend fun acceptInvite(token: String): ApiResult<Group> = call { api.acceptInvite(token) }.onSuccess(::cache)

    /** [from] / [to] are `YYYY-MM-DD`. */
    suspend fun availability(groupId: String, from: String, to: String): ApiResult<Availability> =
        call { api.getAvailability(groupId, from, to) }

    /** How many slots [requirement] produces before busy time ("13 weekends fit this timing"). */
    suspend fun previewRequirement(requirement: Requirement): ApiResult<RequirementPreview> =
        call { api.previewRequirement(RequirementPreviewRequest(requirement)) }

    suspend fun dayAvailability(groupId: String, date: String): ApiResult<DayAvailability> =
        call { api.getDayAvailability(groupId, date) }

    fun clear() {
        _groups.value = null
        details.value = emptyMap()
    }

    private fun cache(group: Group) {
        details.update { it + (group.id to group) }
    }

    private fun forget(id: String) {
        details.update { it - id }
        _groups.update { list -> list?.filterNot { it.id == id } }
    }
}
