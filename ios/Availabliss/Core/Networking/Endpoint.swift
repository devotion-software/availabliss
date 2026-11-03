import Foundation

enum HTTPMethod: String, Sendable {
    case get = "GET", post = "POST", put = "PUT", patch = "PATCH", delete = "DELETE"
}

/// Which mount an endpoint path is relative to.
enum APIMount: String, Sendable {
    case v1 = "/api/v1"
    case auth = "/api/auth"
}

/// A typed description of one API call. `Response` is what the body decodes to.
struct Endpoint<Response: Decodable & Sendable>: Sendable {
    typealias Mount = APIMount

    var method: HTTPMethod
    var mount: Mount
    var path: String
    var query: [URLQueryItem]
    var body: (any Encodable & Sendable)?
    /// Overrides the session's 30s request timeout for slow calls (the AI planner).
    var timeout: TimeInterval?

    init(
        _ method: HTTPMethod,
        _ path: String,
        mount: Mount = .v1,
        query: [String: String?] = [:],
        body: (any Encodable & Sendable)? = nil,
        timeout: TimeInterval? = nil
    ) {
        self.method = method
        self.mount = mount
        self.path = path
        self.query = query
            .compactMap { key, value in value.map { URLQueryItem(name: key, value: $0) } }
            .sorted { $0.name < $1.name }
        self.body = body
        self.timeout = timeout
    }

    /// Whether a 401 here means our session has ended. Better Auth routes and account linking
    /// answer 401 for a bad credential in the body (e.g. an Apple / Google id token that expired
    /// while the merge dialog was open), which is an ordinary error, not a reason to sign out.
    var unauthorizedEndsSession: Bool {
        mount != .auth && path != "/me/accounts/link"
    }
}

/// For `204 No Content` responses, and responses whose body we don't need.
struct EmptyResponse: Decodable, Sendable {
    init() {}
    init(from decoder: Decoder) throws {}
}

extension String {
    /// Percent-encodes a value for use as a single path segment.
    var pathEncoded: String {
        addingPercentEncoding(withAllowedCharacters: .urlPathAllowed.subtracting(CharacterSet(charactersIn: "/"))) ?? self
    }
}
