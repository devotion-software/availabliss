import ImageIO
import QuickLook
import SwiftUI
import UIKit
import UniformTypeIdentifiers

// MARK: - Uploads waiting in the composer

/// A photo or file picked for the next message: uploading, uploaded or failed.
struct PendingUpload: Identifiable, Equatable {
    let id = UUID()
    let name: String
    let mime: String
    let data: Data
    /// A small preview for photos and GIFs.
    let preview: UIImage?
    var progress: Double = 0
    var uploaded: Attachment?
    var error: String?

    var isVisual: Bool { preview != nil }
    var isUploading: Bool { uploaded == nil && error == nil }

    static func == (lhs: PendingUpload, rhs: PendingUpload) -> Bool {
        lhs.id == rhs.id && lhs.progress == rhs.progress && lhs.uploaded == rhs.uploaded && lhs.error == rhs.error
    }
}

enum AttachmentFormat {
    static let maxPerMessage = 10
    static let maxPhotoBytes = 15 * 1024 * 1024
    static let maxFileBytes = 25 * 1024 * 1024

    /// "1.2 MB", "340 KB".
    static func size(_ bytes: Int) -> String {
        ByteCountFormatter.string(fromByteCount: Int64(bytes), countStyle: .file)
    }

    /// The SF Symbol for a file, by its type.
    static func icon(mime: String, name: String) -> String {
        let ext = (name as NSString).pathExtension.lowercased()
        if mime == "application/pdf" { return "doc.richtext" }
        if mime.hasPrefix("audio/") { return "waveform" }
        if mime.hasPrefix("video/") { return "film" }
        if ext == "zip" { return "doc.zipper" }
        if ["xls", "xlsx", "ods", "numbers", "csv"].contains(ext) { return "tablecells" }
        if ["ppt", "pptx", "odp", "key"].contains(ext) { return "rectangle.on.rectangle" }
        if ["ics"].contains(ext) { return "calendar" }
        return "doc.text"
    }

    /// A photo from the library ready to upload: GIFs as they are (so they still move), HEIC and
    /// other formats as JPEG (the server can't always read HEIC), JPEG / PNG as they are.
    static func photo(data: Data, type: UTType?) -> (data: Data, name: String, mime: String, preview: UIImage?)? {
        if type?.conforms(to: .gif) == true || data.starts(with: Data("GIF8".utf8)) {
            return (data, "GIF.gif", "image/gif", UIImage(data: data))
        }
        guard let image = UIImage(data: data) else { return nil }
        let preview = image.preparingThumbnail(of: CGSize(width: 240, height: 240 / max(image.size.width / max(image.size.height, 1), 0.01)))
        if type?.conforms(to: .jpeg) == true { return (data, "Photo.jpg", "image/jpeg", preview ?? image) }
        if type?.conforms(to: .png) == true, data.count <= maxPhotoBytes { return (data, "Photo.png", "image/png", preview ?? image) }
        guard let jpeg = image.jpegData(compressionQuality: 0.9) else { return nil }
        return (jpeg, "Photo.jpg", "image/jpeg", preview ?? image)
    }
}

// MARK: - Loading images

/// Loads chat pictures (signed links, no auth) and keeps them in memory, keyed by attachment so a
/// refreshed signature doesn't load it again. GIFs come back animated.
@MainActor
final class AttachmentImages {
    static let shared = AttachmentImages()
    private let cache = NSCache<NSString, UIImage>()
    private var inFlight: [String: Task<UIImage?, Never>] = [:]
    private let session: URLSession = {
        let configuration = URLSessionConfiguration.default
        configuration.urlCache = URLCache(memoryCapacity: 20 << 20, diskCapacity: 200 << 20)
        configuration.httpShouldSetCookies = false
        return URLSession(configuration: configuration)
    }()

    init() { cache.totalCostLimit = 120 << 20 }

    func cached(_ key: String) -> UIImage? { cache.object(forKey: key as NSString) }

    func image(key: String, url: String) async -> UIImage? {
        if let hit = cached(key) { return hit }
        if let running = inFlight[key] { return await running.value }
        let session = self.session
        let task = Task<UIImage?, Never> {
            guard let url = URL(string: url), let (data, _) = try? await session.data(from: url) else { return nil }
            return await Task.detached(priority: .userInitiated) { AttachmentImages.decode(data) }.value
        }
        inFlight[key] = task
        let image = await task.value
        inFlight[key] = nil
        if let image { cache.setObject(image, forKey: key as NSString, cost: Int(image.size.width * image.size.height * image.scale * 4) * max(image.images?.count ?? 1, 1)) }
        return image
    }

    func data(url: String) async throws -> Data {
        guard let url = URL(string: url) else { throw URLError(.badURL) }
        let (data, response) = try await session.data(from: url)
        guard (response as? HTTPURLResponse)?.statusCode == 200 else { throw URLError(.badServerResponse) }
        return data
    }

    /// A still image, or an animated one for a GIF with more than one frame.
    nonisolated static func decode(_ data: Data) -> UIImage? {
        guard let source = CGImageSourceCreateWithData(data as CFData, nil) else { return UIImage(data: data) }
        let count = CGImageSourceGetCount(source)
        guard count > 1 else { return UIImage(data: data) }
        var frames: [UIImage] = []
        var duration: Double = 0
        for index in 0..<min(count, 300) {
            guard let frame = CGImageSourceCreateImageAtIndex(source, index, nil) else { continue }
            frames.append(UIImage(cgImage: frame))
            let props = CGImageSourceCopyPropertiesAtIndex(source, index, nil) as? [CFString: Any]
            let gif = props?[kCGImagePropertyGIFDictionary] as? [CFString: Any]
            let delay = (gif?[kCGImagePropertyGIFUnclampedDelayTime] as? Double) ?? (gif?[kCGImagePropertyGIFDelayTime] as? Double) ?? 0.1
            duration += delay < 0.02 ? 0.1 : delay
        }
        return UIImage.animatedImage(with: frames, duration: duration)
    }
}

/// UIImageView, so GIFs animate (SwiftUI's Image shows only the first frame).
struct AnimatedImageView: UIViewRepresentable {
    let image: UIImage
    var contentMode: UIView.ContentMode = .scaleAspectFill

    func makeUIView(context: Context) -> UIImageView {
        let view = UIImageView()
        view.clipsToBounds = true
        view.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        view.setContentCompressionResistancePriority(.defaultLow, for: .vertical)
        view.setContentHuggingPriority(.defaultLow, for: .horizontal)
        view.setContentHuggingPriority(.defaultLow, for: .vertical)
        return view
    }

    func updateUIView(_ view: UIImageView, context: Context) {
        view.contentMode = contentMode
        if view.image !== image {
            view.image = image
            if image.images != nil { view.startAnimating() }
        }
    }
}

/// A chat picture: the preview (or the GIF itself), filling its frame, with a soft placeholder while it loads.
struct AttachmentImage: View {
    let attachment: Attachment
    /// The full picture rather than the preview (the viewer).
    var full = false
    var contentMode: UIView.ContentMode = .scaleAspectFill
    @State private var image: UIImage?
    @State private var failed = false
    @Environment(\.theme) private var theme

    private var key: String { full || attachment.thumbnailUrl == nil ? "\(attachment.id)-full" : "\(attachment.id)-thumb" }
    private var url: String { full ? attachment.url : attachment.thumbnailUrl ?? attachment.url }

    var body: some View {
        ZStack {
            if let image = image ?? AttachmentImages.shared.cached(key) {
                AnimatedImageView(image: image, contentMode: contentMode)
            } else {
                Rectangle().fill(full ? Color.clear : theme.surface)
                if failed {
                    Image(systemName: "photo").font(.system(size: 20)).foregroundStyle(theme.muted)
                } else {
                    ProgressView().tint(full ? .white : theme.muted)
                }
            }
        }
        .task(id: key) {
            guard image == nil else { return }
            image = await AttachmentImages.shared.image(key: key, url: url)
            failed = image == nil
        }
    }
}

// MARK: - In a message

/// A message's photos (one sized by its shape, several in a grid) and file cards.
struct AttachmentGallery: View {
    let attachments: [Attachment]
    let isMine: Bool
    let openImage: (Attachment) -> Void
    let openFile: (Attachment) -> Void
    /// The file being downloaded to open, if any.
    var loadingFileId: String?

    static let width: CGFloat = 240
    private var visual: [Attachment] { attachments.filter(\.isVisual) }
    private var files: [Attachment] { attachments.filter { !$0.isVisual } }

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            if visual.count == 1, let only = visual.first {
                tile(only, extra: 0)
                    .frame(width: Self.width, height: Self.singleHeight(only))
                    .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
            } else if !visual.isEmpty {
                grid
            }
            ForEach(files) { file in
                FileCard(attachment: file, isMine: isMine, isLoading: loadingFileId == file.id) { openFile(file) }
            }
        }
        .frame(width: Self.width, alignment: .leading)
    }

    /// Height for a single photo: by its shape, between 120 and 320.
    static func singleHeight(_ attachment: Attachment) -> CGFloat {
        guard let ratio = attachment.aspectRatio else { return width * 0.75 }
        return min(max(width / ratio, 120), 320)
    }

    /// Two square tiles a row (up to 4, "+N" on the last); with three, the third spans the row.
    private var grid: some View {
        let shown = Array(visual.prefix(4))
        let side = (Self.width - 4) / 2
        let rows = stride(from: 0, to: shown.count, by: 2).map { Array(shown[$0..<min($0 + 2, shown.count)]) }
        return VStack(spacing: 4) {
            ForEach(Array(rows.enumerated()), id: \.offset) { rowIndex, row in
                HStack(spacing: 4) {
                    ForEach(Array(row.enumerated()), id: \.element.id) { column, attachment in
                        let index = rowIndex * 2 + column
                        tile(attachment, extra: index == 3 ? visual.count - 4 : 0)
                            .frame(width: row.count == 1 ? Self.width : side, height: side)
                            .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                    }
                }
            }
        }
    }

    private func tile(_ attachment: Attachment, extra: Int) -> some View {
        Button { openImage(attachment) } label: {
            AttachmentImage(attachment: attachment)
                .overlay {
                    if extra > 0 {
                        Color.black.opacity(0.45)
                        Text("+\(extra)").font(.system(size: 22, weight: .bold)).foregroundStyle(.white)
                    }
                }
                .overlay(alignment: .bottomLeading) {
                    if attachment.kind == .gif, extra == 0 {
                        Text("GIF")
                            .font(.system(size: 10, weight: .heavy))
                            .foregroundStyle(.white)
                            .padding(.horizontal, 5)
                            .padding(.vertical, 2)
                            .background(Capsule().fill(.black.opacity(0.5)))
                            .padding(6)
                    }
                }
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(extra > 0 ? "\(attachment.kind == .gif ? "GIF" : "Photo"), and \(extra) more" : attachment.kind == .gif ? "GIF" : "Photo")
        .accessibilityHint("Opens it full screen")
    }
}

/// A file in a message: icon, name and size. Tapping opens it.
struct FileCard: View {
    let attachment: Attachment
    let isMine: Bool
    var isLoading = false
    let open: () -> Void
    @Environment(\.theme) private var theme

    var body: some View {
        Button(action: open) {
            HStack(spacing: 10) {
                ZStack {
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .fill(isMine ? theme.onAccent.opacity(0.18) : theme.accentSoft)
                    if isLoading {
                        ProgressView().tint(isMine ? theme.onAccent : theme.accentInk)
                    } else {
                        Image(systemName: AttachmentFormat.icon(mime: attachment.mime, name: attachment.name))
                            .font(.system(size: 17, weight: .semibold))
                            .foregroundStyle(isMine ? theme.onAccent : theme.accentInk)
                    }
                }
                .frame(width: 40, height: 40)
                VStack(alignment: .leading, spacing: 2) {
                    Text(attachment.name)
                        .bodyFont(14, .semibold)
                        .foregroundStyle(isMine ? theme.onAccent : theme.ink)
                        .lineLimit(2)
                        .truncationMode(.middle)
                        .multilineTextAlignment(.leading)
                    Text(AttachmentFormat.size(attachment.size))
                        .bodyFont(12, relativeTo: .caption)
                        .foregroundStyle(isMine ? theme.onAccent.opacity(0.8) : theme.muted)
                }
                Spacer(minLength: 0)
            }
            .padding(8)
            .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(isMine ? theme.onAccent.opacity(0.12) : theme.surface))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("File: \(attachment.name), \(AttachmentFormat.size(attachment.size))")
        .accessibilityHint("Opens it")
    }
}

// MARK: - Composer tray

/// The photos and files picked for the next message, above the composer, each with its progress and ✕.
struct AttachmentTray: View {
    let uploads: [PendingUpload]
    let remove: (PendingUpload) -> Void
    let retry: (PendingUpload) -> Void
    @Environment(\.theme) private var theme

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(uploads) { upload in
                    item(upload)
                        .transition(.scale(scale: 0.8).combined(with: .opacity))
                }
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 10)
        }
        .glass(cornerRadius: 22)
        .padding(.horizontal, 12)
        .padding(.top, 10)
    }

    @ViewBuilder
    private func item(_ upload: PendingUpload) -> some View {
        let content = SwiftUI.Group {
            if let preview = upload.preview {
                AnimatedImageView(image: preview)
                    .frame(width: 64, height: 64)
                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            } else {
                HStack(spacing: 6) {
                    Image(systemName: AttachmentFormat.icon(mime: upload.mime, name: upload.name))
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(theme.accentInk)
                    VStack(alignment: .leading, spacing: 1) {
                        Text(upload.name).bodyFont(12, .semibold, relativeTo: .caption).foregroundStyle(theme.ink).lineLimit(1).truncationMode(.middle)
                        Text(AttachmentFormat.size(upload.data.count)).bodyFont(11, relativeTo: .caption2).foregroundStyle(theme.muted)
                    }
                }
                .padding(.horizontal, 10)
                .frame(width: 150, height: 64, alignment: .leading)
                .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(theme.surface))
            }
        }
        content
            .overlay {
                if upload.isUploading {
                    ZStack {
                        RoundedRectangle(cornerRadius: 12, style: .continuous).fill(.black.opacity(0.3))
                        UploadRing(progress: upload.progress)
                    }
                } else if upload.error != nil {
                    Button { retry(upload) } label: {
                        ZStack {
                            RoundedRectangle(cornerRadius: 12, style: .continuous).fill(.black.opacity(0.45))
                            Image(systemName: "arrow.clockwise").font(.system(size: 17, weight: .bold)).foregroundStyle(.white)
                        }
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Upload failed: \(upload.error ?? ""). Try again")
                }
            }
            .overlay(alignment: .topTrailing) {
                Button { remove(upload) } label: {
                    Image(systemName: "xmark")
                        .font(.system(size: 9, weight: .heavy))
                        .foregroundStyle(theme.onSolid)
                        .frame(width: 20, height: 20)
                        .background(Circle().fill(theme.solid))
                        .frame(width: 32, height: 32)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .offset(x: 10, y: -10)
                .accessibilityLabel("Remove \(upload.isVisual ? "photo" : upload.name)")
            }
            .accessibilityElement(children: .contain)
            .accessibilityLabel(upload.isVisual ? "Photo" : "File: \(upload.name)")
            .accessibilityValue(upload.isUploading ? "Uploading, \(Int(upload.progress * 100)) percent" : upload.error == nil ? "Ready" : "Failed")
    }
}

private struct UploadRing: View {
    let progress: Double

    var body: some View {
        ZStack {
            Circle().stroke(.white.opacity(0.35), lineWidth: 3)
            Circle()
                .trim(from: 0, to: max(progress, 0.04))
                .stroke(.white, style: StrokeStyle(lineWidth: 3, lineCap: .round))
                .rotationEffect(.degrees(-90))
                .animation(.linear(duration: 0.2), value: progress)
        }
        .frame(width: 24, height: 24)
    }
}

// MARK: - Full-screen viewer

/// A message's photos full screen: swipe between them, pinch or double-tap to zoom, share or save.
struct AttachmentViewer: View {
    let attachments: [Attachment]
    @State var selection: String
    @Environment(\.dismiss) private var dismiss
    @State private var shareURL: URL?
    @State private var isPreparingShare = false
    @State private var error: String?

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            TabView(selection: $selection) {
                ForEach(attachments) { attachment in
                    ZoomableImage(attachment: attachment)
                        .tag(attachment.id)
                        .ignoresSafeArea()
                }
            }
            .tabViewStyle(.page(indexDisplayMode: attachments.count > 1 ? .automatic : .never))
            .ignoresSafeArea()
        }
        .overlay(alignment: .top) {
            HStack {
                Button { dismiss() } label: {
                    Image(systemName: "xmark").font(.system(size: 16, weight: .bold)).frame(width: 44, height: 44)
                }
                .accessibilityLabel("Close")
                Spacer()
                if attachments.count > 1, let index = attachments.firstIndex(where: { $0.id == selection }) {
                    Text("\(index + 1) of \(attachments.count)").font(.system(size: 15, weight: .semibold))
                }
                Spacer()
                Button { Task { await share() } } label: {
                    if isPreparingShare { ProgressView().tint(.white).frame(width: 44, height: 44) } else {
                        Image(systemName: "square.and.arrow.up").font(.system(size: 17, weight: .semibold)).frame(width: 44, height: 44)
                    }
                }
                .accessibilityLabel("Share or save")
            }
            .foregroundStyle(.white)
            .padding(.horizontal, 8)
            .background(LinearGradient(colors: [.black.opacity(0.5), .clear], startPoint: .top, endPoint: .bottom).ignoresSafeArea())
        }
        .sheet(item: Binding(get: { shareURL.map(ShareItem.init) }, set: { if $0 == nil { shareURL = nil } })) { item in
            ShareSheet(items: [item.url])
        }
        .errorAlert($error)
        .statusBarHidden()
    }

    /// Downloads the picture to a file (keeping a GIF's animation) for the share sheet, which also offers Save Image.
    private func share() async {
        guard let attachment = attachments.first(where: { $0.id == selection }) else { return }
        isPreparingShare = true
        defer { isPreparingShare = false }
        do {
            shareURL = try await AttachmentFiles.download(attachment)
        } catch {
            self.error = "Couldn't get that photo. Check your connection and try again."
        }
    }
}

private struct ShareItem: Identifiable {
    let url: URL
    var id: URL { url }
}

/// UIActivityViewController, for sharing a downloaded attachment (and saving photos).
struct ShareSheet: UIViewControllerRepresentable {
    let items: [Any]
    func makeUIViewController(context: Context) -> UIActivityViewController { UIActivityViewController(activityItems: items, applicationActivities: nil) }
    func updateUIViewController(_ controller: UIActivityViewController, context: Context) {}
}

/// One picture in the viewer, zoomable with pinch and double-tap (a UIScrollView does the zooming).
private struct ZoomableImage: View {
    let attachment: Attachment
    @State private var image: UIImage?

    var body: some View {
        ZStack {
            if let image {
                ZoomingScrollView(image: image)
            } else {
                // The preview, while the full picture loads.
                AttachmentImage(attachment: attachment, contentMode: .scaleAspectFit)
            }
        }
        .task {
            image = await AttachmentImages.shared.image(key: "\(attachment.id)-full", url: attachment.url)
        }
        .accessibilityLabel(attachment.kind == .gif ? "GIF" : "Photo")
    }
}

private struct ZoomingScrollView: UIViewRepresentable {
    let image: UIImage

    func makeCoordinator() -> Coordinator { Coordinator() }

    func makeUIView(context: Context) -> UIScrollView {
        let scroll = UIScrollView()
        scroll.delegate = context.coordinator
        scroll.minimumZoomScale = 1
        scroll.maximumZoomScale = 4
        scroll.showsVerticalScrollIndicator = false
        scroll.showsHorizontalScrollIndicator = false
        scroll.contentInsetAdjustmentBehavior = .never
        scroll.backgroundColor = .clear
        let imageView = UIImageView(image: image)
        imageView.contentMode = .scaleAspectFit
        imageView.translatesAutoresizingMaskIntoConstraints = false
        imageView.isUserInteractionEnabled = true
        scroll.addSubview(imageView)
        NSLayoutConstraint.activate([
            imageView.widthAnchor.constraint(equalTo: scroll.frameLayoutGuide.widthAnchor),
            imageView.heightAnchor.constraint(equalTo: scroll.frameLayoutGuide.heightAnchor),
            imageView.leadingAnchor.constraint(equalTo: scroll.contentLayoutGuide.leadingAnchor),
            imageView.trailingAnchor.constraint(equalTo: scroll.contentLayoutGuide.trailingAnchor),
            imageView.topAnchor.constraint(equalTo: scroll.contentLayoutGuide.topAnchor),
            imageView.bottomAnchor.constraint(equalTo: scroll.contentLayoutGuide.bottomAnchor)
        ])
        context.coordinator.imageView = imageView
        if image.images != nil { imageView.startAnimating() }
        let doubleTap = UITapGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.doubleTapped(_:)))
        doubleTap.numberOfTapsRequired = 2
        scroll.addGestureRecognizer(doubleTap)
        return scroll
    }

    func updateUIView(_ scroll: UIScrollView, context: Context) {
        if context.coordinator.imageView?.image !== image { context.coordinator.imageView?.image = image }
    }

    final class Coordinator: NSObject, UIScrollViewDelegate {
        weak var imageView: UIImageView?

        func viewForZooming(in scrollView: UIScrollView) -> UIView? { imageView }

        @objc func doubleTapped(_ gesture: UITapGestureRecognizer) {
            guard let scroll = gesture.view as? UIScrollView else { return }
            if scroll.zoomScale > 1 {
                scroll.setZoomScale(1, animated: true)
            } else {
                let point = gesture.location(in: imageView)
                let size = CGSize(width: scroll.bounds.width / 2.5, height: scroll.bounds.height / 2.5)
                scroll.zoom(to: CGRect(x: point.x - size.width / 2, y: point.y - size.height / 2, width: size.width, height: size.height), animated: true)
            }
        }
    }
}

// MARK: - Files on disk

enum AttachmentFiles {
    /// Downloads an attachment into a temporary folder under its own name (for QuickLook and sharing).
    @MainActor
    static func download(_ attachment: Attachment) async throws -> URL {
        let folder = FileManager.default.temporaryDirectory.appending(path: "attachments/\(attachment.id)", directoryHint: .isDirectory)
        let file = folder.appending(path: attachment.name.replacingOccurrences(of: "/", with: "-"))
        if FileManager.default.fileExists(atPath: file.path) { return file }
        let data = try await AttachmentImages.shared.data(url: attachment.url)
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        try data.write(to: file, options: .atomic)
        return file
    }
}
