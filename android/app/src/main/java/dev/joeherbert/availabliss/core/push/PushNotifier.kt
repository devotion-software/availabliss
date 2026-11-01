package dev.joeherbert.availabliss.core.push

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
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
        val requestCode = (data["messageId"] ?: data["proposalId"] ?: data["eventId"] ?: data["groupId"] ?: title).hashCode()
        val pending = PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val channel = Channel.forKind(data["kind"])
        val notification = NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.av_accent))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setCategory(if (channel == Channel.Message) NotificationCompat.CATEGORY_MESSAGE else NotificationCompat.CATEGORY_EVENT)
            // Group chat notifications for the same group replace each other.
            .apply { data["groupId"]?.let { setGroup(it) } }
            .build()
        NotificationManagerCompat.from(context).notify(requestCode, notification)
    }

    private fun canPost(): Boolean =
        android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}
