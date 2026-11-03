import UIKit
import UserNotifications

/// Owns the `AppModel` and handles the UIKit-only callbacks: APNs registration and notification taps.
final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    let app = AppModel()

    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        return true
    }

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        Task { await app.push.didRegister(deviceToken: deviceToken) }
    }

    func application(_ application: UIApplication, didFailToRegisterForRemoteNotificationsWithError error: Error) {
        // Push is optional; the app works without it (live updates while open, Activity tab).
    }

    // Show banners while the app is open, except for chat messages in the group you're looking at.
    nonisolated func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification) async -> UNNotificationPresentationOptions {
        let data = DeepLink.data(fromPush: notification.request.content.userInfo)
        let isChatMessage = data["kind"] == "message" || data["messageId"] != nil
        let visibleChat = await MainActor.run { app.router.visibleChatGroupId }
        // The message is already arriving in the open chat over the live stream.
        if isChatMessage, let groupId = data["groupId"], groupId == visibleChat { return [] }
        return [.banner, .list, .sound]
    }

    nonisolated func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse) async {
        let data = DeepLink.data(fromPush: response.notification.request.content.userInfo)
        await MainActor.run {
            if let link = DeepLink(data: data) { app.open(link) }
        }
    }
}
