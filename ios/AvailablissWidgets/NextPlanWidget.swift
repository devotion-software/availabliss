import SwiftUI
import WidgetKit

/// "Next plan": the soonest plan you're going to (title, when, group), opening its proposal or
/// group. The timeline has an entry at each midnight and at each plan's start and end, so
/// "Tomorrow" becomes "Today" (and the next plan takes over) without the app running.
struct NextPlanWidget: Widget {
    let kind = "NextPlanWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: NextPlanProvider()) { entry in
            NextPlanView(entry: entry)
        }
        .configurationDisplayName("Next plan")
        .description("The next plan you're going to.")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}

struct NextPlanProvider: TimelineProvider {
    func placeholder(in context: Context) -> SnapshotEntry {
        SnapshotEntry(date: .now, snapshot: .preview())
    }

    func getSnapshot(in context: Context, completion: @escaping (SnapshotEntry) -> Void) {
        let snapshot = context.isPreview ? .preview() : WidgetSnapshotStore.load()
        completion(SnapshotEntry(date: .now, snapshot: snapshot))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<SnapshotEntry>) -> Void) {
        let now = Date.now
        let snapshot = WidgetSnapshotStore.load()
        let dates = [now] + WidgetSnapshots.timelineDates(snapshot?.plans ?? [], now: now)
        let entries = dates.map { SnapshotEntry(date: $0, snapshot: snapshot) }
        // After the last entry (a week out) WidgetKit asks again; the app reloads it sooner whenever
        // the snapshot changes.
        completion(Timeline(entries: entries, policy: .atEnd))
    }
}

struct NextPlanView: View {
    let entry: SnapshotEntry
    @Environment(\.widgetFamily) private var family

    private var snapshot: WidgetSnapshot? { entry.snapshot }
    private var plan: WidgetPlan? { snapshot?.plans.flatMap { WidgetSnapshots.nextPlan($0, now: entry.date) } }

    /// The plan's proposal or group; My plans when there's none; just the app when signed out.
    private var url: URL? {
        if let plan { return plan.url }
        return snapshot?.signedIn == true ? WidgetLinks.myPlans : nil
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            WidgetLabel(text: "Next plan")
            content
            Spacer(minLength: 0)
        }
        .widgetCard()
        .widgetURL(url)
    }

    @ViewBuilder private var content: some View {
        if let snapshot, snapshot.signedIn {
            if snapshot.plans == nil {
                WidgetMessage(title: "Open Availabliss", detail: "to load your plans")
            } else if let plan {
                planDetails(plan)
                if family == .systemMedium, let next = snapshot.plans.flatMap({ WidgetSnapshots.followingPlan($0, now: entry.date) }) {
                    Spacer(minLength: 0)
                    then(next)
                }
            } else {
                WidgetMessage(title: "No plans coming up", detail: "Find a time with one of your groups")
            }
        } else {
            NotReadyMessage(snapshot: snapshot)
        }
    }

    private func planDetails(_ plan: WidgetPlan) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(plan.title)
                .font(.system(size: 17, weight: .bold))
                .foregroundStyle(WidgetPalette.ink)
                .lineLimit(2)
            Text(WidgetSnapshots.whenLabel(plan, now: entry.date))
                .font(.system(size: 14, weight: .medium))
                .foregroundStyle(WidgetPalette.accentInk)
                .lineLimit(1)
            if !plan.groupName.isEmpty {
                Text(plan.groupName)
                    .font(.system(size: 13))
                    .foregroundStyle(WidgetPalette.muted)
                    .lineLimit(1)
            }
        }
    }

    /// "Then: Five-a-side · Fri 4 Dec · 19:30", under the next plan on the medium widget.
    private func then(_ plan: WidgetPlan) -> some View {
        HStack(spacing: 6) {
            Text("Then")
                .font(.system(size: 12, weight: .bold))
                .foregroundStyle(WidgetPalette.accentInk)
            Text("\(plan.title) · \(WidgetSnapshots.whenLabel(plan, now: entry.date))")
                .font(.system(size: 12))
                .foregroundStyle(WidgetPalette.muted)
        }
        .lineLimit(1)
    }
}
