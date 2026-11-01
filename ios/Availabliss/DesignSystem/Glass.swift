import SwiftUI

/// The "Dawn glass" treatment: material blur tinted with `glass`, a 1px `glass-border`,
/// a 1px inner top highlight and a soft long shadow.
struct GlassBackground<S: InsettableShape>: ViewModifier {
    let shape: S
    var fill: Color?
    var shadow = true
    @Environment(\.theme) private var theme

    func body(content: Content) -> some View {
        content
            .background {
                ZStack {
                    shape.fill(.ultraThinMaterial)
                    shape.fill(fill ?? theme.glass)
                }
                .overlay {
                    shape.strokeBorder(theme.glassBorder, lineWidth: 1)
                }
                .overlay {
                    // Inner top highlight.
                    shape.inset(by: 1)
                        .stroke(
                            LinearGradient(
                                colors: [theme.glassHi, theme.glassHi.opacity(0)],
                                startPoint: .top,
                                endPoint: UnitPoint(x: 0.5, y: 0.12)
                            ),
                            lineWidth: 1
                        )
                }
                .shadow(color: shadow ? theme.shadow.opacity(0.55) : .clear, radius: 20, x: 0, y: 16)
            }
    }
}

extension View {
    /// Glass panel with rounded corners.
    func glass(cornerRadius: CGFloat, fill: Color? = nil, shadow: Bool = true) -> some View {
        modifier(GlassBackground(shape: RoundedRectangle(cornerRadius: cornerRadius, style: .continuous), fill: fill, shadow: shadow))
    }

    /// Glass in any shape (e.g. `Circle()` for icon buttons, `Capsule()` for pills).
    func glass<S: InsettableShape>(in shape: S, fill: Color? = nil, shadow: Bool = true) -> some View {
        modifier(GlassBackground(shape: shape, fill: fill, shadow: shadow))
    }

    /// The `surface` fill with a hairline border used by inputs, tiles and cells.
    func surfaceBackground(cornerRadius: CGFloat, border: Color? = nil, lineWidth: CGFloat = 1) -> some View {
        modifier(SurfaceBackground(cornerRadius: cornerRadius, border: border, lineWidth: lineWidth))
    }
}

private struct SurfaceBackground: ViewModifier {
    let cornerRadius: CGFloat
    let border: Color?
    let lineWidth: CGFloat
    @Environment(\.theme) private var theme

    func body(content: Content) -> some View {
        let shape = RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
        content
            .background(shape.fill(theme.surface))
            .overlay(shape.strokeBorder(border ?? theme.glassBorder, lineWidth: lineWidth))
    }
}

/// A glass card container — the most common building block.
struct GlassPanel<Content: View>: View {
    var cornerRadius: CGFloat = 24
    var padding: EdgeInsets = EdgeInsets(top: 16, leading: 16, bottom: 16, trailing: 16)
    var spacing: CGFloat = 12
    @ViewBuilder var content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: spacing) {
            content
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(padding)
        .glass(cornerRadius: cornerRadius)
    }
}

extension EdgeInsets {
    init(all value: CGFloat) { self.init(top: value, leading: value, bottom: value, trailing: value) }
    init(horizontal: CGFloat, vertical: CGFloat) {
        self.init(top: vertical, leading: horizontal, bottom: vertical, trailing: horizontal)
    }
}
