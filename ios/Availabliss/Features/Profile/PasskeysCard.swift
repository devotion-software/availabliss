import SwiftUI

/// "Passkeys": the ones on this account (name and when it was added), each removable, and Add a
/// passkey. Like web's list; a removed passkey can't sign in any more (email codes still work).
struct PasskeysCard: View {
    @Binding var toast: String?
    @Binding var error: String?
    @Environment(AppModel.self) private var app
    @Environment(\.theme) private var theme
    @State private var passkeys: [PasskeySummary]?
    @State private var isAdding = false
    @State private var removing: PasskeySummary?

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("Passkeys").bodyFont(14, .semibold).accessibilityAddTraits(.isHeader)
                .padding(.vertical, 10)
            if let passkeys {
                ForEach(passkeys) { key in
                    HStack(spacing: 12) {
                        Image(systemName: "person.badge.key").foregroundStyle(theme.accentInk).frame(width: 28)
                        VStack(alignment: .leading, spacing: 1) {
                            Text(key.name?.isEmpty == false ? key.name! : "Passkey").bodyFont(15, .semibold).lineLimit(1)
                            Text("Added \(Formatting.dayMonthYear(key.createdAt))").bodyFont(12, relativeTo: .caption).foregroundStyle(theme.muted)
                        }
                        Spacer(minLength: 8)
                        Button {
                            removing = key
                        } label: {
                            Image(systemName: "trash").frame(width: 36, height: 36)
                        }
                        .buttonStyle(.plain)
                        .foregroundStyle(theme.muted)
                        .accessibilityLabel("Remove \(key.name ?? "passkey")")
                    }
                    .frame(minHeight: 52)
                    Rectangle().fill(theme.line).frame(height: 1)
                }
            } else {
                ProgressView().frame(maxWidth: .infinity, minHeight: 44)
            }
            Button {
                Task { await add() }
            } label: {
                HStack(spacing: 12) {
                    Image(systemName: "plus").frame(width: 28)
                    Text(isAdding ? "Adding\u{2026}" : "Add a passkey").bodyFont(15, .semibold)
                    Spacer()
                }
                .foregroundStyle(theme.accentInk)
                .frame(minHeight: 50)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .disabled(isAdding)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 4)
        .glass(cornerRadius: 24)
        .task { await load() }
        .confirmSheet(item: $removing) { key in
            "Remove \(key.name ?? "this passkey")?"
        } message: { _ in
            "You won\u{2019}t be able to sign in with it any more. Email codes still work."
        } options: { key in
            [ConfirmOption("Remove passkey", systemImage: "trash", kind: .destructive) { Task { await remove(key) } }]
        }
    }

    private func load() async {
        do {
            passkeys = try await app.api.send(API.Auth.listPasskeys()).sorted { $0.createdAt < $1.createdAt }
        } catch  where !error.isCancellation {
            passkeys = []
            self.error = error.userMessage
        } catch {}
    }

    private func add() async {
        guard !isAdding else { return }
        isAdding = true
        defer { isAdding = false }
        do {
            try await app.auth.registerPasskey(name: UIDevice.current.name)
            toast = "Passkey added"
            await load()
        } catch  where error.isCancellation {
        } catch {
            self.error = error.userMessage
        }
    }

    private func remove(_ key: PasskeySummary) async {
        do {
            _ = try await app.api.send(API.Auth.deletePasskey(id: key.id))
            passkeys?.removeAll { $0.id == key.id }
            toast = "Passkey removed"
        } catch  where !error.isCancellation {
            self.error = error.userMessage
        } catch {}
    }
}
