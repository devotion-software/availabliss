import BackgroundTasks
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

    private static let busyKey = "calendar.busyIds"
    private static let addedKey = "calendar.addedEvents"

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

    func reloadCalendars() {
        store.refreshSourcesIfNecessary()
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

    // MARK: Sync

    func startObservingChanges() {
        guard changeObserver == nil else { return }
        changeObserver = NotificationCenter.default.addObserver(forName: .EKEventStoreChanged, object: store, queue: .main) { [weak self] _ in
            MainActor.assumeIsolated { self?.scheduleSync() }
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
    func syncIfAuthorized() async {
        authorization = EKEventStore.authorizationStatus(for: .event)
        guard hasFullAccess, api.bearerToken != nil, !isSyncing else { return }
        isSyncing = true
        defer { isSyncing = false }
        do {
            try await upload()
            lastSyncedAt = Date()
            lastError = nil
        } catch {
            lastError = error.userMessage
        }
    }

    private func upload() async throws {
        if calendars.isEmpty { reloadCalendars() }
        let start = Calendar.current.startOfDay(for: Date())
        let end = Calendar.current.date(byAdding: .day, value: Self.syncWindowDays, to: start) ?? start
        let selected = calendars.filter(isBusy)

        let busy: [DeviceSync.BusyRange] = selected.isEmpty ? [] : store
            .events(matching: store.predicateForEvents(withStart: start, end: end, calendars: selected))
            .filter { $0.availability != .free && $0.status != .canceled && !Self.isDeclined($0) }
            .map { DeviceSync.BusyRange(calendarExternalId: $0.calendar.calendarIdentifier, start: $0.startDate, end: $0.endDate) }

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
        try await api.send(API.Calendars.uploadDevice(payload))
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
