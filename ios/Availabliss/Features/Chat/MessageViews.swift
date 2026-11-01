import SwiftUI

/// One message, dispatched by kind.
struct MessageRow: View {
    let message: Message
    let group: Group
    let me: String?
    let model: ChatViewModel
    let isLatestOwn: Bool
    let openInfo: () -> Void
    let openProposal: (String) -> Void
    @Environment(\.theme) private var theme

    private var isMine: Bool { message.authorId != nil && message.authorId == me }
    private var author: Member? { message.authorId.flatMap(group.member) }

    var body: some View {
        switch message.kind {
        case .system:
            Text(message.body ?? "")
                .bodyFont(12, relativeTo: .caption)
                .foregroundStyle(theme.muted)
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity)
        case .text:
            if isMine {
                VStack(alignment: .trailing, spacing: 4) {
                    MessageBubble(text: message.body ?? "", isMine: true)
                        .onTapGesture(perform: openInfo)
                        .accessibilityAddTraits(.isButton)
                        .accessibilityHint("Shows message info")
                    if isLatestOwn, let receipt = message.receipt {
                        ReadReceipt(receipt: receipt, group: group, action: openInfo)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .trailing)
            } else {
                IncomingRow(author: author) { MessageBubble(text: message.body ?? "", isMine: false) }
            }
        case .poll:
            if let poll = message.poll {
                VStack(alignment: .trailing, spacing: 4) {
                    PollCard(poll: poll, me: me, canClose: isMine || group.role.isAdmin, model: model)
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
    @ViewBuilder let content: Content
    @Environment(\.theme) private var theme

    var body: some View {
        HStack(alignment: .bottom, spacing: 8) {
            Avatar(name: author?.name ?? "?", imageURL: author?.image, color: theme.tile(forUser: author?.userId ?? ""), size: 28)
            content
            Spacer(minLength: 40)
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(author?.firstName ?? "Someone") said")
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
    let canClose: Bool
    let model: ChatViewModel
    @Environment(\.theme) private var theme

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 8) {
                Image(systemName: "chart.bar").font(.system(size: 14, weight: .semibold))
                Text("Poll · \(Formatting.plural(poll.totalVotes, "vote"))\(poll.closed ? " · closed" : "")")
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
