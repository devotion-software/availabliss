import SwiftUI
import UIKit

/// A blur of whatever is behind it with none of the system materials' grey or white wash: just the
/// blur. SwiftUI's materials always tint (more so in dark mode), which reads as a flat grey band
/// over our coloured backgrounds.
///
/// Uses a regular `UIVisualEffectView`, hides its tint overlay views, and drops the backdrop's
/// brightness/colour-matrix filters (which wash everything towards grey), keeping only the blur and
/// the saturation boost. If a future iOS changes the view hierarchy or filter names, this simply
/// falls back to the material.
struct ClearBlur: UIViewRepresentable {
    var style: UIBlurEffect.Style = .systemUltraThinMaterial

    func makeUIView(context: Context) -> ClearBlurView { ClearBlurView(effect: UIBlurEffect(style: style)) }

    func updateUIView(_ view: ClearBlurView, context: Context) {
        view.effect = UIBlurEffect(style: style)
        view.setNeedsLayout()
    }
}

final class ClearBlurView: UIVisualEffectView {
    override func layoutSubviews() {
        super.layoutSubviews()
        // The effect view draws the blur in a backdrop subview and the tint in overlay subviews
        // (plain views with a background colour); clear those so only the blur shows.
        for sub in subviews {
            if String(describing: type(of: sub)).contains("Backdrop") {
                keepOnlyBlur(sub.layer)
            } else {
                sub.backgroundColor = .clear
            }
        }
    }

    /// Keeps the backdrop's Gaussian blur (made a little stronger) and saturation, and drops the rest.
    private func keepOnlyBlur(_ layer: CALayer) {
        guard let filters = layer.filters as? [NSObject], !filters.isEmpty else { return }
        let kept = filters.filter { filter in
            let name = filter.value(forKey: "name") as? String ?? ""
            return name == "gaussianBlur" || name == "colorSaturate"
        }
        guard kept.contains(where: { ($0.value(forKey: "name") as? String) == "gaussianBlur" }) else { return }
        for filter in kept where (filter.value(forKey: "name") as? String) == "gaussianBlur" {
            filter.setValue(blurRadius, forKey: "inputRadius")
        }
        if kept.count != filters.count { layer.filters = kept }
    }

    private let blurRadius: CGFloat = 24

    override func traitCollectionDidChange(_ previous: UITraitCollection?) {
        super.traitCollectionDidChange(previous)
        setNeedsLayout()
    }
}
