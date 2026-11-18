import Foundation
import Observation

@MainActor
@Observable
final class ChatViewModel {
    private(set) var messages: [Message] = []
    private(set) var hasMore = false
    private(set) var isLoading = false
    private(set) var isLoadingOlder = false
    private(set) var isSending = false
    private(set) var proposals: [String: Proposal] = [:]
    private(set) var events: [String: GroupEvent] = [:]
    var draft = ""
    var error: String?
    /// Your message being edited in the composer, if any.
    private(set) var editing: Message?
    /// The composer's draft from before editing started, put back when it ends.
    private var draftBeforeEdit = ""
    /// The message your next one replies to, shown above the composer.
    private(set) var replyingTo: Message?

    /// The thread open in its sheet: the first message of a chain of replies and every reply under it, oldest first.
    private(set) var thread: [Message] = []
    private(set) var threadTruncated = false
    private(set) var isLoadingThread = false
    private(set) var isSendingInThread = false
    var threadDraft = ""
    var threadError: String?
    /// The message picked to reply to in the thread; otherwise replies go to the latest one.
    private var threadReplyTargetId: String?

    let groupId: String
    private let api: APIClient
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
        } catch where !error.isCancellation {
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

    func send() async {
        if editing != nil { return await saveEdit() }
        let text = draft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty, !isSending else { return }
        isSending = true
        defer { isSending = false }
        draft = ""
        let replying = replyingTo
        replyingTo = nil
        do {
            let message = try await api.send(API.Chat.send(groupId: groupId, SendMessageBody(body: text, replyToId: replying?.id)))
            insert(message)
        } catch {
            draft = text
            if replyingTo == nil { replyingTo = replying }
            self.error = error.userMessage
        }
    }

    // MARK: Replying

    func startReplying(to message: Message) {
        guard message.canReply else { return }
        cancelEditing()
        replyingTo = message
    }

    func cancelReplying() {
        replyingTo = nil
    }

    // MARK: Threads

    /// Loads the thread `messageId` is part of (it may reach back past what's loaded in the chat).
    func openThread(for messageId: String) async {
        thread = []
        threadTruncated = false
        threadReplyTargetId = nil
        threadDraft = ""
        isLoadingThread = true
        defer { isLoadingThread = false }
        do {
            let result = try await api.send(API.Chat.thread(messageId: messageId))
            thread = result.messages
            threadTruncated = result.truncated
        } catch where !error.isCancellation {
            threadError = error.userMessage
        } catch {}
    }

    func closeThread() {
        thread = []
        threadReplyTargetId = nil
        threadDraft = ""
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
        threadReplyTargetId = message?.id
    }

    func sendInThread() async {
        let text = threadDraft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty, !isSendingInThread, let target = threadReplyingTo else { return }
        isSendingInThread = true
        defer { isSendingInThread = false }
        threadDraft = ""
        do {
            let message = try await api.send(API.Chat.send(groupId: groupId, SendMessageBody(body: text, replyToId: target.id)))
            insert(message)
            threadReplyTargetId = nil
        } catch {
            threadDraft = text
            threadError = error.userMessage
        }
    }

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

    func createPoll(question: String, options: [String], multi: Bool) async -> Bool {
        do {
            let message = try await api.send(API.Chat.send(groupId: groupId, SendMessageBody(poll: .init(question: question, options: options, multi: multi))))
            insert(message)
            return true
        } catch {
            self.error = error.userMessage
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

    func closePoll(_ poll: Poll) async {
        do { update(poll: try await api.send(API.Chat.closePoll(pollId: poll.id))) } catch { self.error = error.userMessage }
    }

    // MARK: Editing and deleting

    func startEditing(_ message: Message) {
        guard message.kind == .text, !message.isDeleted, message.authorId == me else { return }
        replyingTo = nil
        if editing == nil { draftBeforeEdit = draft }
        editing = message
        draft = message.body ?? ""
    }

    func cancelEditing() {
        guard editing != nil else { return }
        editing = nil
        draft = draftBeforeEdit
        draftBeforeEdit = ""
    }

    private func saveEdit() async {
        guard let message = editing, !isSending else { return }
        let text = draft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return }
        guard text != message.body else { return cancelEditing() }
        isSending = true
        defer { isSending = false }
        do {
            replaceKeepingReceipt(try await api.send(API.Chat.edit(messageId: message.id, body: text)))
            cancelEditing()
        } catch {
            self.error = error.userMessage
        }
    }

    func delete(_ message: Message) async {
        do {
            _ = try await api.send(API.Chat.delete(messageId: message.id))
            var deleted = message
            deleted.deleted = true
            deleted.body = nil
            replaceKeepingReceipt(deleted)
            if editing?.id == message.id { cancelEditing() }
            if replyingTo?.id == message.id { cancelReplying() }
        } catch {
            self.error = error.userMessage
        }
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
            if message.isDeleted, editing?.id == message.id { cancelEditing() }
            if message.isDeleted, replyingTo?.id == message.id { cancelReplying() }
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
