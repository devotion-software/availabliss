import SwiftUI

// The intro's little animated scenes, built from the app's own pieces: a group card, calendar rows
// turning into busy time, the month grid lighting up, a proposal filling with replies, and a chat.
// Each is drawn at 340 × 320 and plays while its page shows (with Reduce Motion, its last frame).
// Each leaves a space (`IntroNameSlot`) where your name lands on that page, sized like the names around it.

// MARK: - Playback

/// Steps a scene through its frames (`gaps` = seconds before each) while its page shows, holds the
/// last one, then rests and plays again. Leaving the page puts it back to the start once it's off screen.
private struct Playback: ViewModifier {
    @Binding var step: Int
    let isActive: Bool
    let gaps: [Double]
    let hold: Double
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    func body(content: Content) -> some View {
        content.task(id: isActive) { await play() }
    }

    private func play() async {
        guard isActive else {
            guard await pause(0.6) else { return }
            var transaction = Transaction()
            transaction.disablesAnimations = true
            withTransaction(transaction) { step = 0 }
            return
        }
        if reduceMotion {
            withAnimation(.easeOut(duration: 0.2)) { step = gaps.count }
            return
        }
        while !Task.isCancelled {
            for (index, gap) in gaps.enumerated() {
                guard await pause(gap) else { return }
                withAnimation(.spring(response: 0.5, dampingFraction: 0.78)) { step = index + 1 }
            }
            guard await pause(hold) else { return }
            withAnimation(.easeInOut(duration: 0.5)) { step = 0 }
            guard await pause(0.4) else { return }
        }
    }

    /// False when the page changed while waiting.
    private func pause(_ seconds: Double) async -> Bool {
        try? await Task.sleep(for: .seconds(seconds))
        return !Task.isCancelled
    }
}

extension View {
    fileprivate func playback(_ step: Binding<Int>, isActive: Bool, gaps: [Double], hold: Double = 3) -> some View {
        modifier(Playback(step: step, isActive: isActive, gaps: gaps, hold: hold))
    }
}

/// A made-up friend in the scenes.
private struct Friend {
    let name: String
    let tile: String
}

private let friends = [
    Friend(name: "You", tile: "t2"), Friend(name: "Alex", tile: "t1"), Friend(name: "Priya", tile: "t3"),
    Friend(name: "Jo", tile: "t4"), Friend(name: "Tom", tile: "t5"),
]

// MARK: - 0. Welcome

/// The logo and "Hello," with your name under it, filled in as you type.
struct WelcomeIllustration: View {
    let page: Int
    var body: some View {
        VStack(spacing: 18) {
            VennLogo(size: 84)
            VStack(spacing: 6) {
                Text("Hello,").displayFont(34, .heavy)
                IntroNameSlot(page: page, scale: 34 / 15)
            }
        }
        .offset(y: 6)
    }
}

// MARK: - 1. Make a group

/// A group card filling up as friends join from the invite link.
struct GroupIllustration: View {
    let page: Int
    let isActive: Bool
    @Environment(\.theme) private var theme
    @Environment(\.introName) private var you
    @State private var step = 0

    /// Step 1 sends the link; steps 2–5 bring in a friend each.
    private var members: Int { 1 + max(0, step - 1) }

    var body: some View {
        ZStack {
            // Another of your groups peeking out behind, for depth.
            groupHeader(title: "Book club", monogram: "BC", tile: "t4", subtitle: "6 members")
                .padding(16)
                .frame(width: 260)
                .glass(cornerRadius: 24)
                .rotationEffect(.degrees(-7))
                .offset(x: -24, y: -124)
                .opacity(0.75)
            card.offset(y: -6)
            linkPill
                .offset(y: 112)
                .opacity(step >= 1 ? 1 : 0)
                .scaleEffect(step >= 1 ? 1 : 0.7)
                .offset(y: step >= 1 ? 0 : 18)
        }
        .playback($step, isActive: isActive, gaps: [0.6, 0.7, 0.45, 0.45, 0.45])
    }

    private var card: some View {
        VStack(alignment: .leading, spacing: 14) {
            // Made by you: your name lands here.
            HStack(spacing: 12) {
                GroupTile(monogram: "LD", colorToken: "t3", size: 50, cornerRadius: 16)
                VStack(alignment: .leading, spacing: 4) {
                    Text("Lake District crew").displayFont(18, .bold).lineLimit(1)
                    HStack(spacing: 6) {
                        Text("Made by").bodyFont(13).foregroundStyle(theme.muted)
                        IntroNameSlot(page: page, scale: 13 / 15)
                    }
                }
                Spacer(minLength: 0)
            }
            Rectangle().fill(theme.line).frame(height: 1)
            HStack(spacing: -10) {
                ForEach(Array(friends.enumerated()), id: \.offset) { index, friend in
                    let joined = index < members
                    Avatar(name: index == 0 ? you : friend.name, color: theme.tile(friend.tile), size: 42, border: theme.bg)
                        .scaleEffect(joined ? 1 : 0.3)
                        .opacity(joined ? 1 : 0)
                        .animation(.spring(response: 0.42, dampingFraction: 0.55), value: joined)
                }
                Spacer(minLength: 8)
                Label("Invite", systemImage: "person.badge.plus")
                    .bodyFont(13, .semibold)
                    .foregroundStyle(theme.onAccent)
                    .padding(.horizontal, 12)
                    .frame(height: 34)
                    .background(Capsule().fill(theme.accent))
                    .scaleEffect(step == 1 ? 1.08 : 1)
            }
        }
        .padding(18)
        .frame(width: 300)
        .glass(cornerRadius: 28)
    }

    private func groupHeader(title: String, monogram: String, tile: String, subtitle: String) -> some View {
        HStack(spacing: 12) {
            GroupTile(monogram: monogram, colorToken: tile, size: 50, cornerRadius: 16)
            VStack(alignment: .leading, spacing: 2) {
                Text(title).displayFont(18, .bold).lineLimit(1)
                Text(subtitle)
                    .bodyFont(13)
                    .foregroundStyle(theme.muted)
                    .contentTransition(.numericText())
            }
            Spacer(minLength: 0)
        }
    }

    private var linkPill: some View {
        HStack(spacing: 8) {
            Image(systemName: "link").font(.system(size: 12, weight: .bold))
                .foregroundStyle(theme.accentInk)
                .frame(width: 26, height: 26)
                .background(Circle().fill(theme.accentSoft))
            Text("Invite link copied").bodyFont(13, .semibold)
            Image(systemName: "checkmark").font(.system(size: 11, weight: .heavy)).foregroundStyle(theme.ok)
        }
        .padding(.leading, 6)
        .padding(.trailing, 14)
        .frame(height: 38)
        .glass(in: Capsule())
    }
}

// MARK: - 2. Connect your calendars

/// Your day's events turning, one by one, into plain busy time: all the group ever sees.
struct CalendarIllustration: View {
    let page: Int
    let isActive: Bool
    @Environment(\.theme) private var theme
    @State private var step = 0

    private let events: [(time: String, title: String, color: UInt32)] = [
        ("09:30", "Dentist", 0xFF8A5E),
        ("13:00", "Lunch with Mum", 0x5B8DEF),
        ("19:00", "Five-a-side", 0x2FB98A),
    ]

    var body: some View {
        VStack(spacing: 18) {
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    // Whose day it is: your name lands here.
                    VStack(alignment: .leading, spacing: 3) {
                        HStack(spacing: 0) {
                            IntroNameSlot(page: page, scale: 17 / 15)
                            Text("\u{2019}s Tuesday").bodyFont(17, .semibold)
                        }
                        ZStack(alignment: .leading) {
                            Text("In your calendar").opacity(step >= 1 ? 0 : 1)
                            Text("As your group sees it").opacity(step >= 1 ? 1 : 0)
                        }
                        .bodyFont(13, .semibold)
                        .foregroundStyle(theme.muted)
                    }
                    Spacer()
                    Image(systemName: step >= 1 ? "eye.slash" : "calendar")
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(theme.accentInk)
                        .frame(width: 32, height: 32)
                        .background(Circle().fill(theme.accentSoft))
                        .contentTransition(.symbolEffect(.replace))
                }
                ForEach(Array(events.enumerated()), id: \.offset) { index, event in
                    row(time: event.time, title: event.title, color: Color(hex: event.color), hidden: step >= index + 2)
                }
            }
            .padding(18)
            .frame(width: 300)
            .glass(cornerRadius: 28)

            HStack(spacing: 8) {
                Image(systemName: "lock.shield.fill").font(.system(size: 15)).foregroundStyle(theme.ok)
                Text("Only busy time is shared").bodyFont(13, .semibold)
            }
            .padding(.horizontal, 14)
            .frame(height: 38)
            .glass(in: Capsule())
            .opacity(step >= 5 ? 1 : 0)
            .scaleEffect(step >= 5 ? 1 : 0.7)
            .offset(y: step >= 5 ? 0 : -12)
        }
        .offset(y: 10)
        .playback($step, isActive: isActive, gaps: [0.8, 0.55, 0.4, 0.4, 0.45])
    }

    private func row(time: String, title: String, color: Color, hidden: Bool) -> some View {
        let shape = RoundedRectangle(cornerRadius: 12, style: .continuous)
        return HStack(spacing: 10) {
            Text(time)
                .font(AppFont.body(12, .semibold).monospacedDigit())
                .foregroundStyle(theme.muted)
                .frame(width: 42, alignment: .leading)
            ZStack(alignment: .leading) {
                // The event as it is in your calendar…
                HStack(spacing: 8) {
                    Capsule().fill(color).frame(width: 4, height: 22)
                    Text(title).bodyFont(14, .semibold).lineLimit(1)
                    Spacer(minLength: 0)
                }
                .padding(.horizontal, 8)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(shape.fill(color.opacity(0.18)))
                .blur(radius: hidden ? 8 : 0)
                .opacity(hidden ? 0 : 1)
                // …and as your group sees it.
                HStack(spacing: 6) {
                    Text("Busy").bodyFont(13, .semibold).foregroundStyle(theme.muted)
                    Spacer(minLength: 0)
                }
                .padding(.horizontal, 12)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background {
                    ZStack {
                        shape.fill(theme.busyBg)
                        Stripes(color: theme.busyLine.opacity(0.35), lineWidth: 1.5, spacing: 7).clipShape(shape)
                    }
                    .overlay(shape.strokeBorder(theme.glassBorder, lineWidth: 1))
                }
                .opacity(hidden ? 1 : 0)
                .scaleEffect(hidden ? 1 : 0.94)
            }
            .frame(height: 44)
        }
    }
}

// MARK: - 3. The group calendar

/// A few weeks of the month grid: busy days darken, days outside the timing fade, then the days that
/// work for everyone light up in the accent, in a wave.
struct MonthIllustration: View {
    let page: Int
    let isActive: Bool
    @Environment(\.theme) private var theme
    @Environment(\.introName) private var you
    @State private var step = 0

    private enum Cell { case works, busy, outside, free }

    private let cells: [Cell] = [
        .outside, .busy, .outside, .free, .works, .works, .busy,
        .outside, .busy, .outside, .works, .busy, .works, .works,
        .outside, .outside, .busy, .free, .works, .busy, .works,
    ]
    private let firstDay = 9
    /// The day picked out at the end (Saturday the 14th).
    private let picked = 5

    var body: some View {
        VStack(spacing: 16) {
            VStack(alignment: .leading, spacing: 10) {
                HStack(alignment: .top) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("June").displayFont(20, .bold)
                        // Who's in it: your name lands here.
                        HStack(spacing: 4) {
                            IntroNameSlot(page: page, scale: 12 / 15)
                            Text("and 3 friends").bodyFont(12).foregroundStyle(theme.muted)
                        }
                    }
                    Spacer()
                    HStack(spacing: -7) {
                        ForEach(Array(friends.prefix(4).enumerated()), id: \.offset) { index, friend in
                            Avatar(name: index == 0 ? you : friend.name, color: theme.tile(friend.tile), size: 24, border: theme.bg)
                        }
                    }
                }
                Grid(horizontalSpacing: 5, verticalSpacing: 5) {
                    GridRow {
                        ForEach(Array(["M", "T", "W", "T", "F", "S", "S"].enumerated()), id: \.offset) { _, day in
                            Text(day).bodyFont(11, .semibold).foregroundStyle(theme.muted)
                                .frame(maxWidth: .infinity, alignment: .leading).padding(.leading, 6)
                        }
                    }
                    ForEach(0..<3, id: \.self) { week in
                        GridRow {
                            ForEach(0..<7, id: \.self) { day in cell(week * 7 + day) }
                        }
                    }
                }
                HStack(spacing: 14) {
                    LegendItem(label: "Works for everyone") { theme.accent }
                    LegendItem(label: "Someone's busy") {
                        theme.busyBg.opacity(0.5).overlay(RoundedRectangle(cornerRadius: 3).strokeBorder(theme.busyLine, lineWidth: 1))
                    }
                }
                .bodyFont(11)
                .foregroundStyle(theme.muted)
                .padding(.top, 2)
            }
            .padding(16)
            .frame(width: 330)
            .glass(cornerRadius: 28)

            HStack(spacing: 8) {
                Image(systemName: "sparkles").font(.system(size: 13, weight: .bold)).foregroundStyle(theme.accentInk)
                Text("Sat 14 June works for everyone").bodyFont(13, .semibold)
            }
            .padding(.horizontal, 14)
            .frame(height: 38)
            .glass(in: Capsule())
            .opacity(step >= 3 ? 1 : 0)
            .scaleEffect(step >= 3 ? 1 : 0.7)
            .offset(y: step >= 3 ? 0 : -14)
        }
        .playback($step, isActive: isActive, gaps: [0.6, 0.6, 1.1], hold: 3.2)
    }

    private func cell(_ index: Int) -> some View {
        let kind = cells[index]
        let revealed = step >= 1
        let lit = step >= 2 && kind == .works
        let selected = step >= 3 && index == picked
        let fade = revealed ? (kind == .busy ? 0.5 : (kind == .outside ? 0.35 : 1)) : 1
        let shape = RoundedRectangle(cornerRadius: 11, style: .continuous)
        // The wave: across, then down, as the days light up.
        let wave = step == 2 ? Double(index % 7 + index / 7) * 0.06 : 0
        return Text("\(firstDay + index)")
            .font(AppFont.body(13, .semibold))
            .foregroundStyle(lit ? theme.onAccent : theme.ink)
            .opacity(fade)
            .padding(.leading, 7)
            .padding(.top, 5)
            .frame(maxWidth: .infinity, minHeight: 38, maxHeight: 38, alignment: .topLeading)
            .background {
                ZStack {
                    shape.fill(theme.surface).overlay(shape.strokeBorder(theme.glassBorder, lineWidth: 1))
                    shape.fill(theme.busyBg).overlay(shape.strokeBorder(theme.glassBorder, lineWidth: 1))
                        .opacity(revealed && kind == .busy ? 1 : 0)
                    shape.fill(theme.accent)
                        .shadow(color: theme.accent.opacity(0.5), radius: 8, y: 6)
                        .opacity(lit ? 1 : 0)
                }
                .opacity(fade)
            }
            .overlay { if selected { shape.strokeBorder(theme.onAccent, lineWidth: 2).padding(1) } }
            .scaleEffect(selected ? 1.12 : (kind == .works && !lit ? 0.94 : 1))
            .zIndex(selected ? 1 : 0)
            .animation(.spring(response: 0.45, dampingFraction: 0.62).delay(wave), value: step)
    }
}

// MARK: - 4. Propose a time

/// A proposal: the replies tick in, then "Everyone's in."
struct ProposalIllustration: View {
    let page: Int
    let isActive: Bool
    @Environment(\.theme) private var theme
    @Environment(\.introName) private var you
    @State private var step = 0

    private var replies: Int { 1 + min(step, 3) }
    private var confirmed: Bool { step >= 4 }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            ZStack(alignment: .leading) {
                HStack(spacing: 8) {
                    Avatar(name: "Alex", color: theme.tile("t1"), size: 24)
                    Text("Alex proposed a time").bodyFont(13, .semibold).foregroundStyle(theme.muted)
                }
                .opacity(confirmed ? 0 : 1)
                HStack(spacing: 8) {
                    Image(systemName: "checkmark.seal.fill").font(.system(size: 17))
                    Text("Everyone's in.").displayFont(17, .bold)
                }
                .foregroundStyle(theme.onAccent)
                .padding(.horizontal, 14)
                .frame(height: 36)
                .background(Capsule().fill(theme.accent).shadow(color: theme.accent.opacity(0.55), radius: 12, y: 6))
                .scaleEffect(confirmed ? 1 : 0.5, anchor: .leading)
                .opacity(confirmed ? 1 : 0)
            }
            .frame(height: 36)
            VStack(alignment: .leading, spacing: 3) {
                Text("Saturday 14 June").displayFont(24, .heavy)
                Text("7pm \u{00B7} The Old Bell").bodyFont(14).foregroundStyle(theme.muted)
            }
            Rectangle().fill(theme.line).frame(height: 1)
            VStack(spacing: 10) {
                ForEach(0..<4, id: \.self) { index in
                    let friend = friends[index]
                    let accepted = index < replies
                    HStack(spacing: 10) {
                        if index == 0 {
                            // Your reply: your name lands here, by your face.
                            Avatar(name: you, color: theme.tile(friend.tile), size: 30)
                            IntroNameSlot(page: page)
                        } else {
                            Avatar(name: friend.name, color: theme.tile(friend.tile), size: 30)
                            Text(friend.name).bodyFont(15, .semibold)
                        }
                        Spacer()
                        replyChip(accepted)
                    }
                }
            }
        }
        .padding(18)
        .frame(width: 300)
        .glass(cornerRadius: 28)
        .overlay {
            RoundedRectangle(cornerRadius: 28, style: .continuous)
                .strokeBorder(theme.accent, lineWidth: 2)
                .opacity(confirmed ? 0.9 : 0)
        }
        .scaleEffect(confirmed ? 1.02 : 1)
        .playback($step, isActive: isActive, gaps: [0.8, 0.55, 0.55, 0.7], hold: 3.2)
    }

    private func replyChip(_ accepted: Bool) -> some View {
        ZStack {
            Text("Waiting")
                .bodyFont(12, .semibold)
                .foregroundStyle(theme.muted)
                .padding(.horizontal, 10)
                .frame(height: 26)
                .background(Capsule().strokeBorder(theme.line2, lineWidth: 1))
                .opacity(accepted ? 0 : 1)
            Label("In", systemImage: "checkmark")
                .labelStyle(ChipLabelStyle())
                .bodyFont(12, .bold)
                .foregroundStyle(theme.okInk)
                .padding(.horizontal, 10)
                .frame(height: 26)
                .background(Capsule().fill(theme.okBg))
                .scaleEffect(accepted ? 1 : 0.4)
                .opacity(accepted ? 1 : 0)
        }
        .animation(.spring(response: 0.38, dampingFraction: 0.55), value: accepted)
    }
}

private struct ChipLabelStyle: LabelStyle {
    func makeBody(configuration: Configuration) -> some View {
        HStack(spacing: 4) {
            configuration.icon.font(.system(size: 10, weight: .heavy))
            configuration.title
        }
    }
}

// MARK: - 5. Chat with the group

/// The group's chat: you ask, the replies arrive (someone's typing first), and your message gets a heart.
struct ChatIllustration: View {
    let page: Int
    let isActive: Bool
    @Environment(\.theme) private var theme
    @State private var step = 0

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 10) {
                GroupTile(monogram: "LD", colorToken: "t3", size: 32, cornerRadius: 11)
                Text("Lake District crew").bodyFont(15, .semibold)
                Spacer()
                Image(systemName: "bubble.left.and.bubble.right.fill")
                    .font(.system(size: 13))
                    .foregroundStyle(theme.accentInk)
            }
            .padding(.bottom, 12)
            Rectangle().fill(theme.line).frame(height: 1)
            // From the top, so your message (and your name over it) stays put as the replies arrive.
            VStack(spacing: 8) {
                mine("Lakes in June, anyone?")
                if step >= 1 {
                    incoming(friends[1], "Saturday works for me!").transition(arrival(mine: false))
                }
                if step == 2 {
                    TypingBubble(friend: friends[2]).transition(arrival(mine: false))
                }
                if step >= 3 {
                    incoming(friends[2], "I'll book the Old Bell.").transition(arrival(mine: false))
                }
                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            .padding(.top, 10)
            .clipped()
        }
        .padding(16)
        .frame(width: 310, height: 290)
        .glass(cornerRadius: 28)
        .playback($step, isActive: isActive, gaps: [0.7, 0.7, 1.1, 0.7], hold: 3)
    }

    private func arrival(mine: Bool) -> AnyTransition {
        .asymmetric(
            insertion: .scale(scale: 0.5, anchor: mine ? .bottomTrailing : .bottomLeading).combined(with: .opacity),
            removal: .opacity
        )
    }

    private func incoming(_ friend: Friend, _ text: String) -> some View {
        HStack(alignment: .bottom, spacing: 8) {
            Avatar(name: friend.name, color: theme.tile(friend.tile), size: 26)
            VStack(alignment: .leading, spacing: 3) {
                Text(friend.name).bodyFont(11, .semibold).foregroundStyle(theme.muted).padding(.leading, 4)
                Text(text)
                    .bodyFont(14)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .background(bubble(mine: false).fill(theme.surface))
                    .overlay(bubble(mine: false).strokeBorder(theme.glassBorder, lineWidth: 1))
            }
            Spacer(minLength: 30)
        }
    }

    private func mine(_ text: String) -> some View {
        HStack {
            Spacer(minLength: 50)
            // Your name lands over your message, where the others' names go over theirs.
            VStack(alignment: .trailing, spacing: 4) {
                IntroNameSlot(page: page, scale: 11 / 15, muted: true).padding(.trailing, 4)
                yourBubble(text)
            }
        }
    }

    private func yourBubble(_ text: String) -> some View {
        Text(text)
            .bodyFont(14)
            .foregroundStyle(theme.onAccent)
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(bubble(mine: true).fill(theme.accent))
            .overlay(alignment: .bottomLeading) {
                Image(systemName: "heart.fill")
                    .font(.system(size: 10, weight: .bold))
                    .foregroundStyle(theme.accentInk)
                    .frame(width: 22, height: 22)
                    .background(Circle().fill(theme.bg))
                    .overlay(Circle().strokeBorder(theme.glassBorder, lineWidth: 1))
                    .offset(x: -8, y: 10)
                    .scaleEffect(step >= 4 ? 1 : 0.2)
                    .opacity(step >= 4 ? 1 : 0)
                    .animation(.spring(response: 0.35, dampingFraction: 0.5), value: step)
            }
            .padding(.bottom, 10)
    }

    private func bubble(mine: Bool) -> UnevenRoundedRectangle {
        UnevenRoundedRectangle(
            topLeadingRadius: 18, bottomLeadingRadius: mine ? 18 : 6, bottomTrailingRadius: mine ? 6 : 18, topTrailingRadius: 18,
            style: .continuous
        )
    }
}

/// Three dots that rise in turn, in an incoming bubble.
private struct TypingBubble: View {
    let friend: Friend
    @Environment(\.theme) private var theme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        HStack(alignment: .bottom, spacing: 8) {
            Avatar(name: friend.name, color: theme.tile(friend.tile), size: 26)
            PhaseAnimator([0, 1, 2]) { phase in
                HStack(spacing: 4) {
                    ForEach(0..<3, id: \.self) { dot in
                        Circle()
                            .fill(theme.muted)
                            .frame(width: 7, height: 7)
                            .opacity(phase == dot && !reduceMotion ? 1 : 0.4)
                            .offset(y: phase == dot && !reduceMotion ? -3 : 0)
                    }
                }
            } animation: { _ in
                .easeInOut(duration: 0.28)
            }
            .padding(.horizontal, 14)
            .frame(height: 34)
            .background(UnevenRoundedRectangle(topLeadingRadius: 18, bottomLeadingRadius: 6, bottomTrailingRadius: 18, topTrailingRadius: 18).fill(theme.surface))
            Spacer()
        }
    }
}
