import Foundation
import Observation
import SwiftUI

/// The app's dependency container and lifecycle coordinator. One instance, owned by the app delegate,
/// injected into the view tree with `.environment(app)`.
@MainActor
@Observable
final class AppModel {
    let config: AppConfig
    let api: APIClient
    let session: SessionStore
    let appearance: AppearanceStore
    let router = Router()
    let live: LiveEventsClient
    let push: PushManager
    let deviceCalendar: DeviceCalendarService
    let billing = Billing()

    private(set) var unreadNotifications = 0
    /// Bumped when another account is merged into this one, so screens showing account data reload.
    private(set) var accountVersion = 0
    @ObservationIgnored private var liveTask: Task<Void, Never>?

    init(config: AppConfig = .load()) {
        self.config = config
        let tokenStore = TokenStore()
        let unauthorized = UnauthorizedRelay()
        let api = APIClient(baseURL: config.apiBaseURL, tokenStore: tokenStore) { token in unauthorized.fire(token: token) }
        self.api = api
        let session = SessionStore(api: api, tokenStore: tokenStore, config: config)
        self.session = session
        self.appearance = AppearanceStore(api: api)
        self.live = LiveEventsClient(api: api)
        self.deviceCalendar = DeviceCalendarService(api: api)
        self.push = PushManager(api: api, currentUserId: { session.user?.id })
        unauthorized.handler = { [weak self] token in self?.session.handleUnauthorized(token: token) }
    }

    var auth: AuthService { AuthService(api: api, config: config) }

    // MARK: Lifecycle

    func launch() async {
        await session.restore()
        if session.isSignedIn { await didSignIn() }
    }

    func signIn(token: String) async throws {
        try await session.completeSignIn(token: token)
        await didSignIn()
    }

    func signOut() async {
        await push.unregister()
        await billing.signOut()
        await session.signOut()
        didSignOut()
    }

    func deleteAccount() async throws {
        await push.unregister()
        try await session.deleteAccount()
        await billing.signOut()
        didSignOut()
    }

    /// Reacts to the session ending for any reason (including a 401).
    func sessionStateChanged() {
        if !session.isSignedIn { didSignOut() }
    }

    func scenePhaseChanged(_ phase: ScenePhase) {
        guard session.isSignedIn else { return }
        switch phase {
        case .active:
            live.start()
            Task {
                await refreshUnread()
                await deviceCalendar.syncIfAuthorized()
            }
        case .background:
            live.stop()
            DeviceCalendarService.scheduleBackgroundRefresh()
        default:
            break
        }
    }

    /// BGAppRefreshTask entry point.
    func backgroundRefresh() async {
        DeviceCalendarService.scheduleBackgroundRefresh()
        await deviceCalendar.syncIfAuthorized()
    }

    func open(_ link: DeepLink) {
        router.open(link)
    }

    /// Another account was merged into this one: reload the user and everything that depends on it.
    func accountMerged() async {
        accountVersion += 1
        await session.refreshUser()
        await refreshUnread()
        deviceCalendar.forgetLastUpload()
        await deviceCalendar.syncIfAuthorized()
    }

    func refreshUnread() async {
        guard let response = try? await api.send(API.Notifications.list()) else { return }
        unreadNotifications = response.unread
        push.setBadge(response.unread)
    }

    func markNotificationsRead() async {
        guard unreadNotifications > 0 else { return }
        unreadNotifications = 0
        push.setBadge(0)
        _ = try? await api.send(API.Notifications.markRead())
    }

    // MARK: - Private

    private func didSignIn() async {
        if let user = session.user { appearance.adopt(user) }
        live.start()
        startLiveHandling()
        deviceCalendar.startObservingChanges()
        await push.requestAuthorizationAndRegister()
        await refreshUnread()
        await deviceCalendar.syncIfAuthorized()
        DeviceCalendarService.scheduleBackgroundRefresh()
    }

    private func didSignOut() {
        live.stop(endingSession: true)
        liveTask?.cancel()
        liveTask = nil
        unreadNotifications = 0
        push.forget()
        push.setBadge(0)
        router.reset()
    }

    /// App-wide reactions to live events: delivery receipts and the Activity badge.
    private func startLiveHandling() {
        liveTask?.cancel()
        liveTask = Task { [weak self] in
            guard let stream = self?.live.events() else { return }
            for await event in stream {
                guard let self else { return }
                switch event.kind {
                case .messageCreated:
                    if let message = event.payload(as: Message.self), message.authorId != self.session.user?.id {
                        _ = try? await self.api.send(API.Chat.receipts(groupId: message.groupId, upTo: message.id, state: .delivered))
                    }
                case .notificationCreated:
                    self.unreadNotifications += 1
                    // Decided here, not in ActivityView, so the two subscribers can't race.
                    if self.router.tab == .activity {
                        await self.markNotificationsRead()
                    } else {
                        self.push.setBadge(self.unreadNotifications)
                    }
                case .reconnected:
                    await self.refreshUnread()
                default:
                    break
                }
            }
        }
    }
}

/// Lets the API client (created first) call back into the session (created after it).
@MainActor
private final class UnauthorizedRelay {
    var handler: ((String) -> Void)?

    nonisolated func fire(token: String) {
        Task { @MainActor in self.handler?(token) }
    }
}

/// The user's appearance choice. Cached locally so the sign-in screen and launch use it too;
/// saved to the account with `PATCH /me`.
@MainActor
@Observable
final class AppearanceStore {
    private(set) var mode: ThemeMode
    private(set) var colorTheme: ColorTheme
    @ObservationIgnored private let api: APIClient
    @ObservationIgnored private var saveTask: Task<Void, Never>?

    private static let modeKey = "appearance.mode"
    private static let themeKey = "appearance.theme"

    init(api: APIClient) {
        self.api = api
        let defaults = UserDefaults.standard
        mode = defaults.string(forKey: Self.modeKey).flatMap(ThemeMode.init) ?? .system
        colorTheme = defaults.string(forKey: Self.themeKey).flatMap(ColorTheme.init) ?? .dawn
    }

    /// Takes the server's values (on sign-in / refresh).
    func adopt(_ user: User) {
        apply(mode: user.themeMode, colorTheme: user.colorTheme)
    }

    /// Applies instantly, then saves to the account (debounced so quick taps send one request).
    func set(mode: ThemeMode? = nil, colorTheme: ColorTheme? = nil, session: SessionStore) {
        apply(mode: mode ?? self.mode, colorTheme: colorTheme ?? self.colorTheme)
        session.applyLocally { user in
            user.themeMode = self.mode
            user.colorTheme = self.colorTheme
        }
        saveTask?.cancel()
        let patch = UserPatch(themeMode: self.mode, colorTheme: self.colorTheme)
        saveTask = Task {
            try? await Task.sleep(for: .milliseconds(400))
            guard !Task.isCancelled else { return }
            _ = try? await session.update(patch)
        }
    }

    private func apply(mode: ThemeMode, colorTheme: ColorTheme) {
        self.mode = mode
        self.colorTheme = colorTheme
        UserDefaults.standard.set(mode.rawValue, forKey: Self.modeKey)
        UserDefaults.standard.set(colorTheme.rawValue, forKey: Self.themeKey)
    }
}
