import Foundation
import Testing
@testable import Availabliss

/// Editing and deleting in the chat, and live updates to messages. Ported from Android
/// `ChatEditDeleteTest` and `ChatReplyTest`, through `ChatViewModel` with a stubbed API.
@MainActor
struct ChatViewModelTests {
    private static let receipt = #"{"delivered":2,"seen":1,"recipients":2,"seenBy":["p"]}"#

    private static func json(_ id: String, author: String, body: String, extra: String = "") -> String {
        #"{"id":"\#(id)","groupId":"g1","authorId":"\#(author)","kind":"text","body":"\#(body)","createdAt":"2026-11-16T12:0\#(id):00.000Z"\#(extra)}"#
    }

    private static let page = """
    {"hasMore":false,"messages":[
      \(json("1", author: "me", body: "Hello", extra: #","receipt":\#(receipt)"#)),
      \(json("2", author: "p", body: "Hi back", extra: #","replyTo":{"id":"1","authorId":"me","authorName":"Sam","kind":"text","text":"Hello","deleted":false}"#)),
      \(json("3", author: "p", body: "Park?"))]}
    """

    /// A chat loaded as "me" through a stubbed API. `requests` records each request's method and path.
    private func loadedChat() async -> (ChatViewModel, Recorder) {
        let recorder = Recorder()
        let page = Self.page
        let api = Fixtures.api { request in
            let path = request.url?.path ?? ""
            recorder.add("\(request.httpMethod ?? "") \(path)")
            if request.httpMethod == "GET", path.hasSuffix("/groups/g1/messages") { return .json(page) }
            return .json("")
        }
        let chat = ChatViewModel(groupId: "g1", api: api)
        await chat.load(me: "me")
        return (chat, recorder)
    }

    private func live(_ kind: LiveEvent.Kind, groupId: String = "g1", payload: String) -> LiveEvent {
        LiveEvent(kind: kind, groupId: groupId, raw: Data(#"{"type":"\#(kind.rawValue)","groupId":"\#(groupId)","payload":\#(payload)}"#.utf8))
    }

    @Test func loadingMarksTheNewestMessageFromSomeoneElseSeen() async {
        let (chat, recorder) = await loadedChat()
        #expect(chat.messages.map(\.id) == ["1", "2", "3"])
        #expect(chat.error == nil)
        #expect(chat.latestOwnMessageId == "1")
        #expect(recorder.requests.contains { $0.hasPrefix("POST") && $0.hasSuffix("/groups/g1/messages/receipts") })
    }

    @Test func anEditReplacesTheMessageButKeepsItsReceiptAndRefreshesQuotes() async throws {
        let (chat, _) = await loadedChat()
        let receipt = try #require(chat.messages[0].receipt)
        await chat.apply(live(.messageUpdated, payload: Self.json("1", author: "me", body: "Edited text", extra: #","editedAt":"2026-11-16T12:05:00.000Z","receipt":null"#)))
        #expect(chat.messages[0].body == "Edited text")
        #expect(chat.messages[0].isEdited)
        #expect(chat.messages[0].receipt == receipt)
        #expect(chat.messages[1].replyTo?.text == "Edited text")
        #expect(chat.messages[1].replyTo?.authorName == "Sam")
        #expect(chat.messages[2].body == "Park?")
    }

    @Test func updatesForOtherMessagesOrGroupsChangeNothing() async {
        let (chat, _) = await loadedChat()
        let before = chat.messages
        await chat.apply(live(.messageUpdated, payload: Self.json("9", author: "me", body: "x")))
        await chat.apply(live(.messageUpdated, groupId: "g2", payload: #"{"id":"1","groupId":"g2","authorId":"me","kind":"text","body":"x","createdAt":"2026-11-16T12:00:00.000Z"}"#))
        #expect(chat.messages == before)
    }

    @Test func aDeleteFromSomeoneElseClearsTheMessageAndItsQuotes() async {
        let (chat, _) = await loadedChat()
        await chat.apply(live(.messageUpdated, payload: #"{"id":"1","groupId":"g1","authorId":"me","kind":"text","body":null,"deleted":true,"createdAt":"2026-11-16T12:01:00.000Z"}"#))
        #expect(chat.messages[0].isDeleted)
        #expect(chat.messages[0].body == nil)
        #expect(chat.messages[0].receipt != nil)
        #expect(chat.messages[1].replyTo?.deleted == true)
        #expect(chat.messages[1].replyTo?.text == nil)
    }

    @Test func othersMessagesCantBeEdited() async {
        let (chat, _) = await loadedChat()
        chat.draft = "Half a thought"
        chat.startEditing(chat.messages[2])
        #expect(chat.editing == nil)
        #expect(chat.draft == "Half a thought")
    }

    @Test
    func editingYourMessageAndCancellingPutsBackWhatYouWereTyping() async {
        let (chat, _) = await loadedChat()
        chat.draft = "Half a thought"
        chat.startEditing(chat.messages[0])
        #expect(chat.editing?.id == "1")
        #expect(chat.draft == "Hello")
        chat.cancelEditing()
        #expect(chat.editing == nil)
        #expect(chat.draft == "Half a thought")
    }

    @Test
    func deletingTheMessageYoureEditingStopsTheEdit() async {
        let (chat, _) = await loadedChat()
        chat.startEditing(chat.messages[0])
        await chat.delete(chat.messages[0])
        #expect(chat.editing == nil)
    }

    @Test func deletingYourMessageKeepsTheReceiptAndClearsQuotes() async throws {
        let (chat, recorder) = await loadedChat()
        let receipt = try #require(chat.messages[0].receipt)
        await chat.delete(chat.messages[0])
        #expect(recorder.requests.contains { $0.hasPrefix("DELETE") && $0.hasSuffix("/messages/1") })
        #expect(chat.error == nil)
        #expect(chat.messages[0].isDeleted)
        #expect(chat.messages[0].body == nil)
        #expect(chat.messages[0].receipt == receipt)
        #expect(chat.messages[1].replyTo?.deleted == true)
        // A deleted message can't be edited.
        chat.startEditing(chat.messages[0])
        #expect(chat.editing == nil)
    }

    @Test func replyingIsClearedWhenTheMessageIsDeleted() async {
        let (chat, _) = await loadedChat()
        chat.startReplying(to: chat.messages[2])
        #expect(chat.replyingTo?.id == "3")
        await chat.apply(live(.messageUpdated, payload: #"{"id":"3","groupId":"g1","authorId":"p","kind":"text","body":null,"deleted":true,"createdAt":"2026-11-16T12:03:00.000Z"}"#))
        #expect(chat.replyingTo == nil)
    }
}

/// Collects requests seen by a stub, from whichever thread URLSession calls it on.
final class Recorder: @unchecked Sendable {
    private let lock = NSLock()
    private var items: [String] = []

    func add(_ item: String) { lock.withLock { items.append(item) } }
    var requests: [String] { lock.withLock { items } }
}
