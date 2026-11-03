import Observation
import UIKit
import UserNotifications

/// APNs registration and `POST /devices`.
@MainActor
@Observable
final class PushManager {
    private(set) var authorizationStatus: UNAuthorizationStatus = .notDetermined
    @ObservationIgnored private let api: APIClient
    @ObservationIgnored private let currentUserId: @MainActor () -> String?
    /// The token and the account it was registered for. Keyed on both so signing in as someone
    /// else in the same process registers again and the server moves the device to them.
    @ObservationIgnored private var registered: (token: String, userId: String)?

    private static let tokenKey = "push.lastToken"

    init(api: APIClient, currentUserId: @escaping @MainActor () -> String?) {
        self.api = api
        self.currentUserId = currentUserId
    }

    /// Asks for permission (first time only) and registers with APNs.
    func requestAuthorizationAndRegister() async {
        let center = UNUserNotificationCenter.current()
        let granted = (try? await center.requestAuthorization(options: [.alert, .badge, .sound])) ?? false
        authorizationStatus = await center.notificationSettings().authorizationStatus
        if granted { UIApplication.shared.registerForRemoteNotifications() }
    }

    /// Called from the app delegate with the APNs token.
    func didRegister(deviceToken: Data) async {
        let token = deviceToken.map { String(format: "%02x", $0) }.joined()
        guard api.bearerToken != nil, let userId = currentUserId() else { return }
        if let registered, registered.token == token, registered.userId == userId { return }
        do {
            try await api.send(API.Notifications.registerDevice(token: token, name: UIDevice.current.name))
            registered = (token, userId)
            UserDefaults.standard.set(token, forKey: Self.tokenKey)
        } catch {
            // Will retry on next launch.
        }
    }

    /// Removes this device's token from the account (on sign-out).
    func unregister() async {
        guard let token = registered?.token ?? UserDefaults.standard.string(forKey: Self.tokenKey) else { return }
        _ = try? await api.send(API.Notifications.unregisterDevice(token: token))
        forget()
    }

    /// Drops the local record of the registration when the session ends without a chance to call
    /// `unregister` (a 401), so the next sign-in registers this device for the new account.
    func forget() {
        registered = nil
        UserDefaults.standard.removeObject(forKey: Self.tokenKey)
    }

    func setBadge(_ count: Int) {
        UNUserNotificationCenter.current().setBadgeCount(count)
    }
}
