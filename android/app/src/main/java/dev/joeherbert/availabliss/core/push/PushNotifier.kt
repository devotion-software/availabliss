package dev.joeherbert.availabliss.core.push

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.joeherbert.availabliss.MainActivity
import dev.joeherbert.availabliss.R
import dev.joeherbert.availabliss.core.deeplink.DeepLink
import javax.inject.Inject
import javax.inject.Singleton

/** Shows a system notification for an incoming push, deep-linking on tap. */
@Singleton
class PushNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun show(title: String, body: String, data: Map<String, String>) {
        if (!canPost()) return
        val link = DeepLink.fromPushData(data)
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            this.data = link?.toUri()
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val channel = Channel.forKind(data["kind"])
        // Chat messages for a group share one notification (see [chatStyle]); everything else gets its own.
        val chatGroupId = data["groupId"]?.takeIf { channel == Channel.Message && it.isNotBlank() }
        val id = chatGroupId?.let { "message:$it".hashCode() }
            ?: (data["messageId"] ?: data["proposalId"] ?: data["eventId"] ?: data["groupId"] ?: title).hashCode()
        val pending = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.av_accent))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(if (chatGroupId != null) chatStyle(id, title, body) else NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setCategory(if (channel == Channel.Message) NotificationCompat.CATEGORY_MESSAGE else NotificationCompat.CATEGORY_EVENT)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    /** The group's chat notification so far (if still showing) plus this message, newest last. */
    private fun chatStyle(id: Int, author: String, body: String): NotificationCompat.MessagingStyle {
        val showing = context.getSystemService(NotificationManager::class.java)?.activeNotifications?.firstOrNull { it.id == id }?.notification
        val style = showing?.let { NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(it) }
            ?: NotificationCompat.MessagingStyle(Person.Builder().setName(context.getString(R.string.notification_you)).build())
                .setGroupConversation(true)
        return style.addMessage(body, System.currentTimeMillis(), Person.Builder().setName(author).build())
    }

    private fun canPost(): Boolean =
        android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}
