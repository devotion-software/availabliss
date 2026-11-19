import SwiftUI

/// The first-launch intro, shown once before sign-in: a welcome that asks your name, then five pages on
/// what Availabliss does. Your name is one piece of text that travels with you: it slides from page to
/// page into each scene (the group you made, your calendar, the month, your reply, your message) as you
/// swipe, taking on the size and colour of the names around it.
/// Swipe or tap Next; Skip or Get started goes on to sign in.
struct IntroView: View {
    /// Called with the name typed on the welcome page (empty if skipped).
    let onFinish: (String) -> Void
    @Environment(\.theme) private var theme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var page: Int? = 0
    /// Pages whose text has risen in.
    @State private var seen: Set<Int> = [0]
    @State private var name = IntroName.saved ?? ""
    /// The name's own size (at 15pt), so every page can leave a space that fits it.
    @State private var nameSize = CGSize(width: 180, height: 60)
    /// The page dots step aside while the keyboard is up, to leave the name field room.
    @State private var keyboardUp = false

    private var current: Int { page ?? 0 }
    private var isLast: Bool { current == IntroPage.all.count - 1 }
    private var firstName: String { IntroName.first(of: name) }
    /// The welcome page needs a name before you can go on (Skip still skips the whole intro).
    private var needsName: Bool { current == 0 && firstName.isEmpty }

    var body: some View {
        VStack(spacing: 0) {
            topBar
            ScrollView(.horizontal) {
                // Not lazy: every page's space for the name chip has to be there to slide between.
                HStack(spacing: 0) {
                    ForEach(IntroPage.all) { item in
                        IntroPageView(page: item, isActive: item.id == current, shown: seen.contains(item.id), name: $name, onSubmitName: next)
                            .containerRelativeFrame(.horizontal)
                            .id(item.id)
                            // Added to the page's own name space (a plain preference would replace it).
                            .transformAnchorPreference(key: IntroAnchors.self, value: .bounds) { value, anchor in
                                if item.id == 0 { value[IntroAnchors.firstPage] = IntroSlot(anchor: anchor, tone: .ink) }
                            }
                    }
                }
                .scrollTargetLayout()
            }
            .scrollTargetBehavior(.paging)
            .scrollIndicators(.hidden)
            .scrollPosition(id: $page)
            .scrollBounceBehavior(.basedOnSize)
            .scrollDisabled(needsName)
            .scrollDismissesKeyboard(.immediately)
            controls
        }
        .environment(\.introNameSize, nameSize)
        .environment(\.introName, firstName)
        .overlayPreferenceValue(IntroAnchors.self) { slots in
            GeometryReader { proxy in
                let placement = namePlacement(slots, in: proxy)
                TravellingName(name: firstName, muted: placement?.muted ?? 0, accent: placement?.accent ?? 0)
                    .scaleEffect(placement?.scale ?? 1)
                    .position(placement?.center ?? .zero)
                    .opacity(placement == nil ? 0 : 1)
            }
            .allowsHitTesting(false)
            .accessibilityHidden(true)
        }
        .foregroundStyle(theme.ink)
        .background {
            // The name's size, measured on a hidden copy: measuring the moving one fed back into the
            // spaces it moves between, and never settled. Whole points, so it can't jitter either.
            TravellingName(name: firstName, muted: 0, accent: 0)
                .hidden()
                .onGeometryChange(for: CGSize.self) {
                    CGSize(width: $0.size.width.rounded(.up), height: $0.size.height.rounded(.up))
                } action: {
                    nameSize = $0
                }
                .accessibilityHidden(true)
        }
        .background {
            // The orbs drift to a new place for each page, ending where sign-in's sit.
            ScreenBackground(orbs: IntroPage.all[current].orbs)
                .animation(reduceMotion ? nil : .easeInOut(duration: 0.9), value: current)
        }
        .sensoryFeedback(.selection, trigger: current)
        .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillShowNotification)) { _ in keyboardUp = true }
        .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillHideNotification)) { _ in keyboardUp = false }
        .onChange(of: current) { _, new in
            seen.insert(new)
            Self.dismissKeyboard()
        }
    }

    /// Where the name sits: between the spaces left for it on the two pages either side of the scroll
    /// position, in proportion. (Both pages move with the scroll, so the scroll itself cancels out and
    /// the name glides straight from one space to the next.)
    private func namePlacement(_ slots: [Int: IntroSlot], in proxy: GeometryProxy) -> (center: CGPoint, scale: CGFloat, muted: CGFloat, accent: CGFloat)? {
        guard let first = slots[IntroAnchors.firstPage].map({ proxy[$0.anchor] }), first.width > 0 else { return nil }
        let position = min(max(-first.minX / first.width, 0), CGFloat(IntroPage.all.count - 1))
        let from = Int(position.rounded(.down))
        let fraction = position - CGFloat(from)
        let a = slots[from]
        let b = slots[min(from + 1, IntroPage.all.count - 1)]
        guard let startSlot = a ?? b, let endSlot = b ?? a else { return nil }
        let start = proxy[startSlot.anchor], end = proxy[endSlot.anchor]
        let t = (a != nil && b != nil) ? fraction : 0
        let center = CGPoint(x: start.midX + (end.midX - start.midX) * t, y: start.midY + (end.midY - start.midY) * t)
        let height = start.height + (end.height - start.height) * t
        func blend(_ tone: IntroSlot.Tone) -> CGFloat { (startSlot.tone == tone ? 1 : 0) * (1 - t) + (endSlot.tone == tone ? 1 : 0) * t }
        return (center, nameSize.height > 0 ? height / nameSize.height : 1, blend(.muted), blend(.accent))
    }

    private func next() {
        guard !needsName else { return }
        Self.dismissKeyboard()
        if isLast {
            onFinish(name.trimmingCharacters(in: .whitespacesAndNewlines))
        } else {
            withAnimation(.spring(response: 0.5, dampingFraction: 0.9)) { page = current + 1 }
        }
    }

    private var topBar: some View {
        HStack(spacing: 10) {
            VennLogo(size: 30)
            Text("Availabliss").displayFont(19, .bold)
            Spacer()
            Button("Skip") { onFinish(name.trimmingCharacters(in: .whitespacesAndNewlines)) }
                .buttonStyle(TextActionButtonStyle(fontSize: 15))
                .opacity(isLast ? 0 : 1)
                .disabled(isLast)
                .accessibilityHint("Goes to sign in")
        }
        .accessibilityElement(children: .contain)
        .padding(.horizontal, 24)
        .padding(.top, 8)
        .frame(minHeight: 52)
        .animation(.easeInOut(duration: 0.25), value: isLast)
    }

    private var controls: some View {
        VStack(spacing: 22) {
            if !keyboardUp { PageDots(count: IntroPage.all.count, current: current) }
            Button(action: next) {
                ZStack {
                    // Both labels are laid out so the button never changes size; they cross-fade.
                    Text("Next").opacity(isLast ? 0 : 1)
                    Text("Get started").opacity(isLast ? 1 : 0)
                }
                .animation(.easeInOut(duration: 0.25), value: isLast)
            }
            .buttonStyle(.filled(isLast ? .accent : .primary, height: 54, cornerRadius: 18))
            .disabled(needsName)
            .animation(.easeInOut(duration: 0.2), value: needsName)
            .accessibilityLabel(isLast ? "Get started" : "Next")
            .accessibilityValue("Page \(current + 1) of \(IntroPage.all.count)")
        }
        .padding(.horizontal, 24)
        .padding(.top, 8)
        .padding(.bottom, 12)
    }

    private static func dismissKeyboard() {
        UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
    }
}

// MARK: - The travelling name

/// The name typed on the welcome page, kept for the sign-up step that asks for it.
enum IntroName {
    static let key = "introName"

    static var saved: String? {
        let value = UserDefaults.standard.string(forKey: key)?.trimmingCharacters(in: .whitespacesAndNewlines)
        return value?.isEmpty == false ? value : nil
    }

    /// The first word, as the intro shows it.
    static func first(of name: String) -> String {
        String(name.trimmingCharacters(in: .whitespacesAndNewlines).split(separator: " ").first ?? "")
    }
}

/// Your first name: the one element that moves through the whole intro. Drawn large (3× the scenes'
/// 15pt names, so it stays sharp) and scaled down to each space; `muted` and `accent` (0–1) blend it
/// from ink to the muted grey the chat's author names use, or to the accent of the welcome's "Hi,".
private struct TravellingName: View {
    let name: String
    let muted: CGFloat
    let accent: CGFloat
    @Environment(\.theme) private var theme

    var body: some View {
        ZStack {
            Text(name).foregroundStyle(theme.ink).opacity(max(0, 1 - muted - accent))
            Text(name).foregroundStyle(theme.muted).opacity(muted)
            Text(name).foregroundStyle(theme.accent).opacity(accent)
        }
        .bodyFont(15 * IntroNameSlot.drawScale, .semibold)
        .lineLimit(1)
        .fixedSize()
        .opacity(name.isEmpty ? 0 : 1)
    }
}

/// The space a scene leaves for your name on `page`, at `scale` of 15pt (so 13pt is 13 / 15), in `tone`.
/// The name itself is drawn over the pages and slides between these.
struct IntroNameSlot: View {
    /// The travelling name is drawn at this multiple of 15pt and scaled down into each space.
    static let drawScale: CGFloat = 3
    let page: Int
    var scale: CGFloat = 1
    var tone: IntroSlot.Tone = .ink
    @Environment(\.introNameSize) private var size

    var body: some View {
        Color.clear
            .frame(width: size.width * scale / Self.drawScale, height: size.height * scale / Self.drawScale)
            .anchorPreference(key: IntroAnchors.self, value: .bounds) { [page: IntroSlot(anchor: $0, tone: tone)] }
            .accessibilityHidden(true)
    }
}

/// A space for the name, and how it's drawn there.
struct IntroSlot {
    enum Tone { case ink, muted, accent }
    let anchor: Anchor<CGRect>
    let tone: Tone
}

/// Each page's name space, by page, plus the first page's frame (for the scroll position).
private struct IntroAnchors: PreferenceKey {
    static let firstPage = -1
    static var defaultValue: [Int: IntroSlot] { [:] }
    static func reduce(value: inout [Int: IntroSlot], nextValue: () -> [Int: IntroSlot]) {
        value.merge(nextValue()) { $1 }
    }
}

extension EnvironmentValues {
    @Entry var introNameSize = CGSize(width: 180, height: 60)
    /// Your first name, for the scenes' avatars.
    @Entry var introName = ""
}

// MARK: - Pages

/// The dots under the pages: the current one stretches into a pill in the accent.
private struct PageDots: View {
    let count: Int
    let current: Int
    @Environment(\.theme) private var theme

    var body: some View {
        HStack(spacing: 7) {
            ForEach(0..<count, id: \.self) { index in
                Capsule()
                    .fill(index == current ? theme.accent : theme.line3)
                    .frame(width: index == current ? 24 : 7, height: 7)
            }
        }
        .animation(.spring(response: 0.4, dampingFraction: 0.75), value: current)
        .accessibilityHidden(true)
    }
}

/// One page: its illustration above, title and a line of text (or, on the welcome, the name field) below.
private struct IntroPageView: View {
    let page: IntroPage
    let isActive: Bool
    /// The text rises in the first time the page shows.
    let shown: Bool
    @Binding var name: String
    let onSubmitName: () -> Void
    @Environment(\.theme) private var theme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        GeometryReader { proxy in
            // Smaller illustration on shorter phones (and with the keyboard up), so the text always fits.
            let art = min(320, max(page.kind == .welcome ? 120 : 220, proxy.size.height - 210))
            VStack(spacing: 0) {
                Spacer(minLength: 0)
                // Drawn at 340 × 320 and scaled to fit.
                illustration
                    .frame(width: 340, height: 320)
                    // The welcome's "Hi," lines up with the text under it, so it shrinks towards the left.
                    .scaleEffect(min(art / 320, (proxy.size.width - 24) / 340), anchor: page.kind == .welcome ? .leading : .center)
                    .padding(.leading, page.kind == .welcome ? 24 : 0)
                    .frame(maxWidth: .infinity, alignment: page.kind == .welcome ? .leading : .center)
                    .frame(height: art)
                    .accessibilityHidden(true)
                    // Parallax: the picture travels a little further than the page as you swipe.
                    .scrollTransition(axis: .horizontal) { content, phase in
                        content
                            .offset(x: phase.value * 70)
                            .scaleEffect(1 - abs(phase.value) * 0.08)
                            .opacity(1 - abs(phase.value) * 0.6)
                    }
                Spacer(minLength: 16).frame(maxHeight: 36)
                VStack(alignment: .leading, spacing: 10) {
                    Text(page.eyebrow.uppercased())
                        .bodyFont(12, .bold, relativeTo: .caption)
                        .tracking(1.2)
                        .foregroundStyle(theme.accentInk)
                        .entrance(shown, delay: 0, reduceMotion: reduceMotion)
                    Text(page.title)
                        .displayFont(30, .heavy, relativeTo: .largeTitle)
                        .fixedSize(horizontal: false, vertical: true)
                        .accessibilityAddTraits(.isHeader)
                        .entrance(shown, delay: 0.07, reduceMotion: reduceMotion)
                    if page.kind == .welcome {
                        InputField(placeholder: "First name", text: $name)
                            .textContentType(.givenName)
                            .textInputAutocapitalization(.words)
                            .autocorrectionDisabled()
                            .submitLabel(.next)
                            .onSubmit(onSubmitName)
                            .accessibilityLabel("Your name")
                            .padding(.top, 4)
                            .entrance(shown, delay: 0.14, reduceMotion: reduceMotion)
                    }
                    if !page.body.isEmpty {
                        Text(page.body)
                            .bodyFont(16)
                            .lineSpacing(2)
                            .foregroundStyle(theme.muted)
                            .fixedSize(horizontal: false, vertical: true)
                            .entrance(shown, delay: 0.14, reduceMotion: reduceMotion)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 28)
                .scrollTransition(axis: .horizontal) { content, phase in
                    content.offset(x: phase.value * 24).opacity(1 - abs(phase.value) * 0.8)
                }
                Spacer(minLength: 0)
            }
            .frame(width: proxy.size.width, height: proxy.size.height)
        }
        .accessibilityElement(children: .contain)
    }

    @ViewBuilder private var illustration: some View {
        switch page.kind {
        case .welcome: WelcomeIllustration(page: page.id)
        case .group: GroupIllustration(page: page.id, isActive: isActive)
        case .calendars: CalendarIllustration(page: page.id, isActive: isActive)
        case .grid: MonthIllustration(page: page.id, isActive: isActive)
        case .proposal: ProposalIllustration(page: page.id, isActive: isActive)
        case .chat: ChatIllustration(page: page.id, isActive: isActive)
        }
    }
}

extension View {
    /// Rises and fades in once `shown`, after `delay`.
    fileprivate func entrance(_ shown: Bool, delay: Double, reduceMotion: Bool) -> some View {
        opacity(shown ? 1 : 0)
            .offset(y: shown || reduceMotion ? 0 : 16)
            .animation(reduceMotion ? .easeOut(duration: 0.2) : .spring(response: 0.55, dampingFraction: 0.85).delay(0.1 + delay), value: shown)
    }
}

/// What each page says. The same pages, order and words as Android's intro.
struct IntroPage: Identifiable {
    enum Kind { case welcome, group, calendars, grid, proposal, chat }

    let id: Int
    let kind: Kind
    let eyebrow: String
    let title: String
    let body: String
    let orbs: [OrbSpec]

    static let all: [IntroPage] = [
        IntroPage(
            id: 0, kind: .welcome, eyebrow: "Welcome to Availabliss",
            title: "What should we call you?",
            body: "",
            orbs: OrbLayout.appearance
        ),
        IntroPage(
            id: 1, kind: .group, eyebrow: "Make a group",
            title: "Bring your people together.",
            body: "Start a group for friends, family or a team, and invite everyone with a link.",
            orbs: OrbLayout.groups
        ),
        IntroPage(
            id: 2, kind: .calendars, eyebrow: "Connect your calendars",
            title: "Your plans stay private.",
            body: "Connect your calendars once. Your group only ever sees when you're busy, never what you're doing.",
            orbs: OrbLayout.calendars
        ),
        IntroPage(
            id: 3, kind: .grid, eyebrow: "See what works",
            title: "Spot the days everyone's free.",
            body: "The group calendar lights up the days and times that work for everyone. No more \u{201C}what about Thursday?\u{201D}",
            orbs: OrbLayout.groupCalendar
        ),
        IntroPage(
            id: 4, kind: .proposal, eyebrow: "Propose a time",
            title: "Suggest it. Everyone replies.",
            body: "Pick a time and the group says yes, maybe or no. When everyone's in, it's a plan.",
            orbs: OrbLayout.proposal
        ),
        IntroPage(
            id: 5, kind: .chat, eyebrow: "Chat with the group",
            title: "Talk it through together.",
            body: "Sort out the details in the group chat: where to go, who's driving, what to bring.",
            orbs: OrbLayout.signIn
        ),
    ]
}
