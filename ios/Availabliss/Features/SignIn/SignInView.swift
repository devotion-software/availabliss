import SwiftUI

/// Sign in: email one-time code (two steps), Apple, Google and passkey.
struct SignInView: View {
    @Environment(AppModel.self) private var app
    @Environment(\.theme) private var theme
    @State private var model: SignInViewModel?
    private let auth: AuthService

    init(auth: AuthService) {
        self.auth = auth
    }

    var body: some View {
        GeometryReader { proxy in
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    brand
                    hero.padding(.top, 72)
                    Spacer(minLength: 40)
                    if let model { SignInForm(model: model) }
                }
                .padding(.horizontal, 24)
                .padding(.top, 48)
                .padding(.bottom, 28)
                .frame(minHeight: proxy.size.height, alignment: .top)
            }
        }
        .scrollDismissesKeyboard(.interactively)
        .scrollBounceBehavior(.basedOnSize)
        .screenBackground(OrbLayout.signIn)
        .foregroundStyle(theme.ink)
        .onAppear {
            if model == nil {
                model = SignInViewModel(auth: auth) { [app] token in try await app.signIn(token: token) }
            }
        }
        .errorAlert(Binding(get: { model?.error }, set: { model?.error = $0 }))
    }

    private var brand: some View {
        HStack(spacing: 10) {
            VennLogo(size: 40)
            Text("Availabliss").displayFont(22, .bold)
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Availabliss")
    }

    private var hero: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("Find the time that works for everyone.")
                .displayFont(44, .heavy, relativeTo: .largeTitle)
                .lineSpacing(-4)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityAddTraits(.isHeader)
            Text("Connect your calendar once. We'll find the overlap, and nobody has to say \u{201C}what about Thursday?\u{201D} again.")
                .bodyFont(16)
                .lineSpacing(3)
                .foregroundStyle(theme.muted)
                .frame(maxWidth: 300, alignment: .leading)
        }
    }
}

/// The glass card at the bottom: email/code step, provider buttons and the privacy note.
private struct SignInForm: View {
    @Bindable var model: SignInViewModel
    @Environment(\.theme) private var theme

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            switch model.step {
            case .email: emailStep
            case .code: codeStep
            }
            divider
            providers
            privacyNote
        }
        .padding(20)
        .glass(cornerRadius: 30)
        .animation(.spring(response: 0.4, dampingFraction: 0.85), value: model.step)
    }

    private var emailStep: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Email").bodyFont(13, .semibold, relativeTo: .subheadline)
            InputField(placeholder: "you@example.com", text: $model.email)
                .textContentType(.emailAddress)
                .keyboardType(.emailAddress)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .submitLabel(.send)
                .onSubmit { Task { await model.sendCode() } }
            Button {
                Task { await model.sendCode() }
            } label: {
                LoadingLabel(title: "Email me a code", isLoading: model.busyMethod == .email)
            }
            .buttonStyle(.primary)
            .disabled(model.busyMethod != nil)
        }
        .transition(.asymmetric(insertion: .move(edge: .leading).combined(with: .opacity), removal: .opacity))
    }

    private var codeStep: some View {
        VStack(alignment: .leading, spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                Text("Check your inbox").bodyFont(15, .semibold)
                Text("Enter the 6-digit code we just sent to \(model.email).")
                    .bodyFont(13, relativeTo: .subheadline)
                    .foregroundStyle(theme.muted)
            }
            OneTimeCodeField(code: $model.code, length: SignInViewModel.codeLength, isBusy: model.busyMethod == .email)
                .onChange(of: model.code) { _, _ in Task { await model.codeChanged() } }
            Button("Use a different email") { model.useDifferentEmail() }
                .buttonStyle(.textAction)
                .frame(maxWidth: .infinity)
        }
        .transition(.asymmetric(insertion: .move(edge: .trailing).combined(with: .opacity), removal: .opacity))
    }

    private var divider: some View {
        HStack(spacing: 10) {
            Rectangle().fill(theme.line2).frame(height: 1)
            Text("or").bodyFont(12, relativeTo: .caption).foregroundStyle(theme.muted)
            Rectangle().fill(theme.line2).frame(height: 1)
        }
        .accessibilityHidden(true)
    }

    private var providers: some View {
        HStack(spacing: 8) {
            ProviderButton(title: "Apple", isLoading: model.busyMethod == .apple, accessibility: "Sign in with Apple") {
                Image(systemName: "applelogo").font(.system(size: 17, weight: .medium))
            } action: {
                Task { await model.signInWithApple() }
            }
            if model.showsGoogle {
                ProviderButton(title: "Google", isLoading: model.busyMethod == .google, accessibility: "Sign in with Google") {
                    GoogleMark().frame(width: 18, height: 18)
                } action: {
                    Task { await model.signInWithGoogle() }
                }
            }
            ProviderButton(title: "Passkey", isLoading: model.busyMethod == .passkey, accessibility: "Sign in with a passkey") {
                Image(systemName: "person.badge.key").font(.system(size: 16, weight: .medium))
            } action: {
                Task { await model.signInWithPasskey() }
            }
        }
        .disabled(model.busyMethod != nil)
    }

    private var privacyNote: some View {
        HStack(alignment: .top, spacing: 8) {
            Image(systemName: "shield").font(.system(size: 13, weight: .semibold)).padding(.top, 1)
            Text("Your group only ever sees when you're busy, never what you're doing.")
                .fixedSize(horizontal: false, vertical: true)
        }
        .bodyFont(12, relativeTo: .caption)
        .foregroundStyle(theme.muted)
        .padding(.top, 2)
        .accessibilityElement(children: .combine)
    }
}

private struct ProviderButton<Icon: View>: View {
    let title: String
    let isLoading: Bool
    let accessibility: String
    @ViewBuilder let icon: Icon
    let action: () -> Void
    @Environment(\.theme) private var theme

    var body: some View {
        Button(action: action) {
            ZStack {
                HStack(spacing: 6) {
                    icon.accessibilityHidden(true)
                    Text(title).lineLimit(1).minimumScaleFactor(0.8)
                }
                .opacity(isLoading ? 0 : 1)
                if isLoading { ProgressView() }
            }
            .bodyFont(14, .semibold, relativeTo: .subheadline)
            .foregroundStyle(theme.ink)
            .frame(maxWidth: .infinity, minHeight: 50)
            .surfaceBackground(cornerRadius: 16)
            .contentShape(Rectangle())
        }
        .buttonStyle(.pressable)
        .accessibilityLabel(accessibility)
    }
}

/// The four-colour Google "G", drawn so we don't need an image asset.
private struct GoogleMark: View {
    var body: some View {
        Canvas { context, size in
            let lineWidth = size.width * 0.2
            let rect = CGRect(origin: .zero, size: size).insetBy(dx: lineWidth / 2, dy: lineWidth / 2)
            let center = CGPoint(x: rect.midX, y: rect.midY)
            let radius = rect.width / 2
            let arcs: [(Double, Double, UInt32)] = [(-40, 45, 0x4285F4), (45, 135, 0x34A853), (135, 225, 0xFBBC05), (225, 320, 0xEA4335)]
            for (start, end, color) in arcs {
                var path = Path()
                path.addArc(center: center, radius: radius, startAngle: .degrees(start), endAngle: .degrees(end), clockwise: false)
                context.stroke(path, with: .color(Color(hex: color)), lineWidth: lineWidth)
            }
            var bar = Path()
            bar.move(to: CGPoint(x: center.x, y: center.y))
            bar.addLine(to: CGPoint(x: rect.maxX + lineWidth / 2, y: center.y))
            context.stroke(bar, with: .color(Color(hex: 0x4285F4)), lineWidth: lineWidth)
        }
        .accessibilityHidden(true)
    }
}
