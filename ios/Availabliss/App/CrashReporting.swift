import Foundation
import Sentry

/// Sentry: crashes, app hangs, errors, traces and session replays. Off unless the build has a DSN
/// (`SENTRY_DSN` in Config/*.xcconfig). Events carry the signed-in user's id and nothing else about
/// them: no IP, no screenshots, and replays mask all text and images, so availability, chat and
/// calendar details never leave the phone.
enum CrashReporting {
    static func start(config: AppConfig) {
        guard let dsn = config.sentryDSN, !SentrySDK.isEnabled else { return }
        let apiHost = config.apiBaseURL.host() ?? ""
        SentrySDK.start { options in
            options.dsn = dsn
            #if DEBUG
            options.environment = "development"
            #else
            options.environment = "production"
            #endif
            options.sendDefaultPii = false
            options.attachScreenshot = false
            options.attachViewHierarchy = false
            // A tenth of launches and screen loads are traced in production; every one in development.
            options.tracesSampleRate = NSNumber(value: options.environment == "production" ? 0.1 : 1)
            // Trace headers only go to our own API, never to RevenueCat, Google or map tiles.
            options.tracePropagationTargets = [apiHost]
            options.sessionReplay.sessionSampleRate = options.environment == "production" ? 0.1 : 0
            options.sessionReplay.onErrorSampleRate = 1
            options.sessionReplay.maskAllText = true
            options.sessionReplay.maskAllImages = true
            // Picture and attachment links carry a signature in their query string.
            options.beforeBreadcrumb = { breadcrumb in
                if let url = breadcrumb.data?["url"] as? String {
                    breadcrumb.data?["url"] = withoutQuery(url)
                }
                return breadcrumb
            }
            options.beforeSend = { event in
                if let url = event.request?.url { event.request?.url = withoutQuery(url) }
                event.request?.queryString = nil
                event.request?.headers = nil
                event.request?.cookies = nil
                return event
            }
        }
    }

    /// Attaches the signed-in user's id to later events, or clears it on sign-out.
    static func setUser(id: String?) {
        guard SentrySDK.isEnabled else { return }
        SentrySDK.setUser(id.map { Sentry.User(userId: $0) })
    }

    private static func withoutQuery(_ url: String) -> String {
        url.split(separator: "?", maxSplits: 1).first.map(String.init) ?? url
    }
}
