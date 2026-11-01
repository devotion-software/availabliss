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

    init(
        _ method: HTTPMethod,
        _ path: String,
        mount: Mount = .v1,
        query: [String: String?] = [:],
        body: (any Encodable & Sendable)? = nil
    ) {
        self.method = method
        self.mount = mount
        self.path = path
        self.query = query
            .compactMap { key, value in value.map { URLQueryItem(name: key, value: $0) } }
            .sorted { $0.name < $1.name }
        self.body = body
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
