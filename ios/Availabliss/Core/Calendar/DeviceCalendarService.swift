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
    /// How far ahead to read until the server says (`CalendarConnection.syncUntil`).
    static let defaultHorizonDays = 3 * 365
    /// Never read further than this, whatever the server asks.
    static let maxHorizonDays = 11 * 365
    /// Uploads are split at fixed boundaries this many days apart, so a change only
    /// re-sends its own range (EventKit also only searches four years at a time).
    static let rangeDays = 180
    /// The first range (from today) is re-sent at least this often, so the server's copy
    /// never goes stale for long; later ranges only when they change, or weekly.
    static let maxUploadAge: TimeInterval = 20 * 60 * 60
    static let maxFarUploadAge: TimeInterval = 7 * 24 * 60 * 60

    private(set) var authorization: EKAuthorizationStatus
    private(set) var calendars: [EKCalendar] = []
    private(set) var busyCalendarIds: Set<String>
    private(set) var isSyncing = false
    private(set) var lastSyncedAt: Date?
    private(set) var lastError: String?
    /// Asked to upgrade write-only access to full and didn't get it; iOS won't ask again.
    private(set) var fullAccessDeclined: Bool

    @ObservationIgnored let store = EKEventStore()
    @ObservationIgnored private let api: APIClient
    @ObservationIgnored private var changeObserver: NSObjectProtocol?
    @ObservationIgnored private var pendingSync: Task<Void, Never>?
    /// A sync was asked for while one was running; run once more when it finishes.
    @ObservationIgnored private var needsResync = false

    private static let busyKey = "calendar.busyIds"
    private static let addedKey = "calendar.addedEvents"
    private static let uploadedHashesKey = "calendar.uploadedHashes"
    private static let uploadedTimesKey = "calendar.uploadedTimes"
    private static let horizonKey = "calendar.horizon"
    /// Before uploads were split into ranges.
    private static let legacyKeys = ["calendar.uploadedHash", "calendar.uploadedAt"]
    private static let fullAccessDeclinedKey = "calendar.fullAccessDeclined"

    init(api: APIClient) {
        self.api = api
        self.authorization = EKEventStore.authorizationStatus(for: .event)
        self.busyCalendarIds = Set(UserDefaults.standard.stringArray(forKey: Self.busyKey) ?? [])
        self.fullAccessDeclined = UserDefaults.standard.bool(forKey: Self.fullAccessDeclinedKey)
        if hasFullAccess { reloadCalendars() }
    }

    var hasFullAccess: Bool { authorization == .fullAccess }
    var canWrite: Bool { authorization == .fullAccess || authorization == .writeOnly }
    /// Full access can only be turned on in Settings now.
    var needsSettingsForFullAccess: Bool {
        authorization == .denied || authorization == .restricted || (authorization == .writeOnly && fullAccessDeclined)
    }

    // MARK: Permission

    @discardableResult
    func requestFullAccess() async -> Bool {
        let granted = (try? await store.requestFullAccessToEvents()) ?? false
        authorization = EKEventStore.authorizationStatus(for: .event)
        if authorization == .writeOnly {
            fullAccessDeclined = true
            UserDefaults.standard.set(true, forKey: Self.fullAccessDeclinedKey)
        }
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
        for key in [Self.uploadedHashesKey, Self.uploadedTimesKey] + Self.legacyKeys {
            UserDefaults.standard.removeObject(forKey: key)
        }
    }

    private func upload(force: Bool) async throws {
        // Picks up calendars and accounts added or removed since launch.
        reloadCalendars(refreshSources: false)
        let defaults = UserDefaults.standard
        for key in Self.legacyKeys { defaults.removeObject(forKey: key) }

        let start = Calendar.current.startOfDay(for: Date())
        let ranges = Self.ranges(from: start, through: horizon(from: start))
        guard let coveredUntil = ranges.last?.end else { return }
        let selected = calendars.filter(isBusy)
        let deviceCalendars = calendars.map {
            DeviceSync.DeviceCalendar(externalId: $0.calendarIdentifier, name: $0.title, color: $0.cgColor.flatMap(Self.hex), busy: isBusy($0))
        }
        var hashes = (defaults.dictionary(forKey: Self.uploadedHashesKey) as? [String: String]) ?? [:]
        var times = (defaults.dictionary(forKey: Self.uploadedTimesKey) as? [String: Double]) ?? [:]
        let now = Date().timeIntervalSince1970

        // Each range's payload, and whether the server already has exactly it.
        let items = ranges.enumerated().map { index, range in
            let payload = DeviceSync(
                deviceId: DeviceIdentity.deviceId(),
                deviceName: UIDevice.current.name,
                rangeStart: range.start,
                rangeEnd: range.end,
                calendars: deviceCalendars,
                busy: busy(from: range.start, to: range.end, in: selected)
            )
            let key = String(Int(range.start.timeIntervalSince1970))
            let hash = Self.fingerprint(payload, token: api.bearerToken)
            let maxAge = index == 0 ? Self.maxUploadAge : Self.maxFarUploadAge
            let current = !force && hash != nil && hash == hashes[key] && times[key].map { now - $0 < maxAge } == true
            return (key: key, payload: payload, hash: hash, current: current)
        }
        let far = items.dropFirst().filter { !$0.current }
        guard let near = items.first, !(near.current && far.isEmpty) else { return }

        // Later ranges first; the near one goes last, even unchanged, so groups refresh once.
        var syncUntil: Date?
        for (offset, item) in (Array(far) + [near]).enumerated() {
            var payload = item.payload
            payload.coveredUntil = coveredUntil
            payload.more = offset < far.count
            let connection = try await api.send(API.Calendars.uploadDevice(payload))
            syncUntil = connection.syncUntil
            hashes[item.key] = item.hash
            times[item.key] = Date().timeIntervalSince1970
            defaults.set(hashes, forKey: Self.uploadedHashesKey)
            defaults.set(times, forKey: Self.uploadedTimesKey)
        }

        // Forget ranges that have rolled out of the window.
        let keys = Set(items.map(\.key))
        defaults.set(hashes.filter { keys.contains($0.key) }, forKey: Self.uploadedHashesKey)
        defaults.set(times.filter { keys.contains($0.key) }, forKey: Self.uploadedTimesKey)

        // The server wants more than this sync read (a group now plans further ahead): go again.
        if let syncUntil {
            defaults.set(syncUntil, forKey: Self.horizonKey)
            if syncUntil > coveredUntil { needsResync = true }
        }
    }

    /// How far ahead to read: what the server last asked for, within sensible bounds.
    private func horizon(from start: Date) -> Date {
        let day: TimeInterval = 24 * 60 * 60
        let asked = UserDefaults.standard.object(forKey: Self.horizonKey) as? Date
        let fallback = start.addingTimeInterval(Double(Self.defaultHorizonDays) * day)
        return min(asked.map { max($0, start.addingTimeInterval(day)) } ?? fallback, start.addingTimeInterval(Double(Self.maxHorizonDays) * day))
    }

    /// [start, first boundary), then whole ranges between fixed boundaries until one
    /// reaches `horizon`. Fixed boundaries keep later ranges (and their hashes) stable day to day.
    static func ranges(from start: Date, through horizon: Date) -> [(start: Date, end: Date)] {
        let step = Double(rangeDays) * 24 * 60 * 60
        var bounds = [start]
        var next = (floor(start.timeIntervalSince1970 / step) + 1) * step
        repeat {
            bounds.append(Date(timeIntervalSince1970: next))
            next += step
        } while bounds[bounds.count - 1] < horizon
        return zip(bounds, bounds.dropFirst()).map { (start: $0, end: $1) }
    }

    private func busy(from start: Date, to end: Date, in selected: [EKCalendar]) -> [DeviceSync.BusyRange] {
        guard !selected.isEmpty else { return [] }
        return store
            .events(matching: store.predicateForEvents(withStart: start, end: end, calendars: selected))
            .filter { $0.availability != .free && $0.status != .canceled && !Self.isDeclined($0) }
            .map { DeviceSync.BusyRange(calendarExternalId: $0.calendar.calendarIdentifier, start: $0.startDate, end: $0.endDate) }
            .sorted { ($0.start, $0.end, $0.calendarExternalId) < ($1.start, $1.end, $1.calendarExternalId) }
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
        // The API's all-day end is the exclusive midnight after the last day; EventKit
        // treats an all-day endDate as inclusive, so that midnight would add a day.
        item.endDate = event.allDay ? max(event.start, event.end.addingTimeInterval(-1)) : event.end
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
