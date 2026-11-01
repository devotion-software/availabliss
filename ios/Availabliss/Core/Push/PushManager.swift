import Observation
import UIKit
import UserNotifications

/// APNs registration and `POST /devices`.
@MainActor
@Observable
final class PushManager {
    private(set) var authorizationStatus: UNAuthorizationStatus = .notDetermined
    @ObservationIgnored private let api: APIClient
    @ObservationIgnored private var registeredToken: String?

    private static let tokenKey = "push.lastToken"

    init(api: APIClient) {
        self.api = api
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
        guard token != registeredToken, api.bearerToken != nil else { return }
        do {
            try await api.send(API.Notifications.registerDevice(token: token, name: UIDevice.current.name))
            registeredToken = token
            UserDefaults.standard.set(token, forKey: Self.tokenKey)
        } catch {
            // Will retry on next launch.
        }
    }

    /// Removes this device's token from the account (on sign-out).
    func unregister() async {
        guard let token = registeredToken ?? UserDefaults.standard.string(forKey: Self.tokenKey) else { return }
        _ = try? await api.send(API.Notifications.unregisterDevice(token: token))
        registeredToken = nil
        UserDefaults.standard.removeObject(forKey: Self.tokenKey)
    }

    func setBadge(_ count: Int) {
        UNUserNotificationCenter.current().setBadgeCount(count)
    }
}
