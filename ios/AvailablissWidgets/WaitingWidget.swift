import SwiftUI
import WidgetKit

/// "Waiting for you": how many proposals are waiting for your reply and up to three of their
/// groups, each opening its proposal; "You're all caught up" when there are none.
struct WaitingWidget: Widget {
    let kind = "WaitingWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: WaitingProvider()) { entry in
            WaitingView(entry: entry)
        }
        .configurationDisplayName("Waiting for you")
        .description("Proposals waiting for your reply.")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}

struct WaitingProvider: TimelineProvider {
    func placeholder(in context: Context) -> SnapshotEntry {
        SnapshotEntry(date: .now, snapshot: .preview())
    }

    func getSnapshot(in context: Context, completion: @escaping (SnapshotEntry) -> Void) {
        let snapshot = context.isPreview ? .preview() : WidgetSnapshotStore.load()
        completion(SnapshotEntry(date: .now, snapshot: snapshot))
    }

    /// Nothing here depends on the time: it changes only when the app writes a new snapshot.
    func getTimeline(in context: Context, completion: @escaping (Timeline<SnapshotEntry>) -> Void) {
        completion(Timeline(entries: [SnapshotEntry(date: .now, snapshot: WidgetSnapshotStore.load())], policy: .never))
    }
}

struct WaitingView: View {
    let entry: SnapshotEntry
    @Environment(\.widgetFamily) private var family

    private var snapshot: WidgetSnapshot? { entry.snapshot }
    private var shown: [WidgetWaiting] { Array((snapshot?.waiting ?? []).prefix(WidgetSnapshots.shownWaiting)) }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            WidgetLabel(text: "Waiting for you")
            content
            Spacer(minLength: 0)
        }
        .widgetCard()
        // A small widget is one tap target: the first proposal. The medium one links each row.
        .widgetURL(shown.first?.url)
    }

    @ViewBuilder private var content: some View {
        if let snapshot, snapshot.signedIn {
            if snapshot.waiting == nil {
                WidgetMessage(title: "Open Availabliss", detail: "to load your groups")
            } else if snapshot.waitingCount == 0 || shown.isEmpty {
                WidgetMessage(title: "You're all caught up", detail: "Nothing's waiting for your reply")
            } else {
                waiting(count: snapshot.waitingCount)
            }
        } else {
            NotReadyMessage(snapshot: snapshot)
        }
    }

    private func waiting(count: Int) -> some View {
        let small = family == .systemSmall
        return VStack(alignment: .leading, spacing: small ? 4 : 5) {
            Text(WidgetSnapshots.waitingTitle(count))
                .font(.system(size: small ? 14 : 15, weight: .bold))
                .foregroundStyle(WidgetPalette.ink)
                .lineLimit(2)
            ForEach(shown, id: \.proposalId) { item in
                if small {
                    Text(item.groupName)
                        .font(.system(size: 13, weight: .medium))
                        .foregroundStyle(WidgetPalette.accentInk)
                        .lineLimit(1)
                } else {
                    Link(destination: item.url) { row(item) }
                }
            }
            if let more = WidgetSnapshots.moreLabel(count: count, shown: shown.count) {
                Text(more)
                    .font(.system(size: 12))
                    .foregroundStyle(WidgetPalette.muted)
                    .lineLimit(1)
            }
        }
    }

    private func row(_ item: WidgetWaiting) -> some View {
        HStack(spacing: 8) {
            Text(item.groupName)
                .font(.system(size: 14, weight: .medium))
                .foregroundStyle(WidgetPalette.ink)
                .lineLimit(1)
            Spacer(minLength: 0)
            Text("Reply")
                .font(.system(size: 13, weight: .bold))
                .foregroundStyle(WidgetPalette.accentInk)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 5)
        .background(WidgetPalette.accentSoft, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
    }
}
