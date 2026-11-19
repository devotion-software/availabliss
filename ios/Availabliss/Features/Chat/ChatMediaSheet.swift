import QuickLook
import SwiftUI

/// Everything sent in the chat: Photos (a grid, newest first, opening the full-screen viewer) and Files
/// (with who sent each and when, opening in QuickLook), each with Show in chat. More load as you reach
/// the end. Like web and Android.
struct ChatMediaSheet: View {
    let group: Group
    let me: String?
    let api: APIClient
    /// Goes to the message a photo or file was sent in (the sheet is closing).
    let onShowInChat: (String) -> Void
    @Environment(\.dismiss) private var dismiss
    @Environment(\.theme) private var theme

    enum Kind: String, CaseIterable {
        case media, files
        var title: String { self == .media ? "Photos" : "Files" }
    }

    struct Page {
        var items: [GroupMediaItem] = []
        var hasMore = false
        var loaded = false
        var loading = false
        var failed = false
    }

    @State private var kind = Kind.media
    @State private var pages: [Kind: Page] = [.media: Page(), .files: Page()]
    @State private var viewing: String?
    @State private var previewFile: URL?
    @State private var openingFileId: String?
    @State private var error: String?

    private var page: Page { pages[kind] ?? Page() }
    private let columns = [GridItem(.adaptive(minimum: 96), spacing: 4)]

    var body: some View {
        BottomSheet(title: "Photos and files") {
            Picker("Show", selection: $kind) {
                ForEach(Kind.allCases, id: \.self) { Text($0.title).tag($0) }
            }
            .pickerStyle(.segmented)

            if page.failed && page.items.isEmpty {
                note("Couldn't load them. Check your connection and try again.")
                Button("Try again") { Task { await load(kind) } }.buttonStyle(.textAction)
            } else if page.loaded && page.items.isEmpty {
                note(kind == .media ? "No photos sent in this chat yet." : "No files sent in this chat yet.")
            } else if kind == .media {
                photoGrid
            } else {
                fileList
            }
            if page.loading {
                ProgressView().frame(maxWidth: .infinity)
            }
        }
        .presentationDetents([.large])
        .task(id: kind) { if !(pages[kind]?.loaded ?? false) { await load(kind) } }
        .fullScreenCover(item: Binding(get: { viewing.map(ViewingId.init) }, set: { viewing = $0?.id })) { target in
            let pictures = pages[.media]?.items ?? []
            AttachmentViewer(attachments: pictures.map(\.attachment), selection: target.id) { id in
                guard let messageId = pictures.first(where: { $0.id == id })?.messageId else { return }
                showInChat(messageId)
            }
        }
        .quickLookPreview($previewFile)
        .errorAlert($error)
    }

    private var photoGrid: some View {
        LazyVGrid(columns: columns, spacing: 4) {
            ForEach(page.items) { item in
                Button {
                    viewing = item.id
                } label: {
                    Color.clear
                        .aspectRatio(1, contentMode: .fit)
                        .overlay { AttachmentImage(attachment: item.attachment) }
                        .overlay(alignment: .bottomLeading) {
                            if item.attachment.kind == .video {
                                Text("\u{25B6} " + item.attachment.durationLabel)
                                    .font(.system(size: 11, weight: .bold))
                                    .foregroundStyle(.white)
                                    .padding(.horizontal, 6)
                                    .padding(.vertical, 1)
                                    .background(RoundedRectangle(cornerRadius: 6).fill(.black.opacity(0.55)))
                                    .padding(6)
                            }
                            if item.attachment.kind == .gif {
                                Text("GIF")
                                    .font(.system(size: 11, weight: .bold))
                                    .foregroundStyle(.white)
                                    .padding(.horizontal, 6)
                                    .padding(.vertical, 1)
                                    .background(RoundedRectangle(cornerRadius: 6).fill(.black.opacity(0.55)))
                                    .padding(6)
                            }
                        }
                        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("\(item.attachment.kind == .gif ? "GIF" : item.attachment.kind == .video ? "Video" : "Photo") from \(author(item)), \(Formatting.chatDay(item.createdAt))")
                .onAppear { loadMore(after: item) }
            }
        }
    }

    private var fileList: some View {
        VStack(spacing: 0) {
            ForEach(page.items) { item in
                HStack(spacing: 10) {
                    Button {
                        Task { await open(item.attachment) }
                    } label: {
                        HStack(spacing: 12) {
                            ZStack {
                                RoundedRectangle(cornerRadius: 10, style: .continuous).fill(theme.accentSoft)
                                if openingFileId == item.id {
                                    ProgressView().tint(theme.accentInk)
                                } else {
                                    Image(systemName: AttachmentFormat.icon(mime: item.attachment.mime, name: item.attachment.name))
                                        .font(.system(size: 17, weight: .semibold))
                                        .foregroundStyle(theme.accentInk)
                                }
                            }
                            .frame(width: 40, height: 40)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(item.attachment.name)
                                    .bodyFont(15, .semibold)
                                    .foregroundStyle(theme.ink)
                                    .lineLimit(2)
                                    .truncationMode(.middle)
                                    .multilineTextAlignment(.leading)
                                Text("\(AttachmentFormat.size(item.attachment.size)) · \(author(item)) · \(Formatting.chatDay(item.createdAt))")
                                    .bodyFont(12, relativeTo: .caption)
                                    .foregroundStyle(theme.muted)
                                    .lineLimit(1)
                            }
                            Spacer(minLength: 0)
                        }
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityHint("Opens it")
                    Button("Show in chat") { showInChat(item.messageId) }
                        .buttonStyle(.textAction)
                }
                .padding(.vertical, 8)
                .frame(minHeight: 60)
                .overlay(alignment: .top) {
                    if item.id != page.items.first?.id { Rectangle().fill(theme.line).frame(height: 1) }
                }
                .onAppear { loadMore(after: item) }
            }
        }
        .padding(.horizontal, 14)
        .surfaceBackground(cornerRadius: 18)
    }

    private func note(_ text: String) -> some View {
        Text(text).bodyFont(14).foregroundStyle(theme.muted).fixedSize(horizontal: false, vertical: true)
    }

    private func author(_ item: GroupMediaItem) -> String {
        ReplyNames.name(of: item.authorId, fallback: nil, group: group, me: me)
    }

    private func showInChat(_ messageId: String) {
        viewing = nil
        dismiss()
        onShowInChat(messageId)
    }

    /// The next page once the last item loaded so far comes into view.
    private func loadMore(after item: GroupMediaItem) {
        guard item.id == page.items.last?.id, page.hasMore else { return }
        Task { await load(kind, more: true) }
    }

    private func load(_ kind: Kind, more: Bool = false) async {
        var current = pages[kind] ?? Page()
        guard !current.loading, !more || current.hasMore else { return }
        current.loading = true
        pages[kind] = current
        do {
            let before = more ? current.items.last?.id : nil
            let result = try await api.send(API.Chat.media(groupId: group.id, kind: kind.rawValue, before: before))
            current.items = more ? current.items + result.items : result.items
            current.hasMore = result.hasMore
            current.loaded = true
            current.failed = false
        } catch {
            current.failed = true
        }
        current.loading = false
        pages[kind] = current
    }

    /// Downloads a file and opens it in QuickLook (which can share or save it too).
    private func open(_ attachment: Attachment) async {
        guard openingFileId == nil else { return }
        openingFileId = attachment.id
        defer { openingFileId = nil }
        do {
            previewFile = try await AttachmentFiles.download(attachment)
        } catch {
            self.error = "Couldn't open \(attachment.name). Check your connection and try again."
        }
    }
}

private struct ViewingId: Identifiable {
    let id: String
}
