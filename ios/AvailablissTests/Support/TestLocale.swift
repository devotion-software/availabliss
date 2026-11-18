import Foundation
import Testing

/// Some copy is formatted for the device's locale ("2 Dec", "07:00", "Monday"). `make ios-test` and
/// CI run the tests in English (UK) (`-testLanguage en -testRegion GB`); in another locale, as when
/// run from Xcode on a US simulator, the tests that check that copy word for word are skipped.
enum TestLocale {
    static var isBritish: Bool {
        Locale.current.language.languageCode?.identifier == "en" && Locale.current.region?.identifier == "GB"
    }

    static let reason: Comment = "Checks en-GB formatting: run with make ios-test (or set the scheme's test language to English, region UK)"
}
