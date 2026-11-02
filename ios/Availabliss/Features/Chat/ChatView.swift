import SwiftUI

/// Group chat: bubbles, proposal / event cards, polls, read receipts and the + composer.
struct ChatView: View {
    let detail: GroupDetailModel
    let group: Group
    let onBack: () -> Void
    let onShowCalendar: () -> Void
    @Environment(AppModel.self) private var app
    @Environment(\.theme) private var theme
    @State private var model: ChatViewModel
    @State private var showsMenu = false
    @State private var sheet: ChatSheet?

    enum ChatSheet: Identifiable {
        case poll, propose, event, info(Message)
        var id: String {
            switch self {
            case .poll: "poll"
            case .propose: "propose"
            case .event: "event"
            case .info(let message): "info-\(message.id)"
            }
        }
    }

    init(detail: GroupDetailModel, group: Group, onBack: @escaping () -> Void, onShowCalendar: @escaping () -> Void) {
        self.detail = detail
        self.group = group
        self.onBack = onBack
        self.onShowCalendar = onShowCalendar
        _model = State(initialValue: ChatViewModel(groupId: group.id, api: detail.api))
    }

    private var me: String? { app.session.user?.id }

    var body: some View {
        VStack(spacing: 0) {
            ChatHeader(group: group, me: me, onBack: onBack, onShowCalendar: onShowCalendar)
            messageList
            if showsMenu {
                ComposerMenu { choice in
                    showsMenu = false
                    sheet = choice
                }
                .transition(.move(edge: .bottom).combined(with: .opacity))
            }
            Composer(text: $model.draft, isMenuOpen: $showsMenu, isSending: model.isSending) {
                Task { await model.send() }
            }
        }
        .animation(.spring(response: 0.3, dampingFraction: 0.85), value: showsMenu)
        .screenBackground(OrbLayout.chat)
        .task { await model.load(me: me) }
        .task(id: group.id) {
            for await event in app.live.events(forGroup: group.id) { await model.apply(event) }
        }
        .sheet(item: $sheet) { sheet in sheetContent(sheet) }
        .errorAlert($model.error)
    }

    private var messageList: some View {
        ScrollView {
            LazyVStack(spacing: 10) {
                if model.hasMore {
                    ProgressView()
                        .frame(height: 32)
                        .onAppear { Task { await model.loadOlder() } }
                }
                if model.isLoading { StatusView(kind: .loading) }
                ForEach(Array(model.messages.enumerated()), id: \.element.id) { index, message in
                    if index == 0 || !Calendar.current.isDate(model.messages[index - 1].createdAt, inSameDayAs: message.createdAt) {
                        Text(Formatting.chatDay(message.createdAt))
                            .bodyFont(11, .semibold, relativeTo: .caption2)
                            .foregroundStyle(theme.muted)
                            .padding(.top, 4)
                    }
                    MessageRow(
                        message: message,
                        group: group,
                        me: me,
                        model: model,
                        isLatestOwn: message.id == model.latestOwnMessageId,
                        openInfo: { sheet = .info(message) },
                        openProposal: { id in app.router.push(.proposal(id: id)) }
                    )
                    .id(message.id)
                }
            }
            .padding(.horizontal, 16)
            .padding(.top, 14)
            .padding(.bottom, 8)
        }
        .defaultScrollAnchor(.bottom)
        .scrollDismissesKeyboard(.interactively)
    }

    @ViewBuilder
    private func sheetContent(_ sheet: ChatSheet) -> some View {
        switch sheet {
        case .poll:
            NewPollSheet { question, options, multi in await model.createPoll(question: question, options: options, multi: multi) }
        case .propose:
            NewProposalSheet(group: group, me: me, draft: .blank(), api: detail.api) { _ in }
        case .event:
            EventEditorSheet(groupId: group.id, target: .new, api: detail.api) { _ in }
        case .info(let message):
            MessageInfoSheet(message: message, group: group, model: model)
        }
    }
}

/// Glass header: back, tile, name + members, calendar.
private struct ChatHeader: View {
    let group: Group
    let me: String?
    let onBack: () -> Void
    let onShowCalendar: () -> Void
    @Environment(\.theme) private var theme

    var body: some View {
        HStack(spacing: 10) {
            IconButton(systemImage: "chevron.left", label: "Back to calendar", glass: false, action: onBack)
            GroupTile(monogram: group.monogram, colorToken: group.color, size: 40, cornerRadius: 14)
            VStack(alignment: .leading, spacing: 0) {
                Text(group.name).bodyFont(16, .semibold).lineLimit(1)
                Text(Formatting.memberSummary(group.members.filter { $0.userId != me }.map(\.firstName)))
                    .bodyFont(12, relativeTo: .caption)
                    .foregroundStyle(theme.muted)
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            IconButton(systemImage: "calendar", label: "Group calendar", glass: false, action: onShowCalendar)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 8)
        .glass(cornerRadius: 24)
        .padding(.horizontal, 12)
        .padding(.top, 14)
    }
}

/// The three big buttons above the composer.
private struct ComposerMenu: View {
    let choose: (ChatView.ChatSheet) -> Void
    @Environment(\.theme) private var theme

    var body: some View {
        HStack(spacing: 6) {
            item("Poll", "chart.bar") { choose(.poll) }
            item("Propose a time", "checkmark.circle") { choose(.propose) }
            item("Add event", "calendar.badge.plus") { choose(.event) }
        }
        .padding(8)
        .glass(cornerRadius: 22)
        .padding(.horizontal, 12)
        .padding(.top, 10)
    }

    private func item(_ title: String, _ icon: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: 6) {
                Image(systemName: icon).font(.system(size: 20, weight: .regular)).foregroundStyle(theme.accentInk)
                Text(title).bodyFont(12, .semibold, relativeTo: .caption).foregroundStyle(theme.ink)
            }
            .frame(maxWidth: .infinity, minHeight: 72)
            .background(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(theme.surface))
        }
        .buttonStyle(.pressable)
    }
}

/// Glass composer: + menu toggle, text field, send.
private struct Composer: View {
    @Binding var text: String
    @Binding var isMenuOpen: Bool
    let isSending: Bool
    let send: () -> Void
    @Environment(\.theme) private var theme

    var body: some View {
        HStack(spacing: 6) {
            Button {
                isMenuOpen.toggle()
            } label: {
                Image(systemName: "plus")
                    .font(.system(size: 18, weight: .bold))
                    .rotationEffect(.degrees(isMenuOpen ? 45 : 0))
                    .foregroundStyle(isMenuOpen ? theme.onSolid : theme.ink)
                    .frame(width: 44, height: 44)
                    .background(Circle().fill(isMenuOpen ? theme.solid : theme.surface))
            }
            .buttonStyle(.pressable)
            .accessibilityLabel("Add poll, proposal or event")
            .accessibilityValue(isMenuOpen ? "Expanded" : "Collapsed")

            TextField("", text: $text, prompt: Text("Message the group").foregroundStyle(theme.muted), axis: .vertical)
                .lineLimit(1...5)
                .bodyFont(15)
                .foregroundStyle(theme.ink)
                .frame(minHeight: 44)
                .submitLabel(.send)
                .onSubmit(send)
                .accessibilityLabel("Message")

            Button(action: send) {
                Image(systemName: "arrow.right")
                    .font(.system(size: 17, weight: .bold))
                    .foregroundStyle(theme.onAccent)
                    .frame(width: 44, height: 44)
                    .background(Circle().fill(theme.accent))
            }
            .buttonStyle(.pressable)
            .disabled(text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || isSending)
            .accessibilityLabel("Send")
        }
        .padding(6)
        .glass(cornerRadius: 24)
        .padding(.horizontal, 12)
        .padding(.top, 10)
        .padding(.bottom, 10)
    }
}
