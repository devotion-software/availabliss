import Foundation
import Testing

@testable import Availabliss

/// Emoji reactions: one per person per message. Matches web `reactions.test.ts` and Android `ChatReactionsTest`.
struct ReactionTests {
    private func r(_ emoji: String, _ userIds: String...) -> MessageReaction { MessageReaction(emoji: emoji, userIds: userIds) }

    @Test func addsANewEmojiAtTheEnd() {
        #expect(Reactions.setting([r("👍", "a")], me: "me", to: "❤️") == [r("👍", "a"), r("❤️", "me")])
    }

    @Test func joinsAnEmojiSomeoneAlreadyUsed() {
        #expect(Reactions.setting([r("👍", "a")], me: "me", to: "👍") == [r("👍", "a", "me")])
    }

    @Test func replacesYourEarlierReactionDroppingEmptyOnes() {
        #expect(Reactions.setting([r("😂", "me"), r("👍", "a")], me: "me", to: "👍") == [r("👍", "a", "me")])
    }

    @Test func nilTakesYoursBack() {
        #expect(Reactions.setting([r("👍", "a", "me")], me: "me", to: nil) == [r("👍", "a")])
    }

    @Test func pickingYourOwnAgainTakesItBack() {
        #expect(Reactions.toggled([r("❤️", "me")], me: "me", emoji: "❤️") == nil)
        #expect(Reactions.toggled([r("❤️", "me")], me: "me", emoji: "👍") == "👍")
        #expect(Reactions.mine(in: [r("❤️", "a"), r("👍", "me")], me: "me") == "👍")
        #expect(Reactions.mine(in: [], me: "me") == nil)
    }

    @Test func singleEmoji() {
        #expect(Reactions.single(" 👍🏽 ") == "👍🏽")
        #expect(Reactions.single("👨‍👩‍👧") == "👨‍👩‍👧")
        #expect(Reactions.single("🇬🇧") == "🇬🇧")
        #expect(Reactions.single("❤️") == "❤️")
        #expect(Reactions.single("👍👍") == nil)
        #expect(Reactions.single("a") == nil)
        #expect(Reactions.single("1") == nil)
        #expect(Reactions.single("") == nil)
    }

    @Test func namesPutYouFirst() {
        let names = ["a": "Ana", "b": "Sam", "c": "Kim", "d": "Jo"]
        let name = { (id: String) in names[id] ?? "Someone" }
        #expect(Reactions.names(["a"], me: "me", name: name) == "Ana")
        #expect(Reactions.names(["a", "me"], me: "me", name: name) == "You and Ana")
        #expect(Reactions.names(["a", "b", "me"], me: "me", name: name) == "You, Ana and Sam")
        #expect(Reactions.names(["a", "b", "c", "d"], me: "me", name: name) == "Ana, Sam and 2 others")
    }

    @Test func decodesReactionsAndCopesWithoutThem() throws {
        let json = """
            {"id":"m1","groupId":"g1","authorId":"a","kind":"text","body":"Hi","poll":null,"proposalId":null,"eventId":null,
             "createdAt":"2026-11-16T12:00:00.000Z","editedAt":null,"deleted":false,"receipt":null,"replyTo":null
            """
        let message = try JSONCoding.decoder.decode(Message.self, from: Data((json + #","reactions":[{"emoji":"👍","userIds":["a","b"]}]}"#).utf8))
        #expect(message.shownReactions == [r("👍", "a", "b")])
        #expect(message.canReact)
        let older = try JSONCoding.decoder.decode(Message.self, from: Data((json + "}").utf8))
        #expect(older.shownReactions.isEmpty)
    }

    @Test func noReactionsOnDeletedMessagesOrGroupUpdates() {
        var message = ChatModelTests.message("m1")
        message.reactions = [r("👍", "a")]
        #expect(message.shownReactions.count == 1)
        message.deleted = true
        #expect(message.shownReactions.isEmpty)
        #expect(!message.canReact)
        let system = ChatModelTests.message("s1", author: nil, kind: .system)
        #expect(!system.canReact)
    }
}
