package dev.joeherbert.availabliss.core.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.annotation.StringRes
import dev.joeherbert.availabliss.R

/**
 * Android notification channels. The server sets the FCM channel id to the push
 * `kind` (API.md "Notifications & devices"), so each channel [id] equals a kind.
 */
enum class Channel(
    val id: String,
    @StringRes val nameRes: Int,
    @StringRes val descriptionRes: Int,
    val importance: Int,
) {
    Message("message", R.string.channel_messages_name, R.string.channel_messages_description, NotificationManager.IMPORTANCE_HIGH),
    ProposalCreated("proposal.created", R.string.channel_proposal_created_name, R.string.channel_proposal_created_description, NotificationManager.IMPORTANCE_HIGH),
    ProposalConfirmed("proposal.confirmed", R.string.channel_proposal_confirmed_name, R.string.channel_proposal_confirmed_description, NotificationManager.IMPORTANCE_HIGH),
    EventCreated("event.created", R.string.channel_events_name, R.string.channel_events_description, NotificationManager.IMPORTANCE_DEFAULT),
    Nudge("nudge", R.string.channel_nudges_name, R.string.channel_nudges_description, NotificationManager.IMPORTANCE_DEFAULT),
    InviteAccepted("invite.accepted", R.string.channel_invites_name, R.string.channel_invites_description, NotificationManager.IMPORTANCE_DEFAULT),
    General("general", R.string.channel_general_name, R.string.channel_general_description, NotificationManager.IMPORTANCE_DEFAULT);

    companion object {
        /** Channel for a push `kind`; unknown kinds use [General]. */
        fun forKind(kind: String?): Channel = entries.firstOrNull { it.id == kind } ?: General

        fun createAll(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            manager.createNotificationChannels(
                entries.map { channel ->
                    NotificationChannel(channel.id, context.getString(channel.nameRes), channel.importance).apply {
                        description = context.getString(channel.descriptionRes)
                    }
                },
            )
        }
    }
}
