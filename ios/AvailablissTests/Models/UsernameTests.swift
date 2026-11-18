import Testing
@testable import Availabliss

/// Ported from Android `UsernamesTest` (which mirrors web `usernames.ts`).
@MainActor
struct UsernameTests {
    @Test func normalizeTrimsDropsLeadingAtSignsAndLowercases() {
        #expect(Username.normalize("  @@SamTaylor ") == "samtaylor")
        #expect(Username.normalize("sam.T_1") == "sam.t_1")
        #expect(Username.normalize("\n@priya\t") == "priya")
    }

    @Test func problemsMatchTheServersRules() {
        #expect(Username.problem("sam") == nil)
        #expect(Username.problem("sam.taylor_99") == nil)
        #expect(Username.problem(String(repeating: "a", count: 20)) == nil)
        #expect(Username.problem("sa") == "At least 3 characters")
        #expect(Username.problem(String(repeating: "a", count: 21)) == "At most 20 characters")
        #expect(Username.problem("sam-taylor") == "Only letters, numbers, _ and .")
        #expect(Username.problem("Sam") == "Only letters, numbers, _ and .")
        let dot = "A dot can\u{2019}t come first, last or twice in a row"
        #expect(Username.problem(".sam") == dot)
        #expect(Username.problem("sam.") == dot)
        #expect(Username.problem("sam..t") == dot)
        #expect(Username.problem("admin") == "That one\u{2019}s reserved")
        #expect(Username.problem("you") == "That one\u{2019}s reserved")
    }

    @Test func displayNameFallsBackToTheHandle() {
        #expect(UserSummary(id: "u", username: "sam", name: "Sam Taylor", image: nil).displayName == "Sam Taylor")
        #expect(UserSummary(id: "u", username: "sam", name: "  ", image: nil).displayName == "@sam")
    }

    @Test func anAtSignAfterTheFirstCharacterIsMeantAsAnEmail() {
        #expect(InviteDraft.isEmailLike("Sam@Example.com"))
        #expect(InviteDraft.isEmailLike("sam@localhost"))
        #expect(!InviteDraft.isEmailLike("@sam"))
        #expect(!InviteDraft.isEmailLike("@example.com"))
        #expect(!InviteDraft.isEmailLike("sam"))
    }

    @Test func emailsNeedADottedDomainAndOneAtSign() {
        #expect(InviteDraft.looksLikeEmail("sam@example.com"))
        #expect(!InviteDraft.looksLikeEmail("sam@localhost"))
        #expect(!InviteDraft.looksLikeEmail("sam@@example.com"))
        #expect(!InviteDraft.looksLikeEmail("sam taylor@example.com"))
    }
}
