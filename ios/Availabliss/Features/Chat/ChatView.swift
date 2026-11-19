import PhotosUI
import QuickLook
import SwiftUI
import UniformTypeIdentifiers

/// Group chat: bubbles, proposal / event cards, polls, read receipts and the + composer.
struct ChatView: View {
    let detail: GroupDetailModel
    let group: Group
    @Environment(AppModel.self) private var app
    @Environment(\.theme) private var theme
    @State private var model: ChatViewModel
    @State private var showsMenu = false
    @State private var sheet: ChatSheet?
    /// Your message waiting for "Delete this message?".
    @State private var pendingDelete: Message?
    /// The original of a tapped quote, briefly highlighted after scrolling to it.
    @State private var highlightedId: String?
    /// Where the bottom of the messages sits in the list's frame, and the list's height: together,
    /// how far you've scrolled back from the latest message.
    @State private var contentBottom: CGFloat = 0
    @State private var viewportHeight: CGFloat = 0
    /// Messages from others that arrived while you were scrolled back, shown on the jump button.
    @State private var unseenCount = 0
    /// The list has been put at the latest message after the first load. Until then older messages
    /// aren't fetched: the lazy list can briefly show its top (or nothing) while it measures.
    @State private var hasLandedAtBottom = false
    /// A message to scroll to and flash ("Show in chat" from a thread).
    @State private var showRequest: String?
    /// A message's photos open full screen, at the one tapped.
    @State private var viewing: ViewerTarget?
    /// A downloaded file open in QuickLook.
    @State private var previewFile: URL?
    @State private var openingFileId: String?

    struct ViewerTarget: Identifiable {
        let attachments: [Attachment]
        let selected: String
        var id: String { selected }
    }

    /// Scrolled back further than this, the jump-to-latest button shows.
    private static let jumpThreshold: CGFloat = 200
    private static let scrollSpace = "chat-scroll"
    private var isScrolledBack: Bool { viewportHeight > 0 && contentBottom - viewportHeight > Self.jumpThreshold }

    enum ChatSheet: Identifiable {
        case info(Message)
        /// The thread a message is part of (by the message's id).
        case thread(String)
        var id: String {
            switch self {
            case .thread(let id): "thread-\(id)"
            case .info(let message): "info-\(message.id)"
            }
        }
    }

    init(detail: GroupDetailModel, group: Group) {
        self.detail = detail
        self.group = group
        _model = State(initialValue: ChatViewModel(groupId: group.id, api: detail.api))
    }

    private var me: String? { app.session.user?.id }

    var body: some View {
        VStack(spacing: 0) {
            messageList
            ChatComposerArea(model: model, target: .chat, group: group, me: me, api: detail.api, showsMenu: $showsMenu)
        }
        .fullScreenCover(item: $viewing) { target in
            AttachmentViewer(attachments: target.attachments, selection: target.selected)
        }
        .quickLookPreview($previewFile)
        .animation(.spring(response: 0.3, dampingFraction: 0.85), value: model.replyingTo?.id)
        .confirmSheet(item: $pendingDelete) { _ in
            "Delete this message?"
        } message: { _ in
            "It'll show as deleted for everyone."
        } options: { message in
            [ConfirmOption("Delete message", systemImage: "trash", kind: .destructive) { Task { await model.delete(message) } }]
        }
        .screenBackground(OrbLayout.chat)
        .task { await model.load(me: me) }
        .task(id: group.id) {
            for await event in app.live.events(forGroup: group.id) {
                if event.kind == .reconnected { await model.catchUp() } else { await model.apply(event) }
            }
        }
        .onAppear { app.router.visibleChatGroupId = group.id }
        .onDisappear { if app.router.visibleChatGroupId == group.id { app.router.visibleChatGroupId = nil } }
        .sheet(item: $sheet) { sheet in sheetContent(sheet) }
        .errorAlert($model.error)
    }

    private var messageList: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(spacing: 10) {
                    if model.hasMore {
                        ProgressView()
                            .frame(height: 32)
                            .onAppear { if hasLandedAtBottom { Task { await model.loadOlder() } } }
                    }
                    if model.isLoading { StatusView(kind: .loading) }
                    let repliedTo = Set(model.messages.compactMap(\.replyTo?.id))
                    ForEach(Array(model.messages.enumerated()), id: \.element.id) { index, message in
                        if index == 0 || !Calendar.current.isDate(model.messages[index - 1].createdAt, inSameDayAs: message.createdAt) {
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
                            isLatestOwn: message.id == model.latestOwnMessageId,
                            startsRun: startsRun(at: index),
                            openInfo: { sheet = .info(message) },
                            openProposal: { id in app.router.push(.proposal(id: id)) },
                            onEdit: {
                                showsMenu = false
                                model.startEditing(message)
                            },
                            onDelete: { pendingDelete = message },
                            onReply: { reply(to: message) },
                            openQuote: { _ in openThread(message.id) },
                            openImage: { attachment in openImage(attachment, in: message) },
                            openFile: { attachment in Task { await openFile(attachment) } },
                            openingFileId: openingFileId,
                            onOpenThread: message.replyTo != nil || repliedTo.contains(message.id) ? { openThread(message.id) } : nil
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
                }
                .padding(.horizontal, 16)
                .padding(.top, 14)
                .padding(.bottom, 8)
                .onGeometryChange(for: CGFloat.self) {
                    $0.frame(in: .named(Self.scrollSpace)).maxY
                } action: {
                    contentBottom = $0
                }
            }
            .coordinateSpace(name: Self.scrollSpace)
            .tracksScrollUnderTopBar()
            .onGeometryChange(for: CGFloat.self) {
                $0.size.height
            } action: {
                viewportHeight = $0
            }
            .defaultScrollAnchor(.bottom)
            .scrollDismissesKeyboard(.interactively)
            .overlay(alignment: .bottomTrailing) {
                if isScrolledBack {
                    JumpToLatestButton(count: unseenCount) { scrollToLatest(proxy: proxy) }
                        .padding(.trailing, 14)
                        .padding(.bottom, 8)
                        .transition(.scale(scale: 0.6).combined(with: .opacity))
                }
            }
            .animation(.spring(response: 0.3, dampingFraction: 0.8), value: isScrolledBack)
            .onChange(of: model.messages.last?.id) { oldId, newId in
                newestChanged(from: oldId, to: newId, proxy: proxy)
            }
            .onChange(of: isScrolledBack) { _, back in if !back { unseenCount = 0 } }
            // The bottom anchor alone left the list blank on opening (the lazy rows weren't laid out
            // yet), until a scroll brought them in with a jump: go to the latest message explicitly.
            .onChange(of: model.messages.isEmpty, initial: true) { _, isEmpty in
                if !isEmpty { landAtBottom(proxy: proxy) }
            }
            .onChange(of: showRequest) { _, id in
                guard let id else { return }
                showRequest = nil
                Task {
                    // Lets the thread sheet get out of the way first.
                    try? await Task.sleep(for: .seconds(0.35))
                    if await model.loadUntil(id) { showOriginal(id, proxy: proxy) }
                }
            }
        }
    }

    /// Once, after the first messages load: shows the latest without animation. Twice over a frame,
    /// since the first pass can land short while rows above it are still being measured.
    private func landAtBottom(proxy: ScrollViewProxy) {
        guard !hasLandedAtBottom else { return }
        Task { @MainActor in
            await Task.yield()
            if let last = model.messages.last?.id { proxy.scrollTo(last, anchor: .bottom) }
            try? await Task.sleep(for: .milliseconds(60))
            if let last = model.messages.last?.id { proxy.scrollTo(last, anchor: .bottom) }
            hasLandedAtBottom = true
        }
    }

    private func scrollToLatest(proxy: ScrollViewProxy) {
        guard let last = model.messages.last?.id else { return }
        withAnimation(.easeInOut(duration: 0.35)) { proxy.scrollTo(last, anchor: .bottom) }
        unseenCount = 0
    }

    /// A new newest message: counted on the jump button while you're scrolled back (no jump),
    /// or, if you sent it, the list goes to it.
    private func newestChanged(from oldId: String?, to newId: String?, proxy: ScrollViewProxy) {
        guard let oldId, let newId, oldId != newId,
            let oldIndex = model.messages.firstIndex(where: { $0.id == oldId })
        else { return }
        let added = model.messages[(oldIndex + 1)...]
        if added.contains(where: { $0.authorId != nil && $0.authorId == me }) {
            scrollToLatest(proxy: proxy)
        } else if isScrolledBack {
            unseenCount += added.filter { $0.kind != .system }.count
        }
    }

    private func openImage(_ attachment: Attachment, in message: Message) {
        viewing = ViewerTarget(attachments: message.files.filter(\.isVisual), selected: attachment.id)
    }

    /// Downloads a file and opens it in QuickLook (which can share or save it too).
    private func openFile(_ attachment: Attachment) async {
        guard openingFileId == nil else { return }
        openingFileId = attachment.id
        defer { openingFileId = nil }
        do {
            previewFile = try await AttachmentFiles.download(attachment)
        } catch {
            model.error = "Couldn't open \(attachment.name). Check your connection and try again."
        }
    }

    private func openThread(_ messageId: String) {
        showsMenu = false
        sheet = .thread(messageId)
    }

    private func reply(to message: Message) {
        showsMenu = false
        model.startReplying(to: message)
    }

    /// Scrolls to a message (when it's loaded) and flashes it.
    private func showOriginal(_ id: String, proxy: ScrollViewProxy) {
        guard model.messages.contains(where: { $0.id == id }) else { return }
        withAnimation(.easeInOut(duration: 0.3)) { proxy.scrollTo(id, anchor: .center) }
        withAnimation(.easeOut(duration: 0.2)) { highlightedId = id }
        Task {
            try? await Task.sleep(for: .seconds(1.2))
            if highlightedId == id { withAnimation(.easeOut(duration: 0.6)) { highlightedId = nil } }
        }
    }

    /// First message in a run from the same person on the same day, so it shows their name.
    private func startsRun(at index: Int) -> Bool {
        guard index > 0 else { return true }
        let previous = model.messages[index - 1], message = model.messages[index]
        return previous.authorId != message.authorId
            || previous.kind == .system
            || !Calendar.current.isDate(previous.createdAt, inSameDayAs: message.createdAt)
    }

    @ViewBuilder
    private func sheetContent(_ sheet: ChatSheet) -> some View {
        switch sheet {
        case .info(let message):
            MessageInfoSheet(message: message, group: group, me: app.session.user?.id, model: model)
        case .thread(let id):
            ThreadSheet(focusId: id, group: group, me: me, model: model, api: detail.api) { target in
                self.sheet = nil
                showRequest = target
            }
        }
    }
}

/// Floats over the bottom of the chat while you're scrolled back: a down arrow back to the latest
/// message, with how many new ones arrived meanwhile.
private struct JumpToLatestButton: View {
    let count: Int
    let action: () -> Void
    @Environment(\.theme) private var theme

    var body: some View {
        IconButton(systemImage: "arrow.down", label: "Jump to latest message", action: action)
            .overlay(alignment: .topTrailing) {
                if count > 0 {
                    Text(count > 99 ? "99+" : "\(count)")
                        .bodyFont(11, .bold, relativeTo: .caption2)
                        .foregroundStyle(theme.onAccent)
                        .padding(.horizontal, 6)
                        .frame(minWidth: 20, minHeight: 20)
                        .background(Capsule().fill(theme.accent))
                        .offset(x: 4, y: -4)
                        .accessibilityHidden(true)
                }
            }
            .accessibilityValue(count > 0 ? "\(count) new" : "")
    }
}

/// Above the composer while you edit a message: "Editing message" and ✕ to stop.
struct EditingStrip: View {
    let cancel: () -> Void
    @Environment(\.theme) private var theme

    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: "pencil")
                .font(.system(size: 13, weight: .bold))
                .foregroundStyle(theme.accentInk)
                .accessibilityHidden(true)
            Text("Editing message")
                .bodyFont(13, .semibold, relativeTo: .footnote)
                .foregroundStyle(theme.ink)
            Spacer(minLength: 0)
            Button(action: cancel) {
                Image(systemName: "xmark")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(theme.muted)
                    .frame(width: 36, height: 36)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Stop editing")
        }
        .padding(.leading, 14)
        .padding(.trailing, 4)
        .glass(cornerRadius: 18)
        .padding(.horizontal, 12)
        .padding(.top, 10)
    }
}

/// Above the composer while you reply: "Replying to Ana", one line of what they said, and ✕ to cancel.
struct ReplyingStrip: View {
    let name: String
    let snippet: String
    var cancelLabel = "Cancel reply"
    /// Nil hides ✕.
    let cancel: (() -> Void)?
    @Environment(\.theme) private var theme

    var body: some View {
        HStack(spacing: 10) {
            RoundedRectangle(cornerRadius: 1.5)
                .fill(theme.accentInk)
                .frame(width: 3, height: 32)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 1) {
                Text("Replying to \(name == "You" ? "yourself" : name)")
                    .bodyFont(13, .semibold, relativeTo: .footnote)
                    .foregroundStyle(theme.ink)
                    .lineLimit(1)
                Text(snippet)
                    .bodyFont(12, relativeTo: .caption)
                    .foregroundStyle(theme.muted)
                    .lineLimit(1)
            }
            .accessibilityElement(children: .combine)
            Spacer(minLength: 0)
            if let cancel {
                Button(action: cancel) {
                    Image(systemName: "xmark")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundStyle(theme.muted)
                        .frame(width: 36, height: 36)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(cancelLabel)
            } else {
                Color.clear.frame(width: 10, height: 36)
            }
        }
        .padding(.vertical, 6)
        .padding(.leading, 14)
        .padding(.trailing, 4)
        .glass(cornerRadius: 18)
        .padding(.horizontal, 12)
        .padding(.top, 10)
    }
}

/// The big buttons above the composer: Photos, Files and Location, then Poll, Propose a time and Add event.
struct ComposerMenu: View {
    enum Choice { case photos, files, location, poll, propose, event }
    let choose: (Choice) -> Void
    @Environment(\.theme) private var theme

    var body: some View {
        VStack(spacing: 6) {
            HStack(spacing: 6) {
                item("Photos", "photo.on.rectangle") { choose(.photos) }
                item("Files", "paperclip") { choose(.files) }
                item("Location", "mappin.and.ellipse") { choose(.location) }
            }
            HStack(spacing: 6) {
                item("Poll", "chart.bar") { choose(.poll) }
                item("Propose a time", "checkmark.circle") { choose(.propose) }
                item("Add event", "calendar.badge.plus") { choose(.event) }
            }
        }
        .padding(8)
        .glass(cornerRadius: 22)
        .padding(.horizontal, 12)
        .padding(.top, 10)
    }

    private func item(_ title: String, _ icon: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: 6) {
                Image(systemName: icon).font(.system(size: 20, weight: .regular)).foregroundStyle(theme.accentInk)
                Text(title).bodyFont(12, .semibold, relativeTo: .caption).foregroundStyle(theme.ink)
            }
            .frame(maxWidth: .infinity, minHeight: 72)
            .background(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(theme.surface))
        }
        .buttonStyle(.pressable)
    }
}

/// Glass composer: + menu toggle, text field, send.
struct Composer: View {
    @Binding var text: String
    @Binding var isMenuOpen: Bool
    let isSending: Bool
    /// The message being edited: Send becomes Save, and the field takes focus.
    let editingId: String?
    /// The message being replied to: the field takes focus.
    var replyingId: String?
    /// Off in a thread, where there's only text.
    var showsMenu = true
    var placeholder = "Message the group"
    /// Overrides the usual "has text" rule (e.g. photos with no caption); nil keeps it.
    var canSend: Bool?
    /// Saving an edit with no text is allowed (a caption being removed).
    var allowsEmpty = false
    let send: () -> Void
    @Environment(\.theme) private var theme
    @FocusState private var isFocused: Bool

    var body: some View {
        HStack(spacing: 6) {
            if showsMenu {
                Button {
                    isMenuOpen.toggle()
                } label: {
                    Image(systemName: "plus")
                        .font(.system(size: 18, weight: .bold))
                        .rotationEffect(.degrees(isMenuOpen ? 45 : 0))
                        .foregroundStyle(isMenuOpen ? theme.onSolid : theme.ink)
                        .frame(width: 44, height: 44)
                        .background(Circle().fill(isMenuOpen ? theme.solid : theme.surface))
                }
                .buttonStyle(.pressable)
                .accessibilityLabel("Attach photos or files, or add a poll, proposal or event")
                .accessibilityValue(isMenuOpen ? "Expanded" : "Collapsed")
            }

            TextField("", text: $text, prompt: Text(placeholder).foregroundStyle(theme.muted), axis: .vertical)
                .lineLimit(1...5)
                .bodyFont(15)
                .foregroundStyle(theme.ink)
                .frame(minHeight: 44)
                .padding(.leading, showsMenu ? 0 : 12)
                .submitLabel(editingId == nil ? .send : .done)
                .onSubmit(send)
                .focused($isFocused)
                .onChange(of: editingId) { _, id in if id != nil { isFocused = true } }
                .onChange(of: replyingId) { _, id in if id != nil { isFocused = true } }
                .accessibilityLabel(editingId == nil ? "Message" : "Edit message")

            Button(action: send) {
                Image(systemName: editingId == nil ? "arrow.right" : "checkmark")
                    .font(.system(size: 17, weight: .bold))
                    .foregroundStyle(theme.onAccent)
                    .frame(width: 44, height: 44)
                    .background(Circle().fill(theme.accent))
            }
            .buttonStyle(.pressable)
            .disabled(
                !(canSend ?? !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                    || (editingId != nil && !allowsEmpty && text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty) || isSending
            )
            .accessibilityLabel(editingId == nil ? "Send" : "Save")
        }
        .padding(6)
        .glass(cornerRadius: 24)
        .padding(.horizontal, 12)
        .padding(.top, 10)
        .padding(.bottom, 10)
    }
}
