import Foundation
import Observation

/// Keeps `GET /api/v1/live` (Server-Sent Events) open while the app is in the foreground
/// and fans events out to any number of subscribers.
@MainActor
@Observable
final class LiveEventsClient {
    private(set) var isConnected = false

    @ObservationIgnored private let api: APIClient
    @ObservationIgnored private var task: Task<Void, Never>?
    @ObservationIgnored private var subscribers: [UUID: AsyncStream<LiveEvent>.Continuation] = [:]

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

    /// Events for one group only.
    func events(forGroup groupId: String) -> AsyncFilterSequence<AsyncStream<LiveEvent>> {
        events().filter { $0.groupId == groupId }
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

    func stop() {
        task?.cancel()
        task = nil
        isConnected = false
    }

    // MARK: - Private

    private func consumeStream(onConnected: () -> Void) async throws {
        guard api.bearerToken != nil else { throw CancellationError() }
        var request = try api.authorizedRequest(path: "/live")
        request.setValue("text/event-stream", forHTTPHeaderField: "Accept")
        request.setValue("no-cache", forHTTPHeaderField: "Cache-Control")

        let (bytes, response) = try await Self.session.bytes(for: request)
        guard let http = response as? HTTPURLResponse, http.statusCode == 200 else {
            throw APIError.transport("Live stream unavailable")
        }
        isConnected = true
        onConnected()

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
        let event = LiveEvent(kind: kind, groupId: envelope.groupId, raw: data)
        for continuation in subscribers.values { continuation.yield(event) }
        return true
    }
}
