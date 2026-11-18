import SwiftUI
import WidgetKit

/// The app's home-screen widgets. Both draw from the `WidgetSnapshot` the app keeps in the App
/// Group; neither calls the API.
@main
struct AvailablissWidgetBundle: WidgetBundle {
    var body: some Widget {
        NextPlanWidget()
        WaitingWidget()
    }
}

/// One moment of a widget's timeline: the snapshot as it was read, drawn as of `date`.
struct SnapshotEntry: TimelineEntry {
    let date: Date
    let snapshot: WidgetSnapshot?
}

extension WidgetSnapshot {
    /// What the widget gallery shows.
    static func preview(now: Date = .now) -> WidgetSnapshot {
        let calendar = Calendar.current
        let tomorrow = calendar.date(byAdding: .day, value: 1, to: calendar.startOfDay(for: now)) ?? now
        let start = calendar.date(bySettingHour: 19, minute: 0, second: 0, of: tomorrow) ?? tomorrow
        let plan = WidgetPlan(
            title: "Dinner at Rosa's", groupName: "Book club", groupId: "preview",
            start: start, end: start.addingTimeInterval(3 * 3600), allDay: false, proposalId: nil
        )
        let waiting = [
            WidgetWaiting(groupName: "Lake District crew", proposalId: "preview-1", groupId: "preview-1"),
            WidgetWaiting(groupName: "Five-a-side", proposalId: "preview-2", groupId: "preview-2"),
        ]
        return WidgetSnapshot(signedIn: true, plans: [plan], waiting: waiting, waitingCount: 2)
    }
}
