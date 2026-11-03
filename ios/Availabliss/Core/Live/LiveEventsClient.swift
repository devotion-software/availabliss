import Foundation
import Observation

/// Keeps `GET /api/v1/live` (Server-Sent Events) open while the app is in the foreground
/// and fans events out to any number of subscribers.
///
/// The server keeps no replay buffer, so anything sent while we were disconnected (backgrounded,
/// offline) is lost. Every connection after the first yields a `.reconnected` event instead, and
/// screens refetch what they show.
@MainActor
@Observable
final class LiveEventsClient {
    private(set) var isConnected = false

    @ObservationIgnored private let api: APIClient
    @ObservationIgnored private var task: Task<Void, Never>?
    @ObservationIgnored private var subscribers: [UUID: AsyncStream<LiveEvent>.Continuation] = [:]
    /// Whether this session has connected before, so the next connection may have missed events.
    @ObservationIgnored private var hasConnected = false

    private static let session: URLSession = {
        let configuration = URLSessionConfiguration.default
        configuration.timeoutIntervalForRequest = 120   // idle time between bytes before we reconnect
        configuration.timeoutIntervalForResource = 24 * 60 * 60
        configuration.requestCachePolicy = .reloadIgnoringLocalCacheData
        return URLSession(configuration: configuration)
    }()

    init(api: APIClient) {
        self.api = api
    }

    /// A stream of every live event. Iterate it in a view's `.task`; it ends when the task is cancelled.
    func events() -> AsyncStream<LiveEvent> {
        let id = UUID()
        let (stream, continuation) = AsyncStream<LiveEvent>.makeStream(bufferingPolicy: .bufferingNewest(100))
        subscribers[id] = continuation
        continuation.onTermination = { [weak self] _ in
            Task { @MainActor in self?.subscribers[id] = nil }
        }
        return stream
    }

    /// Events for one group only (plus `.reconnected`, which concerns every group).
    func events(forGroup groupId: String) -> AsyncFilterSequence<AsyncStream<LiveEvent>> {
        events().filter { $0.groupId == groupId || $0.kind == .reconnected }
    }

    func start() {
        guard task == nil else { return }
        task = Task { [weak self] in
            var attempt = 0
            while !Task.isCancelled {
                guard let self else { return }
                do {
                    try await self.consumeStream { attempt = 0 }
                } catch {
                    // Fall through to reconnect.
                }
                self.isConnected = false
                attempt += 1
                let delay = min(30.0, pow(2.0, Double(min(attempt, 5))))
                try? await Task.sleep(for: .seconds(delay))
            }
        }
    }

    /// `endingSession` forgets the earlier connection, so the next account's first connection
    /// doesn't make freshly loaded screens refetch.
    func stop(endingSession: Bool = false) {
        task?.cancel()
        task = nil
        isConnected = false
        if endingSession { hasConnected = false }
    }

    // MARK: - Private

    private func consumeStream(onConnected: () -> Void) async throws {
        guard api.bearerToken != nil else { throw CancellationError() }
        var request = try api.authorizedRequest(path: "/live")
        request.setValue("text/event-stream", forHTTPHeaderField: "Accept")
        request.setValue("no-cache", forHTTPHeaderField: "Cache-Control")

        let (bytes, response) = try await Self.session.bytes(for: request)
        guard let http = response as? HTTPURLResponse, http.statusCode == 200 else {
            // Same rule as every other request: sign out if it's our current token that was
            // rejected. Signing out stops this loop; a stale token just retries with the new one.
            if (response as? HTTPURLResponse)?.statusCode == 401 { api.reportUnauthorized(for: request) }
            throw APIError.transport("Live stream unavailable")
        }
        isConnected = true
        onConnected()
        if hasConnected {
            yield(LiveEvent(kind: .reconnected, groupId: nil, raw: Data("{}".utf8)))
        }
        hasConnected = true

        var buffer = ""
        for try await line in bytes.lines {
            if line.hasPrefix("data:") {
                buffer += line.dropFirst(5).trimmingCharacters(in: .whitespaces)
                // Each event is a single `data: <json>` line; dispatch as soon as it parses.
                if dispatch(buffer) { buffer = "" }
            } else if line.isEmpty || line.hasPrefix(":") {
                if !buffer.isEmpty { _ = dispatch(buffer) }
                buffer = ""
            }
        }
    }

    private struct Envelope: Decodable {
        let type: String
        let groupId: String?
    }

    /// Returns true when `json` was a complete event (dispatched or deliberately ignored).
    private func dispatch(_ json: String) -> Bool {
        let data = Data(json.utf8)
        guard let envelope = try? JSONDecoder().decode(Envelope.self, from: data) else { return false }
        guard let kind = LiveEvent.Kind(rawValue: envelope.type) else { return true }
        guard kind != .reconnected else { return true }
        yield(LiveEvent(kind: kind, groupId: envelope.groupId, raw: data))
        return true
    }

    private func yield(_ event: LiveEvent) {
        for continuation in subscribers.values { continuation.yield(event) }
    }
}
