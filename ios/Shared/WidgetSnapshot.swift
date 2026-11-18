import Foundation

// Compiled into both the app and the AvailablissWidgets extension (see project.yml), so it only
// uses Foundation: no app models, no API client.

/// The App Group the app and its widgets share (the `com.apple.security.application-groups`
/// entitlement on both targets).
enum AppGroup {
    static let id = "group.dev.joeherbert.availabliss"

    /// The group's shared defaults. Falls back to the process's own defaults when the group isn't
    /// available (an unsigned simulator build), so the app still works; the widgets then just see
    /// nothing written.
    static var defaults: UserDefaults { UserDefaults(suiteName: id) ?? .standard }
}

/// What the home-screen widgets (and the Siri group picker) show, cached in the App Group so they
/// can draw without the network. The app writes it whenever it loads your plans or the groups
/// list, after you reply to a proposal and after a push; signing out replaces it with `signedOut`.
/// Matches Android's `WidgetSnapshot`.
struct WidgetSnapshot: Codable, Equatable, Sendable {
    var signedIn: Bool
    /// Upcoming plans you're going to, soonest first (a few, so the widget can move on to the next
    /// one when a plan ends before the app writes again). Nil until plans have loaded.
    var plans: [WidgetPlan]?
    /// Proposals waiting for your reply, a few of them. Nil until groups have loaded.
    var waiting: [WidgetWaiting]?
    /// How many proposals are waiting in all (can be more than `waiting` holds).
    var waitingCount: Int
    /// Your groups (not archived), for "When's everyone in <group> free?". Nil until groups have loaded.
    var groups: [WidgetGroup]?

    init(signedIn: Bool, plans: [WidgetPlan]? = nil, waiting: [WidgetWaiting]? = nil, waitingCount: Int = 0, groups: [WidgetGroup]? = nil) {
        self.signedIn = signedIn
        self.plans = plans
        self.waiting = waiting
        self.waitingCount = waitingCount
        self.groups = groups
    }

    static let signedOut = WidgetSnapshot(signedIn: false)

    private enum CodingKeys: String, CodingKey { case signedIn, plans, waiting, waitingCount, groups }

    /// Tolerant, so a snapshot written by another version of the app still reads.
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        signedIn = (try? c.decodeIfPresent(Bool.self, forKey: .signedIn)) ?? false
        plans = try? c.decodeIfPresent([WidgetPlan].self, forKey: .plans)
        waiting = try? c.decodeIfPresent([WidgetWaiting].self, forKey: .waiting)
        waitingCount = (try? c.decodeIfPresent(Int.self, forKey: .waitingCount)) ?? 0
        groups = try? c.decodeIfPresent([WidgetGroup].self, forKey: .groups)
    }
}

/// A plan for the "Next plan" widget.
struct WidgetPlan: Codable, Equatable, Sendable {
    var title: String
    var groupName: String
    var groupId: String
    var start: Date
    /// Exclusive; for an all-day plan, the midnight after its last day.
    var end: Date
    var allDay: Bool
    /// Nil when the plan was added straight to the calendar (no proposal).
    var proposalId: String?

    /// Opens the proposal the plan came from, or the group when it was added straight to the calendar.
    var url: URL { proposalId.map(WidgetLinks.proposal) ?? WidgetLinks.group(groupId) }
}

/// A group with a proposal waiting for your reply, for the "Waiting for you" widget.
struct WidgetWaiting: Codable, Equatable, Sendable {
    var groupName: String
    var proposalId: String
    var groupId: String

    var url: URL { WidgetLinks.proposal(proposalId) }
}

/// A group, for the Siri / Shortcuts group picker.
struct WidgetGroup: Codable, Equatable, Sendable, Hashable {
    var id: String
    var name: String
}

/// The app's `availabliss://` links the widgets open (parsed by the app's `DeepLink`).
enum WidgetLinks {
    static let scheme = "availabliss"

    static func proposal(_ id: String) -> URL { url("app/proposals/\(escaped(id))") }
    static func group(_ id: String) -> URL { url("app/groups/\(escaped(id))") }
    static let myPlans = url("app/my-plans")

    private static func url(_ path: String) -> URL { URL(string: "\(scheme)://\(path)")! }
    private static func escaped(_ id: String) -> String {
        id.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed.subtracting(CharacterSet(charactersIn: "/"))) ?? id
    }
}

/// Reads and writes the snapshot (JSON in the App Group's defaults).
enum WidgetSnapshotStore {
    static let key = "widgets.snapshot"

    /// The current snapshot, or nil when the app has never written one (e.g. just installed).
    static func load(from defaults: UserDefaults = AppGroup.defaults) -> WidgetSnapshot? {
        guard let data = defaults.data(forKey: key) else { return nil }
        return try? JSONDecoder().decode(WidgetSnapshot.self, from: data)
    }

    /// Replaces the snapshot with `transform` of the current one (or of an empty signed-in one).
    /// Returns whether it changed.
    @discardableResult
    static func update(in defaults: UserDefaults = AppGroup.defaults, _ transform: (WidgetSnapshot) -> WidgetSnapshot) -> Bool {
        let before = load(from: defaults)
        let after = transform(before ?? WidgetSnapshot(signedIn: true))
        guard after != before, let data = try? JSONEncoder().encode(after) else { return false }
        defaults.set(data, forKey: key)
        return true
    }
}

/// Choosing and labelling what the widgets show. Pure, so it's unit tested.
enum WidgetSnapshots {
    /// Plans kept in the snapshot.
    static let maxPlans = 5
    /// Waiting proposals kept in the snapshot (the widget lists up to `shownWaiting`).
    static let maxWaiting = 5
    /// Rows the "Waiting for you" widget lists.
    static let shownWaiting = 3

    /// The plan to show at `now`: the first that hasn't ended (one that's on now counts).
    static func nextPlan(_ plans: [WidgetPlan], now: Date) -> WidgetPlan? {
        plans.first { $0.end > now }
    }

    /// The plan after `nextPlan`, for the medium widget's "Then" line.
    static func followingPlan(_ plans: [WidgetPlan], now: Date) -> WidgetPlan? {
        plans.filter { $0.end > now }.dropFirst().first
    }

    /// When `plan` is, relative to `now`: "Now", "Today 19:00", "Tomorrow 19:00",
    /// "Fri 4 Dec · 19:00", and for an all-day plan "Today", "Tomorrow" or "Fri 4 Dec".
    static func whenLabel(_ plan: WidgetPlan, now: Date, calendar: Calendar = .current) -> String {
        let today = calendar.startOfDay(for: now)
        if plan.allDay {
            // Local midnights; the end is the exclusive midnight after the last day.
            let startDay = calendar.startOfDay(for: plan.start)
            let onNow = startDay <= today && plan.end > today
            return onNow ? "Today" : dayLabel(startDay, today: today, calendar: calendar)
        }
        if plan.start <= now { return "Now" }
        let day = calendar.startOfDay(for: plan.start)
        let time = plan.start.formatted(style(calendar).hour().minute())
        if day == today { return "Today \(time)" }
        if day == calendar.date(byAdding: .day, value: 1, to: today) { return "Tomorrow \(time)" }
        return "\(shortDay(plan.start, calendar: calendar)) · \(time)"
    }

    /// "1 proposal waiting for you", "3 proposals waiting for you".
    static func waitingTitle(_ count: Int) -> String {
        count == 1 ? "1 proposal waiting for you" : "\(count) proposals waiting for you"
    }

    /// "+2 more" under the listed rows, or nil when every waiting proposal is listed.
    static func moreLabel(count: Int, shown: Int) -> String? {
        count > shown ? "+\(count - shown) more" : nil
    }

    /// When the "Next plan" widget's text changes without the app running: each midnight for the
    /// next `days` days ("Tomorrow" becomes "Today") and each plan's start ("Now") and end (the
    /// next plan takes over) before then. Sorted, after `now`.
    static func timelineDates(_ plans: [WidgetPlan], now: Date, calendar: Calendar = .current, days: Int = 7) -> [Date] {
        let today = calendar.startOfDay(for: now)
        let midnights = (1...max(1, days)).compactMap { calendar.date(byAdding: .day, value: $0, to: today) }
        guard let horizon = midnights.last else { return [] }
        let planDates = plans.flatMap { [$0.start, $0.end] }.filter { $0 <= horizon }
        return Array(Set(midnights + planDates)).filter { $0 > now }.sorted()
    }

    // MARK: Formatting

    /// "Today", "Tomorrow" or "Fri 4 Dec".
    private static func dayLabel(_ day: Date, today: Date, calendar: Calendar) -> String {
        if day == today { return "Today" }
        if day == calendar.date(byAdding: .day, value: 1, to: today) { return "Tomorrow" }
        return shortDay(day, calendar: calendar)
    }

    /// "Fri 4 Dec", in `calendar`'s locale and zone.
    private static func shortDay(_ date: Date, calendar: Calendar) -> String {
        date.formatted(style(calendar).weekday(.abbreviated).day().month(.abbreviated))
    }

    private static func style(_ calendar: Calendar) -> Date.FormatStyle {
        Date.FormatStyle(locale: calendar.locale ?? .current, calendar: calendar, timeZone: calendar.timeZone)
    }
}
