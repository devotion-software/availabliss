import Foundation

/// Build-time configuration, injected from `Config/*.xcconfig` through Info.plist.
struct AppConfig: Sendable {
    let apiBaseURL: URL
    let associatedDomainHost: String
    let googleClientID: String
    let googleRedirectScheme: String

    static let fallbackBaseURL = URL(string: "https://availabliss.app")!

    static func load(from bundle: Bundle = .main) -> AppConfig {
        func value(_ key: String) -> String {
            (bundle.object(forInfoDictionaryKey: key) as? String)?.trimmingCharacters(in: .whitespaces) ?? ""
        }
        let base = URL(string: value("AVAPIBaseURL")).flatMap { $0.host() == nil ? nil : $0 } ?? fallbackBaseURL
        let host = value("AVAssociatedDomainHost")
        return AppConfig(
            apiBaseURL: base,
            associatedDomainHost: host.isEmpty ? (base.host() ?? "availabliss.app") : host,
            googleClientID: value("AVGoogleClientID"),
            googleRedirectScheme: value("AVGoogleRedirectScheme")
        )
    }

    /// URL scheme for native deep links and OAuth returns.
    static let urlScheme = "availabliss"

    /// Background refresh task identifier (also listed in Info.plist).
    static let calendarRefreshTask = "dev.joeherbert.availabliss.calendar-refresh"
}
