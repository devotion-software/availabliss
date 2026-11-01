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
        let text = draft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty, !isSending else { return }
        isSending = true
        defer { isSending = false }
        draft = ""
        do {
            let message = try await api.send(API.Chat.send(groupId: groupId, SendMessageBody(body: text)))
            insert(message)
        } catch {
            draft = text
            self.error = error.userMessage
        }
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
    }

    private func update(poll: Poll) {
        guard let index = messages.firstIndex(where: { $0.poll?.id == poll.id }) else { return }
        messages[index].poll = poll
    }

    /// Loads the proposals and events that cards in `batch` refer to.
    private func hydrate(_ batch: [Message]) async {
        let api = self.api
        let proposalIds = Set(batch.compactMap(\.proposalId)).subtracting(proposals.keys)
        let fetchedProposals = await Self.fetchAll(proposalIds) { try await api.send(API.Proposals.get(id: $0)) }
        for proposal in fetchedProposals { proposals[proposal.id] = proposal }

        let eventIds = Set(batch.compactMap(\.eventId)).subtracting(events.keys)
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
