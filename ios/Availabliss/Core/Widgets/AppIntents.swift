import AppIntents
import Foundation

// Siri / Shortcuts / Spotlight actions. They live in the app target and run in the app's process
// (iOS 17 launches the app in the background for an intent with `openAppWhenRun` false), so they
// use the app's own APIClient and the bearer token in the Keychain, rather than duplicating the
// networking and Keychain access in an extension.

/// One of your groups, for "When's everyone in <group> free this month?".
struct GroupEntity: AppEntity {
    static var typeDisplayRepresentation: TypeDisplayRepresentation { "Group" }
    static var defaultQuery: GroupEntityQuery { GroupEntityQuery() }

    let id: String
    let name: String

    var displayRepresentation: DisplayRepresentation { DisplayRepresentation(title: "\(name)") }

    init(id: String, name: String) {
        self.id = id
        self.name = name
    }

    init(_ group: WidgetGroup) {
        self.init(id: group.id, name: group.name)
    }
}

/// Your groups from the cached groups list (the widget snapshot); loads it when the app hasn't yet.
struct GroupEntityQuery: EntityStringQuery {
    func entities(for identifiers: [String]) async throws -> [GroupEntity] {
        let groups = await IntentSupport.groups()
        return identifiers.compactMap { id in groups.first { $0.id == id }.map(GroupEntity.init) }
    }

    func entities(matching string: String) async throws -> [GroupEntity] {
        await IntentSupport.groups()
            .filter { $0.name.localizedStandardContains(string) }
            .map(GroupEntity.init)
    }

    func suggestedEntities() async throws -> [GroupEntity] {
        await IntentSupport.groups().map(GroupEntity.init)
    }
}

/// "Open my next plan": opens the app at the next plan you're going to (its proposal, or its group).
struct OpenNextPlanIntent: AppIntent {
    static var title: LocalizedStringResource { "Open my next plan" }
    static var description: IntentDescription { IntentDescription("Opens Availabliss at the next plan you're going to.") }
    static var openAppWhenRun: Bool { true }

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog {
        guard IntentSupport.isSignedIn else {
            return .result(dialog: "Sign in to Availabliss to see your plans.")
        }
        if WidgetSnapshotStore.load()?.plans == nil { await WidgetSync.shared.refresh() }
        let now = Date.now
        guard let plan = WidgetSnapshotStore.load()?.plans.flatMap({ WidgetSnapshots.nextPlan($0, now: now) }) else {
            IntentSupport.open(WidgetLinks.myPlans)
            return .result(dialog: "You've no plans coming up.")
        }
        IntentSupport.open(plan.url)
        return .result(dialog: "\(plan.title), \(WidgetSnapshots.whenLabel(plan, now: now)).")
    }
}

/// "When's everyone in <group> free this month?": the times that work for everyone from today to
/// the end of the month, answered without opening the app.
struct GroupFreeThisMonthIntent: AppIntent {
    static var title: LocalizedStringResource { "When's everyone free this month?" }
    static var description: IntentDescription {
        IntentDescription("Says when everyone in a group is free for the rest of this month.")
    }
    static var openAppWhenRun: Bool { false }

    @Parameter(title: "Group", requestValueDialog: "Which group?")
    var group: GroupEntity

    static var parameterSummary: some ParameterSummary {
        Summary("When's everyone in \(\.$group) free this month?")
    }

    init() {}

    init(group: GroupEntity) {
        self.group = group
    }

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog {
        guard IntentSupport.isSignedIn, let api = IntentSupport.api else {
            return .result(dialog: "Sign in to Availabliss first.")
        }
        let today = CalendarDay.today()
        let monthEnd = today.lastOfMonth()
        do {
            let availability = try await api.send(API.GroupAvailability.range(groupId: group.id, from: today, to: monthEnd))
            let answer = FreeTimeAnswer.sentence(
                slots: availability.slots,
                groupName: group.name,
                monthName: today.startDate().formatted(.dateTime.month(.wide)),
                now: .now
            )
            return .result(dialog: "\(answer)")
        } catch {
            return .result(dialog: "Couldn't check \(group.name): \(error.userMessage)")
        }
    }
}

/// The phrases Siri and Spotlight offer.
struct AvailablissShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(
            intent: OpenNextPlanIntent(),
            phrases: [
                "Open my next plan in \(.applicationName)",
                "Show my next plan in \(.applicationName)",
                "What's my next \(.applicationName) plan",
            ],
            shortTitle: "Next plan",
            systemImageName: "calendar"
        )
        AppShortcut(
            intent: GroupFreeThisMonthIntent(),
            phrases: [
                "When's everyone in \(\.$group) free this month in \(.applicationName)",
                "When's \(\.$group) free in \(.applicationName)",
                "When's everyone free this month in \(.applicationName)",
            ],
            shortTitle: "When's everyone free",
            systemImageName: "person.3"
        )
    }
}

/// What the intents share with the running app.
@MainActor
enum IntentSupport {
    /// The app's client, or one made for this run if the app model isn't up yet.
    static var api: APIClient? {
        if let app = AppModel.shared { return app.api }
        let client = APIClient(baseURL: AppConfig.load().apiBaseURL, tokenStore: TokenStore())
        return client.bearerToken == nil ? nil : client
    }

    static var isSignedIn: Bool { api?.bearerToken != nil }

    /// Opens `url` (one of `WidgetLinks`) in the app.
    static func open(_ url: URL) {
        guard let app = AppModel.shared, let link = DeepLink(url: url, associatedHost: app.config.associatedDomainHost) else { return }
        app.open(link)
    }

    /// The cached groups; when there are none yet, loads the groups list (which also caches it).
    static func groups() async -> [WidgetGroup] {
        if let cached = WidgetSnapshotStore.load()?.groups { return cached }
        guard let api, let groups = try? await api.send(API.Groups.list()).groups else { return [] }
        WidgetSync.shared.onGroups(groups)
        return WidgetSnapshots.groups(from: groups)
    }
}

/// The answer to "When's everyone in <group> free this month?". Pure, so it's unit tested.
enum FreeTimeAnswer {
    /// "Fri 4 – Sun 6 Dec and Fri 11 – Sun 13 Dec work for everyone in Lake District crew." from the
    /// month's slots that haven't ended and that nobody's missing from (the earliest `limit`).
    static func sentence(slots: [Slot], groupName: String, monthName: String, now: Date, limit: Int = 3) -> String {
        let open = slots.filter { $0.end > now && $0.missingCount == 0 }.sorted { $0.start < $1.start }
        guard !open.isEmpty else {
            return "Nothing works for everyone in \(groupName) for the rest of \(monthName)."
        }
        var labels = open.prefix(limit).map(\.label)
        if open.count > limit { labels.append("\(open.count - limit) more") }
        let verb = open.count == 1 ? "works" : "work"
        return "\(joined(labels)) \(verb) for everyone in \(groupName)."
    }

    /// "A", "A and B", "A, B and C".
    private static func joined(_ items: [String]) -> String {
        guard let last = items.last else { return "" }
        let rest = items.dropLast()
        return rest.isEmpty ? last : "\(rest.joined(separator: ", ")) and \(last)"
    }
}
