import SwiftUI

/// One message, dispatched by kind.
struct MessageRow: View {
    let message: Message
    let group: Group
    let me: String?
    let model: ChatViewModel
    let isLatestOwn: Bool
    /// First in a run from this person: incoming messages show their name above.
    var startsRun = true
    let openInfo: () -> Void
    let openProposal: (String) -> Void
    /// Your own text messages: edit it in the composer / ask to delete it.
    var onEdit: () -> Void = {}
    var onDelete: () -> Void = {}
    @Environment(\.theme) private var theme

    private var isMine: Bool { message.authorId != nil && message.authorId == me }
    private var author: Member? { message.authorId.flatMap(group.member) }

    var body: some View {
        switch message.kind {
        case .system:
            // A confirmed proposal ("Everyone's in for …") gets a celebratory banner once its event is loaded.
            if let id = message.eventId, let event = model.events[id] {
                ConfirmedBanner(event: event, messageId: message.id, isRecent: Date.now.timeIntervalSince(message.createdAt) < 24 * 3600) {
                    if let proposalId = event.proposalId { openProposal(proposalId) }
                }
            } else {
                Text(message.body ?? "")
                    .bodyFont(12, relativeTo: .caption)
                    .foregroundStyle(theme.muted)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)
            }
        case .text:
            if message.isDeleted {
                if isMine {
                    DeletedBubble(isMine: true).frame(maxWidth: .infinity, alignment: .trailing)
                } else {
                    IncomingRow(author: author, showsName: startsRun) { DeletedBubble(isMine: false) }
                }
            } else if isMine {
                VStack(alignment: .trailing, spacing: 4) {
                    MessageBubble(text: message.body ?? "", isMine: true)
                        .onTapGesture(perform: openInfo)
                        .contextMenu {
                            Button(action: openInfo) { Label("Info", systemImage: "info.circle") }
                            Button(action: onEdit) { Label("Edit", systemImage: "pencil") }
                            Button(role: .destructive, action: onDelete) { Label("Delete", systemImage: "trash") }
                        }
                        .accessibilityAddTraits(.isButton)
                        .accessibilityHint("Shows message info")
                        .accessibilityAction(named: "Edit", onEdit)
                        .accessibilityAction(named: "Delete", onDelete)
                    if message.isEdited { EditedLabel() }
                    if isLatestOwn, let receipt = message.receipt {
                        ReadReceipt(receipt: receipt, group: group, action: openInfo)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .trailing)
            } else {
                IncomingRow(author: author, showsName: startsRun) {
                    VStack(alignment: .leading, spacing: 4) {
                        MessageBubble(text: message.body ?? "", isMine: false)
                            .onTapGesture(perform: openInfo)
                        if message.isEdited { EditedLabel() }
                    }
                }
                .accessibilityAddTraits(.isButton)
                .accessibilityHint("Shows message info")
            }
        case .poll:
            if let poll = message.poll {
                VStack(alignment: .trailing, spacing: 4) {
                    PollCard(poll: poll, me: me, title: isMine ? "Your poll" : "Poll by \(author?.firstName ?? "Someone")", canClose: isMine, model: model)
                    if isMine, isLatestOwn, let receipt = message.receipt {
                        ReadReceipt(receipt: receipt, group: group, action: openInfo)
                    }
                }
                .frame(maxWidth: .infinity, alignment: isMine ? .trailing : .leading)
            }
        case .proposal:
            if let id = message.proposalId {
                Button { openProposal(id) } label: {
                    if let proposal = model.proposals[id] {
                        ProposalCard(proposal: proposal, group: group, me: me)
                    } else {
                        ProgressView().frame(maxWidth: .infinity, minHeight: 74).glass(cornerRadius: 20)
                    }
                }
                .buttonStyle(.pressable)
            }
        case .event:
            if let id = message.eventId, let event = model.events[id] {
                EventMessageCard(event: event, author: isMine ? "You" : (author?.firstName ?? "Someone"))
            } else if let body = message.body {
                Text(body).bodyFont(12, relativeTo: .caption).foregroundStyle(theme.muted)
            }
        }
    }
}

private struct IncomingRow<Content: View>: View {
    let author: Member?
    var showsName = true
    @ViewBuilder let content: Content
    @Environment(\.theme) private var theme

    var body: some View {
        HStack(alignment: .bottom, spacing: 8) {
            Avatar(name: author?.name ?? "?", imageURL: author?.image, color: theme.tile(forUser: author?.userId ?? ""), size: 28)
            VStack(alignment: .leading, spacing: 3) {
                if showsName {
                    Text(author?.name ?? "Someone")
                        .bodyFont(11, .semibold, relativeTo: .caption2)
                        .foregroundStyle(theme.muted)
                        .lineLimit(1)
                        .padding(.leading, 4)
                }
                content
            }
            Spacer(minLength: 40)
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(author?.firstName ?? "Someone") said")
    }
}

/// Under an edited message.
private struct EditedLabel: View {
    @Environment(\.theme) private var theme
    var body: some View {
        Text("Edited")
            .bodyFont(11, relativeTo: .caption2)
            .foregroundStyle(theme.muted)
            .padding(.horizontal, 4)
    }
}

/// A message its author deleted: a dashed outline in the bubble's place.
private struct DeletedBubble: View {
    let isMine: Bool
    @Environment(\.theme) private var theme

    var body: some View {
        let shape = UnevenRoundedRectangle(
            topLeadingRadius: 18,
            bottomLeadingRadius: isMine ? 18 : 6,
            bottomTrailingRadius: isMine ? 6 : 18,
            topTrailingRadius: 18,
            style: .continuous
        )
        Text(isMine ? "You deleted this message" : "This message was deleted")
            .bodyFont(14)
            .italic()
            .foregroundStyle(theme.muted)
            .padding(.horizontal, 12)
            .padding(.vertical, 9)
            .background(shape.strokeBorder(theme.line3, style: StrokeStyle(lineWidth: 1, dash: [4, 3])))
            .accessibilityLabel("Message deleted")
    }
}

/// Glass bubble for others (tail bottom-left), `solid` for you (tail bottom-right).
struct MessageBubble: View {
    let text: String
    let isMine: Bool
    @Environment(\.theme) private var theme

    var body: some View {
        let shape = UnevenRoundedRectangle(
            topLeadingRadius: 18,
            bottomLeadingRadius: isMine ? 18 : 6,
            bottomTrailingRadius: isMine ? 6 : 18,
            topTrailingRadius: 18,
            style: .continuous
        )
        Text(text)
            .bodyFont(14)
            .lineSpacing(2)
            .foregroundStyle(isMine ? theme.onSolid : theme.ink)
            .padding(.horizontal, 12)
            .padding(.vertical, 9)
            .background {
                if isMine { shape.fill(theme.solid) } else { Color.clear.modifier(GlassBackground(shape: shape, shadow: false)) }
            }
            .frame(maxWidth: 290, alignment: isMine ? .trailing : .leading)
            .textSelection(.enabled)
    }
}

/// "Seen by 3 of 5" with the avatars of who's seen it.
struct ReadReceipt: View {
    let receipt: Message.Receipt
    let group: Group
    let action: () -> Void
    @Environment(\.theme) private var theme

    var body: some View {
        Button(action: action) {
            HStack(spacing: 6) {
                if !receipt.seenBy.isEmpty {
                    AvatarStack(
                        people: receipt.seenBy.compactMap { group.member($0)?.avatarPerson },
                        size: 16, overlap: 4, maxVisible: 4, border: theme.bg
                    )
                }
                Text(label)
                Image(systemName: receipt.seen > 0 ? "checkmark.circle.fill" : "checkmark")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundStyle(receipt.seen > 0 ? theme.accentInk : theme.muted)
            }
            .bodyFont(11, .medium, relativeTo: .caption2)
            .foregroundStyle(theme.muted)
            .frame(minHeight: 24)
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(label). Shows message info")
    }

    private var label: String {
        if receipt.seen > 0 { return "Seen by \(receipt.seen) of \(receipt.recipients)" }
        if receipt.delivered > 0 { return "Delivered to \(receipt.delivered) of \(receipt.recipients)" }
        return "Sent"
    }
}

/// Poll with live bars; tap an option to vote (again to remove).
struct PollCard: View {
    let poll: Poll
    let me: String?
    /// Who asked: "Your poll" or "Poll by Ana".
    let title: String
    let canClose: Bool
    let model: ChatViewModel
    @Environment(\.theme) private var theme

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 8) {
                Image(systemName: "chart.bar").font(.system(size: 14, weight: .semibold))
                Text("\(title) · \(Formatting.plural(poll.totalVotes, "vote"))\(poll.closed ? " · closed" : "")")
                    .bodyFont(12, .semibold, relativeTo: .caption)
                Spacer()
                if canClose && !poll.closed {
                    Menu {
                        Button("Close poll") { Task { await model.closePoll(poll) } }
                    } label: {
                        Image(systemName: "ellipsis").frame(width: 32, height: 32)
                    }
                    .accessibilityLabel("Poll options")
                }
            }
            .foregroundStyle(theme.accentInk)
            Text(poll.question).displayFont(17, .semibold, relativeTo: .headline)
            ForEach(poll.options) { option in
                PollOptionRow(option: option, poll: poll, me: me) {
                    Task { await model.vote(poll: poll, option: option) }
                }
            }
        }
        .padding(14)
        .frame(maxWidth: 300)
        .glass(cornerRadius: 22)
        .accessibilityElement(children: .contain)
        .accessibilityLabel("Poll: \(poll.question)")
    }
}

private struct PollOptionRow: View {
    let option: Poll.Option
    let poll: Poll
    let me: String?
    let vote: () -> Void
    @Environment(\.theme) private var theme

    private var isMine: Bool { me.map(option.voterIds.contains) ?? false }
    private var share: Double { poll.totalVotes == 0 ? 0 : Double(option.voterIds.count) / Double(poll.totalVotes) }
    private var isLeading: Bool { option.voterIds.count > 0 && option.voterIds.count == poll.options.map(\.voterIds.count).max() }

    var body: some View {
        Button(action: vote) {
            HStack(spacing: 8) {
                ZStack {
                    if isMine {
                        Circle().fill(theme.accent)
                        Image(systemName: "checkmark").font(.system(size: 9, weight: .heavy)).foregroundStyle(.white)
                    } else {
                        Circle().strokeBorder(theme.line3, lineWidth: 2)
                    }
                }
                .frame(width: 18, height: 18)
                Text(option.label).bodyFont(14, .semibold).foregroundStyle(theme.ink)
                Spacer()
                Text("\(option.voterIds.count)").bodyFont(13, .semibold, relativeTo: .subheadline).foregroundStyle(theme.neutralInk)
            }
            .padding(.horizontal, 12)
            .frame(minHeight: 46)
            .background(alignment: .leading) {
                GeometryReader { proxy in
                    Rectangle()
                        .fill(isLeading ? theme.accentSoft : theme.line)
                        .frame(width: proxy.size.width * share)
                        .animation(.spring(response: 0.45, dampingFraction: 0.7), value: share)
                }
            }
            .background(theme.surface)
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).strokeBorder(isMine ? theme.accent : theme.glassHi, lineWidth: 1))
        }
        .buttonStyle(.pressable)
        .disabled(poll.closed)
        .accessibilityLabel("\(option.label), \(Formatting.plural(option.voterIds.count, "vote"))")
        .accessibilityAddTraits(isMine ? .isSelected : [])
    }
}

private struct EventMessageCard: View {
    let event: GroupEvent
    let author: String
    @Environment(\.theme) private var theme

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            if let image = event.image { EventCover(imageURL: image, height: 120, cornerRadius: 12) }
            HStack(spacing: 12) {
                DateBlock(date: event.start)
                VStack(alignment: .leading, spacing: 2) {
                    Text("\(author) added an event").bodyFont(12, relativeTo: .caption).foregroundStyle(theme.muted)
                    Text(event.title).bodyFont(15, .semibold)
                    Text(Formatting.span(start: event.start, end: event.end, allDay: event.allDay)).bodyFont(12, relativeTo: .caption).foregroundStyle(theme.muted)
                }
            }
            AddToCalendarButton(event: event, height: 40)
        }
        .padding(12)
        .glass(cornerRadius: 20)
    }
}

/// "Everyone's in": a confirmed plan, celebrated in the chat. Bursts confetti the first time a
/// recent one appears in this session.
private struct ConfirmedBanner: View {
    let event: GroupEvent
    let messageId: String
    let isRecent: Bool
    let open: () -> Void
    @Environment(\.theme) private var theme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var burst = 0

    /// Banners that have already burst this session.
    @MainActor private static var burstIds = Set<String>()

    private var when: String {
        let span = Formatting.span(start: event.start, end: event.end, allDay: event.allDay)
        return event.location.map { "\(span) \u{00B7} \($0)" } ?? span
    }

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 20, style: .continuous)
        VStack(alignment: .leading, spacing: 12) {
            Button(action: open) {
                HStack(spacing: 12) {
                    Image(systemName: "checkmark")
                        .font(.system(size: 18, weight: .heavy))
                        .foregroundStyle(theme.onAccent)
                        .frame(width: 44, height: 44)
                        .background(Circle().fill(theme.ok))
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Everyone\u{2019}s in").bodyFont(12, .semibold, relativeTo: .caption).foregroundStyle(theme.okInk)
                        Text(event.title).displayFont(17, .bold, relativeTo: .headline).foregroundStyle(theme.ink).lineLimit(2)
                        Text(when).bodyFont(12, relativeTo: .caption).foregroundStyle(theme.muted).lineLimit(2)
                    }
                    .multilineTextAlignment(.leading)
                    Spacer(minLength: 0)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("Everyone\u{2019}s in. \(event.title), \(when)")
            .accessibilityAddTraits(event.proposalId == nil ? [] : .isButton)
            AddToCalendarButton(event: event, height: 40)
        }
        .padding(14)
        .background {
            ZStack(alignment: .topTrailing) {
                shape.fill(theme.surface)
                shape.fill(theme.okBg)
                ConfettiSprinkle().padding(.top, 8).padding(.trailing, 12)
            }
        }
        .overlay(shape.strokeBorder(theme.ok.opacity(0.35), lineWidth: 1))
        .clipShape(shape)
        .overlay(alignment: .top) {
            ConfettiBurst(trigger: burst).allowsHitTesting(false).accessibilityHidden(true)
        }
        .zIndex(1)
        .sensoryFeedback(.success, trigger: burst)
        .onAppear {
            guard isRecent, !reduceMotion, !Self.burstIds.contains(messageId) else { return }
            Self.burstIds.insert(messageId)
            burst += 1
        }
    }
}

/// The colours confetti is cut from.
private extension Theme {
    var confetti: [Color] { [accent, ok, warn, tile("t4"), tile("t2")] }
}

/// A few still bits of confetti in the banner's corner.
private struct ConfettiSprinkle: View {
    @Environment(\.theme) private var theme
    private static let bits: [(x: CGFloat, y: CGFloat, angle: Double, round: Bool)] = [
        (0, 4, 20, false), (-22, 18, -35, true), (-44, 2, 60, false), (-10, 34, 75, false),
        (-60, 26, -15, true), (-34, 44, 40, false), (-78, 10, 10, false), (4, 54, -50, true),
    ]

    var body: some View {
        ZStack(alignment: .topTrailing) {
            ForEach(Array(Self.bits.enumerated()), id: \.offset) { index, bit in
                let color = theme.confetti[index % theme.confetti.count]
                SwiftUI.Group {
                    if bit.round { Circle().fill(color).frame(width: 5, height: 5) } else { RoundedRectangle(cornerRadius: 1.5).fill(color).frame(width: 4, height: 9) }
                }
                .rotationEffect(.degrees(bit.angle))
                .offset(x: bit.x, y: bit.y)
            }
        }
        .frame(width: 90, height: 64, alignment: .topTrailing)
        .opacity(0.6)
        .accessibilityHidden(true)
    }
}

/// A one-off burst: pieces fly up and out from the top centre, then fall and fade.
private struct ConfettiBurst: View {
    let trigger: Int
    @Environment(\.theme) private var theme
    @State private var start: Date?

    private struct Piece {
        let vx: Double, vy: Double, spin: Double, size: CGSize, round: Bool, color: Int
    }
    private static let duration = 1.6
    private static let pieces: [Piece] = (0..<36).map { i in
        var rng = SeededRandom(seed: UInt64(i) &* 2654435761 &+ 7)
        let angle = Double.pi * (1.1 + 0.8 * rng.next())   // up and out
        let speed = 260 + 260 * rng.next()
        return Piece(
            vx: cos(angle) * speed, vy: sin(angle) * speed,
            spin: (rng.next() - 0.5) * 1440,
            size: rng.next() < 0.3 ? CGSize(width: 6, height: 6) : CGSize(width: 6, height: 10),
            round: rng.next() < 0.3, color: i
        )
    }

    var body: some View {
        TimelineView(.animation(paused: start == nil)) { context in
            Canvas { canvas, size in
                guard let start else { return }
                let t = context.date.timeIntervalSince(start)
                guard t < Self.duration else { return }
                let origin = CGPoint(x: size.width / 2, y: 160)  // just inside the banner's top (the canvas starts 140 above it)
                let colors = theme.confetti
                for piece in Self.pieces {
                    let x = origin.x + piece.vx * t
                    let y = origin.y + piece.vy * t + 0.5 * 900 * t * t
                    var ctx = canvas
                    ctx.opacity = max(0, 1 - t / Self.duration)
                    ctx.translateBy(x: x, y: y)
                    ctx.rotate(by: .degrees(piece.spin * t))
                    let rect = CGRect(x: -piece.size.width / 2, y: -piece.size.height / 2, width: piece.size.width, height: piece.size.height)
                    let path = piece.round ? Path(ellipseIn: rect) : Path(roundedRect: rect, cornerRadius: 1.5)
                    ctx.fill(path, with: .color(colors[piece.color % colors.count]))
                }
            }
        }
        .frame(height: 360)
        .offset(y: -140)
        .onChange(of: trigger) { _, _ in
            start = .now
            Task {
                try? await Task.sleep(for: .seconds(Self.duration))
                start = nil
            }
        }
    }
}

/// Small deterministic generator so the burst looks the same every time.
private struct SeededRandom {
    var state: UInt64
    init(seed: UInt64) { state = seed == 0 ? 0x9E3779B97F4A7C15 : seed }
    mutating func next() -> Double {
        state ^= state << 13
        state ^= state >> 7
        state ^= state << 17
        return Double(state % 10_000) / 10_000
    }
}
