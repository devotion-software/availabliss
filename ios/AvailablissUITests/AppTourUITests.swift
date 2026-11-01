import XCTest

/// Signs in with a real email code (read from Mailpit) and walks the main screens,
/// attaching a screenshot of each. Needs `make dev` + `make db-up` and a seeded,
/// onboarded user whose email is passed as `TEST_RUNNER_AV_EMAIL` (see README.md).
@MainActor
final class AppTourUITests: XCTestCase {
    private let app = XCUIApplication()
    private let mailpit = URL(string: ProcessInfo.processInfo.environment["AV_MAILPIT_URL"] ?? "http://localhost:8027")!

    func testSignInAndTourMainScreens() async throws {
        continueAfterFailure = true
        let email = try XCTUnwrap(ProcessInfo.processInfo.environment["AV_EMAIL"], "Set TEST_RUNNER_AV_EMAIL to a seeded user's email")
        app.launch()

        // Sign in with an emailed one-time code.
        let emailField = app.textFields["you@example.com"]
        XCTAssertTrue(emailField.waitForExistence(timeout: 20), "Sign-in screen didn't appear")
        snapshot("01-sign-in")
        emailField.tap()
        emailField.typeText(email)
        app.buttons["Email me a code"].tap()

        let codeField = app.textFields["One-time code"]
        XCTAssertTrue(codeField.waitForExistence(timeout: 10), "Code entry didn't appear")
        snapshot("02-code")
        let code = try await Self.latestCode(for: email, mailpit: mailpit)
        codeField.tap()
        codeField.typeText(code)

        // Groups home. The first launch asks for notification permission.
        XCTAssertTrue(app.staticTexts["Your groups"].waitForExistence(timeout: 20), "Groups home didn't appear after sign-in")
        allowSystemAlert()
        settle()
        snapshot("03-groups-home")

        // Group availability: month, week, day.
        let group = app.staticTexts["Lake District crew"].firstMatch
        if group.waitForExistence(timeout: 5) {
            group.tap()
            settle(2)
            snapshot("04-group-month")
            tapIfPresent(app.buttons["Week"]) { snapshot("05-group-week") }
            tapIfPresent(app.buttons["Day"]) { snapshot("06-group-day") }
            tapIfPresent(app.buttons["Chat"]) { snapshot("07-group-chat") }
            // Chat is full screen (no group tab bar); go back to the calendar first.
            tapIfPresent(app.buttons["Back to calendar"]) {}
            tapIfPresent(app.buttons["Proposals"]) { snapshot("08-group-proposals") }
            tapIfPresent(app.buttons["Timing"]) { snapshot("09-group-timing") }
            returnToGroups()
        } else {
            XCTFail("Seeded group 'Lake District crew' not found on the groups list")
        }

        // Main tabs.
        // Labels can carry state ("Activity, new", "Appearance, System · Dawn"), so match by prefix.
        tapIfPresent(button(startingWith: "My time")) { snapshot("10-my-time") }
        tapIfPresent(button(startingWith: "Activity")) { snapshot("11-activity") }
        tapIfPresent(button(startingWith: "You")) { snapshot("12-you") }
        tapIfPresent(button(startingWith: "Appearance")) { snapshot("13-appearance") }
    }

    // MARK: - Helpers

    private func tapIfPresent(_ element: XCUIElement, then action: () -> Void) {
        guard element.waitForExistence(timeout: 5) else {
            XCTFail("Expected \(element) to exist")
            return
        }
        element.tap()
        settle(1.5)
        action()
    }

    private func button(startingWith prefix: String) -> XCUIElement {
        app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", prefix)).firstMatch
    }

    /// Taps back buttons ("Back", "Back to calendar", "Back to groups", …) until the groups list shows.
    private func returnToGroups() {
        let back = button(startingWith: "Back")
        for _ in 0..<4 where !app.staticTexts["Your groups"].exists {
            guard back.waitForExistence(timeout: 3) else { break }
            back.tap()
            settle()
        }
        XCTAssertTrue(app.staticTexts["Your groups"].waitForExistence(timeout: 5), "Couldn't get back to the groups list")
    }

    /// Taps "Allow" on a system permission alert if one is showing.
    private func allowSystemAlert() {
        let allow = XCUIApplication(bundleIdentifier: "com.apple.springboard").buttons["Allow"]
        if allow.waitForExistence(timeout: 3) { allow.tap() }
    }

    /// Lets loads and animations finish before a screenshot.
    private func settle(_ seconds: TimeInterval = 1) {
        RunLoop.current.run(until: Date().addingTimeInterval(seconds))
    }

    private func snapshot(_ name: String) {
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    /// Polls Mailpit for the newest sign-in code sent to `email`.
    nonisolated private static func latestCode(for email: String, mailpit: URL) async throws -> String {
        struct Search: Decodable { let messages: [Message] }
        struct Message: Decodable { let Subject: String }
        var components = URLComponents(url: mailpit.appending(path: "api/v1/search"), resolvingAgainstBaseURL: false)!
        components.queryItems = [URLQueryItem(name: "query", value: "to:\(email)")]
        for _ in 0..<30 {
            let (data, _) = try await URLSession.shared.data(from: components.url!)
            if let subject = try JSONDecoder().decode(Search.self, from: data).messages.first?.Subject,
               let code = subject.split(separator: " ").first, code.count == 6, code.allSatisfy(\.isNumber) {
                return String(code)
            }
            try await Task.sleep(for: .milliseconds(500))
        }
        throw XCTSkip("No sign-in code arrived in Mailpit for \(email)")
    }
}

