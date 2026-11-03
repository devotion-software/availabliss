import Foundation

/// `GET /me/plan`: free or Pro, the group limit, and how to buy Pro on each platform.
struct Plan: Decodable, Sendable, Equatable {
    enum Tier: String, Decodable, Sendable { case free, pro }

    struct Purchase: Decodable, Sendable, Equatable {
        /// RevenueCat public SDK keys; nil hides purchasing on that platform.
        let ios: String?
        let android: String?
        let web: String?
        let entitlement: String
    }

    struct AIPlanner: Decodable, Sendable, Equatable {
        /// Set up on the server.
        let available: Bool
        /// Usable on this plan.
        let included: Bool
    }

    let tier: Tier
    /// Renews / ends; nil on free or lifetime.
    let proUntil: Date?
    let lifetime: Bool
    let willRenew: Bool
    /// `app_store`, `play_store`, `stripe`, `rc_billing`, `promotional`.
    let store: String?
    /// nil = unlimited.
    let groupLimit: Int?
    let groupsOwned: Int
    let canCreateGroup: Bool
    let purchase: Purchase
    let aiPlanner: AIPlanner

    var isPro: Bool { tier == .pro }

    /// "Renews 2 Dec", "Ends 2 Dec", "It never expires".
    var renewalText: String? {
        if lifetime { return "It never expires" }
        guard let proUntil else { return nil }
        return "\(willRenew ? "Renews" : "Ends") \(Formatting.dayMonth(CalendarDay(proUntil)))"
    }
}
