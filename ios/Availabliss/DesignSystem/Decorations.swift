import SwiftUI

/// Diagonal (135°) stripes — "blocked by hand".
struct Stripes: View {
    var color: Color
    var lineWidth: CGFloat = 2
    var spacing: CGFloat = 5

    var body: some View {
        Canvas { context, size in
            var path = Path()
            let step = lineWidth + spacing
            var x = -size.height
            while x < size.width {
                path.move(to: CGPoint(x: x, y: size.height))
                path.addLine(to: CGPoint(x: x + size.height, y: 0))
                x += step
            }
            context.stroke(path, with: .color(color), lineWidth: lineWidth)
        }
        .accessibilityHidden(true)
    }
}

/// The Availabliss logo: three overlapping circles.
struct VennLogo: View {
    var size: CGFloat = 40
    /// Loading: the circles drift apart and back together into their overlap, over and over
    /// (still with Reduce Motion).
    var animated = false
    @Environment(\.theme) private var theme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// 1 = the logo as drawn; more pushes the circles apart from their shared centre.
    @State private var spread: CGFloat = 1

    var body: some View {
        let scale = size / 40
        ZStack(alignment: .topLeading) {
            circle(0xFF9E78, x: 14, y: 15, scale: scale)
            circle(0x8F9BFF, x: 26, y: 15, scale: scale)
            circle(0x6FD8BB, x: 20, y: 25, scale: scale)
        }
        .frame(width: size, height: size, alignment: .topLeading)
        .compositingGroup()
        .accessibilityHidden(true)
        .onAppear {
            guard animated, !reduceMotion else { return }
            spread = 1.5
            withAnimation(.easeInOut(duration: 1.1).repeatForever(autoreverses: true)) { spread = 1 }
        }
    }

    private func circle(_ hex: UInt32, x: CGFloat, y: CGFloat, scale: CGFloat) -> some View {
        // The centre of the three circles, so they spread out evenly.
        let cx: CGFloat = 20, cy: CGFloat = 55.0 / 3
        return Circle()
            .fill(Color(hex: hex))
            .frame(width: 20 * scale, height: 20 * scale)
            .offset(x: (cx + (x - cx) * spread - 10) * scale, y: (cy + (y - cy) * spread - 10) * scale)
            .blendMode(theme.isDark ? .screen : .multiply)
    }
}

/// Little square legend swatch with a caption.
struct LegendItem<Swatch: View>: View {
    let label: String
    @ViewBuilder var swatch: Swatch

    var body: some View {
        HStack(spacing: 5) {
            swatch.frame(width: 12, height: 10).clipShape(RoundedRectangle(cornerRadius: 3))
            Text(label)
        }
    }
}

/// Glass toast shown at the top of the screen.
struct ToastModifier: ViewModifier {
    @Binding var message: String?
    @Environment(\.theme) private var theme
    /// Set on screens under a group's header, whose blur reaches 48pt down over the content.
    @Environment(TopBarScrollState.self) private var topBar: TopBarScrollState?

    func body(content: Content) -> some View {
        content.overlay(alignment: .top) {
            if let message {
                Text(message)
                    .bodyFont(14, .semibold)
                    .foregroundStyle(theme.ink)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 12)
                    .glass(in: Capsule())
                    // Under a group's header, clear of its blur so it isn't hidden behind it.
                    .padding(.top, topBar == nil ? 8 : 56)
                    .transition(.move(edge: .top).combined(with: .opacity))
                    .task(id: message) {
                        try? await Task.sleep(for: .seconds(2.5))
                        withAnimation { self.message = nil }
                    }
                    .accessibilityAddTraits(.isStaticText)
                    .onAppear { UIAccessibility.post(notification: .announcement, argument: message) }
            }
        }
        .animation(.spring(response: 0.35, dampingFraction: 0.8), value: message)
    }
}

extension View {
    func toast(_ message: Binding<String?>) -> some View {
        modifier(ToastModifier(message: message))
    }

    /// Presents `error` (if any) as an alert and clears it when dismissed.
    func errorAlert(_ error: Binding<String?>) -> some View {
        alert(
            "Something went wrong",
            isPresented: Binding(get: { error.wrappedValue != nil }, set: { if !$0 { error.wrappedValue = nil } }),
            actions: { Button("OK", role: .cancel) {} },
            message: { Text(error.wrappedValue ?? "") }
        )
    }
}

/// Centered loading / error / empty placeholder.
struct StatusView: View {
    enum Kind {
        case loading
        case error(String, retry: () -> Void)
        case empty(title: String, message: String, systemImage: String)
    }

    let kind: Kind
    @Environment(\.theme) private var theme

    var body: some View {
        VStack(spacing: 12) {
            switch kind {
            case .loading:
                ProgressView().controlSize(.large)
            case .error(let message, let retry):
                Image(systemName: "wifi.exclamationmark").font(.system(size: 28)).foregroundStyle(theme.muted)
                Text(message).bodyFont(14).foregroundStyle(theme.muted).multilineTextAlignment(.center)
                Button("Try again", action: retry).buttonStyle(.textAction)
            case .empty(let title, let message, let systemImage):
                Image(systemName: systemImage).font(.system(size: 28)).foregroundStyle(theme.accentInk)
                Text(title).displayFont(18, .semibold)
                Text(message).bodyFont(14).foregroundStyle(theme.muted).multilineTextAlignment(.center)
            }
        }
        .padding(24)
        .frame(maxWidth: .infinity)
    }
}
