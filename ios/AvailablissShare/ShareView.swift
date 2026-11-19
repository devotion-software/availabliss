import SwiftUI
import UniformTypeIdentifiers

struct ShareView: View {
    let providers: [NSItemProvider]
    let done: () -> Void
    let cancel: () -> Void

    @State private var groups: [WidgetGroup] = WidgetSnapshotStore.load()?.groups ?? []
    @State private var picked: WidgetGroup?
    @State private var caption = ""
    @State private var sending = false
    @State private var progress = ""
    @State private var error: String?

    private var signedIn: Bool { SharedSession.token != nil && (WidgetSnapshotStore.load()?.signedIn ?? false) }
    /// At most 10 attachments in a message.
    private var items: [NSItemProvider] { Array(providers.filter(\.isFile).prefix(10)) }

    var body: some View {
        NavigationStack {
            content
                .navigationTitle("Send to…")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) { Button("Cancel", action: cancel).disabled(sending) }
                    ToolbarItem(placement: .confirmationAction) {
                        if sending {
                            ProgressView()
                        } else {
                            Button("Send") { Task { await send() } }.disabled(picked == nil || (items.isEmpty && caption.isEmpty))
                        }
                    }
                }
                .alert("Couldn't send", isPresented: Binding(get: { error != nil }, set: { if !$0 { error = nil } })) {
                    Button("OK", role: .cancel) {}
                } message: {
                    Text(error ?? "")
                }
        }
        .task { caption = await sharedText() ?? "" }
    }

    @ViewBuilder
    private var content: some View {
        if !signedIn {
            ContentUnavailableView("Sign in to Availabliss", systemImage: "person.crop.circle", description: Text("Open Availabliss and sign in, then share again."))
        } else if groups.isEmpty {
            ContentUnavailableView("No group chats yet", systemImage: "bubble.left.and.bubble.right", description: Text("Open Availabliss to make or join a group, then share again."))
        } else {
            Form {
                Section {
                    Text(items.count == 1 ? "1 item" : items.isEmpty ? "Text" : "\(items.count) items")
                        .foregroundStyle(.secondary)
                    TextField("Add a caption", text: $caption, axis: .vertical)
                        .lineLimit(1...4)
                }
                Section("Group chat") {
                    ForEach(groups, id: \.id) { group in
                        Button {
                            picked = group
                        } label: {
                            HStack {
                                Text(group.name).foregroundStyle(.primary)
                                Spacer()
                                if picked == group { Image(systemName: "checkmark").foregroundStyle(.tint) }
                            }
                        }
                        .accessibilityAddTraits(picked == group ? .isSelected : [])
                    }
                }
                if sending, !progress.isEmpty {
                    Section { Text(progress).foregroundStyle(.secondary) }
                }
            }
            .disabled(sending)
        }
    }

    /// Text or a link shared on its own (no files) becomes the caption / message.
    private func sharedText() async -> String? {
        guard items.isEmpty else { return nil }
        for provider in providers {
            if provider.hasItemConformingToTypeIdentifier(UTType.url.identifier),
                let url = try? await provider.loadItem(forTypeIdentifier: UTType.url.identifier) as? URL
            {
                return url.absoluteString
            }
            if provider.hasItemConformingToTypeIdentifier(UTType.plainText.identifier),
                let text = try? await provider.loadItem(forTypeIdentifier: UTType.plainText.identifier) as? String
            {
                return text
            }
        }
        return nil
    }

    private func send() async {
        guard let group = picked, let token = SharedSession.token else { return }
        sending = true
        defer { sending = false }
        let client = ShareClient(token: token)
        do {
            var ids: [String] = []
            for (index, provider) in items.enumerated() {
                progress = "Uploading \(index + 1) of \(items.count)…"
                let file = try await provider.sharedFile()
                ids.append(try await client.upload(groupId: group.id, file: file))
            }
            progress = "Sending…"
            try await client.send(groupId: group.id, attachmentIds: ids, text: caption.trimmingCharacters(in: .whitespacesAndNewlines))
            done()
        } catch {
            self.error = (error as? ShareError)?.message ?? "Check your connection and try again."
        }
    }
}

extension NSItemProvider {
    /// Something to upload: a file, photo or pass (not just text or a link).
    var isFile: Bool {
        hasItemConformingToTypeIdentifier(UTType.image.identifier) || hasItemConformingToTypeIdentifier(UTType.data.identifier)
            && !hasItemConformingToTypeIdentifier(UTType.url.identifier) && !hasItemConformingToTypeIdentifier(UTType.plainText.identifier)
    }

    /// The shared item as bytes, a name and a type, copied out before the provider's temporary file goes.
    @MainActor func sharedFile() async throws -> SharedFile {
        let type = registeredContentTypes.first { $0.conforms(to: .image) } ?? registeredContentTypes.first { $0.conforms(to: .data) } ?? .data
        let suggested = suggestedName
        return try await withCheckedThrowingContinuation { continuation in
            _ = loadFileRepresentation(for: type) { url, _, error in
                guard let url, let data = try? Data(contentsOf: url) else {
                    continuation.resume(throwing: error ?? ShareError(message: "Couldn't read that item."))
                    return
                }
                let name = suggested.map { $0.contains(".") ? $0 : "\($0).\(type.preferredFilenameExtension ?? "bin")" } ?? url.lastPathComponent
                continuation.resume(returning: SharedFile(data: data, name: name, mime: type.preferredMIMEType ?? "application/octet-stream"))
            }
        }
    }
}

struct SharedFile: Sendable {
    let data: Data
    let name: String
    let mime: String
}

struct ShareError: Error {
    let message: String
}
