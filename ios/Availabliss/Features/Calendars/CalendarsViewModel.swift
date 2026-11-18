import Foundation
import Observation

/// Connected calendars (Google / Outlook / iCloud CalDAV) plus the toggles for on-device calendars.
@MainActor
@Observable
final class CalendarsViewModel {
    private(set) var connections: [CalendarConnection] = []
    private(set) var isLoading = false
    private(set) var connectingProvider: CalendarProvider?
    /// Connections a "Sync now" is running for.
    private(set) var syncingIds: Set<String> = []
    var error: String?
    var toast: String?
    /// Your secret link to subscribe to your plans; loaded when its card first appears.
    private(set) var calendarFeed: CalendarFeed?
    private(set) var isResettingFeed = false

    private let api: APIClient

    init(api: APIClient) {
        self.api = api
    }

    /// Remote connections. On-device calendars are shown from EventKit directly, so
    /// `device` connections (this or other phones) are left out here.
    var remoteConnections: [CalendarConnection] { connections.filter { $0.provider != .device } }

    func load() async {
        isLoading = connections.isEmpty
        defer { isLoading = false }
        do {
            connections = try await api.send(API.Calendars.list()).connections
        } catch where !error.isCancellation {
            self.error = error.userMessage
        } catch {}
    }

    func setBusy(_ busy: Bool, calendar: CalendarConnection.Calendar, in connection: CalendarConnection) async {
        replace(connection.id) { conn in
            if let index = conn.calendars.firstIndex(where: { $0.id == calendar.id }) { conn.calendars[index].busy = busy }
        }
        do {
            let updated = try await api.send(API.Calendars.setBusy(connectionId: connection.id, calendarId: calendar.id, busy: busy))
            replace(connection.id) { $0 = updated }
        } catch {
            self.error = error.userMessage
            await load()
        }
    }

    /// Google / Microsoft consent: get a signed consent URL, then open it in `ASWebAuthenticationSession`.
    func connectOAuth(_ provider: CalendarProvider) async {
        connectingProvider = provider
        defer { connectingProvider = nil }
        do {
            let url = try await api.send(API.Calendars.oauthURL(provider: provider, redirect: "\(AppConfig.urlScheme)://calendars")).url
            let callback = try await WebAuthenticator().authenticate(url: url, callbackScheme: AppConfig.urlScheme)
            // The server returns a one-time code; finishing with our bearer token proves
            // the signed-in user is the one who started the flow.
            guard case .calendars(let complete, _)? = DeepLink(url: callback), let complete else {
                if case .calendars(_, let failure?)? = DeepLink(url: callback) {
                    error = "Couldn't connect: \(failure)"
                } else {
                    error = "Couldn't connect that calendar"
                }
                return
            }
            _ = try await api.send(API.Calendars.completeOAuth(provider: provider, code: complete))
            await load()
            toast = "\(provider.displayName) connected"
        } catch where error.isCancellation {
        } catch {
            self.error = error.userMessage
        }
    }

    func connectCalDAV(username: String, appPassword: String) async -> Bool {
        connectingProvider = .caldav
        defer { connectingProvider = nil }
        do {
            let connection = try await api.send(API.Calendars.connectCalDAV(CalDAVBody(username: username, appPassword: appPassword)))
            connections.append(connection)
            toast = "iCloud connected"
            return true
        } catch {
            self.error = error.userMessage
            return false
        }
    }

    /// "Sync now": the server pulls the account straight away; a failed pull comes back as its status.
    func sync(_ connection: CalendarConnection) async {
        guard !syncingIds.contains(connection.id) else { return }
        syncingIds.insert(connection.id)
        defer { syncingIds.remove(connection.id) }
        do {
            let updated = try await api.send(API.Calendars.sync(connectionId: connection.id))
            replace(connection.id) { $0 = updated }
            switch updated.status {
            case .ok: break
            case .reauthRequired: error = "Couldn't sync \(updated.provider.displayName). It needs reconnecting."
            case .error: error = "Couldn't sync \(updated.provider.displayName). Try again in a moment."
            }
        } catch where !error.isCancellation {
            self.error = error.userMessage
        } catch {}
    }

    func disconnect(_ connection: CalendarConnection) async {
        do {
            try await api.send(API.Calendars.delete(connectionId: connection.id))
            connections.removeAll { $0.id == connection.id }
        } catch {
            self.error = error.userMessage
        }
    }

    /// Fetches the calendar feed link (the server makes one the first time).
    func loadCalendarFeed() async {
        guard calendarFeed == nil else { return }
        do {
            calendarFeed = try await api.send(API.Me.calendarFeed())
        } catch where !error.isCancellation {
            self.error = error.userMessage
        } catch {}
    }

    /// Swaps the feed link for a new one; calendars subscribed with the old one stop updating.
    func resetCalendarFeed() async {
        isResettingFeed = true
        defer { isResettingFeed = false }
        do {
            calendarFeed = try await api.send(API.Me.resetCalendarFeed())
            toast = "New link ready"
        } catch where !error.isCancellation {
            self.error = error.userMessage
        } catch {}
    }

    private func replace(_ id: String, _ change: (inout CalendarConnection) -> Void) {
        guard let index = connections.firstIndex(where: { $0.id == id }) else { return }
        change(&connections[index])
    }
}
