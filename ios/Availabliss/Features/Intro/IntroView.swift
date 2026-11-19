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
    /// How far the keyboard reaches above the bottom safe area. The intro doesn't resize for the keyboard
    /// (that relaid out every page and its scene); the buttons and the welcome's greeting just slide up.
    @State private var keyboardLift: CGFloat = 0
    /// The name and its size as the other pages show them: caught up when you leave the welcome (or put
    /// the keyboard away) rather than on every key, so typing only redraws the welcome page.
    @State private var settledName = IntroName.first(of: IntroName.saved ?? "")
    @State private var settledSize = CGSize(width: 180, height: 60)

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
                        let isWelcome = item.id == 0
                        IntroPageView(
                            page: item, isActive: item.id == current, shown: seen.contains(item.id), name: isWelcome ? $name : .constant(""), onSubmitName: next
                        )
                        .equatable()
                        .environment(\.introNameSize, isWelcome ? nameSize : settledSize)
                        .environment(\.introName, isWelcome ? firstName : settledName)
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
                .offset(y: -keyboardLift)
        }
        .ignoresSafeArea(.keyboard)
        .environment(\.introKeyboardLift, keyboardLift)
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
                    if !keyboardUp { settle() }
                }
                .accessibilityHidden(true)
        }
        .background {
            // The orbs drift to a new place for each page, ending where sign-in's sit.
            ScreenBackground(orbs: IntroPage.all[current].orbs)
                .animation(reduceMotion ? nil : .easeInOut(duration: 0.9), value: current)
        }
        .sensoryFeedback(.selection, trigger: current)
        .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillShowNotification)) { note in
            keyboardUp = true
            let frame = (note.userInfo?[UIResponder.keyboardFrameEndUserInfoKey] as? NSValue)?.cgRectValue ?? .zero
            // The window's own bottom inset (the home indicator), which the buttons already sit above.
            let window = UIApplication.shared.connectedScenes.compactMap { ($0 as? UIWindowScene)?.keyWindow }.first
            let bottomInset = window?.safeAreaInsets.bottom ?? 0
            withAnimation(Self.keyboardAnimation(note)) { keyboardLift = max(0, frame.height - bottomInset) }
        }
        .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillHideNotification)) { note in
            keyboardUp = false
            withAnimation(Self.keyboardAnimation(note)) { keyboardLift = 0 }
            settle()
        }
        .onChange(of: current) { _, new in
            seen.insert(new)
            settle()
            Self.dismissKeyboard()
        }
        // Tapping anywhere outside the name field puts the keyboard away.
        .contentShape(Rectangle())
        .onTapGesture { if keyboardUp { Self.dismissKeyboard() } }
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

    /// Brings the other pages up to date with the name typed on the welcome.
    private func settle() {
        if settledName != firstName { settledName = firstName }
        if settledSize != nameSize { settledSize = nameSize }
    }

    private func next() {
        guard !needsName else { return }
        settle()
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
            // Faded rather than removed, so the layout stays put while the keyboard is up.
            PageDots(count: IntroPage.all.count, current: current)
                .opacity(keyboardUp ? 0 : 1)
                .animation(.easeOut(duration: 0.2), value: keyboardUp)
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

    /// The keyboard's own timing, so the buttons and greeting move with it.
    private static func keyboardAnimation(_ note: Notification) -> Animation {
        let duration = (note.userInfo?[UIResponder.keyboardAnimationDurationUserInfoKey] as? Double) ?? 0.25
        return .smooth(duration: duration)
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
    /// How far the keyboard reaches into the intro (it slides the welcome's greeting up).
    @Entry var introKeyboardLift: CGFloat = 0
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

/// One page: its illustration above, title and a line of text below (the welcome has its own layout).
/// Equal when nothing it shows has changed, so typing on the welcome doesn't redraw the other pages.
private struct IntroPageView: View, Equatable {
    let page: IntroPage
    let isActive: Bool
    /// The text rises in the first time the page shows.
    let shown: Bool
    @Binding var name: String
    let onSubmitName: () -> Void
    @Environment(\.theme) private var theme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    nonisolated static func == (lhs: Self, rhs: Self) -> Bool {
        MainActor.assumeIsolated {
            lhs.page.id == rhs.page.id && lhs.isActive == rhs.isActive && lhs.shown == rhs.shown
                && (lhs.page.kind != .welcome || lhs.name == rhs.name)
        }
    }

    var body: some View {
        if page.kind == .welcome {
            WelcomePage(page: page, shown: shown, name: $name, onSubmit: onSubmitName)
        } else {
            scenePage
        }
    }

    private var scenePage: some View {
        GeometryReader { proxy in
            // Smaller illustration on shorter phones, so the text always fits.
            let art = min(320, max(220, proxy.size.height - 210))
            VStack(spacing: 0) {
                Spacer(minLength: 0)
                // Drawn at 340 × 320 and scaled to fit.
                illustration
                    .frame(width: 340, height: 320)
                    .scaleEffect(min(art / 320, (proxy.size.width - 24) / 340))
                    .frame(maxWidth: .infinity)
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
                    Text(page.body)
                        .bodyFont(16)
                        .lineSpacing(2)
                        .foregroundStyle(theme.muted)
                        .fixedSize(horizontal: false, vertical: true)
                        .entrance(shown, delay: 0.14, reduceMotion: reduceMotion)
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
        case .welcome: EmptyView()
        case .group: GroupIllustration(page: page.id, isActive: isActive)
        case .calendars: CalendarIllustration(page: page.id, isActive: isActive)
        case .grid: MonthIllustration(page: page.id, isActive: isActive)
        case .proposal: ProposalIllustration(page: page.id, isActive: isActive)
        case .chat: ChatIllustration(page: page.id, isActive: isActive)
        }
    }
}

/// The welcome: a big "Hi" you type your name straight into, with "Welcome to Availabliss" under it.
/// The field's own text is invisible: the travelling name is drawn over it (in the space left here), so
/// the name you type is the very one that moves on through the intro. Long names shrink to fit.
private struct WelcomePage: View {
    let page: IntroPage
    let shown: Bool
    @Binding var name: String
    let onSubmit: () -> Void
    @Environment(\.theme) private var theme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.introNameSize) private var nameSize
    @Environment(\.introKeyboardLift) private var keyboardLift
    @FocusState private var focused: Bool

    private static let size: CGFloat = 56

    var body: some View {
        GeometryReader { proxy in
            let nameFont = fittedSize(width: proxy.size.width - 56)
            VStack(alignment: .leading, spacing: 0) {
                Spacer(minLength: 12)
                VStack(alignment: .leading, spacing: 6) {
                    Text(IntroName.first(of: name).isEmpty ? "Hi" : "Hi,")
                        .bodyFont(Self.size, .semibold)
                        .lineLimit(1)
                        .accessibilityHidden(true)
                    ZStack(alignment: .leading) {
                        TextField("", text: $name, prompt: Text("Your name").foregroundStyle(theme.muted.opacity(0.55)))
                            .bodyFont(nameFont, .semibold)
                            .foregroundStyle(.clear)
                            .tint(theme.accent)
                            .focused($focused)
                            .textInputAutocapitalization(.words)
                            .autocorrectionDisabled()
                            .submitLabel(.next)
                            .onSubmit(onSubmit)
                            // One word (it's the first name that travels) and not too long to fit.
                            .onChange(of: name) { _, new in
                                let cleaned = String(new.filter { !$0.isWhitespace }.prefix(30))
                                if cleaned != new { name = cleaned }
                            }
                            .accessibilityLabel("Your first name")
                        IntroNameSlot(page: page.id, scale: nameFont / 15, tone: .accent)
                            .allowsHitTesting(false)
                    }
                    Capsule()
                        .fill(theme.accent.opacity(focused ? 1 : 0.3))
                        .frame(height: 3)
                        .animation(.easeOut(duration: 0.2), value: focused)
                }
                .contentShape(Rectangle())
                .onTapGesture { focused = true }
                .entrance(shown, delay: 0, reduceMotion: reduceMotion)
                // Slides up into the space above the keyboard (moved, not relaid out).
                .offset(y: -keyboardLift / 2)
                Text(page.eyebrow)
                    .displayFont(24, .heavy, relativeTo: .title2)
                    .foregroundStyle(theme.accentInk)
                    .accessibilityAddTraits(.isHeader)
                    .padding(.top, 22)
                    .entrance(shown, delay: 0.07, reduceMotion: reduceMotion)
                    .offset(y: -keyboardLift / 2)
                Spacer(minLength: 12)
            }
            .padding(.horizontal, 28)
            .frame(width: proxy.size.width, height: proxy.size.height, alignment: .leading)
        }
    }

    /// The name's size: as big as "Hi", or smaller so a long name still fits on the line.
    private func fittedSize(width: CGFloat) -> CGFloat {
        // `nameSize` is measured at 15 × the draw scale.
        let natural = nameSize.width * Self.size / (15 * IntroNameSlot.drawScale)
        guard natural > width, natural > 0 else { return Self.size }
        return max(24, (Self.size * width / natural).rounded(.down))
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
            title: "",
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
