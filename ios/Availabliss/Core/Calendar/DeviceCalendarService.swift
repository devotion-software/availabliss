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
    /// Plans added to a calendar from this phone, by event id.
    private(set) var addedEvents: [String: AddedEvent] = [:]
    /// Bumped when the calendar database changes, so views re-check added plans.
    private(set) var storeVersion = 0

    @ObservationIgnored let store = EKEventStore()
    @ObservationIgnored private let api: APIClient
    @ObservationIgnored private var changeObserver: NSObjectProtocol?
    @ObservationIgnored private var pendingSync: Task<Void, Never>?
    /// A sync was asked for while one was running; run once more when it finishes.
    @ObservationIgnored private var needsResync = false

    private static let busyKey = "calendar.busyIds"
    /// Before the calendar and the plan's times were kept too (event id → item id).
    private static let addedKey = "calendar.addedEvents"
    private static let addedV2Key = "calendar.addedEvents.v2"
    /// The calendar plans were last added to, offered first next time.
    private static let lastAddCalendarKey = "calendar.lastAddCalendar"
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
        self.addedEvents = loadAdded()
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
                self?.storeVersion += 1
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

    /// An opaque key for the event's series: every occurrence of a recurring event shares its
    /// calendar item id. Hashed (22 base64url chars, as the server does) so the id isn't sent.
    static func eventKey(_ event: EKEvent) -> String? {
        guard let id = event.calendarItemExternalIdentifier ?? event.calendarItemIdentifier as String?, !id.isEmpty else { return nil }
        let digest = Data(SHA256.hash(data: Data(id.utf8)))
        return String(digest.base64EncodedString().replacingOccurrences(of: "+", with: "-").replacingOccurrences(of: "/", with: "_").prefix(22))
    }

    private func busy(from start: Date, to end: Date, in selected: [EKCalendar]) -> [DeviceSync.BusyRange] {
        guard !selected.isEmpty else { return [] }
        return
            store
            .events(matching: store.predicateForEvents(withStart: start, end: end, calendars: selected))
            .filter { $0.availability != .free && $0.status != .canceled && !Self.isDeclined($0) }
            .map {
                DeviceSync.BusyRange(
                    calendarExternalId: $0.calendar.calendarIdentifier, start: $0.startDate, end: Self.busyEnd($0),
                    eventKey: Self.eventKey($0), title: $0.title?.trimmingCharacters(in: .whitespacesAndNewlines)
                )
            }
            .sorted { ($0.start, $0.end, $0.calendarExternalId) < ($1.start, $1.end, $1.calendarExternalId) }
    }

    /// EventKit ends an all-day event at 23:59:59 on its last day; busy time runs to the midnight
    /// after, so that day counts as busy all day like the ones before it.
    static func busyEnd(_ event: EKEvent, calendar: Calendar = .current) -> Date {
        guard event.isAllDay, let end = event.endDate else { return event.endDate }
        let midnight = calendar.startOfDay(for: end)
        return midnight == end ? end : calendar.date(byAdding: .day, value: 1, to: midnight) ?? end
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
            let parts = rgb.components, parts.count >= 3
        else { return nil }
        return String(format: "#%02X%02X%02X", Int(parts[0] * 255), Int(parts[1] * 255), Int(parts[2] * 255))
    }

    private func persistBusy() {
        UserDefaults.standard.set(Array(busyCalendarIds), forKey: Self.busyKey)
    }

    // MARK: Adding events

    /// A plan this phone put in a calendar: the calendar copy, and the plan's times when it was
    /// added (to notice the plan moving). Older installs only kept the identifier.
    struct AddedEvent: Codable, Equatable {
        var itemId: String
        var calendarId: String?
        var start: Date?
        var end: Date?
    }

    /// Where a plan stands in the phone's calendars.
    enum AddedState: Equatable {
        case notAdded
        /// In `calendar` (nil when write-only access can't say which, or check it's still there).
        case added(EKCalendar?)
    }

    func addedState(_ event: GroupEvent) -> AddedState {
        if let record = addedEvents[event.id] {
            guard hasFullAccess else { return .added(nil) }
            if let item = addedItem(record) { return .added(item.calendar) }
        }
        // Not added from this install (e.g. the app was reinstalled), or deleted in the calendar
        // app since: it still counts as added if the calendar has an event with the same title and times.
        if let item = matchingItem(for: event) { return .added(item.calendar) }
        return .notAdded
    }

    /// Matches found by `matchingItem`, by plan id, for the current `storeVersion`.
    @ObservationIgnored private var matches: [String: (version: Int, key: String, itemId: String?)] = [:]

    /// An event in the phone's calendars with the plan's title and times (needs full access).
    private func matchingItem(for event: GroupEvent) -> EKEvent? {
        let version = storeVersion
        guard hasFullAccess else { return nil }
        let key = "\(event.title)|\(event.start.timeIntervalSince1970)|\(event.end.timeIntervalSince1970)|\(event.allDay)"
        if let cached = matches[event.id], cached.version == version, cached.key == key {
            return cached.itemId.flatMap { store.event(withIdentifier: $0) }
        }
        let probe = EKEvent(eventStore: store)
        fill(probe, from: event, includeNote: false)
        let title = event.title.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let predicate = store.predicateForEvents(withStart: probe.startDate.addingTimeInterval(-60), end: probe.endDate.addingTimeInterval(60), calendars: nil)
        let found = store.events(matching: predicate).first { item in
            item.title?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() == title
                && item.isAllDay == probe.isAllDay
                && sameTime(item.startDate, probe.startDate, allDay: probe.isAllDay)
                && sameTime(item.endDate, probe.endDate, allDay: probe.isAllDay)
        }
        matches[event.id] = (version, key, found?.eventIdentifier)
        return found
    }

    /// Same minute, or the same day for all-day events.
    private func sameTime(_ a: Date?, _ b: Date, allDay: Bool) -> Bool {
        guard let a else { return false }
        return allDay ? Calendar.current.isDate(a, inSameDayAs: b) : abs(a.timeIntervalSince(b)) < 60
    }

    private func addedItem(_ record: AddedEvent) -> EKEvent? {
        _ = storeVersion
        guard hasFullAccess, !record.itemId.isEmpty else { return nil }
        return store.event(withIdentifier: record.itemId)
    }

    private func loadAdded() -> [String: AddedEvent] {
        let defaults = UserDefaults.standard
        var added: [String: AddedEvent] = [:]
        if let data = defaults.data(forKey: Self.addedV2Key),
            let decoded = try? JSONDecoder().decode([String: AddedEvent].self, from: data)
        {
            added = decoded
        }
        if let legacy = defaults.dictionary(forKey: Self.addedKey) as? [String: String] {
            for (eventId, itemId) in legacy where added[eventId] == nil {
                added[eventId] = AddedEvent(itemId: itemId)
            }
            defaults.removeObject(forKey: Self.addedKey)
            if let data = try? JSONEncoder().encode(added) { defaults.set(data, forKey: Self.addedV2Key) }
        }
        return added
    }

    private func record(_ item: EKEvent, for event: GroupEvent) {
        addedEvents[event.id] = AddedEvent(itemId: item.eventIdentifier ?? "", calendarId: item.calendar?.calendarIdentifier, start: event.start, end: event.end)
        if let data = try? JSONEncoder().encode(addedEvents) { UserDefaults.standard.set(data, forKey: Self.addedV2Key) }
    }

    /// Whether to ask which calendar before adding: only with full access (write-only
    /// access can't list calendars) and more than one to choose from.
    var canChooseCalendar: Bool { hasFullAccess && writableCalendars.count > 1 }

    /// The calendar to offer first: the one last added to, else the phone's default.
    var preferredCalendar: EKCalendar? {
        if let id = UserDefaults.standard.string(forKey: Self.lastAddCalendarKey),
            let calendar = writableCalendars.first(where: { $0.calendarIdentifier == id })
        {
            return calendar
        }
        return store.defaultCalendarForNewEvents
    }

    /// Writable calendars grouped by account, for choosing where a plan goes.
    var writableCalendarsBySource: [(source: String, calendars: [EKCalendar])] {
        Dictionary(grouping: writableCalendars, by: { $0.source.title })
            .map { (source: $0.key, calendars: $0.value) }
            .sorted { $0.source < $1.source }
    }

    /// Saves a confirmed plan into the user's calendar. Asks for write access if needed.
    func add(_ event: GroupEvent, to calendar: EKCalendar? = nil) async throws {
        if !canWrite {
            _ = try await store.requestWriteOnlyAccessToEvents()
            authorization = EKEventStore.authorizationStatus(for: .event)
        }
        guard canWrite else { throw CalendarAccessError.denied }

        let item = EKEvent(eventStore: store)
        fill(item, from: event)
        item.calendar = calendar ?? store.defaultCalendarForNewEvents
        try store.save(item, span: .thisEvent, commit: true)
        record(item, for: event)
        if let calendar {
            UserDefaults.standard.set(calendar.calendarIdentifier, forKey: Self.lastAddCalendarKey)
        }
    }

    /// Moves an added plan to another calendar, or adds it there again if it was deleted from
    /// the calendar since. Remembers the calendar for next time.
    func move(_ event: GroupEvent, to calendar: EKCalendar) async throws {
        guard let item = addedEvents[event.id].flatMap(addedItem) ?? matchingItem(for: event) else {
            try await add(event, to: calendar)
            return
        }
        let record = addedEvents[event.id] ?? AddedEvent(itemId: item.eventIdentifier ?? "")
        UserDefaults.standard.set(calendar.calendarIdentifier, forKey: Self.lastAddCalendarKey)
        guard item.calendar?.calendarIdentifier != calendar.calendarIdentifier else { return }
        let previous = item.calendar
        item.calendar = calendar
        do {
            try store.save(item, span: .thisEvent, commit: true)
            self.record(item, for: event)
        } catch {
            // Some accounts won't take an event from another account: copy it over instead.
            item.calendar = previous
            let copy = EKEvent(eventStore: store)
            fill(copy, from: event)
            copy.calendar = calendar
            try store.save(copy, span: .thisEvent, commit: true)
            self.record(copy, for: event)
            if let old = store.event(withIdentifier: record.itemId) {
                try? store.remove(old, span: .thisEvent, commit: true)
            }
        }
    }

    /// Updates the calendar copy when the plan has moved since it was added (only the time,
    /// title and place, and only if the plan itself changed, so edits made in the calendar stay).
    func updateIfPlanChanged(_ event: GroupEvent) {
        guard let record = addedEvents[event.id], let start = record.start, let end = record.end,
            start != event.start || end != event.end,
            let item = addedItem(record)
        else { return }
        fill(item, from: event, includeNote: false)
        guard (try? store.save(item, span: .thisEvent, commit: true)) != nil else { return }
        self.record(item, for: event)
    }

    private func fill(_ item: EKEvent, from event: GroupEvent, includeNote: Bool = true) {
        item.title = event.title
        item.startDate = event.start
        // The API's all-day end is the exclusive midnight after the last day; EventKit
        // treats an all-day endDate as inclusive, so that midnight would add a day.
        item.endDate = event.allDay ? max(event.start, event.end.addingTimeInterval(-1)) : event.end
        item.isAllDay = event.allDay
        item.location = event.location
        if includeNote { item.notes = event.note }
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
