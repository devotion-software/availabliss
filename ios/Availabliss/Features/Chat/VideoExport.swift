import AVFoundation
import CoreTransferable
import SwiftUI
import UIKit
import UniformTypeIdentifiers

/// A video ready to send: shrunk to at most 720p (H.264 MP4), with a frame from it as its poster.
struct PreparedVideo: Sendable {
    let data: Data
    let name: String
    /// JPEG, at most 720 px: the chat's preview until it plays.
    let poster: Data?
    let preview: UIImage?
    let seconds: Double
}

/// A video picked from the library, copied somewhere the app can read it.
struct PickedMovie: Transferable {
    let url: URL

    static var transferRepresentation: some TransferRepresentation {
        FileRepresentation(contentType: .movie) { movie in
            SentTransferredFile(movie.url)
        } importing: { received in
            let copy = FileManager.default.temporaryDirectory.appending(path: "picked-\(UUID().uuidString).\(received.file.pathExtension)")
            try FileManager.default.copyItem(at: received.file, to: copy)
            return PickedMovie(url: copy)
        }
    }
}

struct VideoExportError: LocalizedError {
    let message: String
    var errorDescription: String? { message }
}

enum VideoExport {
    /// Shrinks `source` to 720p MP4 and takes a poster from it (same as Android; the web uploads as is).
    /// Refuses one longer than 10 minutes before doing any work (the server has the last word: 3 minutes
    /// without Pro).
    static func prepare(_ source: URL, name: String) async throws -> PreparedVideo {
        defer { try? FileManager.default.removeItem(at: source) }
        let asset = AVURLAsset(url: source)
        let seconds = try await asset.load(.duration).seconds
        guard seconds.isFinite, seconds > 0 else { throw VideoExportError(message: "That video couldn't be read.") }
        guard seconds <= AttachmentFormat.maxVideoSeconds + 1 else {
            throw VideoExportError(message: "Videos can be up to 10 minutes (3 minutes without Pro).")
        }
        let output = FileManager.default.temporaryDirectory.appending(path: "video-\(UUID().uuidString).mp4")
        defer { try? FileManager.default.removeItem(at: output) }
        guard let session = AVAssetExportSession(asset: asset, presetName: AVAssetExportPreset1280x720) else {
            throw VideoExportError(message: "That video couldn't be prepared.")
        }
        session.shouldOptimizeForNetworkUse = true
        if #available(iOS 18, *) {
            try await session.export(to: output, as: .mp4)
        } else {
            session.outputURL = output
            session.outputFileType = .mp4
            await session.export()
            guard session.status == .completed else { throw VideoExportError(message: "That video couldn't be prepared.") }
        }
        let data = try Data(contentsOf: output)
        let poster = await posterFrame(asset, seconds: seconds)
        let base = (name as NSString).deletingPathExtension
        return PreparedVideo(
            data: data, name: (base.isEmpty ? "Video" : base) + ".mp4", poster: poster?.jpegData(compressionQuality: 0.8), preview: poster, seconds: seconds)
    }

    /// A frame from near the start, upright, at most 720 px.
    private static func posterFrame(_ asset: AVAsset, seconds: Double) async -> UIImage? {
        let generator = AVAssetImageGenerator(asset: asset)
        generator.appliesPreferredTrackTransform = true
        generator.maximumSize = CGSize(width: 720, height: 720)
        let at = CMTime(seconds: min(0.5, seconds / 2), preferredTimescale: 600)
        guard let frame = try? await generator.image(at: at).image else { return nil }
        return UIImage(cgImage: frame)
    }
}
