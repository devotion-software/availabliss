import Foundation
import Testing
@testable import Availabliss

/// Links and push notifications open the right screen. The iOS routes, compared with Android `PushRoutingTest`.
struct DeepLinkTests {
    private func link(_ string: String, host: String? = "availabliss.com") -> DeepLink? {
        DeepLink(url: URL(string: string)!, associatedHost: host)
    }

    @Test func appSchemeLinks() {
        #expect(link("availabliss://invite/abc") == .invite(token: "abc"))
        #expect(link("availabliss://calendars?complete=xyz") == .calendars(complete: "xyz", error: nil))
        #expect(link("availabliss://calendars?error=denied") == .calendars(complete: nil, error: "denied"))
        #expect(link("availabliss://app/groups/g1") == .group(id: "g1"))
        #expect(link("availabliss://nowhere") == nil)
    }

    @Test func universalLinksOnlyFromOurHost() {
        #expect(link("https://availabliss.com/invite/abc") == .invite(token: "abc"))
        #expect(link("https://availabliss.com/app/groups/g1") == .group(id: "g1"))
        #expect(link("https://availabliss.com/app/groups/g1/chat") == .groupChat(id: "g1"))
        #expect(link("https://availabliss.com/app/proposals/p1") == .proposal(id: "p1"))
        #expect(link("https://example.com/invite/abc") == nil)
        #expect(link("https://availabliss.com/app/groups") == nil)
        // Without a host to check against, the path is enough.
        #expect(link("https://staging.example.com/invite/abc", host: nil) == .invite(token: "abc"))
    }

    @Test func pushesOpenTheProposalWhenThereIsOneElseTheGroup() {
        #expect(DeepLink(data: ["kind": "reminder", "groupId": "g1", "eventId": "e1", "proposalId": "p1", "reminder": "day"]) == .proposal(id: "p1"))
        #expect(DeepLink(data: ["kind": "reminder", "groupId": "g1", "eventId": "e1", "reminder": "hour"]) == .group(id: "g1"))
        #expect(DeepLink(data: ["kind": "poll.closed", "groupId": "g1", "proposalId": "p1"]) == .proposal(id: "p1"))
        #expect(DeepLink(data: ["kind": "role.changed", "groupId": "g1"]) == .group(id: "g1"))
        #expect(DeepLink(data: ["kind": "proposal.created", "groupId": "g1", "proposalId": ""]) == .group(id: "g1"))
    }

    @Test func messagesOpenTheChat() {
        #expect(DeepLink(data: ["kind": "message", "groupId": "g1"]) == .groupChat(id: "g1"))
        #expect(DeepLink(data: ["kind": "poll.closed", "groupId": "g1", "messageId": "m1"]) == .groupChat(id: "g1"))
    }

    @Test func invitesOpenTheInviteAndPushesWithNowhereToGoOpenNothing() {
        #expect(DeepLink(data: ["kind": "invite.received", "token": "abc", "groupId": "g1"]) == .invite(token: "abc"))
        #expect(DeepLink(data: ["kind": "invite.received", "token": ""]) == nil)
        #expect(DeepLink(data: ["kind": "nudge"]) == nil)
        #expect(DeepLink(data: ["kind": "reminder"]) == nil)
    }

    @Test func pushDataSkipsTheAPSPayloadAndNonStrings() {
        let data = DeepLink.data(fromPush: ["aps": ["alert": "Hi"], "kind": "message", "groupId": "g1", "count": 3])
        #expect(data == ["kind": "message", "groupId": "g1"])
    }
}
