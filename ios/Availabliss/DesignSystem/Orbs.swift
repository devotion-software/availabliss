import SwiftUI

/// One blurred colour orb, positioned relative to the screen edges like the concept's CSS.
struct OrbSpec: Sendable {
    enum Horizontal: Sendable { case left(CGFloat), right(CGFloat) }
    enum Vertical: Sendable { case top(CGFloat), bottom(CGFloat) }

    /// Which theme orb colour (0, 1 or 2).
    let color: Int
    let size: CGFloat
    let x: Horizontal
    let y: Vertical
    var opacity: Double = 0.85
}

/// Per-screen orb layouts, transcribed from the design concept.
enum OrbLayout {
    static let signIn: [OrbSpec] = [
        .init(color: 0, size: 380, x: .left(-160), y: .top(-60), opacity: 0.9),
        .init(color: 1, size: 360, x: .right(-170), y: .top(80), opacity: 0.9),
        .init(color: 2, size: 340, x: .left(10), y: .top(330), opacity: 0.75),
    ]
    static let groups: [OrbSpec] = [
        .init(color: 0, size: 340, x: .left(-140), y: .top(-110)),
        .init(color: 1, size: 300, x: .right(-150), y: .top(230)),
        .init(color: 2, size: 300, x: .left(-90), y: .bottom(-40), opacity: 0.8),
    ]
    static let calendars: [OrbSpec] = [
        .init(color: 2, size: 320, x: .right(-120), y: .top(-90)),
        .init(color: 1, size: 320, x: .left(-170), y: .top(280), opacity: 0.8),
        .init(color: 0, size: 300, x: .right(-110), y: .bottom(-60)),
    ]
    static let groupCalendar: [OrbSpec] = [
        .init(color: 1, size: 320, x: .right(-130), y: .top(-100)),
        .init(color: 0, size: 300, x: .left(-150), y: .top(300)),
        .init(color: 2, size: 280, x: .right(-80), y: .bottom(40)),
    ]
    static let myTime: [OrbSpec] = [
        .init(color: 2, size: 320, x: .left(-140), y: .top(-90)),
        .init(color: 0, size: 300, x: .right(-150), y: .top(260)),
        .init(color: 1, size: 300, x: .left(-80), y: .bottom(-60)),
    ]
    static let timing: [OrbSpec] = [
        .init(color: 1, size: 320, x: .left(-130), y: .top(-100)),
        .init(color: 0, size: 300, x: .right(-140), y: .top(330)),
        .init(color: 2, size: 300, x: .left(-60), y: .bottom(-80), opacity: 0.8),
    ]
    static let proposal: [OrbSpec] = [
        .init(color: 2, size: 340, x: .left(-120), y: .top(-60)),
        .init(color: 0, size: 300, x: .right(-150), y: .top(200)),
        .init(color: 1, size: 320, x: .left(-40), y: .bottom(-110)),
    ]
    static let chat: [OrbSpec] = [
        .init(color: 0, size: 320, x: .right(-130), y: .top(-80)),
        .init(color: 2, size: 320, x: .left(-170), y: .top(320), opacity: 0.8),
        .init(color: 1, size: 300, x: .right(-120), y: .bottom(-40)),
    ]
    static let appearance: [OrbSpec] = [
        .init(color: 0, size: 340, x: .left(-130), y: .top(-100)),
        .init(color: 1, size: 320, x: .right(-150), y: .top(250)),
        .init(color: 2, size: 300, x: .left(-70), y: .bottom(-70)),
    ]
}

/// The three soft background orbs.
struct Orbs: View {
    let layout: [OrbSpec]
    @Environment(\.theme) private var theme

    var body: some View {
        GeometryReader { proxy in
            ZStack(alignment: .topLeading) {
                ForEach(Array(layout.enumerated()), id: \.offset) { _, orb in
                    Circle()
                        .fill(theme.orbs[safe: orb.color] ?? theme.accent)
                        .frame(width: orb.size, height: orb.size)
                        .blur(radius: 60)
                        .opacity(orb.opacity * (theme.orbOpacity / 0.85))
                        .offset(offset(for: orb, in: proxy.size))
                }
            }
            .frame(width: proxy.size.width, height: proxy.size.height, alignment: .topLeading)
        }
        .ignoresSafeArea()
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    private func offset(for orb: OrbSpec, in size: CGSize) -> CGSize {
        let x: CGFloat = switch orb.x {
        case .left(let value): value
        case .right(let value): size.width - orb.size - value
        }
        let y: CGFloat = switch orb.y {
        case .top(let value): value
        case .bottom(let value): size.height - orb.size - value
        }
        return CGSize(width: x, height: y)
    }
}

/// Screen background: `bg` + orbs, edge to edge.
struct ScreenBackground: View {
    var orbs: [OrbSpec] = OrbLayout.groups
    @Environment(\.theme) private var theme

    var body: some View {
        ZStack {
            theme.bg.ignoresSafeArea()
            Orbs(layout: orbs)
        }
    }
}

extension View {
    /// Places the view over the themed background with the given orb layout.
    func screenBackground(_ orbs: [OrbSpec]) -> some View {
        background(ScreenBackground(orbs: orbs))
    }
}
