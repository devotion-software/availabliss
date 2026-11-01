import SwiftUI

/// Chooses between splash, sign-in, onboarding and the main app, and applies the theme.
struct RootView: View {
    @Environment(AppModel.self) private var app

    var body: some View {
        ThemeProvider(colorTheme: app.appearance.colorTheme) {
            content
                .animation(.easeInOut(duration: 0.35), value: phase)
        }
        .preferredColorScheme(app.appearance.mode.colorScheme)
        .task { await app.launch() }
        .onChange(of: app.session.state) { _, _ in app.sessionStateChanged() }
    }

    private enum Phase: Equatable { case splash, failed, signIn, onboarding, main }

    private var phase: Phase {
        switch app.session.state {
        case .restoring: .splash
        case .failed: .failed
        case .signedOut: .signIn
        case .signedIn: app.session.user?.onboarded == true ? .main : .onboarding
        }
    }

    @ViewBuilder
    private var content: some View {
        switch phase {
        case .splash:
            SplashView()
        case .failed:
            SplashView(error: failureMessage) { Task { await app.launch() } }
        case .signIn:
            SignInView(auth: app.auth)
                .transition(.opacity)
        case .onboarding:
            OnboardingView()
                .transition(.move(edge: .trailing).combined(with: .opacity))
        case .main:
            MainTabView()
                .transition(.opacity)
                .sheet(item: Bindable(app.router).pendingInvite) { invite in
                    InviteAcceptView(token: invite.token, api: app.api)
                }
        }
    }

    private var failureMessage: String {
        if case .failed(let message) = app.session.state { return message }
        return ""
    }
}

/// Logo on the themed background while the session restores.
struct SplashView: View {
    var error: String?
    var retry: (() -> Void)?

    var body: some View {
        VStack(spacing: 16) {
            VennLogo(size: 72)
            if let error, let retry {
                StatusView(kind: .error(error, retry: retry))
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .screenBackground(OrbLayout.signIn)
    }
}
