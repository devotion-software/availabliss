import SwiftUI

/// The first-launch intro: five pages on what Availabliss does, shown once before sign-in.
/// Swipe or tap Next; Skip or Get started goes on to sign in.
struct IntroView: View {
    let onFinish: () -> Void
    @Environment(\.theme) private var theme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var page: Int? = 0

    private var current: Int { page ?? 0 }
    private var isLast: Bool { current == IntroPage.all.count - 1 }

    var body: some View {
        VStack(spacing: 0) {
            topBar
            ScrollView(.horizontal) {
                LazyHStack(spacing: 0) {
                    ForEach(IntroPage.all) { item in
                        IntroPageView(page: item, isActive: item.id == current)
                            .containerRelativeFrame(.horizontal)
                            .id(item.id)
                    }
                }
                .scrollTargetLayout()
            }
            .scrollTargetBehavior(.paging)
            .scrollIndicators(.hidden)
            .scrollPosition(id: $page)
            .scrollBounceBehavior(.basedOnSize)
            controls
        }
        .foregroundStyle(theme.ink)
        .background {
            // The orbs drift to a new place for each page, ending where sign-in's sit.
            ScreenBackground(orbs: IntroPage.all[current].orbs)
                .animation(reduceMotion ? nil : .easeInOut(duration: 0.9), value: current)
        }
        .sensoryFeedback(.selection, trigger: current)
    }

    private var topBar: some View {
        HStack(spacing: 10) {
            VennLogo(size: 30)
            Text("Availabliss").displayFont(19, .bold)
            Spacer()
            Button("Skip", action: onFinish)
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
            PageDots(count: IntroPage.all.count, current: current)
            Button {
                if isLast {
                    onFinish()
                } else {
                    withAnimation(.spring(response: 0.5, dampingFraction: 0.9)) { page = current + 1 }
                }
            } label: {
                ZStack {
                    // Both labels are laid out so the button never changes size; they cross-fade.
                    Text("Next").opacity(isLast ? 0 : 1)
                    HStack(spacing: 8) {
                        Text("Get started")
                        Image(systemName: "arrow.right").fontWeight(.semibold)
                    }
                    .opacity(isLast ? 1 : 0)
                }
                .animation(.easeInOut(duration: 0.25), value: isLast)
            }
            .buttonStyle(.filled(isLast ? .accent : .primary, height: 54, cornerRadius: 18))
            .accessibilityLabel(isLast ? "Get started" : "Next")
            .accessibilityValue("Page \(current + 1) of \(IntroPage.all.count)")
        }
        .padding(.horizontal, 24)
        .padding(.top, 8)
        .padding(.bottom, 12)
    }
}

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

/// One page: its illustration above, title and a line of text below.
private struct IntroPageView: View {
    let page: IntroPage
    let isActive: Bool
    @Environment(\.theme) private var theme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// The text rises in the first time the page shows.
    @State private var shown = false

    var body: some View {
        GeometryReader { proxy in
            // Smaller illustration on shorter phones, so the text always fits under it.
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
        .onAppear { if isActive { shown = true } }
        .onChange(of: isActive) { _, active in if active { shown = true } }
    }

    @ViewBuilder private var illustration: some View {
        switch page.kind {
        case .group: GroupIllustration(isActive: isActive)
        case .calendars: CalendarIllustration(isActive: isActive)
        case .grid: MonthIllustration(isActive: isActive)
        case .proposal: ProposalIllustration(isActive: isActive)
        case .chat: ChatIllustration(isActive: isActive)
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
    enum Kind { case group, calendars, grid, proposal, chat }

    let id: Int
    let kind: Kind
    let eyebrow: String
    let title: String
    let body: String
    let orbs: [OrbSpec]

    static let all: [IntroPage] = [
        IntroPage(
            id: 0, kind: .group, eyebrow: "Make a group",
            title: "Bring your people together.",
            body: "Start a group for friends, family or a team, and invite everyone with a link.",
            orbs: OrbLayout.groups
        ),
        IntroPage(
            id: 1, kind: .calendars, eyebrow: "Connect your calendars",
            title: "Your plans stay private.",
            body: "Connect your calendars once. Your group only ever sees when you're busy, never what you're doing.",
            orbs: OrbLayout.calendars
        ),
        IntroPage(
            id: 2, kind: .grid, eyebrow: "See what works",
            title: "Spot the days everyone's free.",
            body: "The group calendar lights up the days and times that work for everyone. No more \u{201C}what about Thursday?\u{201D}",
            orbs: OrbLayout.groupCalendar
        ),
        IntroPage(
            id: 3, kind: .proposal, eyebrow: "Propose a time",
            title: "Suggest it. Everyone replies.",
            body: "Pick a time and the group says yes, maybe or no. When everyone's in, it's a plan.",
            orbs: OrbLayout.proposal
        ),
        IntroPage(
            id: 4, kind: .chat, eyebrow: "Chat with the group",
            title: "Talk it through together.",
            body: "Sort out the details in the group chat: where to go, who's driving, what to bring.",
            orbs: OrbLayout.signIn
        ),
    ]
}
