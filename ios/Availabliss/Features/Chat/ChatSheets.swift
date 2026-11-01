import SwiftUI

/// Message info: who has seen, who it's delivered to, who's still pending.
struct MessageInfoSheet: View {
    let message: Message
    let group: Group
    let model: ChatViewModel
    @Environment(\.theme) private var theme
    @State private var info: MessageInfo?

    var body: some View {
        BottomSheet(title: "Message info") {
            VStack(alignment: .trailing, spacing: 4) {
                MessageBubble(text: message.body ?? message.poll?.question ?? "", isMine: true)
                Text("Sent \(Formatting.chatDay(message.createdAt).lowercased()) at \(Formatting.time(message.createdAt))")
                    .bodyFont(11, relativeTo: .caption2)
                    .foregroundStyle(theme.muted)
            }
            .frame(maxWidth: .infinity, alignment: .trailing)
            if let info {
                if !info.seen.isEmpty {
                    ReceiptSection(title: "Seen by", seen: true, rows: info.seen.map { ($0.userId, Formatting.time($0.at)) }, group: group)
                }
                if !info.delivered.isEmpty {
                    ReceiptSection(title: "Delivered to, not seen yet", seen: false, rows: info.delivered.map { ($0.userId, "Delivered \(Formatting.time($0.at))") }, group: group)
                }
                if !info.pending.isEmpty {
                    ReceiptSection(title: "Not delivered yet", seen: false, rows: info.pending.map { ($0, "Pending") }, group: group)
                }
            } else {
                ProgressView().frame(maxWidth: .infinity, minHeight: 120)
            }
        }
        .task { info = await model.info(for: message.id) }
    }
}

private struct ReceiptSection: View {
    let title: String
    let seen: Bool
    let rows: [(userId: String, detail: String)]
    let group: Group
    @Environment(\.theme) private var theme

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 8) {
                Image(systemName: seen ? "checkmark.circle.fill" : "checkmark.circle")
                    .foregroundStyle(seen ? theme.accentInk : theme.muted)
                Text(title)
                Spacer()
                Text("\(rows.count)")
            }
            .bodyFont(12, .semibold, relativeTo: .caption)
            .foregroundStyle(theme.muted)
            .padding(.top, 10)
            .padding(.bottom, 6)
            ForEach(rows, id: \.userId) { row in
                let member = group.member(row.userId)
                HStack(spacing: 12) {
                    Avatar(name: member?.name ?? "?", imageURL: member?.image, color: theme.tile(forUser: row.userId), size: 32)
                    Text(member?.name ?? "Former member").bodyFont(15, .semibold)
                    Spacer()
                    Text(row.detail).bodyFont(12, relativeTo: .caption).foregroundStyle(theme.muted)
                }
                .frame(minHeight: 50)
                .overlay(alignment: .top) { Rectangle().fill(theme.line).frame(height: 1) }
                .accessibilityElement(children: .combine)
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 4)
        .surfaceBackground(cornerRadius: 22)
    }
}

/// Create a poll: question, 2–10 options, multiple choice.
struct NewPollSheet: View {
    let create: (_ question: String, _ options: [String], _ multi: Bool) async -> Bool
    @Environment(\.dismiss) private var dismiss
    @Environment(\.theme) private var theme
    @State private var question = ""
    @State private var options = ["", ""]
    @State private var multi = false
    @State private var isSaving = false

    private var cleanOptions: [String] {
        options.map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }
    }

    var body: some View {
        BottomSheet(title: "New poll") {
            InputField(placeholder: "Ask a question", text: $question)
            Text("Options").sectionLabelStyle()
            ForEach(options.indices, id: \.self) { index in
                HStack(spacing: 8) {
                    InputField(placeholder: "Option \(index + 1)", text: $options[index], height: 46, cornerRadius: 14)
                    if options.count > 2 {
                        IconButton(systemImage: "minus", label: "Remove option \(index + 1)", size: 36, glass: false) {
                            options.remove(at: index)
                        }
                    }
                }
            }
            if options.count < 10 {
                Button("+ Add option") { options.append("") }.buttonStyle(.textAction)
            }
            Toggle("Allow more than one choice", isOn: $multi)
                .toggleStyle(.glassSwitch)
                .bodyFont(15, .medium)
            Button {
                Task {
                    isSaving = true
                    defer { isSaving = false }
                    if await create(question.trimmingCharacters(in: .whitespaces), cleanOptions, multi) { dismiss() }
                }
            } label: {
                LoadingLabel(title: "Post poll", isLoading: isSaving)
            }
            .buttonStyle(.accent)
            .disabled(question.trimmingCharacters(in: .whitespaces).isEmpty || cleanOptions.count < 2 || isSaving)
        }
    }
}
