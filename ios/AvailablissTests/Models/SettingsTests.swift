import Foundation
import Testing

@testable import Availabliss

/// Notification settings, linked sign-ins and profile patches. Ported from Android
/// `NotificationSettingsTest` and `LinkAccountTest`.
struct SettingsTests {
    let now = Fixtures.date("2026-11-18T21:15:42.123Z")

    @Test func mutesHoldForGoodOrUntilAFutureInstant() {
        let settings = NotificationSettings(mutes: [
            GroupMute(groupId: "forever", until: nil),
            GroupMute(groupId: "later", until: Fixtures.date("2026-11-19T05:00:00.000Z")),
            GroupMute(groupId: "ranOut", until: Fixtures.date("2026-11-18T20:00:00.000Z")),
        ])
        #expect(settings.mutes[0].isActive(at: now))
        #expect(settings.mutes[1].isActive(at: now))
        #expect(!settings.mutes[2].isActive(at: now))
        #expect(settings.mute(for: "forever") != nil)
        #expect(settings.mute(for: "other") == nil)
    }

    @Test func patchesSendOnlyWhatChanged() throws {
        #expect(try Fixtures.encode(NotificationSettingsPatch(off: [.chat, .nudges])) == #"{"off":["chat","nudges"]}"#)
        #expect(try Fixtures.encode(NotificationSettingsPatch(quietHours: .some(nil))) == #"{"quietHours":null}"#)
        #expect(try Fixtures.encode(NotificationSettingsPatch(quietHours: .some(.default))) == #"{"quietHours":{"from":"22:00","to":"07:00"}}"#)
        #expect(try Fixtures.encode(NotificationSettingsPatch()) == "{}")
    }

    @Test func mutingSendsUntilEvenWhenItsForGood() throws {
        #expect(try Fixtures.encode(MuteGroupBody(until: nil)) == #"{"until":null}"#)
        #expect(try Fixtures.encode(MuteGroupBody(until: Fixtures.date("2026-11-19T05:15:42Z"))) == #"{"until":"2026-11-19T05:15:42.000Z"}"#)
    }

    @Test func decodesLinkedAccounts() throws {
        let accounts = try Fixtures.decode(
            LinkedAccounts.self, #"{"linked":[{"provider":"apple","linkedAt":"2026-11-01T10:00:00.000Z"}],"available":["apple","google"]}"#)
        #expect(accounts.isLinked(.apple))
        #expect(!accounts.isLinked(.google))
        #expect(accounts.available == [.apple, .google])
        #expect(LinkProvider.google.label == "Google")
    }

    @Test func linkRequestsLeaveOutNonceAndMergeUnlessSet() throws {
        #expect(try Fixtures.encode(LinkAccountBody(provider: .google, idToken: "t", nonce: nil, merge: nil)) == #"{"idToken":"t","provider":"google"}"#)
        #expect(
            try Fixtures.encode(LinkAccountBody(provider: .apple, idToken: "t", nonce: "n", merge: true))
                == #"{"idToken":"t","merge":true,"nonce":"n","provider":"apple"}"#)
    }

    @Test func aRegionUpdateClearsWhatItDoesntSet() throws {
        let region = try Fixtures.encode(UserPatch.region(country: "IE", subdivision: nil, schoolAuthority: nil, timezone: "Europe/Dublin"))
        #expect(region == #"{"country":"IE","schoolAuthority":null,"subdivision":null,"timezone":"Europe/Dublin"}"#)
        #expect(try Fixtures.encode(UserPatch(country: "IE")) == #"{"country":"IE"}"#)
        #expect(try Fixtures.encode(UserPatch(name: "Sam", username: "sam")) == #"{"name":"Sam","username":"sam"}"#)
    }

    @Test func accountNamesFallBackToTheEmail() throws {
        let user = try Fixtures.decode(
            User.self, #"{"id":"u","name":" ","email":"s@example.com","themeMode":"light","colorTheme":"grove","timezone":"Europe/London","onboarded":false}"#)
        #expect(user.accountName == "s@example.com")
        #expect(user.colorTheme.label == "Grove")
    }
}
