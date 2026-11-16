import PhotosUI
import SwiftUI
import UIKit

enum PhotoPicking {
    /// Any photo (HEIC, PNG…) as a JPEG no bigger than `maxDimension`, so the upload stays small.
    static func jpeg(from data: Data, maxDimension: CGFloat = 1024) -> Data? {
        guard let image = UIImage(data: data) else { return nil }
        let longest = max(image.size.width, image.size.height)
        guard longest > maxDimension else { return image.jpegData(compressionQuality: 0.85) }
        let scale = maxDimension / longest
        let size = CGSize(width: image.size.width * scale, height: image.size.height * scale)
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1
        return UIGraphicsImageRenderer(size: size, format: format).jpegData(withCompressionQuality: 0.85) { _ in
            image.draw(in: CGRect(origin: .zero, size: size))
        }
    }
}

extension View {
    /// The photo library; `onPick` gets the chosen photo as a JPEG, or nil if it couldn't be read.
    func jpegPhotoPicker(isPresented: Binding<Bool>, maxDimension: CGFloat = 1024, onPick: @escaping @MainActor (Data?) -> Void) -> some View {
        modifier(JPEGPhotoPicker(isPresented: isPresented, maxDimension: maxDimension, onPick: onPick))
    }
}

private struct JPEGPhotoPicker: ViewModifier {
    @Binding var isPresented: Bool
    let maxDimension: CGFloat
    let onPick: @MainActor (Data?) -> Void
    @State private var item: PhotosPickerItem?

    func body(content: Content) -> some View {
        content
            .photosPicker(isPresented: $isPresented, selection: $item, matching: .images)
            .onChange(of: item) { _, picked in
                guard let picked else { return }
                item = nil
                Task {
                    let data = try? await picked.loadTransferable(type: Data.self)
                    onPick(data.flatMap { PhotoPicking.jpeg(from: $0, maxDimension: maxDimension) })
                }
            }
    }
}
