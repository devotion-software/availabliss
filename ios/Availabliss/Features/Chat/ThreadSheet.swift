import QuickLook
import SwiftUI

/// A thread: the first message of a chain of replies and every reply under it, oldest first, with
/// the same composer as the chat (photos, files, places, polls and the rest), replying within the
/// thread (to the latest message unless you pick another). Tapping a message offers Reply, Show in
/// chat and, on your own, Edit and Delete; tapping a quote goes to the original in the thread.
struct ThreadSheet: View {
    /// The message the thread was opened from: shown and briefly highlighted once loaded.
    let focusId: String
    let group: Group
    let me: String?
    @Bindable var model: ChatViewModel
    let api: APIClient
    /// Close the thread and go to this message in the chat.
    let showInChat: (String) -> Void
    @Environment(\.dismiss) private var dismiss
    @Environment(\.theme) private var theme
    @State private var highlightedId: String?
    /// The message whose Reply / Show in chat options are showing.
    @State private var actionsFor: Message?
    @State private var viewing: ChatView.ViewerTarget?
    @State private var previewFile: URL?
    @State private var openingFileId: String?
    @State private var showsMenu = false
    /// Your message waiting for "Delete this message?".
    @State private var pendingDelete: Message?

    var body: some View {
        VStack(spacing: 0) {
            header
            messages
            if model.threadReplyingTo != nil || model.composer(.thread).editing != nil {
                ChatComposerArea(model: model, target: .thread, group: group, me: me, api: api, showsMenu: $showsMenu)
            }
        }
        .animation(.spring(response: 0.3, dampingFraction: 0.85), value: model.threadReplyingTo?.id)
        .foregroundStyle(theme.ink)
        .presentationDetents([.large])
        .presentationCornerRadius(32)
        .presentationBackground(theme.bg)
        .presentationDragIndicator(.hidden)
        .task { await model.openThread(for: focusId) }
        .fullScreenCover(item: $viewing) { target in
            AttachmentViewer(attachments: target.attachments, selection: target.selected)
        }
        .quickLookPreview($previewFile)
        .onDisappear { model.closeThread() }
        .confirmSheet(item: $actionsFor) { message in
            ReplyNames.name(of: message.authorId, fallback: nil, group: group, me: me) == "You"
                ? "Your message" : "\(ReplyNames.name(of: message.authorId, fallback: nil, group: group, me: me))\u{2019}s message"
        } message: { message in
            message.asReplyPreview(authorName: nil).snippet
        } options: { message in
            var options: [ConfirmOption] = []
            if message.canReply {
                options.append(ConfirmOption("Reply", systemImage: "arrowshape.turn.up.left") { reply(to: message) })
            }
            if canEdit(message) {
                options.append(ConfirmOption("Edit", systemImage: "pencil", kind: .secondary) { edit(message) })
            }
            options.append(ConfirmOption("Show in chat", systemImage: "text.bubble", kind: .secondary) { showInChat(message.id) })
            if canDelete(message) {
                options.append(ConfirmOption("Delete", systemImage: "trash", kind: .destructive) { pendingDelete = message })
            }
            return options
        }
        .confirmSheet(item: $pendingDelete) { _ in
            "Delete this message?"
        } message: { _ in
            "It'll show as deleted for everyone."
        } options: { message in
            [ConfirmOption("Delete message", systemImage: "trash", kind: .destructive) { Task { await model.delete(message, from: .thread) } }]
        }
        .errorAlert($model.threadError)
    }

    private var header: some View {
        HStack(alignment: .center) {
            VStack(alignment: .leading, spacing: 2) {
                Text("Thread")
                    .displayFont(22, .bold)
                    .accessibilityAddTraits(.isHeader)
                if !model.thread.isEmpty {
                    Text(Formatting.plural(model.thread.count, "message"))
                        .bodyFont(12, relativeTo: .caption)
                        .foregroundStyle(theme.muted)
                }
            }
            Spacer()
            CloseButton { dismiss() }
        }
        .padding(.horizontal, 18)
        .padding(.top, 18)
        .padding(.bottom, 6)
    }

    private var messages: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(spacing: 10) {
                    if model.isLoadingThread && model.thread.isEmpty {
                        ProgressView().frame(maxWidth: .infinity, minHeight: 160)
                    }
                    ForEach(Array(model.thread.enumerated()), id: \.element.id) { index, message in
                        if index == 0 || !Calendar.current.isDate(model.thread[index - 1].createdAt, inSameDayAs: message.createdAt) {
                            Text(Formatting.chatDay(message.createdAt))
                                .bodyFont(11, .semibold, relativeTo: .caption2)
                                .foregroundStyle(theme.muted)
                                .padding(.top, 4)
                        }
                        MessageRow(
                            message: message,
                            group: group,
                            me: me,
                            model: model,
                            isLatestOwn: false,
                            startsRun: startsRun(at: index),
                            openInfo: { actionsFor = message },
                            openProposal: { _ in },
                            onEdit: canEdit(message) ? { edit(message) } : nil,
                            onDelete: canDelete(message) ? { pendingDelete = message } : nil,
                            onReply: { reply(to: message) },
                            openQuote: { id in highlight(id, proxy: proxy) },
                            openImage: { attachment in viewing = ChatView.ViewerTarget(attachments: message.files.filter(\.isVisual), selected: attachment.id) },
                            openFile: { attachment in Task { await openFile(attachment) } },
                            openingFileId: openingFileId,
                            onShowInChat: { showInChat(message.id) },
                            showsInfo: false,
                            tapHint: "Shows reply, show in chat and, on your own messages, edit and delete",
                            quoteHint: "Shows the original message"
                        )
                        .modifier(SwipeToReply(isEnabled: message.canReply) { reply(to: message) })
                        .background {
                            RoundedRectangle(cornerRadius: 18, style: .continuous)
                                .fill(theme.accentSoft)
                                .padding(-6)
                                .opacity(highlightedId == message.id ? 1 : 0)
                                .allowsHitTesting(false)
                        }
                        .id(message.id)
                    }
                    if model.threadTruncated {
                        Text("Later replies aren\u{2019}t shown here. Find them in the chat.")
                            .bodyFont(12, relativeTo: .caption)
                            .foregroundStyle(theme.muted)
                            .multilineTextAlignment(.center)
                            .frame(maxWidth: .infinity)
                            .padding(.top, 4)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.top, 8)
                .padding(.bottom, 8)
            }
            .defaultScrollAnchor(.bottom)
            .scrollDismissesKeyboard(.interactively)
            // Once loaded, show where you came from.
            .onChange(of: model.isLoadingThread) { _, loading in
                if !loading, model.thread.count > 1 { highlight(focusId, proxy: proxy, animated: false) }
            }
            // Something new in the thread: follow it down.
            .onChange(of: model.thread.last?.id) { oldId, newId in
                guard oldId != nil, let newId else { return }
                withAnimation(.easeInOut(duration: 0.3)) { proxy.scrollTo(newId, anchor: .bottom) }
            }
        }
    }

    private func canEdit(_ message: Message) -> Bool {
        message.kind == .text && !message.isDeleted && message.place == nil && message.authorId != nil && message.authorId == me
    }

    private func canDelete(_ message: Message) -> Bool {
        message.kind == .text && !message.isDeleted && message.authorId != nil && message.authorId == me
    }

    private func reply(to message: Message) {
        showsMenu = false
        model.replyInThread(to: message)
    }

    private func edit(_ message: Message) {
        showsMenu = false
        model.startEditing(message, in: .thread)
    }

    /// Scrolls to a message in the thread and flashes it.
    private func highlight(_ id: String, proxy: ScrollViewProxy, animated: Bool = true) {
        guard model.thread.contains(where: { $0.id == id }) else { return }
        if animated {
            withAnimation(.easeInOut(duration: 0.3)) { proxy.scrollTo(id, anchor: .center) }
        } else {
            proxy.scrollTo(id, anchor: .center)
        }
        withAnimation(.easeOut(duration: 0.2)) { highlightedId = id }
        Task {
            try? await Task.sleep(for: .seconds(1.2))
            if highlightedId == id { withAnimation(.easeOut(duration: 0.6)) { highlightedId = nil } }
        }
    }

    /// First message in a run from the same person on the same day, so it shows their name.
    private func openFile(_ attachment: Attachment) async {
        guard openingFileId == nil else { return }
        openingFileId = attachment.id
        defer { openingFileId = nil }
        do {
            previewFile = try await AttachmentFiles.download(attachment)
        } catch {
            model.threadError = "Couldn't open \(attachment.name). Check your connection and try again."
        }
    }

    private func startsRun(at index: Int) -> Bool {
        guard index > 0 else { return true }
        let previous = model.thread[index - 1], message = model.thread[index]
        return previous.authorId != message.authorId
            || !Calendar.current.isDate(previous.createdAt, inSameDayAs: message.createdAt)
    }
}
