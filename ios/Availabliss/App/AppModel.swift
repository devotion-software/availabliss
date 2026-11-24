import Foundation
import Observation
import SwiftUI
import WidgetKit

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
    let groupDetails = GroupDetailStore()

    private(set) var unreadNotifications = 0
    /// The first-launch intro has been seen (or skipped), or someone has signed in on this device:
    /// it isn't shown again.
    var hasSeenIntro = UserDefaults.standard.bool(forKey: AppModel.hasSeenIntroKey) {
        didSet { UserDefaults.standard.set(hasSeenIntro, forKey: Self.hasSeenIntroKey) }
    }
    private static let hasSeenIntroKey = "hasSeenIntro"

    /// Skip or Get started on the intro: on to sign in, keeping the name typed there for the sign-up step.
    func finishIntro(name: String) {
        if !name.isEmpty { UserDefaults.standard.set(name, forKey: IntroName.key) }
        hasSeenIntro = true
    }
    /// Activity items known to be unread, so an update to one doesn't count it twice.
    @ObservationIgnored private var unreadIDs: Set<String> = []
    /// Bumped when another account is merged into this one, so screens showing account data reload.
    private(set) var accountVersion = 0
    /// Bumped when a timing template is saved, so the You screen's list reloads.
    private(set) var templatesVersion = 0
    @ObservationIgnored private var liveTask: Task<Void, Never>?

    /// The running app's model, for App Intents (which run in the app's process but outside the view tree).
    private(set) static weak var shared: AppModel?

    init(config: AppConfig = .load()) {
        // First, so a crash while the rest is set up is reported too.
        CrashReporting.start(config: config)
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
        WidgetSync.shared.configure(api: api)
        // New or renamed groups become phrases Siri recognises ("When's everyone in <group> free…").
        WidgetSync.shared.onGroupsChanged = { AvailablissShortcuts.updateAppShortcutParameters() }
        Self.shared = self
    }

    var auth: AuthService { AuthService(api: api, config: config) }

    // MARK: Lifecycle

    func launch() async {
        // A returning user opens straight from the cached account; it's refreshed alongside.
        let fromCache = await session.restore()
        guard session.isSignedIn else { return }
        if fromCache {
            Task {
                if await session.revalidate(), let user = session.user { appearance.adopt(user) }
            }
        }
        await didSignIn()
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
        await WidgetSync.shared.refresh()
    }

    /// A push arrived (in the foreground, or a background push): refreshes the widgets if it's one
    /// that can change them (a proposal, event or reminder).
    func pushReceived(kind: String?) async {
        guard session.isSignedIn, WidgetSync.refreshesOn(kind: kind) else { return }
        await WidgetSync.shared.refresh()
    }

    func open(_ link: DeepLink) {
        router.open(link)
    }

    func openFromActivity(_ link: DeepLink) {
        router.openFromActivity(link)
    }

    func templatesChanged() { templatesVersion += 1 }

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
        unreadIDs = Set(response.notifications.filter { $0.readAt == nil }.map(\.id))
        unreadNotifications = response.unread
        push.setBadge(response.unread)
    }

    /// Opening Activity reads everything but chats, which stay unread until the chat is read.
    func markNotificationsRead() async {
        guard unreadNotifications > 0 else { return }
        _ = try? await api.send(API.Notifications.markRead())
        await refreshUnread()
    }

    /// A new Activity item, or an update to one (a group's chat item gets a new message or is read).
    private func noteNotification(_ notification: AppNotification) {
        let wasUnread = unreadIDs.contains(notification.id)
        if notification.readAt == nil {
            unreadIDs.insert(notification.id)
            if !wasUnread { unreadNotifications += 1 }
        } else {
            unreadIDs.remove(notification.id)
            if wasUnread { unreadNotifications = max(0, unreadNotifications - 1) }
        }
    }

    // MARK: - Private

    private func didSignIn() async {
        if !hasSeenIntro { hasSeenIntro = true }
        if let user = session.user { appearance.adopt(user) }
        live.start()
        startLiveHandling()
        deviceCalendar.startObservingChanges()
        DeviceCalendarService.scheduleBackgroundRefresh()
        // A new account is asked about notifications once the sign-up wizard is done
        // (`didFinishOnboarding`), not over its first screen. None of these wait on each other.
        let onboarded = session.user?.onboarded ?? true
        async let pushReady: Void = onboarded ? push.requestAuthorizationAndRegister() : ()
        async let unread: Void = refreshUnread()
        async let calendars: Void = deviceCalendar.syncIfAuthorized()
        async let widgets: Void = onboarded ? WidgetSync.shared.refresh() : ()
        _ = await (pushReady, unread, calendars, widgets)
    }

    /// The end of the sign-up wizard: what `didSignIn` leaves until then.
    func didFinishOnboarding() async {
        async let pushReady: Void = push.requestAuthorizationAndRegister()
        async let widgets: Void = WidgetSync.shared.refresh()
        _ = await (pushReady, widgets)
    }

    private func didSignOut() {
        // Signed-out screens follow the device, like a new account's wizard does, rather than the
        // last account's choice (which comes back when they sign in again).
        appearance.reset()
        live.stop(endingSession: true)
        liveTask?.cancel()
        liveTask = nil
        unreadNotifications = 0
        unreadIDs = []
        push.forget()
        push.setBadge(0)
        router.reset()
        WidgetSync.shared.onSignedOut()
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
                    guard let notification = event.payload(as: AppNotification.self) else { break }
                    self.noteNotification(notification)
                    // Decided here, not in ActivityView, so the two subscribers can't race.
                    if self.router.tab == .activity, notification.readAt == nil, notification.kind != .message {
                        await self.markNotificationsRead()
                    } else {
                        self.push.setBadge(self.unreadNotifications)
                    }
                case .reconnected:
                    await self.refreshUnread()
                case .memberChanged:
                    // Removed from a group, or it was deleted: leave its screens wherever they're open.
                    if let groupId = event.groupId, let removed = event.removedMemberId, removed == self.session.user?.id {
                        self.router.closeGroup(groupId)
                    }
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

/// The user's appearance choice. Cached locally so launch uses it before the account loads;
/// saved to the account with `PATCH /me`. Reset on sign-out.
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

    /// Back to the defaults (follow the system, Dawn), e.g. after signing out.
    func reset() {
        saveTask?.cancel()
        apply(mode: .system, colorTheme: .fallback)
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
        // The widgets draw in your theme too.
        if WidgetAppearance.save(colorTheme: colorTheme.rawValue, mode: mode.rawValue) {
            WidgetCenter.shared.reloadAllTimelines()
        }
    }
}
