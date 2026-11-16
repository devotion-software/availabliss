import SwiftUI

/// "You": account, appearance, calendars, region, plan, passkeys, timing templates, sign out.
struct ProfileView: View {
    @Environment(AppModel.self) private var app
    @Environment(\.theme) private var theme
    @State private var confirmsSignOut = false
    @State private var confirmsDelete = false
    @State private var isAddingPasskey = false
    @State private var error: String?
    @State private var toast: String?
    @State private var editsName = false
    @State private var editsUsername = false
    @State private var showsPhotoPicker = false
    @State private var isUploadingPhoto = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                account
                settingsList
                PlanSection(toast: $toast)
                SignInMethodsSection(toast: $toast, error: $error)
                TemplatesSection(api: app.api, toast: $toast, error: $error)
                VStack(spacing: 4) {
                    Button("Sign out") { confirmsSignOut = true }.buttonStyle(.textAction)
                    Button("Delete account") { confirmsDelete = true }.buttonStyle(.textAction).foregroundStyle(theme.warnInk)
                    Text(Self.version).bodyFont(12, relativeTo: .caption).foregroundStyle(theme.muted).padding(.top, 8)
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
        .sheet(isPresented: $editsName) {
            EditNameSheet(initialName: app.session.user?.name ?? "") { toast = "Name updated" }
        }
        .sheet(isPresented: $editsUsername) {
            EditUsernameSheet(api: app.api, current: app.session.user?.username) { toast = "Username updated" }
        }
        .errorAlert($error)
        .toast($toast)
    }

    /// "Availabliss v1.2.0 (14)": marketing version and build number from Info.plist.
    private static var version: String {
        let info = Bundle.main.infoDictionary
        let short = info?["CFBundleShortVersionString"] as? String ?? "?"
        let build = info?["CFBundleVersion"] as? String ?? "?"
        return "Availabliss v\(short) (\(build))"
    }

    private var account: some View {
        let name = app.session.user?.name.trimmingCharacters(in: .whitespaces) ?? ""
        return HStack(spacing: 14) {
            Menu {
                Button { showsPhotoPicker = true } label: {
                    Label(app.session.user?.image == nil ? "Add a photo" : "Choose a new photo", systemImage: "photo")
                }
                if app.session.user?.image != nil {
                    Button(role: .destructive) { Task { await setPhoto(nil) } } label: { Label("Remove photo", systemImage: "trash") }
                }
            } label: {
                Avatar(name: name.isEmpty ? (app.session.user?.email ?? "?") : name, imageURL: app.session.user?.image, color: theme.tiles[0], size: 56)
                    .overlay(alignment: .bottomTrailing) {
                        ZStack {
                            Circle().fill(theme.solid).frame(width: 22, height: 22)
                            if isUploadingPhoto {
                                ProgressView().controlSize(.mini).tint(theme.onSolid)
                            } else {
                                Image(systemName: "camera.fill").font(.system(size: 10, weight: .bold)).foregroundStyle(theme.onSolid)
                            }
                        }
                        .overlay(Circle().stroke(theme.bg, lineWidth: 2))
                        .offset(x: 2, y: 2)
                    }
            }
            .disabled(isUploadingPhoto)
            .accessibilityLabel("Profile photo")
            .jpegPhotoPicker(isPresented: $showsPhotoPicker) { jpeg in
                guard let jpeg else {
                    error = "Couldn\u{2019}t read that photo."
                    return
                }
                Task { await setPhoto(jpeg) }
            }
            VStack(alignment: .leading, spacing: 2) {
                if name.isEmpty {
                    Button("Add your name") { editsName = true }
                        .bodyFont(20, .bold)
                        .foregroundStyle(theme.accentInk)
                } else {
                    Text(name).displayFont(24, .bold).lineLimit(2).accessibilityAddTraits(.isHeader)
                }
                Button { editsUsername = true } label: {
                    Text(app.session.user?.username.map { "@\($0)" } ?? "Choose a username")
                        .bodyFont(14, .semibold, relativeTo: .subheadline)
                        .foregroundStyle(theme.accentInk)
                        .lineLimit(1)
                }
                .buttonStyle(.plain)
                .accessibilityHint("Change your username")
                Text(app.session.user?.email ?? "").bodyFont(13, relativeTo: .subheadline).foregroundStyle(theme.muted)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            IconButton(systemImage: "pencil", label: "Edit your name") { editsName = true }
        }
        .padding(.bottom, 4)
    }

    private func setPhoto(_ jpeg: Data?) async {
        isUploadingPhoto = true
        defer { isUploadingPhoto = false }
        do {
            try await app.session.setAvatar(jpeg: jpeg)
            toast = jpeg == nil ? "Photo removed" : "Photo updated"
        } catch {
            self.error = error.userMessage
        }
    }

    private var settingsList: some View {
        VStack(spacing: 0) {
            SettingsRow(title: "Appearance", systemImage: "paintpalette", value: "\(app.appearance.mode.rawValue.capitalized) · \(app.appearance.colorTheme.label)") {
                app.router.push(.appearance)
            }
            divider
            SettingsRow(title: "Calendars", systemImage: "calendar", value: app.deviceCalendar.statusSummary) {
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
            .disabled(isAddingPasskey)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 4)
        .glass(cornerRadius: 24)
    }

    private var divider: some View { Rectangle().fill(theme.line).frame(height: 1) }

    private func addPasskey() async {
        guard !isAddingPasskey else { return }
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

/// Change the username people can invite you by.
private struct EditUsernameSheet: View {
    let current: String?
    let onSaved: () -> Void
    @Environment(AppModel.self) private var app
    @Environment(\.dismiss) private var dismiss
    @State private var model: UsernameFieldModel
    @State private var isSaving = false
    @State private var error: String?

    init(api: APIClient, current: String?, onSaved: @escaping () -> Void) {
        self.current = current
        self.onSaved = onSaved
        _model = State(initialValue: UsernameFieldModel(api: api))
    }

    var body: some View {
        BottomSheet(title: "Your username") {
            UsernameField(model: model, focusOnAppear: true)
                .onSubmit { Task { await save() } }
            Text("People can invite you to groups with it instead of your email.")
                .bodyFont(13, relativeTo: .subheadline)
                .foregroundStyle(.secondary)
            Button {
                Task { await save() }
            } label: {
                LoadingLabel(title: "Save", isLoading: isSaving)
            }
            .buttonStyle(.filled(.primary, height: 54, cornerRadius: 18))
            .disabled(!model.hasChanges || !model.canSave || isSaving)
        }
        .onAppear { model.prefill(current) }
        .errorAlert($error)
    }

    private func save() async {
        guard let username = model.patchValue, model.canSave, !isSaving else { return }
        isSaving = true
        defer { isSaving = false }
        do {
            let user = try await app.session.update(UserPatch(username: username))
            model.saved(user.username)
            onSaved()
            dismiss()
        } catch {
            if !model.handleSaveError(error) { self.error = error.userMessage }
        }
    }
}

/// Change the name your groups see.
private struct EditNameSheet: View {
    let initialName: String
    let onSaved: () -> Void
    @Environment(AppModel.self) private var app
    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var isSaving = false
    @State private var error: String?
    @FocusState private var focused: Bool

    private var trimmed: String { name.trimmingCharacters(in: .whitespacesAndNewlines) }

    var body: some View {
        BottomSheet(title: "Your name") {
            InputField(placeholder: "First and last name", text: $name)
                .textContentType(.name)
                .textInputAutocapitalization(.words)
                .submitLabel(.done)
                .focused($focused)
                .onSubmit { Task { await save() } }
            Text("This is how you appear to people in your groups.")
                .bodyFont(13, relativeTo: .subheadline)
                .foregroundStyle(.secondary)
            Button {
                Task { await save() }
            } label: {
                LoadingLabel(title: "Save", isLoading: isSaving)
            }
            .buttonStyle(.filled(.primary, height: 54, cornerRadius: 18))
            .disabled(trimmed.isEmpty || trimmed == initialName || isSaving)
        }
        .onAppear {
            name = initialName
            focused = true
        }
        .errorAlert($error)
    }

    private func save() async {
        guard !trimmed.isEmpty, !isSaving else { return }
        isSaving = true
        defer { isSaving = false }
        do {
            _ = try await app.session.update(UserPatch(name: String(trimmed.prefix(80))))
            onSaved()
            dismiss()
        } catch {
            self.error = error.userMessage
        }
    }
}
