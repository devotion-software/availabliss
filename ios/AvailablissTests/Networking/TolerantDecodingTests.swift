import Foundation
import Testing

@testable import Availabliss

/// Values a newer server might send decode to a safe default instead of failing the whole response.
/// Ported from Android `LenientEnumTest`, `ApiErrorTest` (group status) and `BlockDraftTest` (repeats).
struct TolerantDecodingTests {
    @Test func notificationKindsDecodeByWireValueAndUnknownKindsFallBack() throws {
        let body = """
            {"unread":2,"notifications":[
              {"id":"1","groupId":"g","kind":"proposal.confirmed","title":"t","body":"b","data":{},"createdAt":"2026-11-03T10:00:00Z"},
              {"id":"2","groupId":null,"kind":"poll.closed","title":"t","body":"b","data":{},"createdAt":"2026-11-03T10:00:00Z"},
              {"id":"3","kind":"invite.received","title":"t","body":"b","data":{"token":"abc"},"createdAt":"2026-11-03T10:00:00Z"},
              {"id":"4","kind":"reminder","title":"t","body":"b","data":{"proposalId":"p","reminder":"day"},"createdAt":"2026-11-03T10:00:00.000Z"}]}
            """
        let response = try Fixtures.decode(NotificationsResponse.self, body)
        #expect(response.notifications.map(\.kind) == [.proposalConfirmed, .unknown, .inviteReceived, .reminder])
        #expect(response.notifications[2].data["token"] == "abc")
        #expect(response.unread == 2)
    }

    @Test func planRemindersDecodeByWireValueAndFallBackToBoth() throws {
        func decode(_ extra: String) throws -> EventReminders? {
            try Fixtures.decode(
                User.self,
                """
                {"id":"u","name":"Sam","email":"s@example.com","themeMode":"system","colorTheme":"dawn","timezone":"Europe/London","onboarded":true\(extra)}
                """
            ).eventReminders
        }
        // Missing: an older server; the app treats nil as `.both`.
        #expect(try decode("") == nil)
        #expect(try decode(#","eventReminders":"hour""#) == .hour)
        #expect(try decode(#","eventReminders":"off""#) == .off)
        #expect(try decode(#","eventReminders":"week""#) == .both)
        #expect(try Fixtures.encode(UserPatch(eventReminders: .day)) == #"{"eventReminders":"day"}"#)
    }

    @Test func userEnumsFallBack() throws {
        let user = try Fixtures.decode(
            User.self,
            """
            {"id":"u","name":"Sam Taylor","email":"s@example.com","themeMode":"sepia","colorTheme":"neon","timezone":"Europe/London","onboarded":true}
            """)
        #expect(user.themeMode == .system)
        #expect(user.colorTheme == .dawn)
        #expect(user.firstName == "Sam")
        #expect(try Fixtures.decode(Role.self, #""superuser""#) == .member)
        #expect(try Fixtures.decode(Role.self, #""owner""#).isAdmin)
    }

    @Test func proposalRepliesDecodeByWireValueAndUnknownRepliesArePending() throws {
        let body = """
            {"id":"p","groupId":"g","start":"2026-11-20T18:00:00Z","end":"2026-11-20T21:00:00Z","allDay":false,
             "status":"open","createdBy":"a","createdAt":"2026-11-18T10:00:00Z","responses":[
              {"userId":"a","response":"accepted","updatedAt":"2026-11-18T10:00:00Z"},
              {"userId":"b","response":"maybe","updatedAt":"2026-11-18T10:00:00Z"},
              {"userId":"c","response":"declined","updatedAt":"2026-11-18T10:00:00Z"},
              {"userId":"d","response":"pending","updatedAt":"2026-11-18T10:00:00Z"},
              {"userId":"e","response":"tentative","updatedAt":"2026-11-18T10:00:00Z"}]}
            """
        let proposal = try Fixtures.decode(Proposal.self, body)
        #expect(proposal.responses.map(\.response) == [.accepted, .maybe, .declined, .pending, .pending])
        // Fields older servers leave out.
        #expect(proposal.busyMemberIds.isEmpty)
        #expect(!proposal.needsReview)
        #expect(try Fixtures.encode(RespondBody(response: .maybe)) == #"{"response":"maybe"}"#)
    }

    @Test func calendarProvidersDecodeByWireValue() throws {
        let body = """
            {"connections":[
              {"id":"a","provider":"caldav","label":"iCloud","status":"ok","calendars":[]},
              {"id":"b","provider":"microsoft","label":"Work","status":"reauth_required","calendars":[{"id":"c","name":"Cal","busy":true}]}]}
            """
        let connections = try Fixtures.decode(ConnectionsResponse.self, body).connections
        #expect(connections.map(\.provider) == [.caldav, .microsoft])
        #expect(connections[1].status == .reauthRequired)
        #expect(CalendarProvider.microsoft.displayName == "Outlook")
    }

    @Test func groupStatusDecodesByKindWithAFallback() throws {
        func status(_ json: String) throws -> GroupStatus {
            try Fixtures.decode(
                GroupSummary.self,
                """
                {"id":"g","name":"G","color":"t2","monogram":"G","role":"admin","memberCount":3,"syncedCount":2,
                 "requirementSummary":"x","status":\(json),"unreadCount":0,"memberPreview":[]}
                """
            ).status
        }
        #expect(try status(#"{"kind":"matches","count":2,"noun":"weekend"}"#) == .matches(count: 2, noun: .weekend))
        #expect(try status(#"{"kind":"matches","count":2,"noun":"fortnight"}"#) == .matches(count: 2, noun: .slot))
        #expect(try status(#"{"kind":"needs_reply","proposalId":"p1"}"#) == .needsReply(proposalId: "p1"))
        #expect(try status(#"{"kind":"something_new"}"#) == .searching)
        #expect(SlotNoun.weekend.counted(1) == "1 weekend")
        #expect(SlotNoun.evening.counted(3) == "3 evenings")
    }

    @Test func blockRepeatsDecodeNewValuesAndUnknownOnesSafely() throws {
        func decode(_ repeat: String) throws -> ManualBlock.Repeat {
            try Fixtures.decode(
                ManualBlock.self,
                """
                {"id":"b","start":"2026-11-16T07:00:00Z","end":"2026-11-16T08:00:00Z","allDay":false,"repeat":"\(`repeat`)"}
                """
            ).repeat
        }
        #expect(try decode("monthlyWeekday") == .monthlyWeekday)
        #expect(try decode("fortnightly") == .fortnightly)
        #expect(try decode("hourly") == .unknown)
        #expect(!ManualBlock.Repeat.allCases.contains(.unknown))
        #expect(ManualBlock.Repeat.allCases.map(\.rawValue) == ["none", "daily", "weekdays", "weekly", "fortnightly", "monthly", "monthlyWeekday", "yearly"])
    }

    @Test func newBlocksSendTheirOptionalFieldsAsNull() throws {
        let block = NewManualBlock(
            start: Fixtures.date("2026-11-16T07:00:00Z"), end: Fixtures.date("2026-11-16T08:00:00Z"), allDay: false, repeat: .weekly, until: nil, note: nil)
        #expect(
            try Fixtures.encode(block)
                == #"{"allDay":false,"end":"2026-11-16T08:00:00.000Z","note":null,"repeat":"weekly","start":"2026-11-16T07:00:00.000Z","until":null}"#)
    }

    @Test func groupRhythmFromANewerServerReadsAsWeekly() throws {
        #expect(try Fixtures.decode(Group.Rhythm.self, #"{"every":"two-weeks","prompt":true}"#) == Group.Rhythm(every: .twoWeeks, prompt: true))
        #expect(try Fixtures.decode(Group.Rhythm.self, #"{"every":"quarter","prompt":false}"#).every == .week)
        #expect(Group.Rhythm.Every.twoWeeks.label == "Every two weeks")
    }

    @Test func groupRhythmIsSentClearedOrLeftOut() throws {
        #expect(try Fixtures.encode(UpdateGroupBody(rhythm: .clear)) == #"{"rhythm":null}"#)
        #expect(try Fixtures.encode(UpdateGroupBody(name: "Walks")) == #"{"name":"Walks"}"#)
        #expect(try Fixtures.encode(UpdateGroupBody(rhythm: .set(.init(every: .month, prompt: true)))) == #"{"rhythm":{"every":"month","prompt":true}}"#)
    }

    @Test func notificationSettingsSkipCategoriesThisVersionDoesntKnow() throws {
        let settings = try Fixtures.decode(NotificationSettings.self, #"{"off":["nudges","digest"],"quietHours":null,"mutes":[]}"#)
        #expect(settings.off == [.nudges])
        #expect(settings.quietHours == nil)
        #expect(try Fixtures.decode(NotificationSettings.self, "{}") == NotificationSettings())
    }

    @Test func groupPhotosAndEventCoversAreOptional() throws {
        let summary = """
            {"id":"g","name":"G","color":"t2","monogram":"G","role":"member","memberCount":1,"syncedCount":1,
             "requirementSummary":"x","status":{"kind":"searching"},"unreadCount":0,"memberPreview":[]
            """
        #expect(try Fixtures.decode(GroupSummary.self, summary + #","image":"https://x/g.jpg"}"#).image == "https://x/g.jpg")
        #expect(try Fixtures.decode(GroupSummary.self, summary + #","image":null}"#).image == nil)
        #expect(try Fixtures.decode(GroupSummary.self, summary + "}").image == nil)
    }

    @Test func anUnknownCalendarProviderDecodesInsteadOfFailingTheList() throws {
        #expect(try Fixtures.decode(CalendarProvider.self, #""fastmail""#) == .unknown)
        #expect(try Fixtures.decode(CalendarProvider.self, #""google""#) == .google)
        #expect(CalendarProvider.unknown.displayName == "Calendar")
    }

    @Test func notificationCategoriesFromANewerServerAreKeptWhenSaving() throws {
        let settings = try Fixtures.decode(NotificationSettings.self, #"{"off":["chat","polls"],"quietHours":null,"mutes":[]}"#)
        #expect(settings.off == [.chat])
        #expect(settings.unknownOff == ["polls"])
        let patch = NotificationSettingsPatch(off: [.chat, .nudges], keeping: settings.unknownOff)
        #expect(patch.off == ["chat", "nudges", "polls"])
    }

    @Test func aPlannerResultWithoutItsListsStillDecodes() throws {
        let result = try Fixtures.decode(
            PlannerResult.self,
            """
            {"best":{"slot":{"start":"2026-12-04T18:00:00Z","end":"2026-12-06T23:00:00Z","label":"Fri 4 – Sun 6 Dec"},"why":"Long weekend"},
             "proposalTitle":"Lakes","proposalNote":"Shall we?"}
            """)
        #expect(result.alternatives.isEmpty && result.ideas.isEmpty && result.checklist.isEmpty)
        #expect(result.proposalTitle == "Lakes")
    }
}
