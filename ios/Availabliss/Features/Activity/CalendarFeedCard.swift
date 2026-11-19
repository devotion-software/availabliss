import SwiftUI
import UniformTypeIdentifiers

/// "Subscribe to your plans" (on My plans): a secret webcal link of your plans from every group, for
/// Apple Calendar or another app to subscribe to, so edits and cancellations follow automatically.
struct CalendarFeedCard: View {
    let api: APIClient
    @Binding var toast: String?
    @Binding var error: String?
    @Environment(\.openURL) private var openURL
    @Environment(\.theme) private var theme
    @State private var feed: CalendarFeed?
    @State private var isResetting = false
    @State private var confirmsReset = false

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            VStack(alignment: .leading, spacing: 2) {
                Text("Subscribe to your plans").bodyFont(14, .semibold).accessibilityAddTraits(.isHeader)
                Text("Your plans from every group in Apple Calendar or another app. Changes and cancellations follow automatically.")
                    .bodyFont(12, relativeTo: .caption)
                    .foregroundStyle(theme.muted)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Button {
                if let feed { openURL(feed.webcalUrl) }
            } label: {
                LoadingLabel(title: "Subscribe in Calendar", systemImage: "calendar.badge.plus", isLoading: feed == nil)
            }
            .buttonStyle(.accent)
            .disabled(feed == nil || isResetting)
            HStack {
                Button("Copy link", systemImage: "doc.on.doc") {
                    guard let feed else { return }
                    // Anyone with the link can read your plans: kept off other devices (no
                    // Universal Clipboard) and cleared after 10 minutes.
                    UIPasteboard.general.setItems(
                        [[UTType.url.identifier: feed.url]], options: [.localOnly: true, .expirationDate: Date.now.addingTimeInterval(600)])
                    toast = "Link copied"
                }
                .buttonStyle(.textAction)
                Spacer()
                Button(isResetting ? "Resetting\u{2026}" : "Reset link") { confirmsReset = true }
                    .buttonStyle(.textAction)
            }
            .disabled(feed == nil || isResetting)
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .glass(cornerRadius: 20)
        .task { await load() }
        .confirmSheet(
            "Reset your calendar link?",
            isPresented: $confirmsReset,
            message: "Calendars subscribed with the old link stop updating. You\u{2019}ll need to subscribe again."
        ) {
            [ConfirmOption("Reset link", kind: .destructive) { Task { await reset() } }]
        }
    }

    /// Fetches the link (the server makes one the first time).
    private func load() async {
        guard feed == nil else { return }
        do {
            feed = try await api.send(API.Me.calendarFeed())
        } catch  where !error.isCancellation {
            self.error = error.userMessage
        } catch {}
    }

    /// Swaps the link for a new one; calendars subscribed with the old one stop updating.
    private func reset() async {
        isResetting = true
        defer { isResetting = false }
        do {
            feed = try await api.send(API.Me.resetCalendarFeed())
            toast = "New link ready"
        } catch  where !error.isCancellation {
            self.error = error.userMessage
        } catch {}
    }
}
