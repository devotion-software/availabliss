import SwiftUI

@main
struct AvailablissApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var delegate
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(delegate.app)
                .onOpenURL { url in
                    if let link = DeepLink(url: url, associatedHost: delegate.app.config.associatedDomainHost) {
                        delegate.app.open(link)
                    }
                }
                .onContinueUserActivity(NSUserActivityTypeBrowsingWeb) { activity in
                    if let url = activity.webpageURL,
                       let link = DeepLink(url: url, associatedHost: delegate.app.config.associatedDomainHost) {
                        delegate.app.open(link)
                    }
                }
        }
        .onChange(of: scenePhase) { _, phase in
            delegate.app.scenePhaseChanged(phase)
        }
        .backgroundTask(.appRefresh(AppConfig.calendarRefreshTask)) { [app = delegate.app] in
            await app.backgroundRefresh()
        }
    }
}
