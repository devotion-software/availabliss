package dev.joeherbert.availabliss.core.deeplink

import android.net.Uri
import dev.joeherbert.availabliss.BuildConfig

/**
 * Every place the app can be opened from outside: custom scheme links
 * (`availabliss://…`), verified app links (`https://<host>/…`) and push payloads.
 * See API.md "Deep links" and "Notifications & devices".
 */
sealed interface DeepLink {
    data class Invite(val token: String) : DeepLink
    data class GroupCalendar(val groupId: String) : DeepLink
    data class GroupChat(val groupId: String) : DeepLink
    data class GroupEvents(val groupId: String) : DeepLink
    data class Proposal(val proposalId: String) : DeepLink
    /**
     * OAuth return from connecting Google / Microsoft calendars: a one-time [complete] code
     * to finish the connection with (for [provider]), or an [error].
     */
    data class Calendars(val complete: String?, val provider: String?, val error: String?) : DeepLink
    /** Return from the Sign in with Apple web flow, carrying a one-time code to exchange. */
    data class AuthCallback(val code: String?, val error: String?) : DeepLink
    data object MyTime : DeepLink
    data object Activity : DeepLink
    /** Every plan across your groups (from Activity); the "My plans" app shortcut. */
    data object MyPlans : DeepLink
    /** The new group form; the "New group" app shortcut. */
    data object NewGroup : DeepLink

    /** A URI that [parse] maps back to this link; used for notification intents. */
    fun toUri(): Uri = when (this) {
        is Invite -> Uri.parse("$SCHEME://invite/$token")
        is GroupCalendar -> Uri.parse("$SCHEME://app/groups/$groupId")
        is GroupChat -> Uri.parse("$SCHEME://app/groups/$groupId/chat")
        is GroupEvents -> Uri.parse("$SCHEME://app/groups/$groupId/events")
        is Proposal -> Uri.parse("$SCHEME://app/proposals/$proposalId")
        is Calendars -> Uri.parse("$SCHEME://calendars")
        is AuthCallback -> Uri.parse("$SCHEME://auth/callback")
        MyTime -> Uri.parse("$SCHEME://app/my-time")
        Activity -> Uri.parse("$SCHEME://app/activity")
        MyPlans -> Uri.parse("$SCHEME://app/my-plans")
        NewGroup -> Uri.parse("$SCHEME://app/new-group")
    }

    companion object {
        const val SCHEME = "availabliss"

        /**
         * Parses `availabliss://invite/abc`, `availabliss://app/groups/1/chat`,
         * `https://<host>/invite/abc`, `https://<host>/app/proposals/9`, etc.
         * For the custom scheme the URI "host" is the first path segment.
         */
        fun parse(uri: Uri): DeepLink? {
            val segments: List<String> = when (uri.scheme) {
                SCHEME -> listOfNotNull(uri.host) + uri.pathSegments
                "https" -> if (uri.host.equals(BuildConfig.APP_LINK_HOST, ignoreCase = true)) uri.pathSegments else return null
                else -> return null
            }
            return when (segments.firstOrNull()) {
                "invite" -> segments.getOrNull(1)?.let(::Invite)
                "calendars" -> Calendars(
                    complete = uri.getQueryParameter("complete"),
                    provider = uri.getQueryParameter("provider"),
                    error = uri.getQueryParameter("error"),
                )
                "auth" -> AuthCallback(
                    code = uri.getQueryParameter("code"),
                    error = uri.getQueryParameter("error"),
                )
                "app" -> parseApp(segments.drop(1))
                else -> null
            }
        }

        private fun parseApp(rest: List<String>): DeepLink? = when (rest.firstOrNull()) {
            "groups" -> rest.getOrNull(1)?.let { id ->
                when (rest.getOrNull(2)) {
                    "chat" -> GroupChat(id)
                    "events" -> GroupEvents(id)
                    else -> GroupCalendar(id)
                }
            }
            "proposals" -> rest.getOrNull(1)?.let(::Proposal)
            "my-time" -> MyTime
            "activity" -> Activity
            "my-plans" -> MyPlans
            "new-group" -> NewGroup
            else -> null
        }

        /** Push payload `data`: `{ kind, groupId, proposalId?, eventId?, messageId?, token? }`. */
        fun fromPushData(data: Map<String, String>): DeepLink? {
            val groupId = data["groupId"]?.takeIf { it.isNotBlank() }
            val proposalId = data["proposalId"]?.takeIf { it.isNotBlank() }
            val token = data["token"]?.takeIf { it.isNotBlank() }
            return when (data["kind"]) {
                "proposal.created", "proposal.confirmed", "proposal.changed" ->
                    proposalId?.let(::Proposal) ?: groupId?.let(::GroupCalendar)
                "message" -> groupId?.let(::GroupChat)
                "event.created" -> groupId?.let(::GroupEvents)
                "invite.accepted", "role.changed" -> groupId?.let(::GroupCalendar)
                // Someone invited you by username: open the invite, as `/invite/:token` does.
                "invite.received" -> token?.let(::Invite) ?: Activity
                "nudge" -> MyTime
                // The catch-up after quiet hours: its group when it was all one, else Activity.
                "quiet.summary" -> groupId?.let(::GroupCalendar) ?: Activity
                // Before a plan: the proposal it came from (or that needs a reply), else the group.
                "reminder" -> proposalId?.let(::Proposal) ?: groupId?.let(::GroupCalendar) ?: Activity
                else -> proposalId?.let(::Proposal) ?: groupId?.let(::GroupCalendar) ?: Activity
            }
        }
    }
}
