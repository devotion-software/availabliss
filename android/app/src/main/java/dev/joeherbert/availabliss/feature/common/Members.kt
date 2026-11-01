package dev.joeherbert.availabliss.feature.common

import dev.joeherbert.availabliss.core.common.firstName
import dev.joeherbert.availabliss.core.network.model.Member
import dev.joeherbert.availabliss.designsystem.component.AvatarPerson

/** Looks up members by id for chats, proposals and timelines. */
class MemberDirectory(members: List<Member>, private val meId: String?) {
    private val byId = members.associateBy { it.userId }

    operator fun get(userId: String): Member? = byId[userId]

    /** "Sam (you)", "Priya", or "Someone" for ex-members. */
    fun displayName(userId: String?, youSuffix: Boolean = true): String = when {
        userId == null -> "Availabliss"
        userId == meId && youSuffix -> "${byId[userId]?.name?.firstName() ?: "You"} (you)"
        else -> byId[userId]?.name?.firstName() ?: "Someone"
    }

    fun avatar(userId: String): AvatarPerson = AvatarPerson(userId, byId[userId]?.name ?: "?", byId[userId]?.image)

    fun isMe(userId: String?) = userId != null && userId == meId
}

fun Member.avatar() = AvatarPerson(userId, name, image)
