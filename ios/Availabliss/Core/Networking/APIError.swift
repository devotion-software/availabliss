import Foundation

/// The error body every non-2xx response carries (see "Errors" in API.md).
struct APIErrorBody: Decodable, Sendable, Equatable {
    struct Issue: Decodable, Sendable, Equatable {
        let path: String
        let message: String
    }
    let message: String
    let code: String?
    let issues: [Issue]?
}

enum APIError: LocalizedError, Sendable, Equatable {
    case unauthorized(String)
    case forbidden(String)
    case notFound(String)
    case validation(String, issues: [APIErrorBody.Issue])
    case conflict(String)
    case rateLimited(String)
    case server(String)
    case http(status: Int, message: String)
    case transport(String)
    case decoding(String)
    case cancelled

    init(status: Int, body: APIErrorBody?) {
        let message = body?.message ?? HTTPURLResponse.localizedString(forStatusCode: status).capitalized
        switch (body?.code, status) {
        case ("unauthorized", _), (_, 401): self = .unauthorized(message)
        case ("forbidden", _), (_, 403): self = .forbidden(message)
        case ("not_found", _), (_, 404): self = .notFound(message)
        case ("validation_error", _), (_, 400): self = .validation(message, issues: body?.issues ?? [])
        case ("conflict", _), (_, 409): self = .conflict(message)
        case ("rate_limited", _), (_, 429): self = .rateLimited(message)
        case ("server_error", _), (_, 500...): self = .server(message)
        default: self = .http(status: status, message: message)
        }
    }

    var errorDescription: String? {
        switch self {
        case .unauthorized(let m), .forbidden(let m), .notFound(let m), .conflict(let m),
             .rateLimited(let m), .server(let m), .http(_, let m):
            m
        case .validation(let m, let issues):
            issues.isEmpty ? m : issues.map(\.message).joined(separator: "\n")
        case .transport(let m): m
        case .decoding: "Something unexpected came back from the server."
        case .cancelled: "Cancelled."
        }
    }

    var isUnauthorized: Bool {
        if case .unauthorized = self { return true }
        return false
    }
}

extension Error {
    /// A short message suitable for showing to the user.
    var userMessage: String {
        (self as? LocalizedError)?.errorDescription ?? localizedDescription
    }

    /// True for task / URL cancellation, which should never surface as an error.
    var isCancellation: Bool {
        if self is CancellationError { return true }
        if let api = self as? APIError, api == .cancelled { return true }
        return (self as? URLError)?.code == .cancelled
    }
}
