import Foundation
import Observation
import UIKit

@MainActor
@Observable
final class ChatViewModel {
    private(set) var messages: [Message] = []
    private(set) var hasMore = false
    private(set) var isLoading = false
    private(set) var isLoadingOlder = false
    private(set) var proposals: [String: Proposal] = [:]
    private(set) var events: [String: GroupEvent] = [:]
    var error: String?
    /// The message your next one in the chat replies to, shown above the composer.
    private(set) var replyingTo: Message?
    private var uploadTasks: [UUID: Task<Void, Never>] = [:]

    /// Where a message is being written: the chat's composer, or the open thread's. Both work the
    /// same way (text, photos and files, places, editing); a thread's messages reply within it.
    enum ComposerTarget: Hashable, Sendable { case chat, thread }

    struct ComposerState {
        var draft = ""
        /// Photos and files picked for the next message, uploading as soon as they're picked.
        var uploads: [PendingUpload] = []
        /// Your message being edited, if any.
        var editing: Message?
        /// The draft from before editing started, put back when it ends.
        var draftBeforeEdit = ""
        var isSending = false
    }

    private(set) var composers: [ComposerTarget: ComposerState] = [.chat: ComposerState(), .thread: ComposerState()]

    func composer(_ target: ComposerTarget) -> ComposerState { composers[target] ?? ComposerState() }

    func setDraft(_ text: String, for target: ComposerTarget) { composers[target, default: ComposerState()].draft = text }

    /// The chat composer's state, as the chat screen reads it.
    var draft: String {
        get { composer(.chat).draft }
        set { setDraft(newValue, for: .chat) }
    }
    var editing: Message? { composer(.chat).editing }
    var uploads: [PendingUpload] { composer(.chat).uploads }
    var isSending: Bool { composer(.chat).isSending }

    /// The thread open in its sheet: the first message of a chain of replies and every reply under it, oldest first.
    private(set) var thread: [Message] = []
    private(set) var threadTruncated = false
    private(set) var isLoadingThread = false
    var threadError: String?
    var threadDraft: String {
        get { composer(.thread).draft }
        set { setDraft(newValue, for: .thread) }
    }
    var isSendingInThread: Bool { composer(.thread).isSending }
    /// The message picked to reply to in the thread; otherwise replies go to the latest one.
    private var threadReplyTargetId: String?

    let groupId: String
    let api: APIClient
    /// Who's busy at each date poll option's time (by option id), from the conflicts endpoint.
    private(set) var pollBusy: [String: [String]] = [:]
    private var me: String?
    private var lastSeenSent: String?

    init(groupId: String, api: APIClient) {
        self.groupId = groupId
        self.api = api
    }

    var latestOwnMessageId: String? { messages.last { $0.authorId == me && $0.kind != .system }?.id }

    // MARK: Loading

    func load(me: String?) async {
        self.me = me
        isLoading = messages.isEmpty
        defer { isLoading = false }
        do {
            let page = try await api.send(API.Chat.messages(groupId: groupId))
            messages = page.messages
            hasMore = page.hasMore
            await hydrate(page.messages)
            await markSeen()
        } catch  where !error.isCancellation {
            self.error = error.userMessage
        } catch {}
    }

    /// Refetches the newest page after the live stream reconnects. Keeps older pages already
    /// scrolled in when the new page overlaps them; otherwise the gap is too big to stitch, so
    /// the list restarts from the newest page. Silent on failure: this runs in the background.
    func catchUp() async {
        guard let page = try? await api.send(API.Chat.messages(groupId: groupId)) else { return }
        if let oldestNew = page.messages.first, let overlap = messages.firstIndex(where: { $0.id == oldestNew.id }) {
            messages = Array(messages[..<overlap]) + page.messages
        } else {
            messages = page.messages
            hasMore = page.hasMore
        }
        // Proposals and events on the cards may have changed while we were away too.
        await hydrate(page.messages, refreshing: true)
        await markSeen()
    }

    func loadOlder() async {
        guard hasMore, !isLoadingOlder, let oldest = messages.first else { return }
        isLoadingOlder = true
        defer { isLoadingOlder = false }
        do {
            let page = try await api.send(API.Chat.messages(groupId: groupId, before: oldest.id))
            messages.insert(contentsOf: page.messages.filter { message in !messages.contains { $0.id == message.id } }, at: 0)
            hasMore = page.hasMore
            await hydrate(page.messages)
        } catch {
            self.error = error.userMessage
        }
    }

    // MARK: Sending

    /// Whether Send can go: some text or an uploaded attachment, with nothing still uploading or failed.
    func canSend(_ target: ComposerTarget = .chat) -> Bool {
        let state = composer(target)
        if state.editing != nil { return true }
        if target == .thread && threadReplyingTo == nil { return false }
        let hasText = !state.draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        if state.uploads.isEmpty { return hasText }
        return state.uploads.allSatisfy { $0.uploaded != nil }
    }

    var canSend: Bool { canSend(.chat) }

    /// Who a new message from `target` replies to: the one picked in the chat, or in a thread the
    /// picked message or else the latest.
    private func replyTarget(_ target: ComposerTarget) -> Message? {
        target == .chat ? replyingTo : threadReplyingTo
    }

    /// Shows an error where it was caused: over the chat or over the thread.
    func report(_ message: String, _ target: ComposerTarget) {
        if target == .thread { threadError = message } else { error = message }
    }

    func send(_ target: ComposerTarget = .chat) async {
        if composer(target).editing != nil { return await saveEdit(target) }
        let state = composer(target)
        let text = state.draft.trimmingCharacters(in: .whitespacesAndNewlines)
        let attachments = state.uploads.compactMap(\.uploaded)
        guard canSend(target), !state.isSending, !text.isEmpty || !attachments.isEmpty else { return }
        let replying = replyTarget(target)
        if target == .thread && replying == nil { return }
        composers[target]?.isSending = true
        defer { composers[target]?.isSending = false }
        composers[target]?.draft = ""
        let sent = state.uploads
        composers[target]?.uploads = []
        if target == .chat { replyingTo = nil }
        do {
            let body = SendMessageBody(body: text.isEmpty ? nil : text, replyToId: replying?.id, attachmentIds: attachments.isEmpty ? nil : attachments.map(\.id))
            let message = try await api.send(API.Chat.send(groupId: groupId, body))
            insert(message)
            if target == .thread { threadReplyTargetId = nil }
        } catch {
            composers[target]?.draft = text
            if composer(target).uploads.isEmpty { composers[target]?.uploads = sent }
            if target == .chat, replyingTo == nil { replyingTo = replying }
            report(error.userMessage, target)
        }
    }

    // MARK: Attachments

    /// Adds picked photos or files to the next message and starts uploading them (up to 10 in all).
    func attach(_ picked: [(data: Data, name: String, mime: String, preview: UIImage?)], to target: ComposerTarget = .chat) {
        let room = AttachmentFormat.maxPerMessage - composer(target).uploads.count
        if picked.count > room { report("A message can have up to \(AttachmentFormat.maxPerMessage) photos and files.", target) }
        for item in picked.prefix(max(room, 0)) {
            let limit = item.preview != nil ? AttachmentFormat.maxPhotoBytes : AttachmentFormat.maxFileBytes
            guard item.data.count <= limit else {
                report(item.preview != nil ? "Photos can be up to 15 MB." : "\(item.name) is too big: files can be up to 25 MB.", target)
                continue
            }
            let upload = PendingUpload(name: item.name, mime: item.mime, data: item.data, preview: item.preview)
            composers[target, default: ComposerState()].uploads.append(upload)
            start(upload)
        }
    }

    /// The composer an upload belongs to.
    private func targetOf(_ id: UUID) -> ComposerTarget? {
        composers.first { $0.value.uploads.contains { $0.id == id } }?.key
    }

    func removeUpload(_ upload: PendingUpload) {
        uploadTasks[upload.id]?.cancel()
        uploadTasks[upload.id] = nil
        guard let target = targetOf(upload.id) else { return }
        composers[target]?.uploads.removeAll { $0.id == upload.id }
    }

    func retryUpload(_ upload: PendingUpload) {
        guard let target = targetOf(upload.id), let index = composer(target).uploads.firstIndex(where: { $0.id == upload.id }) else { return }
        composers[target]?.uploads[index].error = nil
        composers[target]?.uploads[index].progress = 0
        start(composer(target).uploads[index])
    }

    private func start(_ upload: PendingUpload) {
        let api = self.api, groupId = self.groupId, id = upload.id
        uploadTasks[id] = Task { [weak self] in
            do {
                let attachment = try await api.upload(API.Chat.upload(groupId: groupId, data: upload.data, name: upload.name, mime: upload.mime)) { fraction in
                    Task { @MainActor in self?.setProgress(id, fraction) }
                }
                self?.finishUpload(id, attachment: attachment, error: nil)
            } catch  where error.isCancellation {
            } catch {
                self?.finishUpload(id, attachment: nil, error: error.userMessage)
            }
        }
    }

    private func setProgress(_ id: UUID, _ fraction: Double) {
        guard let target = targetOf(id), let index = composer(target).uploads.firstIndex(where: { $0.id == id }),
            composer(target).uploads[index].isUploading
        else { return }
        // Held just short of done until the server has answered.
        composers[target]?.uploads[index].progress = min(fraction, 0.95)
    }

    private func finishUpload(_ id: UUID, attachment: Attachment?, error: String?) {
        uploadTasks[id] = nil
        guard let target = targetOf(id), let index = composer(target).uploads.firstIndex(where: { $0.id == id }) else { return }
        composers[target]?.uploads[index].progress = 1
        composers[target]?.uploads[index].uploaded = attachment
        composers[target]?.uploads[index].error = error
        if let error { report(error, target) }
    }

    /// Drops a composer's draft, picked files (cancelling their uploads) and any edit.
    private func resetComposer(_ target: ComposerTarget) {
        for upload in composer(target).uploads {
            uploadTasks[upload.id]?.cancel()
            uploadTasks[upload.id] = nil
        }
        composers[target] = ComposerState()
    }

    // MARK: Replying

    func startReplying(to message: Message) {
        guard message.canReply else { return }
        cancelEditing(.chat)
        replyingTo = message
    }

    func cancelReplying() {
        replyingTo = nil
    }

    // MARK: Threads

    /// Loads the thread `messageId` is part of (it may reach back past what's loaded in the chat).
    /// Your reply starts out going to `messageId`, the message you opened it from.
    func openThread(for messageId: String) async {
        thread = []
        threadTruncated = false
        threadReplyTargetId = messageId
        resetComposer(.thread)
        isLoadingThread = true
        defer { isLoadingThread = false }
        do {
            let result = try await api.send(API.Chat.thread(messageId: messageId))
            thread = result.messages
            threadTruncated = result.truncated
        } catch  where !error.isCancellation {
            threadError = error.userMessage
        } catch {}
    }

    func closeThread() {
        thread = []
        threadReplyTargetId = nil
        resetComposer(.thread)
    }

    /// Who your reply in the thread goes to: the one you picked, else the latest from someone else
    /// (so it isn't to yourself straight after you send), else the latest.
    var threadReplyingTo: Message? {
        if let id = threadReplyTargetId, let picked = thread.first(where: { $0.id == id }), picked.canReply { return picked }
        return thread.last { $0.canReply && $0.authorId != me } ?? thread.last(where: \.canReply)
    }

    /// True once you've picked a message to reply to, rather than the latest.
    var hasPickedThreadReply: Bool { threadReplyTargetId != nil && threadReplyingTo?.id == threadReplyTargetId }

    func replyInThread(to message: Message?) {
        if message != nil { cancelEditing(.thread) }
        threadReplyTargetId = message?.id
    }

    func sendInThread() async { await send(.thread) }

    /// Loads older pages until `id` is in the chat (or there's nothing older), so it can be shown there.
    func loadUntil(_ id: String) async -> Bool {
        var pages = 0
        while !messages.contains(where: { $0.id == id }), hasMore, pages < 20 {
            let before = messages.count
            await loadOlder()
            if messages.count == before { break }
            pages += 1
        }
        return messages.contains { $0.id == id }
    }

    /// Shares a place (as a reply, if one is being written, or in a thread). False if it couldn't be sent.
    func sendLocation(_ location: MessageLocation, in target: ComposerTarget = .chat) async -> Bool {
        let replying = replyTarget(target)
        do {
            let message = try await api.send(API.Chat.send(groupId: groupId, SendMessageBody(replyToId: replying?.id, location: location)))
            if target == .chat, replyingTo?.id == replying?.id { replyingTo = nil }
            if target == .thread { threadReplyTargetId = nil }
            insert(message)
            return true
        } catch {
            report(error.userMessage, target)
            return false
        }
    }

    /// Polls go in the chat itself (they can't be replies), from a thread too.
    func createPoll(question: String, options: [String], multi: Bool, from target: ComposerTarget = .chat) async -> Bool {
        do {
            let message = try await api.send(API.Chat.send(groupId: groupId, SendMessageBody(poll: .init(question: question, options: options, multi: multi))))
            insert(message)
            return true
        } catch {
            report(error.userMessage, target)
            return false
        }
    }

    func vote(poll: Poll, option: Poll.Option) async {
        guard let me, !poll.closed else { return }
        let mine = Set(poll.options.filter { $0.voterIds.contains(me) }.map(\.id))
        var next: Set<String>
        if mine.contains(option.id) {
            next = mine.subtracting([option.id])
        } else {
            next = poll.multi ? mine.union([option.id]) : [option.id]
        }
        // Optimistic update so the bars move immediately.
        var optimistic = poll
        for index in optimistic.options.indices {
            optimistic.options[index].voterIds.removeAll { $0 == me }
            if next.contains(optimistic.options[index].id) { optimistic.options[index].voterIds.append(me) }
        }
        update(poll: optimistic)
        do {
            update(poll: try await api.send(API.Chat.vote(pollId: poll.id, optionIds: Array(next))))
        } catch {
            update(poll: poll)
            self.error = error.userMessage
        }
    }

    /// Looks up who's busy at each of a date poll's times (once per option).
    func loadBusy(for poll: Poll) async {
        for option in poll.options where pollBusy[option.id] == nil {
            guard let slot = option.slot else { continue }
            guard let busy = try? await api.send(API.Groups.conflicts(groupId: groupId, start: slot.start, end: slot.end)) else { continue }
            pollBusy[option.id] = busy.busyMemberIds
        }
    }

    func closePoll(_ poll: Poll) async {
        do { update(poll: try await api.send(API.Chat.closePoll(pollId: poll.id))) } catch { self.error = error.userMessage }
    }

    // MARK: Editing and deleting

    func startEditing(_ message: Message, in target: ComposerTarget = .chat) {
        guard message.kind == .text, !message.isDeleted, message.place == nil, message.authorId == me else { return }
        if target == .chat { replyingTo = nil }
        // Read before writing: `composers[target]?.x = composer(target).y` would access `composers`
        // twice at once, which Swift's exclusivity checks stop at run time.
        let current = composer(target)
        if current.editing == nil { composers[target]?.draftBeforeEdit = current.draft }
        composers[target]?.editing = message
        composers[target]?.draft = message.text ?? ""
    }

    func cancelEditing(_ target: ComposerTarget = .chat) {
        guard composer(target).editing != nil else { return }
        composers[target]?.editing = nil
        let restored = composer(target).draftBeforeEdit
        composers[target]?.draft = restored
        composers[target]?.draftBeforeEdit = ""
    }

    private func saveEdit(_ target: ComposerTarget) async {
        let state = composer(target)
        guard let message = state.editing, !state.isSending else { return }
        let text = state.draft.trimmingCharacters(in: .whitespacesAndNewlines)
        // With attachments the caption can be cleared.
        guard !text.isEmpty || !message.files.isEmpty else { return }
        guard text != (message.text ?? "") else { return cancelEditing(target) }
        composers[target]?.isSending = true
        defer { composers[target]?.isSending = false }
        do {
            replaceKeepingReceipt(try await api.send(API.Chat.edit(messageId: message.id, body: text)))
            cancelEditing(target)
        } catch {
            report(error.userMessage, target)
        }
    }

    func delete(_ message: Message, from target: ComposerTarget = .chat) async {
        do {
            _ = try await api.send(API.Chat.delete(messageId: message.id))
            var deleted = message
            deleted.deleted = true
            deleted.body = nil
            deleted.attachments = []
            deleted.caption = nil
            replaceKeepingReceipt(deleted)
            stopEditingOrReplying(to: message.id)
        } catch {
            report(error.userMessage, target)
        }
    }

    /// A deleted message can't be edited or replied to any more.
    private func stopEditingOrReplying(to messageId: String) {
        for target in [ComposerTarget.chat, .thread] where composer(target).editing?.id == messageId { cancelEditing(target) }
        if replyingTo?.id == messageId { cancelReplying() }
        if threadReplyTargetId == messageId { threadReplyTargetId = nil }
    }

    /// An edited or deleted message: everything but the receipt, which is per viewer.
    private func replaceKeepingReceipt(_ message: Message) {
        Self.replaceKeepingReceipt(message, in: &messages)
        Self.replaceKeepingReceipt(message, in: &thread)
    }

    private static func replaceKeepingReceipt(_ message: Message, in list: inout [Message]) {
        refreshQuotes(of: message, in: &list)
        guard let index = list.firstIndex(where: { $0.id == message.id }) else { return }
        var updated = message
        updated.receipt = list[index].receipt
        list[index] = updated
    }

    /// Quotes of a message aren't republished when it's edited or deleted, so update loaded replies here.
    private static func refreshQuotes(of message: Message, in list: inout [Message]) {
        for index in list.indices where list[index].replyTo?.id == message.id {
            let fresh = message.asReplyPreview(authorName: list[index].replyTo?.authorName)
            list[index].replyTo?.text = fresh.text
            list[index].replyTo?.deleted = fresh.deleted
        }
    }

    func info(for messageId: String) async -> MessageInfo? {
        do { return try await api.send(API.Chat.info(messageId: messageId)) } catch {
            self.error = error.userMessage
            return nil
        }
    }

    // MARK: Live

    func apply(_ event: LiveEvent) async {
        switch event.kind {
        case .messageCreated:
            guard let message = event.payload(as: Message.self), message.groupId == groupId else { return }
            insert(message)
            await hydrate([message])
            if message.authorId != me { await markSeen() }
        case .messageUpdated:
            guard let message = event.payload(as: Message.self), message.groupId == groupId else { return }
            replaceKeepingReceipt(message)
            if message.isDeleted { stopEditingOrReplying(to: message.id) }
        case .pollUpdated:
            if let poll = event.payload(as: Poll.self) { update(poll: poll) }
        case .messageReceipts:
            guard let payload = event.payload(as: MessageReceiptsPayload.self) else { return }
            for entry in payload.receipts {
                if let index = messages.firstIndex(where: { $0.id == entry.messageId }) {
                    messages[index].receipt = Message.Receipt(delivered: entry.delivered, seen: entry.seen, recipients: entry.recipients, seenBy: entry.seenBy)
                }
            }
        case .proposalCreated, .proposalUpdated:
            if let proposal = event.payload(as: Proposal.self) { proposals[proposal.id] = proposal }
        case .proposalConfirmed:
            if let payload = event.payload(as: ProposalConfirmedPayload.self) {
                proposals[payload.proposal.id] = payload.proposal
                events[payload.event.id] = payload.event
            }
        case .eventChanged:
            if let id = event.deletedEventId { events[id] = nil } else if let changed = event.payload(as: GroupEvent.self) { events[changed.id] = changed }
        default:
            break
        }
    }

    /// Tells the server you've seen up to the newest message.
    func markSeen() async {
        guard let last = messages.last(where: { $0.authorId != me }), last.id != lastSeenSent else { return }
        lastSeenSent = last.id
        _ = try? await api.send(API.Chat.receipts(groupId: groupId, upTo: last.id, state: .seen))
    }

    // MARK: - Private

    private func insert(_ message: Message) {
        if let index = messages.firstIndex(where: { $0.id == message.id }) {
            messages[index] = message
        } else {
            messages.append(message)
        }
        // A reply to something in the open thread joins it.
        if let index = thread.firstIndex(where: { $0.id == message.id }) {
            thread[index] = message
        } else if let parent = message.replyTo?.id, thread.contains(where: { $0.id == parent }) {
            thread.append(message)
        }
    }

    private func update(poll: Poll) {
        if let index = messages.firstIndex(where: { $0.poll?.id == poll.id }) { messages[index].poll = poll }
        if let index = thread.firstIndex(where: { $0.poll?.id == poll.id }) { thread[index].poll = poll }
    }

    /// Loads the proposals and events that cards in `batch` refer to (`refreshing` refetches ones already loaded).
    private func hydrate(_ batch: [Message], refreshing: Bool = false) async {
        let api = self.api
        var proposalIds = Set(batch.compactMap(\.proposalId))
        if !refreshing { proposalIds.subtract(proposals.keys) }
        let fetchedProposals = await Self.fetchAll(proposalIds) { try await api.send(API.Proposals.get(id: $0)) }
        for proposal in fetchedProposals { proposals[proposal.id] = proposal }

        var eventIds = Set(batch.compactMap(\.eventId))
        if !refreshing { eventIds.subtract(events.keys) }
        let fetchedEvents = await Self.fetchAll(eventIds) { try await api.send(API.Events.get(id: $0)) }
        for event in fetchedEvents { events[event.id] = event }
    }

    /// Fetches each id concurrently, skipping failures.
    private static func fetchAll<T: Sendable>(_ ids: Set<String>, _ fetch: @escaping @Sendable (String) async throws -> T) async -> [T] {
        await withTaskGroup(of: T?.self) { group in
            for id in ids { group.addTask { try? await fetch(id) } }
            var result: [T] = []
            for await item in group { if let item { result.append(item) } }
            return result
        }
    }
}
