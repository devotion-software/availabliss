import Foundation

/// A kind of push you can turn off (`NotificationCategory` in API.md). Invites and role changes always push.
enum NotificationCategory: String, Codable, Sendable, CaseIterable, Identifiable {
    /// Chat messages.
    case chat
    /// New proposals, and changes to confirmed plans.
    case proposals
    /// Confirmed plans and new events.
    case plans
    /// Before a plan, and proposals you haven't answered.
    case reminders
    /// Someone asking you to add your busy times.
    case nudges

    var id: String { rawValue }

    var label: String {
        switch self {
        case .chat: "Chat messages"
        case .proposals: "New proposals and changes"
        case .plans: "Confirmed plans"
        case .reminders: "Reminders"
        case .nudges: "Nudges to add busy times"
        }
    }
}

/// No pushes between `from` and `to` ("HH:MM", local times in the user's time zone; may wrap midnight).
struct QuietHours: Codable, Sendable, Equatable {
    var from: String
    var to: String

    static let `default` = QuietHours(from: "22:00", to: "07:00")
}

/// A muted group: no pushes from it until `until` (nil: until it's unmuted).
struct GroupMute: Codable, Sendable, Equatable {
    let groupId: String
    let until: Date?

    /// Whether it still holds at `date` (the server only returns active mutes, but one can run out while on screen).
    func isActive(at date: Date = Date()) -> Bool { until.map { $0 > date } ?? true }
}

/// Which pushes you get (`GET /me/notification-settings`). Activity still records everything.
struct NotificationSettings: Decodable, Sendable, Equatable {
    /// Categories whose pushes (and the confirmation email) are turned off.
    var off: [NotificationCategory]
    var quietHours: QuietHours?
    /// Active mutes only.
    var mutes: [GroupMute]

    init(off: [NotificationCategory] = [], quietHours: QuietHours? = nil, mutes: [GroupMute] = []) {
        self.off = off
        self.quietHours = quietHours
        self.mutes = mutes
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        // Skips categories this version doesn't know, rather than failing the whole screen.
        off = (try container.decodeIfPresent([String].self, forKey: .off) ?? []).compactMap(NotificationCategory.init(rawValue:))
        quietHours = try container.decodeIfPresent(QuietHours.self, forKey: .quietHours)
        mutes = try container.decodeIfPresent([GroupMute].self, forKey: .mutes) ?? []
    }

    /// The group's mute, if it's muted now.
    func mute(for groupId: String) -> GroupMute? {
        mutes.first { $0.groupId == groupId && $0.isActive() }
    }

    private enum CodingKeys: String, CodingKey { case off, quietHours, mutes }
}

/// `PATCH /me/notification-settings`: only the fields being changed are sent.
struct NotificationSettingsPatch: Encodable, Sendable {
    /// The whole list of categories that are off.
    var off: [NotificationCategory]?
    /// Left out: unchanged. `.some(nil)`: sent as `null`, turning quiet hours off.
    var quietHours: QuietHours??

    init(off: [NotificationCategory]? = nil, quietHours: QuietHours?? = nil) {
        self.off = off
        self.quietHours = quietHours
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encodeIfPresent(off, forKey: .off)
        if case .some(let quietHours) = quietHours {
            if let quietHours {
                try container.encode(quietHours, forKey: .quietHours)
            } else {
                try container.encodeNil(forKey: .quietHours)
            }
        }
    }

    private enum CodingKeys: String, CodingKey { case off, quietHours }
}

/// `PUT /groups/:id/mute`: `until` is always sent, `null` meaning until it's unmuted.
struct MuteGroupBody: Encodable, Sendable {
    let until: Date?

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        if let until {
            try container.encode(until, forKey: .until)
        } else {
            try container.encodeNil(forKey: .until)
        }
    }

    private enum CodingKeys: String, CodingKey { case until }
}
