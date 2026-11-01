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
    @Environment(\.theme) private var theme

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
    }

    private func circle(_ hex: UInt32, x: CGFloat, y: CGFloat, scale: CGFloat) -> some View {
        Circle()
            .fill(Color(hex: hex))
            .frame(width: 20 * scale, height: 20 * scale)
            .offset(x: (x - 10) * scale, y: (y - 10) * scale)
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

    func body(content: Content) -> some View {
        content.overlay(alignment: .top) {
            if let message {
                Text(message)
                    .bodyFont(14, .semibold)
                    .foregroundStyle(theme.ink)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 12)
                    .glass(in: Capsule())
                    .padding(.top, 8)
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
