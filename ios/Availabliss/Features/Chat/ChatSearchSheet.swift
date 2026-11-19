import SwiftUI

/// Search in the chat: matches as you type (from 2 characters), newest first, each with who sent it,
/// when, and the matching words picked out. Tapping one goes to it in the chat. Like web and Android.
struct ChatSearchSheet: View {
    let group: Group
    let me: String?
    let api: APIClient
    /// Goes to the message in the chat (the sheet is closing).
    let onPick: (String) -> Void
    @Environment(\.dismiss) private var dismiss
    @Environment(\.theme) private var theme
    @State private var query = ""
    @State private var results: [Message]?
    @State private var hasMore = false
    @State private var isSearching = false
    @State private var failed = false
    @FocusState private var focused: Bool

    private var trimmed: String { query.trimmingCharacters(in: .whitespacesAndNewlines) }

    var body: some View {
        BottomSheet(title: "Search chat") {
            searchField
            if failed {
                note("Couldn't search. Check your connection and try again.")
            } else if let results, results.isEmpty {
                note("No messages with “\(trimmed)”.")
            } else if let results {
                resultsList(results)
                if hasMore { note("Showing the newest \(results.count). Search for something more specific to narrow it down.") }
            } else {
                note("Search for at least 2 characters.")
            }
        }
        .presentationDetents([.large])
        .onAppear { focused = true }
        // A moment after you stop typing; a new keystroke cancels the search before.
        .task(id: trimmed) { await search(trimmed) }
    }

    private var searchField: some View {
        HStack(spacing: 8) {
            Image(systemName: "magnifyingglass").foregroundStyle(theme.muted)
            TextField("", text: $query, prompt: Text("Messages, polls, places, files").foregroundStyle(theme.muted))
                .bodyFont(16)
                .submitLabel(.search)
                .autocorrectionDisabled()
                .focused($focused)
                .accessibilityLabel("Search messages")
            if isSearching {
                ProgressView()
            } else if !query.isEmpty {
                Button {
                    query = ""
                } label: {
                    Image(systemName: "xmark.circle.fill").foregroundStyle(theme.muted)
                }
                .accessibilityLabel("Clear search")
            }
        }
        .padding(.horizontal, 14)
        .frame(height: 46)
        .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(theme.surface))
        .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).stroke(theme.line, lineWidth: 1))
    }

    private func resultsList(_ results: [Message]) -> some View {
        VStack(spacing: 0) {
            ForEach(results) { message in
                Button {
                    dismiss()
                    onPick(message.id)
                } label: {
                    VStack(alignment: .leading, spacing: 2) {
                        HStack {
                            Text(ReplyNames.name(of: message.authorId, fallback: nil, group: group, me: me))
                                .bodyFont(12, .semibold, relativeTo: .caption)
                                .foregroundStyle(theme.ink)
                            Spacer()
                            Text(Formatting.chatDay(message.createdAt))
                                .bodyFont(12, relativeTo: .caption)
                                .foregroundStyle(theme.muted)
                        }
                        snippet(message)
                            .bodyFont(15)
                            .foregroundStyle(theme.ink)
                            .lineLimit(2)
                            .multilineTextAlignment(.leading)
                    }
                    .padding(.vertical, 10)
                    .frame(maxWidth: .infinity, minHeight: 56, alignment: .leading)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .overlay(alignment: .top) {
                    if message.id != results.first?.id { Rectangle().fill(theme.line).frame(height: 1) }
                }
            }
        }
        .padding(.horizontal, 14)
        .surfaceBackground(cornerRadius: 18)
    }

    /// The line with the match on the accent's soft colour, and a chart before a poll.
    private func snippet(_ message: Message) -> Text {
        let s = message.searchSnippet(for: trimmed)
        var match = AttributedString(s.match)
        match.backgroundColor = theme.accentSoft
        match.foregroundColor = theme.accentInk
        let line = Text(s.before) + Text(match) + Text(s.after)
        return message.kind == .poll ? Text(Image(systemName: "chart.bar.fill")).foregroundStyle(theme.muted) + Text(" ") + line : line
    }

    private func note(_ text: String) -> some View {
        Text(text)
            .bodyFont(13, relativeTo: .footnote)
            .foregroundStyle(theme.muted)
            .fixedSize(horizontal: false, vertical: true)
    }

    private func search(_ text: String) async {
        guard text.count >= 2 else {
            results = nil
            isSearching = false
            return
        }
        isSearching = true
        do {
            try await Task.sleep(for: .milliseconds(300))
            let page = try await api.send(API.Chat.search(groupId: group.id, query: text))
            results = page.messages
            hasMore = page.hasMore
            failed = false
        } catch is CancellationError {
            return
        } catch {
            if Task.isCancelled { return }
            failed = true
        }
        isSearching = false
    }
}
