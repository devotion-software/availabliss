import Foundation
import Testing

@testable import Availabliss

/// Swift Testing has an `Attachment` too.
typealias Attachment = Availabliss.Attachment

/// Messages, quotes, attachments, polls and places. Ported from Android `ChatAttachmentsTest`,
/// `ChatReplyTest`, `ChatEditDeleteTest` (decoding) and `ChatLocationTest`.
struct ChatModelTests {
    static func message(
        _ id: String, author: String? = "me", kind: Message.Kind = .text, body: String? = nil,
        attachments: [Attachment]? = nil, caption: String? = nil, deleted: Bool? = nil, location: MessageLocation? = nil
    ) -> Message {
        Message(
            id: id, groupId: "g1", authorId: author, kind: kind, body: body ?? "Hello \(id)", poll: nil, proposalId: nil, eventId: nil,
            createdAt: Fixtures.date("2026-11-16T12:00:00Z"), editedAt: nil, deleted: deleted, receipt: nil, replyTo: nil,
            attachments: attachments, caption: caption, location: location
        )
    }

    static func attachment(_ id: String, _ kind: Attachment.Kind, name: String = "IMG_1.jpg", width: Int? = 4032, height: Int? = 3024) -> Attachment {
        Attachment(id: id, kind: kind, name: name, mime: "image/jpeg", size: 120_000, width: width, height: height, url: "https://x/a/\(id)", thumbnailUrl: nil)
    }

    private let photo = attachment("a1", .image)
    private let gif = attachment("g1", .gif, name: "dance.gif")
    private let file = attachment("f1", .file, name: "Plan.pdf", width: nil, height: nil)

    @Test func oldMessagesWithoutTheNewFieldsDecode() throws {
        let m = try Fixtures.decode(Message.self, #"{"id":"1","groupId":"g","authorId":"me","kind":"text","body":"hi","createdAt":"2026-11-16T12:00:00.000Z"}"#)
        #expect(m.editedAt == nil)
        #expect(!m.isDeleted)
        #expect(!m.isEdited)
        #expect(m.files.isEmpty)
        #expect(m.text == "hi")
    }

    @Test func anEditedOrDeletedMessageDecodes() throws {
        let m = try Fixtures.decode(
            Message.self,
            """
            {"id":"1","groupId":"g","authorId":"me","kind":"text","body":null,"poll":null,"proposalId":null,"eventId":null,
             "createdAt":"2026-11-16T12:00:00.000Z","editedAt":"2026-11-16T12:05:00.000Z","deleted":true,"receipt":null}
            """)
        #expect(m.isDeleted)
        #expect(m.body == nil)
        #expect(m.editedAt == Fixtures.date("2026-11-16T12:05:00Z"))
        // A deleted message never reads as edited.
        #expect(!m.isEdited)
        #expect(!m.canReply)
    }

    @Test func attachmentsShowTheCaptionNotTheFallbackBody() {
        let m = Self.message("1", body: "📷 Photo", attachments: [photo], caption: "Sunset")
        #expect(m.text == "Sunset")
        #expect(m.files == [photo])
        #expect(Self.message("2", body: "📷 Photo", attachments: [photo]).text == nil)
        #expect(Self.message("3", attachments: [photo], deleted: true).files.isEmpty)
    }

    @Test func summaries() {
        #expect(Attachment.summary([]) == "")
        #expect(Attachment.summary([photo]) == "Photo")
        #expect(Attachment.summary([Self.attachment("1", .image), Self.attachment("2", .image), Self.attachment("3", .image)]) == "3 photos")
        #expect(Attachment.summary([gif]) == "GIF")
        #expect(Attachment.summary([photo, gif]) == "2 photos")
        #expect(Attachment.summary([file]) == "Plan.pdf")
        #expect(Attachment.summary([file, Self.attachment("f2", .file, name: "b.pdf")]) == "2 files")
        #expect(Attachment.summary([photo, file]) == "2 attachments")
    }

    @Test func unknownAttachmentKindsShowAsFiles() throws {
        let a = try Fixtures.decode(
            Attachment.self, #"{"id":"x","kind":"hologram","name":"x.holo","mime":"application/x","size":1,"width":null,"height":null,"url":"u","thumbnailUrl":null}"#)
        #expect(a.kind == .file)
        #expect(!a.isVisual)
        #expect(a.aspectRatio == nil)
        #expect(abs((photo.aspectRatio ?? 0) - 4.0 / 3.0) < 0.0001)
    }

    @Test func walletPassesDecodeAndAreSummarised() throws {
        let a = try Fixtures.decode(
            Attachment.self,
            """
            {"id":"w","kind":"pass","name":"Gig.pkpass","mime":"application/vnd.apple.pkpass","size":1882,"width":null,"height":null,"url":"u","thumbnailUrl":"logo",
             "pass":{"style":"eventTicket","organizationName":"Roundhouse","description":"Concert ticket","logoText":null,"passTypeIdentifier":"p","serialNumber":"s",
             "relevantDate":"2026-12-02T00:30:00.000Z","expirationDate":null,"voided":false,"eventName":"Winter Gig","venue":"Roundhouse, London",
             "fields":[{"label":"Event","value":"Winter Gig","date":false},{"label":"Seat","value":"12A","date":false}],
             "backgroundColor":"#201840","foregroundColor":"#ffffff","labelColor":null,"count":1,"future":true}}
            """)
        #expect(a.kind == .pass)
        #expect(!a.isVisual)
        #expect(a.pass?.eventName == "Winter Gig")
        #expect(Attachment.summary([a]) == "Concert ticket")
        let second = Attachment(id: "x", kind: a.kind, name: a.name, mime: a.mime, size: a.size, width: nil, height: nil, url: a.url, thumbnailUrl: nil, pass: a.pass)
        #expect(Attachment.summary([a, second]) == "2 passes")
        #expect(AttachmentFormat.icon(mime: a.mime, name: a.name) == "wallet.pass")
    }

    @Test func walletPassTitles() throws {
        func pass(style: String, description: String = "", count: Int = 1) -> WalletPass {
            WalletPass(
                style: style, organizationName: "o", description: description, logoText: nil, passTypeIdentifier: "p", serialNumber: "s",
                relevantDate: nil, expirationDate: nil, voided: false, eventName: nil, venue: nil, fields: [],
                backgroundColor: nil, foregroundColor: nil, labelColor: nil, count: count
            )
        }
        #expect(pass(style: "boardingPass").title == "Boarding pass")
        #expect(pass(style: "eventTicket").title == "Ticket")
        #expect(pass(style: "storeCard").title == "Card")
        #expect(pass(style: "generic", description: "Gym").title == "Gym")
        #expect(pass(style: "boardingPass", count: 2).title == "2 boarding passes")
        #expect(pass(style: "eventTicket", description: "Gig", count: 3).title == "3 tickets")
        #expect(pass(style: "coupon", count: 2).title == "2 passes")
    }

    @Test func fileIcons() {
        #expect(AttachmentFormat.icon(mime: "application/pdf", name: "Plan.PDF") == "doc.richtext")
        #expect(AttachmentFormat.icon(mime: "", name: "sheet.xlsx") == "tablecells")
        #expect(AttachmentFormat.icon(mime: "application/zip", name: "photos.zip") == "doc.zipper")
        #expect(AttachmentFormat.icon(mime: "audio/mp4", name: "memo.m4a") == "waveform")
        #expect(AttachmentFormat.icon(mime: "video/mp4", name: "clip") == "film")
        #expect(AttachmentFormat.icon(mime: "text/calendar", name: "invite.ics") == "calendar")
        #expect(AttachmentFormat.icon(mime: "application/octet-stream", name: "thing.bin") == "doc.text")
    }

    @Test func repliesQuoteOneLineOfTheOriginal() {
        let original = Self.message("1", author: "p", body: "Shall we\n  go   to the park?")
        let quote = original.asReplyPreview(authorName: "Priya")
        #expect(quote.text == "Shall we go to the park?")
        #expect(quote.authorName == "Priya")
        #expect(!quote.deleted)
        #expect(quote.snippet == "Shall we go to the park?")
        // Photos are quoted by what they are, or their caption.
        #expect(Self.message("2", body: "📷 Photo", attachments: [photo]).asReplyPreview(authorName: nil).text == "Photo")
        #expect(Self.message("3", body: "📷 Photo", attachments: [photo], caption: "Sunset").asReplyPreview(authorName: nil).text == "Sunset")
        let deleted = Self.message("4", deleted: true).asReplyPreview(authorName: nil)
        #expect(deleted.text == nil)
        #expect(deleted.snippet == "Deleted message")
    }

    @Test func quoteSnippetsByKind() {
        #expect(Message.ReplyPreview(id: "1", authorId: "p", authorName: nil, kind: .poll, text: "Which park?", deleted: false).snippet == "Poll: Which park?")
        #expect(Message.ReplyPreview(id: "1", authorId: "p", authorName: nil, kind: .proposal, text: nil, deleted: false).snippet == "Proposal")
        #expect(Message.ReplyPreview(id: "1", authorId: "p", authorName: nil, kind: .event, text: nil, deleted: false).snippet == "Event")
        #expect(Message.ReplyPreview(id: "1", authorId: "p", authorName: nil, kind: .text, text: nil, deleted: false).snippet == "")
    }

    @Test func onlyTextAndPollsThatAreStillThereCanBeRepliedTo() {
        #expect(Self.message("1").canReply)
        #expect(Self.message("2", kind: .poll).canReply)
        #expect(!Self.message("3", kind: .system).canReply)
        #expect(!Self.message("4", kind: .proposal).canReply)
        #expect(!Self.message("5", deleted: true).canReply)
    }

    @Test func quotedAuthorsAreNamedAsTheChatNamesThem() {
        let group = Fixtures.group(Fixtures.people)
        #expect(ReplyNames.name(of: "me", fallback: "Sam", group: group, me: "me") == "You")
        #expect(ReplyNames.name(of: "p", fallback: nil, group: group, me: "me") == "Priya Shah")
        #expect(ReplyNames.name(of: "left", fallback: "Old Name", group: group, me: "me") == "Old Name")
        #expect(ReplyNames.name(of: nil, fallback: nil, group: group, me: "me") == "Someone")
    }

    // MARK: Places

    private let dishoom = MessageLocation(lat: 51.5245453, lng: -0.0768549, name: "Dishoom Shoreditch", address: "7 Boundary St", accuracy: nil)

    @Test func aPlaceReplacesTheTextAndIsQuotedByName() throws {
        let m = Self.message("1", body: "📍 Dishoom Shoreditch", location: dishoom)
        #expect(m.text == nil)
        #expect(m.place == dishoom)
        #expect(m.asReplyPreview(authorName: nil).text == "📍 Dishoom Shoreditch")
        #expect(MessageLocation(lat: 0, lng: 0).summary == "📍 Location")
        #expect(Self.message("2", deleted: true, location: dishoom).place == nil)
        let decoded = try Fixtures.decode(
            Message.self,
            """
            {"id":"1","groupId":"g","authorId":"me","kind":"text","body":"📍 Location","createdAt":"2026-11-16T12:00:00.000Z",
             "location":{"lat":51.5,"lng":-0.07,"accuracy":12.5}}
            """)
        #expect(decoded.place?.accuracy == 12.5)
        #expect(decoded.place?.name == nil)
    }

    @Test func openInLinksCarryTheCoordinates() {
        #expect(LocationLinks.what3words(dishoom).absoluteString == "https://what3words.com/51.524545,-0.076855")
        let apple = URLComponents(url: LocationLinks.appleMaps(dishoom), resolvingAgainstBaseURL: false)
        #expect(apple?.host == "maps.apple.com")
        #expect(apple?.queryItems?.first { $0.name == "ll" }?.value == "51.524545,-0.076855")
        #expect(apple?.queryItems?.first { $0.name == "q" }?.value == "Dishoom Shoreditch")
    }

    @Test func sendingAPlaceSendsOnlyWhatItHas() throws {
        #expect(try Fixtures.encode(SendMessageBody(location: MessageLocation(lat: 1.5, lng: 2))) == #"{"location":{"lat":1.5,"lng":2}}"#)
        #expect(try Fixtures.encode(SendMessageBody(body: "Hi", replyToId: "m1")) == #"{"body":"Hi","replyToId":"m1"}"#)
    }

    // MARK: Polls

    private func poll(_ votes: [Int], slots: Bool = false) -> Poll {
        Poll(
            id: "q", question: "When?", multi: true, closed: false,
            options: votes.enumerated().map { i, count in
                Poll.Option(
                    id: "o\(i)", label: "Option \(i)", voterIds: (0..<count).map { "u\($0)" },
                    slot: slots ? PollSlot(start: Fixtures.date("2026-11-2\(i)T18:00:00Z"), end: Fixtures.date("2026-11-2\(i)T22:00:00Z"), allDay: false) : nil
                )
            })
    }

    @Test func theLeaderHasTheMostVotesAndTheEarliestWinsATie() {
        #expect(poll([0, 0, 0]).leadingOption == nil)
        #expect(poll([1, 3, 2]).leadingOption?.id == "o1")
        #expect(poll([2, 1, 2]).leadingOption?.id == "o0")
        #expect(poll([1, 3, 2]).totalVotes == 6)
    }

    @Test func datePollsHaveSlots() throws {
        #expect(!poll([1, 2]).isDatePoll)
        #expect(poll([1, 2], slots: true).isDatePoll)
        let option = try Fixtures.decode(
            Poll.Option.self, #"{"id":"o","label":"Fri","voterIds":[],"slot":{"start":"2026-11-20T18:00:00Z","end":"2026-11-20T22:00:00Z"}}"#)
        // Older servers leave out allDay.
        #expect(option.slot?.allDay == false)
        #expect(option.slot?.draft.start == Fixtures.date("2026-11-20T18:00:00Z"))
    }
}
