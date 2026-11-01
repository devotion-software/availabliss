package dev.joeherbert.availabliss.core.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import dev.joeherbert.availabliss.core.common.ApplicationScope
import dev.joeherbert.availabliss.core.data.NotificationsRepository
import dev.joeherbert.availabliss.core.live.LiveConnectionObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Receives FCM token rotations and data/notification messages. */
@AndroidEntryPoint
class AvailablissMessagingService : FirebaseMessagingService() {

    @Inject lateinit var registrar: PushRegistrar
    @Inject lateinit var notifier: PushNotifier
    @Inject lateinit var notifications: NotificationsRepository
    @Inject lateinit var lifecycle: LiveConnectionObserver
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onNewToken(token: String) {
        scope.launch { registrar.onNewToken(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val title = message.notification?.title ?: data["title"] ?: getString(dev.joeherbert.availabliss.R.string.app_name)
        val body = message.notification?.body ?: data["body"].orEmpty()
        // In the foreground the live stream already updates the UI; just refresh the badge.
        scope.launch { notifications.refresh() }
        if (!lifecycle.isForeground) notifier.show(title, body, data)
    }
}
