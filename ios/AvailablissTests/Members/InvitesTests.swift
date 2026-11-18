import Foundation
import Testing
@testable import Availabliss

/// Invite links and the new-group name. Ported from Android `InviteLinkTest` and `NewGroupNameTest`.
@MainActor
struct InvitesTests {
    @Test func aLinksLimitIsSentAsANumberNullForNoLimitAndLeftOutOtherwise() throws {
        #expect(try Fixtures.encode(CreateInviteBody(maxUses: .people(5))) == #"{"maxUses":5}"#)
        #expect(try Fixtures.encode(CreateInviteBody(maxUses: .unlimited)) == #"{"maxUses":null}"#)
        #expect(try Fixtures.encode(CreateInviteBody(emails: ["a@b.c"])) == #"{"emails":["a@b.c"]}"#)
        #expect(try Fixtures.encode(CreateInviteBody(usernames: ["sam"], userIds: ["u1"])) == #"{"userIds":["u1"],"usernames":["sam"]}"#)
    }

    private func link(maxUses: Int?, uses: Int?, email: String? = nil, username: String? = nil) -> GroupInvite {
        GroupInvite(
            id: "i", email: email, username: username, name: nil, url: URL(string: "https://availabliss.com/invite/abc")!,
            maxUses: maxUses, uses: uses, expiresAt: Fixtures.date("2026-12-01T00:00:00Z")
        )
    }

    @Test func linksDescribeHowManyPeopleCanStillJoin() {
        // An older server leaves out both: single-use.
        #expect(link(maxUses: nil, uses: nil).linkUsage == "Link for one person")
        #expect(link(maxUses: nil, uses: nil).usesLeft == 1)
        #expect(link(maxUses: 1, uses: 0).linkSummary == "One person can join with this link.")
        #expect(link(maxUses: 5, uses: 0).linkSummary == "5 people can join with this link.")
        #expect(link(maxUses: 5, uses: 2).linkSummary == "3 of 5 people can still join with this link.")
        #expect(link(maxUses: 5, uses: 2).linkUsage == "3 of 5 left")
        #expect(link(maxUses: 5, uses: 7).usesLeft == 0)
        #expect(link(maxUses: nil, uses: 4).linkSummary == "Anyone with this link can join.")
        #expect(link(maxUses: nil, uses: 4).linkUsage == "Unlimited · 4 joined")
        #expect(link(maxUses: nil, uses: 0).linkUsage == "Unlimited")
    }

    @Test func personalInvitesNameWhoTheyreFor() {
        #expect(link(maxUses: nil, uses: nil).isLink)
        #expect(link(maxUses: nil, uses: nil).recipient == nil)
        #expect(link(maxUses: nil, uses: nil, email: "sam@example.com").recipient == "sam@example.com")
        #expect(link(maxUses: nil, uses: nil, email: "sam@example.com", username: "sam").recipient == "@sam")
        #expect(!link(maxUses: nil, uses: nil, username: "sam").isLink)
    }

    @Test func linkLimitLabels() {
        #expect(LinkLimit.choices.map(\.label) == ["1", "5", "10", "25", "Unlimited"])
        #expect(LinkLimit.standard.accessibilityLabel == "One person")
        #expect(LinkLimit.people(5).accessibilityLabel == "5 people")
    }

    @Test func nextNameAddsATwo() {
        #expect(NewGroupSheet.nextName(after: "Book club") == "Book club 2")
    }

    @Test func nextNameCountsOnFromANumber() {
        #expect(NewGroupSheet.nextName(after: " Book club 2 ") == "Book club 3")
    }

    @Test func nextNameOnlyCountsOnFromAPlainSmallNumber() {
        #expect(NewGroupSheet.nextName(after: "Mum's 60th") == "Mum's 60th 2")
        #expect(NewGroupSheet.nextName(after: "Christmas 2026") == "Christmas 2026 2")
        #expect(NewGroupSheet.nextName(after: String(repeating: "a", count: 80)).count == 80)
    }

    @Test func memberNamesAreSentAsNullToGoBackToTheAccountName() throws {
        #expect(try Fixtures.encode(MemberNameBody(displayName: nil)) == #"{"displayName":null}"#)
        #expect(try Fixtures.encode(MemberNameBody(displayName: "Sammy")) == #"{"displayName":"Sammy"}"#)
    }
}
