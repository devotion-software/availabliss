import Foundation

/// The single entry point for talking to the Availabliss API.
///
/// Every request goes through `send(_:)`, which adds the bearer token, encodes
/// the body, maps error bodies to `APIError` and decodes the response.
final class APIClient: Sendable {
    let baseURL: URL
    private let session: URLSession
    private let tokenStore: TokenStore
    /// Called with the bearer token a request was rejected for.
    private let onUnauthorized: @Sendable (String) -> Void

    init(baseURL: URL, tokenStore: TokenStore, session: URLSession = .availabliss, onUnauthorized: @escaping @Sendable (String) -> Void = { _ in }) {
        self.baseURL = baseURL
        self.tokenStore = tokenStore
        self.session = session
        self.onUnauthorized = onUnauthorized
    }

    /// Performs `endpoint` and decodes its body.
    @discardableResult
    func send<Response>(_ endpoint: Endpoint<Response>) async throws -> Response {
        try await sendReturningHeaders(endpoint).value
    }

    /// Performs `endpoint` and also returns the HTTP response (for reading headers like `set-auth-token`).
    func sendReturningHeaders<Response>(_ endpoint: Endpoint<Response>) async throws -> (value: Response, response: HTTPURLResponse) {
        let request = try makeRequest(for: endpoint)
        let (data, response) = try await perform(request, unauthorizedEndsSession: endpoint.unauthorizedEndsSession)

        if Response.self == EmptyResponse.self || data.isEmpty, let empty = EmptyResponse() as? Response {
            return (empty, response)
        }
        do {
            return (try JSONCoding.decoder.decode(Response.self, from: data), response)
        } catch {
            throw APIError.decoding(String(describing: error))
        }
    }

    /// Builds an authorised `URLRequest` for an arbitrary path (used by the SSE client).
    func authorizedRequest(path: String, mount: APIMount = .v1) throws -> URLRequest {
        try makeRequest(for: Endpoint<EmptyResponse>(.get, path, mount: mount))
    }

    func url(path: String, mount: APIMount = .v1, query: [String: String] = [:]) -> URL {
        var components = URLComponents(url: baseURL.appending(path: mount.rawValue + path), resolvingAgainstBaseURL: false)
        if !query.isEmpty {
            components?.queryItems = query.map { URLQueryItem(name: $0.key, value: $0.value) }
        }
        return components?.url ?? baseURL
    }

    var bearerToken: String? { tokenStore.token }

    /// Reports a 401 for `request` as the session ending, but only when it was sent with the token
    /// we hold now: a slow request from before a re-sign-in must not sign the new session out.
    func reportUnauthorized(for request: URLRequest) {
        guard let header = request.value(forHTTPHeaderField: "Authorization"), header.hasPrefix("Bearer ") else { return }
        let sentToken = String(header.dropFirst("Bearer ".count))
        guard sentToken == tokenStore.token else { return }
        onUnauthorized(sentToken)
    }

    // MARK: - Private

    private func makeRequest<Response>(for endpoint: Endpoint<Response>) throws -> URLRequest {
        var components = URLComponents(url: baseURL.appending(path: endpoint.mount.rawValue + endpoint.path), resolvingAgainstBaseURL: false)
        if !endpoint.query.isEmpty { components?.queryItems = endpoint.query }
        guard let url = components?.url else { throw APIError.transport("Invalid URL for \(endpoint.path)") }

        var request = URLRequest(url: url)
        request.httpMethod = endpoint.method.rawValue
        if let timeout = endpoint.timeout { request.timeoutInterval = timeout }
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        if let token = tokenStore.token {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        if let body = endpoint.body {
            request.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type")
            request.httpBody = try JSONCoding.encoder.encode(body)
        } else if endpoint.method != .get {
            // Better Auth rejects bodiless POSTs that claim JSON; send an empty object instead.
            request.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type")
            request.httpBody = Data("{}".utf8)
        }
        return request
    }

    private func perform(_ request: URLRequest, unauthorizedEndsSession: Bool) async throws -> (Data, HTTPURLResponse) {
        let data: Data
        let response: URLResponse
        do {
            (data, response) = try await session.data(for: request)
        } catch {
            if error.isCancellation { throw APIError.cancelled }
            throw APIError.transport(error.localizedDescription)
        }
        guard let http = response as? HTTPURLResponse else {
            throw APIError.transport("No HTTP response")
        }
        guard (200..<300).contains(http.statusCode) else {
            let body = try? JSONCoding.decoder.decode(APIErrorBody.self, from: data)
            let error = APIError(status: http.statusCode, body: body)
            if error.isUnauthorized, unauthorizedEndsSession { reportUnauthorized(for: request) }
            throw error
        }
        return (data, http)
    }
}

extension URLSession {
    /// Shared session. Cookies are kept because Better Auth's passkey flow stores the
    /// WebAuthn challenge in a short-lived cookie between the options and verify calls.
    static let availabliss: URLSession = {
        let configuration = URLSessionConfiguration.default
        configuration.httpCookieAcceptPolicy = .always
        configuration.httpShouldSetCookies = true
        configuration.timeoutIntervalForRequest = 30
        // Fail fast when offline. With waitsForConnectivity the 30 s timeout only starts
        // once there's a connection, so a request could wait up to the 7-day resource
        // timeout with its spinner (and the device sync's isSyncing) stuck.
        configuration.waitsForConnectivity = false
        configuration.timeoutIntervalForResource = 60
        return URLSession(configuration: configuration)
    }()
}
