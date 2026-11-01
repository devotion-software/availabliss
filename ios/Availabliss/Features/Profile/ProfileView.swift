import SwiftUI

/// "You": account, appearance, calendars, region, passkeys, sign out.
struct ProfileView: View {
    @Environment(AppModel.self) private var app
    @Environment(\.theme) private var theme
    @State private var confirmsSignOut = false
    @State private var confirmsDelete = false
    @State private var isAddingPasskey = false
    @State private var error: String?
    @State private var toast: String?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                account
                settingsList
                VStack(spacing: 4) {
                    Button("Sign out") { confirmsSignOut = true }.buttonStyle(.textAction)
                    Button("Delete account") { confirmsDelete = true }.buttonStyle(.textAction).foregroundStyle(theme.warnInk)
                }
                .frame(maxWidth: .infinity)
            }
            .padding(.horizontal, 20)
            .padding(.top, 28)
            .padding(.bottom, 24)
        }
        .screenBackground(OrbLayout.appearance)
        .foregroundStyle(theme.ink)
        .chromeless()
        .confirmationDialog("Sign out of Availabliss?", isPresented: $confirmsSignOut, titleVisibility: .visible) {
            Button("Sign out", role: .destructive) { Task { await app.signOut() } }
        }
        .confirmationDialog("Delete your account and all your data? This can't be undone.", isPresented: $confirmsDelete, titleVisibility: .visible) {
            Button("Delete account", role: .destructive) {
                Task {
                    do { try await app.deleteAccount() } catch { self.error = error.userMessage }
                }
            }
        }
        .errorAlert($error)
        .toast($toast)
    }

    private var account: some View {
        HStack(spacing: 14) {
            Avatar(name: app.session.user?.name ?? "?", imageURL: app.session.user?.image, color: theme.tiles[0], size: 56)
            VStack(alignment: .leading, spacing: 2) {
                Text(app.session.user?.name ?? "").displayFont(24, .bold).accessibilityAddTraits(.isHeader)
                Text(app.session.user?.email ?? "").bodyFont(13, relativeTo: .subheadline).foregroundStyle(theme.muted)
            }
        }
        .padding(.bottom, 4)
    }

    private var settingsList: some View {
        VStack(spacing: 0) {
            SettingsRow(title: "Appearance", systemImage: "paintpalette", value: "\(app.appearance.mode.rawValue.capitalized) · \(app.appearance.colorTheme.label)") {
                app.router.push(.appearance)
            }
            divider
            SettingsRow(title: "Calendars", systemImage: "calendar", value: app.deviceCalendar.hasFullAccess ? "Syncing" : nil) {
                app.router.push(.calendars)
            }
            divider
            SettingsRow(title: "Region", systemImage: "mappin.and.ellipse", value: app.session.user?.country.flatMap { Locale.current.localizedString(forRegionCode: $0) }) {
                app.router.push(.region)
            }
            divider
            SettingsRow(title: "Add a passkey", systemImage: "person.badge.key", value: isAddingPasskey ? "Adding…" : nil) {
                Task { await addPasskey() }
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 4)
        .glass(cornerRadius: 24)
    }

    private var divider: some View { Rectangle().fill(theme.line).frame(height: 1) }

    private func addPasskey() async {
        isAddingPasskey = true
        defer { isAddingPasskey = false }
        do {
            try await app.auth.registerPasskey(name: UIDevice.current.name)
            toast = "Passkey added"
        } catch where error.isCancellation {
        } catch {
            self.error = error.userMessage
        }
    }
}

private struct SettingsRow: View {
    let title: String
    let systemImage: String
    var value: String?
    let action: () -> Void
    @Environment(\.theme) private var theme

    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Image(systemName: systemImage)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(theme.accentInk)
                    .frame(width: 32, height: 32)
                    .background(RoundedRectangle(cornerRadius: 10).fill(theme.accentSoft))
                Text(title).bodyFont(15, .semibold).foregroundStyle(theme.ink)
                Spacer()
                if let value { Text(value).bodyFont(13, relativeTo: .subheadline).foregroundStyle(theme.muted).lineLimit(1) }
                Image(systemName: "chevron.right").font(.system(size: 12, weight: .semibold)).foregroundStyle(theme.muted)
            }
            .frame(minHeight: 56)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}
