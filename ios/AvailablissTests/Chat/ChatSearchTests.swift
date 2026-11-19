import Foundation
import Testing

@testable import Availabliss

/// Search results' lines. Same cases as web's `searchSnippet` tests and Android's `ChatSearchTest`.
struct ChatSearchTests {
    private typealias Snippet = Message.SearchSnippet
    private func message(_ body: String?, kind: Message.Kind = .text, attachments: [Attachment]? = nil, location: MessageLocation? = nil) -> Message {
        var m = ChatModelTests.message("1", kind: kind, body: body, attachments: attachments, location: location)
        m.body = body
        return m
    }

    @Test func picksOutTheMatchKeepingItsCase() {
        #expect(message("Shall we try the Crown?").searchSnippet(for: "crown") == Snippet(before: "Shall we try the ", match: "Crown", after: "?"))
    }

    @Test func aLongMessageIsCutAtAWordBeforeTheMatch() {
        let s = message("We could go somewhere near the station after work on Friday, maybe the Crown").searchSnippet(for: "crown")
        #expect(s.before == "…work on Friday, maybe the ")
        #expect(s.match == "Crown")
    }

    @Test func pollOptionsPlacesAndFileNames() {
        var poll = message(nil, kind: .poll)
        poll.poll = Poll(id: "p", question: "Which pub?", multi: false, closed: false, options: [.init(id: "o", label: "Red Lion", voterIds: [])])
        #expect(poll.searchSnippet(for: "lion") == Snippet(before: "Which pub? · Red ", match: "Lion", after: ""))
        let place = message("📍 The Crown", location: MessageLocation(lat: 1, lng: 2, name: "The Crown", address: "1 High St"))
        #expect(place.searchSnippet(for: "high").match == "High")
        let file = message("📎 Crown menu.pdf", attachments: [ChatModelTests.attachment("f", .file, name: "Crown menu.pdf")])
        #expect(file.searchSnippet(for: "menu") == Snippet(before: "Crown ", match: "menu", after: ".pdf"))
    }

    @Test func noMatchShowsTheTextFromTheStart() {
        #expect(message("hello there").searchSnippet(for: "zzz") == Snippet(before: "hello there", match: "", after: ""))
    }
}
