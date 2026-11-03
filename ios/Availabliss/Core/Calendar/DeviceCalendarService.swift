import BackgroundTasks
import CryptoKit
import EventKit
import Observation
import UIKit

/// Everything EventKit: permission, choosing busy calendars, uploading busy time
/// (`PUT /calendars/device` — only start/end, never titles) and adding events.
@MainActor
@Observable
final class DeviceCalendarService {
    /// Days ahead the device sync covers.
    static let syncWindowDays = 120
    /// An unchanged payload is still re-sent after this long, so the server's copy
    /// never goes stale for long (the window also rolls forward each day).
    static let maxUploadAge: TimeInterval = 20 * 60 * 60

    private(set) var authorization: EKAuthorizationStatus
    private(set) var calendars: [EKCalendar] = []
    private(set) var busyCalendarIds: Set<String>
    private(set) var isSyncing = false
    private(set) var lastSyncedAt: Date?
    private(set) var lastError: String?

    @ObservationIgnored let store = EKEventStore()
    @ObservationIgnored private let api: APIClient
    @ObservationIgnored private var changeObserver: NSObjectProtocol?
    @ObservationIgnored private var pendingSync: Task<Void, Never>?
    /// A sync was asked for while one was running; run once more when it finishes.
    @ObservationIgnored private var needsResync = false

    private static let busyKey = "calendar.busyIds"
    private static let addedKey = "calendar.addedEvents"
    private static let uploadedHashKey = "calendar.uploadedHash"
    private static let uploadedAtKey = "calendar.uploadedAt"

    init(api: APIClient) {
        self.api = api
        self.authorization = EKEventStore.authorizationStatus(for: .event)
        self.busyCalendarIds = Set(UserDefaults.standard.stringArray(forKey: Self.busyKey) ?? [])
        if hasFullAccess { reloadCalendars() }
    }

    var hasFullAccess: Bool { authorization == .fullAccess }
    var canWrite: Bool { authorization == .fullAccess || authorization == .writeOnly }

    // MARK: Permission

    @discardableResult
    func requestFullAccess() async -> Bool {
        let granted = (try? await store.requestFullAccessToEvents()) ?? false
        authorization = EKEventStore.authorizationStatus(for: .event)
        if granted {
            reloadCalendars()
            startObservingChanges()
        }
        return granted
    }

    // MARK: Calendars

    /// Re-reads the phone's calendars. `refreshSources` also asks EventKit to refresh
    /// accounts, which can itself post EKEventStoreChanged, so the sync path skips it.
    func reloadCalendars(refreshSources: Bool = true) {
        if refreshSources { store.refreshSourcesIfNecessary() }
        calendars = store.calendars(for: .event).sorted {
            ($0.source.title, $0.title) < ($1.source.title, $1.title)
        }
        if UserDefaults.standard.object(forKey: Self.busyKey) == nil {
            // First run: everything except birthdays and subscribed calendars means busy.
            busyCalendarIds = Set(calendars.filter { $0.type != .birthday && $0.type != .subscription }.map(\.calendarIdentifier))
            persistBusy()
        }
    }

    /// Calendars grouped by account ("iCloud", "Gmail", …) for the picker.
    var calendarsBySource: [(source: String, calendars: [EKCalendar])] {
        Dictionary(grouping: calendars, by: { $0.source.title })
            .map { (source: $0.key, calendars: $0.value) }
            .sorted { $0.source < $1.source }
    }

    func isBusy(_ calendar: EKCalendar) -> Bool { busyCalendarIds.contains(calendar.calendarIdentifier) }

    func setBusy(_ busy: Bool, for calendar: EKCalendar) {
        if busy { busyCalendarIds.insert(calendar.calendarIdentifier) } else { busyCalendarIds.remove(calendar.calendarIdentifier) }
        persistBusy()
        scheduleSync()
    }

    var writableCalendars: [EKCalendar] { calendars.filter(\.allowsContentModifications) }

    /// Short sync status for the Profile row: nil without access.
    var statusSummary: String? {
        guard hasFullAccess else { return nil }
        if lastError != nil { return "Sync problem" }
        if let lastSyncedAt { return "Synced \(Formatting.relative(lastSyncedAt))" }
        return isSyncing ? "Syncing…" : "On"
    }

    // MARK: Sync

    func startObservingChanges() {
        guard changeObserver == nil else { return }
        changeObserver = NotificationCenter.default.addObserver(forName: .EKEventStoreChanged, object: store, queue: .main) { [weak self] _ in
            MainActor.assumeIsolated {
                // Calendars or whole accounts may have been added or removed.
                self?.reloadCalendars(refreshSources: false)
                self?.scheduleSync()
            }
        }
    }

    /// Debounced sync after changes.
    func scheduleSync(after delay: Duration = .seconds(2)) {
        pendingSync?.cancel()
        pendingSync = Task { [weak self] in
            try? await Task.sleep(for: delay)
            guard !Task.isCancelled else { return }
            await self?.syncIfAuthorized()
        }
    }

    /// Uploads busy ranges if the user granted calendar access and is signed in.
    /// Skips the upload when nothing changed since the last one (see `maxUploadAge`).
    /// A call made while a sync is running makes that sync go round once more.
    func syncIfAuthorized(force: Bool = false) async {
        authorization = EKEventStore.authorizationStatus(for: .event)
        guard hasFullAccess, api.bearerToken != nil else { return }
        guard !isSyncing else {
            needsResync = true
            return
        }
        isSyncing = true
        defer { isSyncing = false }
        var force = force
        repeat {
            needsResync = false
            do {
                try await upload(force: force)
                lastSyncedAt = Date()
                lastError = nil
            } catch {
                lastError = error.userMessage
            }
            force = false
        } while needsResync && hasFullAccess && api.bearerToken != nil
    }

    /// Forgets the last upload so the next sync sends everything (e.g. after an account merge).
    func forgetLastUpload() {
        UserDefaults.standard.removeObject(forKey: Self.uploadedHashKey)
        UserDefaults.standard.removeObject(forKey: Self.uploadedAtKey)
    }

    private func upload(force: Bool) async throws {
        // Picks up calendars and accounts added or removed since launch.
        reloadCalendars(refreshSources: false)
        let start = Calendar.current.startOfDay(for: Date())
        let end = Calendar.current.date(byAdding: .day, value: Self.syncWindowDays, to: start) ?? start
        let selected = calendars.filter(isBusy)

        let busy: [DeviceSync.BusyRange] = selected.isEmpty ? [] : store
            .events(matching: store.predicateForEvents(withStart: start, end: end, calendars: selected))
            .filter { $0.availability != .free && $0.status != .canceled && !Self.isDeclined($0) }
            .map { DeviceSync.BusyRange(calendarExternalId: $0.calendar.calendarIdentifier, start: $0.startDate, end: $0.endDate) }
            .sorted { ($0.start, $0.end, $0.calendarExternalId) < ($1.start, $1.end, $1.calendarExternalId) }

        let payload = DeviceSync(
            deviceId: DeviceIdentity.deviceId(),
            deviceName: UIDevice.current.name,
            rangeStart: start,
            rangeEnd: end,
            calendars: calendars.map {
                DeviceSync.DeviceCalendar(externalId: $0.calendarIdentifier, name: $0.title, color: $0.cgColor.flatMap(Self.hex), busy: isBusy($0))
            },
            busy: busy
        )

        let hash = Self.fingerprint(payload, token: api.bearerToken)
        let defaults = UserDefaults.standard
        if !force, let hash, hash == defaults.string(forKey: Self.uploadedHashKey),
           let uploadedAt = defaults.object(forKey: Self.uploadedAtKey) as? Date,
           Date().timeIntervalSince(uploadedAt) < Self.maxUploadAge {
            return
        }
        try await api.send(API.Calendars.uploadDevice(payload))
        defaults.set(hash, forKey: Self.uploadedHashKey)
        defaults.set(Date(), forKey: Self.uploadedAtKey)
    }

    /// SHA-256 of the payload plus the session, so another account on this phone
    /// (or a new sign-in) always uploads.
    private static func fingerprint(_ payload: DeviceSync, token: String?) -> String? {
        let encoder = JSONEncoder()
        encoder.outputFormatting = .sortedKeys
        encoder.dateEncodingStrategy = .secondsSince1970
        guard var data = try? encoder.encode(payload) else { return nil }
        data.append(Data((token ?? "").utf8))
        return SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
    }

    private static func isDeclined(_ event: EKEvent) -> Bool {
        event.attendees?.contains { $0.isCurrentUser && $0.participantStatus == .declined } ?? false
    }

    private static func hex(_ color: CGColor) -> String? {
        guard let rgb = color.converted(to: CGColorSpace(name: CGColorSpace.sRGB) ?? CGColorSpaceCreateDeviceRGB(), intent: .defaultIntent, options: nil),
              let parts = rgb.components, parts.count >= 3 else { return nil }
        return String(format: "#%02X%02X%02X", Int(parts[0] * 255), Int(parts[1] * 255), Int(parts[2] * 255))
    }

    private func persistBusy() {
        UserDefaults.standard.set(Array(busyCalendarIds), forKey: Self.busyKey)
    }

    // MARK: Adding events

    func isAdded(_ eventId: String) -> Bool {
        (UserDefaults.standard.dictionary(forKey: Self.addedKey) as? [String: String])?[eventId] != nil
    }

    /// Saves a confirmed plan into the user's calendar. Asks for write access if needed.
    func add(_ event: GroupEvent, to calendar: EKCalendar? = nil) async throws {
        if !canWrite {
            _ = try await store.requestWriteOnlyAccessToEvents()
            authorization = EKEventStore.authorizationStatus(for: .event)
        }
        guard canWrite else { throw CalendarAccessError.denied }

        let item = EKEvent(eventStore: store)
        item.title = event.title
        item.startDate = event.start
        item.endDate = event.end
        item.isAllDay = event.allDay
        item.location = event.location
        item.notes = event.note
        item.calendar = calendar ?? store.defaultCalendarForNewEvents
        try store.save(item, span: .thisEvent, commit: true)

        var added = (UserDefaults.standard.dictionary(forKey: Self.addedKey) as? [String: String]) ?? [:]
        added[event.id] = item.eventIdentifier ?? ""
        UserDefaults.standard.set(added, forKey: Self.addedKey)
    }

    // MARK: Background refresh

    static func scheduleBackgroundRefresh() {
        let request = BGAppRefreshTaskRequest(identifier: AppConfig.calendarRefreshTask)
        request.earliestBeginDate = Date(timeIntervalSinceNow: 4 * 60 * 60)
        try? BGTaskScheduler.shared.submit(request)
    }
}

enum CalendarAccessError: LocalizedError {
    case denied
    var errorDescription: String? {
        "Availabliss doesn't have access to your calendar. You can allow it in Settings."
    }
}
