import PhotosUI
import SwiftUI
import UniformTypeIdentifiers

/// The bottom of the chat or of a thread: the + menu (photos, files, location, poll, propose a time,
/// add event), the editing or replying strip, the photos and files picked for the next message, and
/// the composer. The chat and its threads share it, so sending works the same way in both; in a
/// thread everything you send (except polls, proposals and events, which go in the chat) replies
/// within the thread.
struct ChatComposerArea: View {
    @Bindable var model: ChatViewModel
    let target: ChatViewModel.ComposerTarget
    let group: Group
    let me: String?
    let api: APIClient
    @Binding var showsMenu: Bool
    @Environment(\.theme) private var theme
    @State private var sheet: ComposerSheet?
    @State private var pickingPhotos = false
    @State private var photoSelection: [PhotosPickerItem] = []
    @State private var pickingFiles = false

    enum ComposerSheet: String, Identifiable {
        case poll, propose, event, location
        var id: String { rawValue }
    }

    private var state: ChatViewModel.ComposerState { model.composer(target) }

    var body: some View {
        VStack(spacing: 0) {
            if showsMenu {
                ComposerMenu { choice in
                    showsMenu = false
                    switch choice {
                    case .photos: pickingPhotos = true
                    case .files: pickingFiles = true
                    case .location: sheet = .location
                    case .poll: sheet = .poll
                    case .propose: sheet = .propose
                    case .event: sheet = .event
                    }
                }
                .transition(.move(edge: .bottom).combined(with: .opacity))
            }
            strip
            if !state.uploads.isEmpty, state.editing == nil {
                AttachmentTray(uploads: state.uploads, remove: model.removeUpload, retry: model.retryUpload)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
            }
            Composer(
                text: Binding(get: { model.composer(target).draft }, set: { model.setDraft($0, for: target) }),
                isMenuOpen: $showsMenu,
                isSending: state.isSending,
                editingId: state.editing?.id,
                replyingId: replyingId,
                placeholder: placeholder,
                canSend: model.canSend(target),
                allowsEmpty: !(state.editing?.files.isEmpty ?? true)
            ) {
                Task { await model.send(target) }
            }
        }
        .animation(.spring(response: 0.3, dampingFraction: 0.85), value: state.uploads.map(\.id))
        .animation(.spring(response: 0.3, dampingFraction: 0.85), value: state.editing?.id)
        .animation(.spring(response: 0.3, dampingFraction: 0.85), value: showsMenu)
        .photosPicker(
            isPresented: $pickingPhotos, selection: $photoSelection, maxSelectionCount: AttachmentFormat.maxPerMessage, matching: .images, preferredItemEncoding: .current
        )
        .onChange(of: photoSelection) { _, items in
            guard !items.isEmpty else { return }
            photoSelection = []
            Task { await attachPhotos(items) }
        }
        .fileImporter(isPresented: $pickingFiles, allowedContentTypes: [.item], allowsMultipleSelection: true) { result in
            if case .success(let urls) = result { attachFiles(urls) }
        }
        .sheet(item: $sheet) { sheet in
            switch sheet {
            case .poll:
                NewPollSheet { question, options, multi in await model.createPoll(question: question, options: options, multi: multi, from: target) }
            case .propose:
                NewProposalSheet(group: group, me: me, draft: .blank(), api: api) { _ in }
            case .event:
                EventEditorSheet(groupId: group.id, target: .new, api: api) { _ in }
            case .location:
                LocationPickerSheet { location in await model.sendLocation(location, in: target) }
            }
        }
    }

    /// Editing beats replying; in a thread, "Replying to …" always shows (to the latest message
    /// unless you picked one, with ✕ to go back to the latest).
    @ViewBuilder
    private var strip: some View {
        if state.editing != nil {
            EditingStrip { model.cancelEditing(target) }
                .transition(.move(edge: .bottom).combined(with: .opacity))
        } else if target == .chat, let replying = model.replyingTo {
            ReplyingStrip(
                name: ReplyNames.name(of: replying.authorId, fallback: nil, group: group, me: me),
                snippet: replying.asReplyPreview(authorName: nil).snippet
            ) { model.cancelReplying() }
            .transition(.move(edge: .bottom).combined(with: .opacity))
        } else if target == .thread, let replying = model.threadReplyingTo {
            ReplyingStrip(
                name: ReplyNames.name(of: replying.authorId, fallback: nil, group: group, me: me),
                snippet: replying.asReplyPreview(authorName: nil).snippet,
                cancelLabel: "Reply to the latest message instead",
                cancel: model.hasPickedThreadReply ? { model.replyInThread(to: nil) } : nil
            )
        }
    }

    private var replyingId: String? {
        switch target {
        case .chat: model.replyingTo?.id
        case .thread: model.hasPickedThreadReply ? model.threadReplyingTo?.id : nil
        }
    }

    private var placeholder: String {
        if !state.uploads.isEmpty, state.editing == nil { return "Add a caption" }
        return target == .thread ? "Reply in thread" : "Message the group"
    }

    /// Loads picked photos (GIFs as they are, others as JPEG where needed) and starts uploading them.
    private func attachPhotos(_ items: [PhotosPickerItem]) async {
        var picked: [(data: Data, name: String, mime: String, preview: UIImage?)] = []
        var failed = 0
        for item in items {
            let type = item.supportedContentTypes.first { $0.conforms(to: .gif) } ?? item.supportedContentTypes.first
            guard let data = try? await item.loadTransferable(type: Data.self),
                let photo = await Task.detached(priority: .userInitiated, operation: { AttachmentFormat.photo(data: data, type: type) }).value
            else { failed += 1; continue }
            picked.append(photo)
        }
        model.attach(picked, to: target)
        if failed > 0 { model.report(failed == 1 ? "One photo couldn't be loaded." : "\(failed) photos couldn't be loaded.", target) }
    }

    /// Reads picked files and starts uploading them (the server says which types it takes).
    private func attachFiles(_ urls: [URL]) {
        var picked: [(data: Data, name: String, mime: String, preview: UIImage?)] = []
        for url in urls {
            let scoped = url.startAccessingSecurityScopedResource()
            defer { if scoped { url.stopAccessingSecurityScopedResource() } }
            guard let data = try? Data(contentsOf: url) else {
                model.report("\(url.lastPathComponent) couldn't be read. Folders and packages can't be sent: zip them first.", target)
                continue
            }
            let mime = UTType(filenameExtension: url.pathExtension)?.preferredMIMEType ?? "application/octet-stream"
            picked.append((data, url.lastPathComponent, mime, nil))
        }
        model.attach(picked, to: target)
    }
}
