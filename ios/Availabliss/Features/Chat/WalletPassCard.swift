import PassKit
import SwiftUI
import UIKit

// MARK: - Wallet state

/// Downloads chat passes (once each) and remembers which are already in the user's Wallet.
@MainActor
@Observable
final class WalletPasses {
    static let shared = WalletPasses()
    private var passes: [String: PKPass] = [:]
    private(set) var inWallet: Set<String> = []
    private let library = PKPassLibrary()

    static var isAvailable: Bool { PKPassLibrary.isPassLibraryAvailable() && PKAddPassesViewController.canAddPasses() }

    /// The pass as PassKit sees it. Throws if it can't be downloaded or isn't a valid, signed pass.
    func pass(for attachment: Attachment) async throws -> PKPass {
        if let pass = passes[attachment.id] { return pass }
        let data = try await AttachmentImages.shared.data(url: attachment.url)
        let pass = try PKPass(data: data)
        passes[attachment.id] = pass
        return pass
    }

    /// Checks (quietly) whether the pass is already in Wallet.
    func refresh(_ attachment: Attachment) async {
        guard PKPassLibrary.isPassLibraryAvailable(), let pass = try? await pass(for: attachment) else { return }
        if library.containsPass(pass) { inWallet.insert(attachment.id) } else { inWallet.remove(attachment.id) }
    }
}

// MARK: - Card

/// A Wallet pass in a message, drawn like the pass itself (its colours, logo, what and when),
/// with Add to Apple Wallet, or In Wallet once it's there (which opens it in Wallet).
struct WalletPassCard: View {
    let attachment: Attachment
    let pass: WalletPass
    var isLoading = false
    /// Opens the file itself (Quick Look), for bundles of several passes or where Wallet isn't available.
    let open: () -> Void
    @State private var adding: PassToAdd?
    @State private var isPreparing = false
    @State private var error: String?
    @Environment(\.theme) private var theme
    @Environment(\.openURL) private var openURL

    private var background: Color { Color(hexString: pass.backgroundColor) ?? theme.surface }
    private var foreground: Color { Color(hexString: pass.foregroundColor) ?? theme.ink }
    private var labelColour: Color { Color(hexString: pass.labelColor) ?? foreground.opacity(0.7) }
    private var store: WalletPasses { .shared }
    private var isInWallet: Bool { store.inWallet.contains(attachment.id) }
    private var headline: String { pass.count > 1 ? pass.title : pass.eventName ?? pass.title }

    /// Up to three fields that say something the headline and date don't.
    private var fields: [WalletPass.Field] {
        pass.fields
            .filter { $0.value != headline && $0.value != pass.organizationName && !($0.date && pass.relevantDate != nil) }
            .prefix(3)
            .map { $0 }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            header
            VStack(alignment: .leading, spacing: 3) {
                Text(headline)
                    .bodyFont(16, .semibold)
                    .foregroundStyle(foreground)
                    .lineLimit(2)
                if let date = pass.relevantDate {
                    Text(date.formatted(.dateTime.weekday(.abbreviated).day().month(.abbreviated).hour().minute()))
                        .bodyFont(13, .medium, relativeTo: .subheadline)
                        .foregroundStyle(labelColour)
                }
                if let venue = pass.venue {
                    Text(venue)
                        .bodyFont(12, relativeTo: .caption)
                        .foregroundStyle(foreground.opacity(0.85))
                        .lineLimit(1)
                }
            }
            if !fields.isEmpty {
                HStack(alignment: .top, spacing: 14) {
                    ForEach(Array(fields.enumerated()), id: \.offset) { _, field in
                        VStack(alignment: .leading, spacing: 1) {
                            if let label = field.label {
                                Text(label.uppercased())
                                    .font(.system(size: 9, weight: .semibold))
                                    .foregroundStyle(labelColour)
                                    .lineLimit(1)
                            }
                            Text(value(of: field))
                                .bodyFont(13, .semibold, relativeTo: .subheadline)
                                .foregroundStyle(foreground)
                                .lineLimit(1)
                        }
                    }
                }
            }
            action
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(background))
        .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).strokeBorder(theme.ink.opacity(0.08)))
        .accessibilityElement(children: .contain)
        .accessibilityLabel("Wallet pass: \(pass.organizationName), \(headline)")
        .task(id: attachment.id) {
            if pass.count == 1, WalletPasses.isAvailable { await store.refresh(attachment) }
        }
        .sheet(item: $adding, onDismiss: { Task { await store.refresh(attachment) } }) { item in
            AddPassesSheet(pass: item.pass).ignoresSafeArea()
        }
        .alert("Couldn't add to Wallet", isPresented: Binding(get: { error != nil }, set: { if !$0 { error = nil } })) {
            Button("Open file") { open() }
            Button("OK", role: .cancel) {}
        } message: {
            Text(error ?? "")
        }
    }

    private var header: some View {
        HStack(spacing: 8) {
            if attachment.thumbnailUrl != nil {
                PassLogo(attachment: attachment)
                    .frame(maxWidth: 110, maxHeight: 26, alignment: .leading)
            }
            Text(pass.logoText ?? pass.organizationName)
                .bodyFont(13, .semibold, relativeTo: .subheadline)
                .foregroundStyle(foreground)
                .lineLimit(1)
            Spacer(minLength: 0)
            if pass.voided {
                Text("Voided")
                    .font(.system(size: 10, weight: .bold))
                    .foregroundStyle(foreground)
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background(Capsule().strokeBorder(foreground.opacity(0.6)))
            } else {
                Image(systemName: pass.style == "boardingPass" ? "airplane" : "wallet.pass")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(labelColour)
            }
        }
    }

    @ViewBuilder
    private var action: some View {
        if pass.count > 1 || !WalletPasses.isAvailable {
            Button(action: open) {
                HStack(spacing: 6) {
                    if isLoading { ProgressView().controlSize(.small).tint(foreground) }
                    Text(pass.count > 1 ? "Open \(pass.count) passes" : "Open pass")
                }
                .bodyFont(14, .semibold)
                .foregroundStyle(foreground)
                .frame(maxWidth: .infinity, minHeight: 40)
                .background(RoundedRectangle(cornerRadius: 10, style: .continuous).fill(foreground.opacity(0.14)))
            }
            .buttonStyle(.plain)
        } else if isInWallet {
            Button {
                Task {
                    if let url = try? await store.pass(for: attachment).passURL { openURL(url) }
                }
            } label: {
                Label("In Wallet", systemImage: "checkmark.circle.fill")
                    .bodyFont(14, .semibold)
                    .foregroundStyle(foreground)
                    .frame(maxWidth: .infinity, minHeight: 40)
                    .background(RoundedRectangle(cornerRadius: 10, style: .continuous).fill(foreground.opacity(0.14)))
            }
            .buttonStyle(.plain)
            .accessibilityHint("Opens it in Wallet")
        } else {
            Button(action: add) {
                AddPassButton()
                    .frame(height: 40)
                    .frame(maxWidth: .infinity)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.pressable)
            .disabled(isPreparing)
            .opacity(isPreparing ? 0.5 : 1)
            .overlay { if isPreparing { ProgressView().tint(.white) } }
            .accessibilityLabel("Add to Apple Wallet")
        }
    }

    private func value(of field: WalletPass.Field) -> String {
        guard field.date, let date = JSONCoding.parseDate(field.value) else { return field.value }
        return date.formatted(.dateTime.day().month(.abbreviated).hour().minute())
    }

    private func add() {
        guard !isPreparing else { return }
        isPreparing = true
        Task {
            defer { isPreparing = false }
            do {
                adding = PassToAdd(pass: try await store.pass(for: attachment))
            } catch let error as URLError {
                self.error =
                    error.code == .notConnectedToInternet
                    ? "Check your connection and try again."
                    : "The pass couldn't be downloaded. Try again."
            } catch {
                self.error = "This pass can't be added to Apple Wallet: it may be damaged or not properly signed."
            }
        }
    }
}

private struct PassToAdd: Identifiable {
    let id = UUID()
    let pass: PKPass
}

/// The pass's logo (the server's PNG thumbnail), fitted, without a placeholder.
private struct PassLogo: View {
    let attachment: Attachment
    @State private var image: UIImage?

    var body: some View {
        SwiftUI.Group {
            if let image = image ?? AttachmentImages.shared.cached("\(attachment.id)-thumb") {
                Image(uiImage: image).resizable().scaledToFit()
            } else {
                Color.clear.frame(width: 1, height: 1)
            }
        }
        .task(id: attachment.id) {
            guard image == nil, let url = attachment.thumbnailUrl else { return }
            image = await AttachmentImages.shared.image(key: "\(attachment.id)-thumb", url: url)
        }
        .accessibilityHidden(true)
    }
}

/// Apple's own Add to Apple Wallet button, drawn only: the SwiftUI button around it takes the tap.
/// (As a live UIKit control it kept the touch, so a scroll that started on it didn't move the chat.)
private struct AddPassButton: UIViewRepresentable {
    func makeUIView(context: Context) -> PKAddPassButton {
        let button = PKAddPassButton(addPassButtonStyle: .black)
        button.isUserInteractionEnabled = false
        button.setContentHuggingPriority(.defaultLow, for: .horizontal)
        button.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        return button
    }

    func updateUIView(_ button: PKAddPassButton, context: Context) {}
}

/// Wallet's own add-pass screen.
private struct AddPassesSheet: UIViewControllerRepresentable {
    let pass: PKPass
    @Environment(\.dismiss) private var dismiss

    func makeCoordinator() -> Coordinator { Coordinator(dismiss: { dismiss() }) }

    func makeUIViewController(context: Context) -> UIViewController {
        guard let controller = PKAddPassesViewController(pass: pass) else { return UIViewController() }
        controller.delegate = context.coordinator
        return controller
    }

    func updateUIViewController(_ controller: UIViewController, context: Context) {}

    final class Coordinator: NSObject, PKAddPassesViewControllerDelegate {
        let dismiss: () -> Void
        init(dismiss: @escaping () -> Void) { self.dismiss = dismiss }
        func addPassesViewControllerDidFinish(_ controller: PKAddPassesViewController) { dismiss() }
    }
}
