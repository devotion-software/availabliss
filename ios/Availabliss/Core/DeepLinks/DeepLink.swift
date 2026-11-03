import Foundation

/// Every place a URL or push notification can send the user (see "Deep links" in API.md).
enum DeepLink: Equatable, Sendable {
    case invite(token: String)
    case group(id: String)
    case groupChat(id: String)
    case proposal(id: String)
    /// `availabliss://calendars` — OAuth return / calendar settings. `complete` is the
    /// one-time code to finish a Google / Microsoft connection with.
    case calendars(complete: String?, error: String?)

    /// Parses `availabliss://…` and `https://<host>/…` links.
    init?(url: URL, associatedHost: String? = nil) {
        var segments = url.pathComponents.filter { $0 != "/" }
        if url.scheme == AppConfig.urlScheme {
            // availabliss://invite/abc → host "invite", path "/abc"
            if let host = url.host(), !host.isEmpty { segments.insert(host, at: 0) }
        } else if let associatedHost, let host = url.host(), host != associatedHost {
            return nil
        }
        let query = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems ?? []
        func param(_ name: String) -> String? { query.first { $0.name == name }?.value }

        switch segments.count {
        case 1 where segments[0] == "calendars":
            self = .calendars(complete: param("complete"), error: param("error"))
        case 2 where segments[0] == "invite":
            self = .invite(token: segments[1])
        case 3 where segments[0] == "app" && segments[1] == "groups":
            self = .group(id: segments[2])
        case 4 where segments[0] == "app" && segments[1] == "groups" && segments[3] == "chat":
            self = .groupChat(id: segments[2])
        case 3 where segments[0] == "app" && segments[1] == "proposals":
            self = .proposal(id: segments[2])
        default:
            return nil
        }
    }

    /// Maps push / notification `data` (`{ kind, groupId, proposalId?, eventId?, messageId? }`).
    init?(data: [String: String]) {
        if let proposalId = data["proposalId"], !proposalId.isEmpty {
            self = .proposal(id: proposalId)
        } else if let groupId = data["groupId"], !groupId.isEmpty {
            self = data["kind"] == "message" || data["messageId"] != nil ? .groupChat(id: groupId) : .group(id: groupId)
        } else {
            return nil
        }
    }

    /// Extracts the data keys from an APNs `userInfo` (they're top level, next to `aps`).
    static func data(fromPush userInfo: [AnyHashable: Any]) -> [String: String] {
        var result: [String: String] = [:]
        for (key, value) in userInfo {
            guard let key = key as? String, key != "aps" else { continue }
            if let string = value as? String { result[key] = string }
        }
        return result
    }
}
