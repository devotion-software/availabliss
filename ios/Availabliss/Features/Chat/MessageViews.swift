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
    /// Your own text messages: edit it in the composer / ask to delete it (nil hides them).
    var onEdit: (() -> Void)?
    var onDelete: (() -> Void)?
    /// Start a reply to this message in the composer.
    var onReply: () -> Void = {}
    /// Tapped the quote above a reply (by the original's id).
    var openQuote: (String) -> Void = { _ in }
    /// Tapped a photo / a file in the message.
    var openImage: (Attachment) -> Void = { _ in }
    var openFile: (Attachment) -> Void = { _ in }
    /// The file being downloaded to open.
    var openingFileId: String?
    /// Open the thread this message is part of (nil hides "View thread").
    var onOpenThread: (() -> Void)?
    /// In a thread: go to this message in the chat.
    var onShowInChat: (() -> Void)?
    /// Whether the menu has "Info" (tapping the bubble runs `openInfo` either way).
    var showsInfo = true
    var tapHint = "Shows message info"
    var quoteHint = "Shows the thread"
    @Environment(\.theme) private var theme

    private var isMine: Bool { message.authorId != nil && message.authorId == me }
    private var author: Member? { message.authorId.flatMap(group.member) }

    private var quote: MessageQuote? {
        message.replyTo.map { MessageQuote(preview: $0, name: ReplyNames.name(of: $0.authorId, fallback: $0.authorName, group: group, me: me)) }
    }

    /// The long-press menu on texts and polls.
    @ViewBuilder private var menu: some View {
        Button(action: onReply) { Label("Reply", systemImage: "arrowshape.turn.up.left") }
        if let onOpenThread { Button(action: onOpenThread) { Label("View thread", systemImage: "bubble.left.and.bubble.right") } }
        if let onShowInChat { Button(action: onShowInChat) { Label("Show in chat", systemImage: "text.bubble") } }
        if showsInfo, message.kind == .text { Button(action: openInfo) { Label("Info", systemImage: "info.circle") } }
        if isMine, message.kind == .text {
            if let onEdit, message.place == nil { Button(action: onEdit) { Label("Edit", systemImage: "pencil") } }
            if let onDelete { Button(role: .destructive, action: onDelete) { Label("Delete", systemImage: "trash") } }
        }
    }

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
                    MessageBubble(text: message.text ?? "", isMine: true, quote: quote, quoteHint: quoteHint, openQuote: openQuote, attachments: message.files, location: message.place, openImage: openImage, openFile: openFile, openingFileId: openingFileId)
                        .onTapGesture(perform: openInfo)
                        .contextMenu { menu }
                        .accessibilityAddTraits(.isButton)
                        .accessibilityHint(tapHint)
                        .accessibilityAction(named: "Reply", onReply)
                        .accessibilityActions { extraActions }
                    if message.isEdited { EditedLabel() }
                    if isLatestOwn, let receipt = message.receipt {
                        ReadReceipt(receipt: receipt, group: group, action: openInfo)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .trailing)
            } else {
                IncomingRow(author: author, showsName: startsRun) {
                    VStack(alignment: .leading, spacing: 4) {
                        MessageBubble(text: message.text ?? "", isMine: false, quote: quote, quoteHint: quoteHint, openQuote: openQuote, attachments: message.files, location: message.place, openImage: openImage, openFile: openFile, openingFileId: openingFileId)
                            .onTapGesture(perform: openInfo)
                            .contextMenu { menu }
                        if message.isEdited { EditedLabel() }
                    }
                }
                .accessibilityAddTraits(.isButton)
                .accessibilityHint(tapHint)
                .accessibilityAction(named: "Reply", onReply)
                .accessibilityActions { extraActions }
            }
        case .poll:
            if let poll = message.poll {
                VStack(alignment: .trailing, spacing: 4) {
                    PollCard(poll: poll, me: me, title: isMine ? "Your poll" : "Poll by \(author?.firstName ?? "Someone")", canClose: isMine, model: model)
                        .contextMenu { menu }
                        .accessibilityAction(named: "Reply", onReply)
                        .accessibilityActions { extraActions }
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

extension MessageRow {
    /// VoiceOver actions beyond Reply, matching the menu.
    @ViewBuilder fileprivate var extraActions: some View {
        if let onOpenThread { Button("View thread", action: onOpenThread) }
        if let onShowInChat { Button("Show in chat", action: onShowInChat) }
        if isMine, message.kind == .text {
            if let onEdit, message.place == nil { Button("Edit", action: onEdit) }
            if let onDelete { Button("Delete", action: onDelete) }
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

/// Who a quoted message is from, as the chat names people.
enum ReplyNames {
    static func name(of authorId: String?, fallback: String?, group: Group, me: String?) -> String {
        if let authorId, authorId == me { return "You" }
        return authorId.flatMap(group.member)?.name ?? fallback ?? "Someone"
    }
}

/// The quoted message above a reply's text.
struct MessageQuote: Equatable {
    let preview: Message.ReplyPreview
    let name: String
}

/// Swipe a message right to reply: it follows your finger, an arrow fades in behind it, and past
/// the threshold it clicks; let go there to reply.
struct SwipeToReply: ViewModifier {
    let isEnabled: Bool
    let onReply: () -> Void
    @State private var dragX: CGFloat = 0
    @State private var armed = false
    @Environment(\.theme) private var theme

    private static let threshold: CGFloat = 56

    func body(content: Content) -> some View {
        if isEnabled {
            content
                .offset(x: dragX)
                .background(alignment: .leading) {
                    Image(systemName: "arrowshape.turn.up.left.fill")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundStyle(armed ? theme.onAccent : theme.accentInk)
                        .frame(width: 30, height: 30)
                        .background(Circle().fill(armed ? theme.accent : theme.accentSoft))
                        .scaleEffect(armed ? 1 : 0.7 + 0.3 * min(dragX / Self.threshold, 1))
                        .opacity(min(dragX / Self.threshold, 1))
                        .offset(x: min(dragX, Self.threshold) - 38)
                        .accessibilityHidden(true)
                }
                .sensoryFeedback(.impact(weight: .light), trigger: armed) { _, now in now }
                .simultaneousGesture(
                    DragGesture(minimumDistance: 20)
                        .onChanged { drag in
                            let dx = drag.translation.width, dy = drag.translation.height
                            guard dx > 0, abs(dx) > abs(dy) * 1.5 || dragX > 0 else { return }
                            // Follows the finger, then resists past the threshold.
                            dragX = dx <= Self.threshold ? dx : Self.threshold + (dx - Self.threshold) * 0.25
                            armed = dx >= Self.threshold
                        }
                        .onEnded { _ in
                            if armed { onReply() }
                            armed = false
                            withAnimation(.spring(response: 0.3, dampingFraction: 0.8)) { dragX = 0 }
                        }
                )
        } else {
            content
        }
    }
}

/// Stacks a quote over the text at the width of the wider of the two (up to what's offered),
/// so the quote spans a long reply but a short reply doesn't stretch to the quote.
private struct QuoteStack: Layout {
    var spacing: CGFloat

    private func width(_ proposal: ProposedViewSize, _ subviews: Subviews) -> CGFloat {
        let ideal = subviews.map { $0.sizeThatFits(.unspecified).width }.max() ?? 0
        return min(ideal, proposal.width ?? .infinity)
    }

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let w = width(proposal, subviews)
        let heights = subviews.map { $0.sizeThatFits(ProposedViewSize(width: w, height: nil)).height }
        return CGSize(width: w, height: heights.reduce(0, +) + spacing * CGFloat(max(subviews.count - 1, 0)))
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var y = bounds.minY
        for subview in subviews {
            let size = ProposedViewSize(width: bounds.width, height: nil)
            let height = subview.sizeThatFits(size).height
            subview.place(at: CGPoint(x: bounds.minX, y: y), anchor: .topLeading, proposal: ProposedViewSize(width: bounds.width, height: height))
            y += height + spacing
        }
    }
}

/// "Ana" over one line of what they said, with a bar down the side. Tapping it opens the thread.
private struct QuoteBlock: View {
    let quote: MessageQuote
    let isMine: Bool
    var hint = "Shows the thread"
    let open: (String) -> Void
    @Environment(\.theme) private var theme

    var body: some View {
        Button { open(quote.preview.id) } label: {
            HStack(spacing: 8) {
                RoundedRectangle(cornerRadius: 1.5)
                    .fill(isMine ? theme.onAccent.opacity(0.6) : theme.accentInk)
                    .frame(width: 3)
                VStack(alignment: .leading, spacing: 1) {
                    Text(quote.name)
                        .bodyFont(12, .semibold, relativeTo: .caption)
                        .foregroundStyle(isMine ? theme.onAccent : theme.accentInk)
                        .lineLimit(1)
                    Text(quote.preview.snippet)
                        .bodyFont(12, relativeTo: .caption)
                        .italic(quote.preview.deleted)
                        .foregroundStyle(isMine ? theme.onAccent.opacity(0.82) : theme.muted)
                        .lineLimit(1)
                }
                Spacer(minLength: 0)
            }
            .padding(.vertical, 6)
            .padding(.leading, 6)
            .padding(.trailing, 10)
            .fixedSize(horizontal: false, vertical: true)
            .background(RoundedRectangle(cornerRadius: 10, style: .continuous).fill(isMine ? theme.onAccent.opacity(0.16) : theme.accentSoft))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Replying to \(quote.name): \(quote.preview.snippet)")
        .accessibilityHint(hint)
    }
}

/// Glass bubble for others (tail bottom-left), the theme `accent` for you (tail bottom-right).
struct MessageBubble: View {
    let text: String
    let isMine: Bool
    /// The message this one replies to, quoted above the text.
    var quote: MessageQuote?
    var quoteHint = "Shows the thread"
    var openQuote: (String) -> Void = { _ in }
    /// Photos, GIFs and files, above the text (the caption).
    var attachments: [Attachment] = []
    /// A shared place, drawn as a map card (instead of text).
    var location: MessageLocation?
    var openImage: (Attachment) -> Void = { _ in }
    var openFile: (Attachment) -> Void = { _ in }
    var openingFileId: String?
    @Environment(\.theme) private var theme

    var body: some View {
        let shape = UnevenRoundedRectangle(
            topLeadingRadius: 18,
            bottomLeadingRadius: isMine ? 18 : 6,
            bottomTrailingRadius: isMine ? 6 : 18,
            topTrailingRadius: 18,
            style: .continuous
        )
        QuoteStack(spacing: 6) {
            if let quote {
                QuoteBlock(quote: quote, isMine: isMine, hint: quoteHint, open: openQuote)
                    .padding(.horizontal, -4)
                    .padding(.top, -2)
            }
            if !attachments.isEmpty {
                AttachmentGallery(attachments: attachments, isMine: isMine, openImage: openImage, openFile: openFile, loadingFileId: openingFileId)
                    .padding(.horizontal, -8)
                    .padding(.top, -5)
                    .padding(.bottom, text.isEmpty ? -5 : 0)
            }
            if let location {
                LocationCard(location: location, isMine: isMine)
                    .padding(.horizontal, -8)
                    .padding(.vertical, -5)
            } else if !text.isEmpty || attachments.isEmpty {
                Text(text)
                    .bodyFont(14)
                    .lineSpacing(2)
                    .foregroundStyle(isMine ? theme.onAccent : theme.ink)
                    .textSelection(.enabled)
            }
        }
            .padding(.horizontal, 12)
            .padding(.vertical, 9)
            .background {
                if isMine { shape.fill(theme.accent) } else { Color.clear.modifier(GlassBackground(shape: shape, shadow: false)) }
            }
            .frame(maxWidth: 290, alignment: isMine ? .trailing : .leading)
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
