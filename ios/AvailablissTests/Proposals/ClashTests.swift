import Testing

@testable import Availabliss

/// Ported from web `proposals/clash.test.ts` and Android `ClashTest`.
struct ClashTests {
    let group = Fixtures.group(Fixtures.people)

    @Test func text() {
        #expect(Clash.text([], group: group, me: "me") == nil)
        #expect(Clash.text(["p"], group: group, me: "me") == "Priya is busy then")
        #expect(Clash.text(["me"], group: group, me: "me") == "You're busy then")
        #expect(Clash.text(["me", "p"], group: group, me: "me") == "You and Priya are busy then")
        #expect(Clash.text(["p", "a", "j", "t", "l"], group: group, me: "me") == "Priya, Alex, Jo and 2 others are busy then")
        #expect(Clash.text(["gone"], group: group, me: "me") == "Someone is busy then")
    }

    @Test func textWithoutAGroupOrSignedInUser() {
        #expect(Clash.text(["p"], group: nil, me: nil) == "Someone is busy then")
        #expect(Clash.text(["me", "p"], group: group, me: nil) == "Sam and Priya are busy then")
    }

    @Test func short() {
        #expect(Clash.short([], group: group) == nil)
        #expect(Clash.short(["p"], group: group) == "Priya busy then")
        #expect(Clash.short(["p", "a"], group: group) == "2 busy then")
    }

    @Test func cantMake() {
        #expect(Clash.cantMake(["p"], group: group, me: "me") == "Priya can\u{2019}t make it")
        #expect(Clash.cantMake(["me"], group: group, me: "me") == "You can\u{2019}t make it")
        #expect(Clash.cantMake(["me", "p"], group: group, me: "me") == "You and Priya can\u{2019}t make it")
        #expect(Clash.cantMake(["p", "t"], group: group, me: "me") == "Priya and Tom can\u{2019}t make it")
    }

    @Test func fairFor() {
        #expect(Clash.fairFor([], group: group, me: "me") == nil)
        #expect(Clash.fairFor(["p"], group: group, me: "me") == "Fair for Priya, who missed out last time")
        #expect(Clash.fairFor(["me", "p"], group: group, me: "me") == "Fair for you and Priya, who missed out last time")
    }

    @Test func listNames() {
        #expect(Clash.listNames([]) == "")
        #expect(Clash.listNames(["Priya"]) == "Priya")
        #expect(Clash.listNames(["Priya", "Alex"]) == "Priya and Alex")
        #expect(Clash.listNames(["Priya", "Alex", "Jo"]) == "Priya, Alex and Jo")
        #expect(Clash.listNames(["Priya", "Alex", "Jo", "Tom"]) == "Priya, Alex, Jo and 1 other")
        #expect(Clash.listNames(["Priya", "Alex", "Jo", "Tom", "Lee"], max: 2) == "Priya, Alex and 3 others")
    }
}
