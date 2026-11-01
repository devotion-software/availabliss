import SwiftUI

/// Two steps: region, then calendars. Finishing sets `onboarded: true`.
struct OnboardingView: View {
    @Environment(AppModel.self) private var app
    @State private var step = 0
    @State private var error: String?

    var body: some View {
        ZStack {
            if step == 0 {
                RegionView(api: app.api, mode: .onboarding { withAnimation { step = 1 } })
                    .transition(.asymmetric(insertion: .move(edge: .leading), removal: .move(edge: .leading)))
            } else {
                CalendarsView(api: app.api, mode: .onboarding(
                    onBack: { withAnimation { step = 0 } },
                    onContinue: { finish(openMyTime: false) },
                    onSkip: { finish(openMyTime: true) }
                ))
                .transition(.asymmetric(insertion: .move(edge: .trailing), removal: .move(edge: .trailing)))
            }
        }
        .animation(.spring(response: 0.4, dampingFraction: 0.9), value: step)
        .errorAlert($error)
    }

    private func finish(openMyTime: Bool) {
        Task {
            do {
                try await app.session.update(UserPatch(onboarded: true))
                if openMyTime { app.router.tab = .myTime }
            } catch {
                self.error = error.userMessage
            }
        }
    }
}
