import SwiftUI

/// Group settings, from the group's More menu: its photo, name and colour, the name you go by in it, a new group with the same people, Archive / Unarchive, Leave
/// group and Delete group. Everyone can open it and leave; only the owner and admins can change things
/// (the server's rules), and only the owner can delete the group. Each part is its own section. Admins
/// can also have the group archive itself once its event has passed, and set how often it meets.
struct GroupSettingsView: View {
    let detail: GroupDetailModel
    let group: Group
    /// Opens "New group with these people".
    var onNewGroup: () -> Void = {}
    @Environment(AppModel.self) private var app
    @Environment(\.theme) private var theme
    @State private var name: String
    /// The name you go by in this group, as typed (empty: your account name).
    @State private var myName: String
    @State private var isSavingMyName = false
    @State private var showsPhotoPicker = false
    @State private var confirmsDelete = false
    @State private var confirmsLeave = false
    /// Leave group's sheet sent the only member to Delete group: open it once that sheet has gone.
    @State private var deletesAfterLeaveSheet = false
    @State private var isUploadingPhoto = false
    @State private var isSavingName = false
    @State private var isArchiving = false
    @State private var isSavingAutoArchive = false
    @State private var isSavingRhythm = false
    @State private var isDeleting = false
    @State private var error: String?
    @State private var toast: String?

    init(detail: GroupDetailModel, group: Group, onNewGroup: @escaping () -> Void = {}) {
        self.detail = detail
        self.group = group
        self.onNewGroup = onNewGroup
        _name = State(initialValue: group.name)
        _myName = State(initialValue: "")
    }

    private var canEdit: Bool { group.role.isAdmin }
    private var isOwner: Bool { group.role == .owner }
    private var trimmedName: String { name.trimmingCharacters(in: .whitespacesAndNewlines) }
    private var nameChanged: Bool { !trimmedName.isEmpty && trimmedName != group.name }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                photoCard
                detailsCard
                myNameCard
                if group.members.count > 1 { newGroupCard }
                if canEdit { rhythmCard }
                if canEdit { archiveCard }
                leaveCard
                if isOwner { deleteCard }
            }
            .padding(.horizontal, 20)
            .padding(.top, 20)
            .padding(.bottom, 24)
        }
        .screenBackground(OrbLayout.groups)
        .foregroundStyle(theme.ink)
        // Renamed elsewhere (or by someone else): show the new name unless you're mid-edit.
        .onChange(of: group.name) { old, new in if name == old { name = new } }
        .onAppear { myName = myNameInGroup ?? "" }
        .onChange(of: myNameInGroup) { old, new in if myName == (old ?? "") { myName = new ?? "" } }
        .sheet(isPresented: $confirmsDelete) { deleteSheet }
        .sheet(isPresented: $confirmsLeave, onDismiss: {
            if deletesAfterLeaveSheet {
                deletesAfterLeaveSheet = false
                confirmsDelete = true
            }
        }) { leaveSheet }
        .errorAlert($error)
        .toast($toast)
    }

    // MARK: Photo

    /// The group's photo, shown instead of its monogram tile. Admins can add, change or remove it.
    private var photoCard: some View {
        HStack(spacing: 14) {
            if canEdit {
                Menu {
                    Button { showsPhotoPicker = true } label: {
                        Label(group.image == nil ? "Add photo" : "Change photo", systemImage: "photo")
                    }
                    if group.image != nil {
                        Button(role: .destructive) { setPhoto(nil) } label: { Label("Remove photo", systemImage: "trash") }
                    }
                } label: {
                    tile.overlay(alignment: .bottomTrailing) { cameraBadge }
                }
                .disabled(isUploadingPhoto)
                .accessibilityLabel("Group photo")
                .jpegPhotoPicker(isPresented: $showsPhotoPicker) { jpeg in
                    guard let jpeg else {
                        error = "Couldn\u{2019}t read that photo."
                        return
                    }
                    setPhoto(jpeg)
                }
            } else {
                tile.accessibilityHidden(true)
            }
            VStack(alignment: .leading, spacing: 2) {
                Text("Group photo").bodyFont(15, .semibold)
                Text(photoCaption)
                    .bodyFont(13, relativeTo: .subheadline)
                    .foregroundStyle(theme.muted)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(16)
        .glass(cornerRadius: 24)
    }

    private var tile: some View {
        GroupTile(monogram: group.monogram, colorToken: group.color, imageURL: group.image, size: 64, cornerRadius: 20)
    }

    private var cameraBadge: some View {
        ZStack {
            Circle().fill(theme.solid).frame(width: 24, height: 24)
            if isUploadingPhoto {
                ProgressView().controlSize(.mini).tint(theme.onSolid)
            } else {
                Image(systemName: "camera.fill").font(.system(size: 11, weight: .bold)).foregroundStyle(theme.onSolid)
            }
        }
        .overlay(Circle().stroke(theme.bg, lineWidth: 2))
        .offset(x: 4, y: 4)
    }

    private var photoCaption: String {
        if !canEdit { return "Only the group\u{2019}s owner and admins can change the photo, name and colour." }
        return group.image == nil ? "Shown instead of the \u{201C}\(group.monogram)\u{201D} tile." : "Everyone in the group sees it."
    }

    private func setPhoto(_ jpeg: Data?) {
        Task {
            isUploadingPhoto = true
            defer { isUploadingPhoto = false }
            do {
                let updated = try await detail.api.send(jpeg.map { API.Groups.setImage(id: group.id, jpeg: $0) } ?? API.Groups.removeImage(id: group.id))
                detail.update(updated)
                toast = jpeg == nil ? "Photo removed" : "Photo updated"
            } catch {
                self.error = error.userMessage
            }
        }
    }

    // MARK: Name and colour

    private var detailsCard: some View {
        GlassPanel(cornerRadius: 24) {
            Text("Name").sectionLabelStyle()
            if canEdit {
                HStack(spacing: 8) {
                    InputField(placeholder: "Group name", text: $name)
                        .submitLabel(.done)
                        .onSubmit(saveName)
                    if nameChanged {
                        Button(action: saveName) {
                            LoadingLabel(title: "Save", isLoading: isSavingName)
                        }
                        .buttonStyle(.filled(.accent, height: 50, cornerRadius: 16, fontSize: 15, fullWidth: false))
                        .disabled(isSavingName)
                    }
                }
            } else {
                Text(group.name).bodyFont(16, .semibold)
            }
            Text("Colour").sectionLabelStyle().padding(.top, 6)
            GroupColorPicker(selection: group.color) { token in
                guard token != group.color else { return }
                save(UpdateGroupBody(color: token), toast: "Colour updated")
            }
            .disabled(!canEdit)
            .opacity(canEdit ? 1 : 0.6)
        }
    }

    private func saveName() {
        guard nameChanged, !isSavingName else { return }
        Task {
            isSavingName = true
            defer { isSavingName = false }
            if await update(UpdateGroupBody(name: trimmedName)) { toast = "Name updated" }
        }
    }

    private func save(_ body: UpdateGroupBody, toast message: String) {
        Task { if await update(body) { toast = message } }
    }

    /// Saves the change and shows it on every screen of the group straight away (the live event follows).
    private func update(_ body: UpdateGroupBody) async -> Bool {
        do {
            detail.update(try await detail.api.send(API.Groups.update(id: group.id, body)))
            return true
        } catch {
            self.error = error.userMessage
            return false
        }
    }

    // MARK: Your name in this group

    private var accountName: String { app.session.user?.accountName ?? "" }
    private var myNameInGroup: String? { group.member(app.session.user?.id ?? "")?.nameInGroup }
    private var trimmedMyName: String { myName.trimmingCharacters(in: .whitespacesAndNewlines) }
    /// Blank, or the account name itself, means going by the account name.
    private var myNameToSave: String? { trimmedMyName.isEmpty || trimmedMyName == accountName ? nil : trimmedMyName }
    private var myNameChanged: Bool { myNameToSave != myNameInGroup }

    /// Everyone can choose the name they go by in this group (a nickname, say); blank goes back to the account name.
    private var myNameCard: some View {
        GlassPanel(cornerRadius: 24) {
            VStack(alignment: .leading, spacing: 4) {
                Text("Your name in this group").bodyFont(15, .semibold)
                Text(myNameInGroup == nil
                     ? "\(group.name) sees you as \(accountName), your account name. Choose another name just for this group."
                     : "Only \(group.name) sees this. Everywhere else you\u{2019}re \(accountName).")
                    .bodyFont(13, relativeTo: .subheadline)
                    .foregroundStyle(theme.muted)
                    .fixedSize(horizontal: false, vertical: true)
            }
            HStack(spacing: 8) {
                InputField(placeholder: accountName, text: $myName)
                    .textContentType(.nickname)
                    .submitLabel(.done)
                    .onSubmit { saveMyName(myNameToSave) }
                if myNameChanged {
                    Button { saveMyName(myNameToSave) } label: {
                        LoadingLabel(title: "Save", isLoading: isSavingMyName)
                    }
                    .buttonStyle(.filled(.accent, height: 50, cornerRadius: 16, fontSize: 15, fullWidth: false))
                    .disabled(isSavingMyName)
                }
            }
            if myNameInGroup != nil {
                Button("Use \(accountName) instead") { saveMyName(nil) }
                    .buttonStyle(.textAction)
                    .disabled(isSavingMyName)
            }
        }
    }

    private func saveMyName(_ value: String?) {
        guard value != myNameInGroup, !isSavingMyName else { return }
        Task {
            isSavingMyName = true
            defer { isSavingMyName = false }
            do {
                let member = try await detail.api.send(API.Groups.setMyName(groupId: group.id, displayName: value))
                var updated = group
                if let index = updated.members.firstIndex(where: { $0.userId == member.userId }) { updated.members[index] = member }
                detail.update(updated)
                myName = member.nameInGroup ?? ""
                toast = value == nil ? "Back to \(accountName)" : "Name updated"
            } catch {
                self.error = error.userMessage
            }
        }
    }

    // MARK: New group with these people

    private var newGroupCard: some View {
        GlassPanel(cornerRadius: 24) {
            VStack(alignment: .leading, spacing: 4) {
                Text("Same people, new plan").bodyFont(15, .semibold)
                Text("Start another group with everyone here. You choose who to invite, and each of them gets an invite to accept.")
                    .bodyFont(13, relativeTo: .subheadline)
                    .foregroundStyle(theme.muted)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Button(action: onNewGroup) {
                LoadingLabel(title: "New group with these people", systemImage: "plus.square.on.square")
            }
            .buttonStyle(.filled(.secondary, height: 44, cornerRadius: 14, fontSize: 14))
        }
    }

    // MARK: How often you meet

    /// "How often do you meet?": None / Every week / Every two weeks / Every month, and whether to suggest
    /// the next one in the chat. Admins only; the calendar then shows a "Next one" card.
    private var rhythmCard: some View {
        GlassPanel(cornerRadius: 24) {
            VStack(alignment: .leading, spacing: 4) {
                Text("How often do you meet?").bodyFont(15, .semibold)
                Text("The calendar suggests the free time nearest the next one.")
                    .bodyFont(13, relativeTo: .subheadline)
                    .foregroundStyle(theme.muted)
                    .fixedSize(horizontal: false, vertical: true)
            }
            LazyVGrid(columns: [GridItem(.flexible(), spacing: 8), GridItem(.flexible(), spacing: 8)], alignment: .leading, spacing: 0) {
                rhythmChip("None", isOn: group.rhythm == nil) { setRhythm(nil) }
                ForEach(Group.Rhythm.Every.allCases, id: \.self) { every in
                    rhythmChip(every.label, isOn: group.rhythm?.every == every) {
                        setRhythm(Group.Rhythm(every: every, prompt: group.rhythm?.prompt ?? true))
                    }
                }
            }
            .disabled(isSavingRhythm)
            if let rhythm = group.rhythm {
                Divider().overlay(theme.line).padding(.vertical, 2)
                Toggle(isOn: Binding(get: { rhythm.prompt }, set: { on in
                    setRhythm(Group.Rhythm(every: rhythm.every, prompt: on))
                })) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Suggest the next one in the chat").bodyFont(15, .semibold)
                        Text("Once a plan has ended and nothing newer is planned, it posts the best time for the next one.")
                            .bodyFont(13, relativeTo: .subheadline)
                            .foregroundStyle(theme.muted)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
                .toggleStyle(.glassSwitch)
                .disabled(isSavingRhythm)
            }
        }
    }

    private func rhythmChip(_ label: String, isOn: Bool, action: @escaping () -> Void) -> some View {
        Chip(label: label, isOn: isOn) { if !isOn { action() } }
            .frame(maxWidth: .infinity)
    }

    /// Saves how often the group meets (nil: it doesn't have a rhythm).
    private func setRhythm(_ rhythm: Group.Rhythm?) {
        let old = group.rhythm
        guard rhythm != old else { return }
        // "Every two weeks", "Suggestions off" (only the toggle changed), or "No regular meet-up".
        let message: String
        if let rhythm {
            message = rhythm.every == old?.every ? (rhythm.prompt ? "Suggestions on" : "Suggestions off") : rhythm.every.label
        } else {
            message = "No regular meet-up"
        }
        Task {
            isSavingRhythm = true
            defer { isSavingRhythm = false }
            if await update(UpdateGroupBody(rhythm: rhythm.map { .set($0) } ?? .clear)) { toast = message }
        }
    }

    // MARK: Archive

    private var archiveCard: some View {
        GlassPanel(cornerRadius: 24) {
            VStack(alignment: .leading, spacing: 4) {
                Text(group.isArchived ? "Archived" : "Archive").bodyFont(15, .semibold)
                Text(group.isArchived
                     ? "It\u{2019}s under Archived on everyone\u{2019}s groups list. Unarchive it to bring it back."
                     : "Moves it to Archived at the bottom of everyone\u{2019}s groups list. Nothing is deleted, and you can unarchive it any time.")
                    .bodyFont(13, relativeTo: .subheadline)
                    .foregroundStyle(theme.muted)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Button {
                Task {
                    isArchiving = true
                    defer { isArchiving = false }
                    let archive = !group.isArchived
                    if await update(UpdateGroupBody(archived: archive)) { toast = archive ? "Group archived" : "Group unarchived" }
                }
            } label: {
                LoadingLabel(title: group.isArchived ? "Unarchive group" : "Archive group",
                             systemImage: group.isArchived ? "tray.and.arrow.up" : "archivebox", isLoading: isArchiving)
            }
            .buttonStyle(.filled(.secondary, height: 44, cornerRadius: 14, fontSize: 14))
            .disabled(isArchiving)
            Divider().overlay(theme.line).padding(.vertical, 2)
            Toggle(isOn: Binding(get: { group.archivesAutomatically }, set: setAutoArchive)) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Archive automatically").bodyFont(15, .semibold)
                    Text("Once its last event has passed and nothing else is planned. A new plan brings it back.")
                        .bodyFont(13, relativeTo: .subheadline)
                        .foregroundStyle(theme.muted)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            .toggleStyle(.glassSwitch)
            .disabled(isSavingAutoArchive)
        }
    }

    private func setAutoArchive(_ on: Bool) {
        Task {
            isSavingAutoArchive = true
            defer { isSavingAutoArchive = false }
            if await update(UpdateGroupBody(autoArchive: on)) {
                toast = on ? "Automatic archiving on" : "Automatic archiving off"
            }
        }
    }

    // MARK: Leave

    private var leaveCard: some View {
        GlassPanel(cornerRadius: 24) {
            VStack(alignment: .leading, spacing: 4) {
                Text("Leave group").bodyFont(15, .semibold)
                Text(leaveCaption)
                    .bodyFont(13, relativeTo: .subheadline)
                    .foregroundStyle(theme.muted)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Button { confirmsLeave = true } label: {
                Label("Leave group", systemImage: "rectangle.portrait.and.arrow.right")
            }
            .buttonStyle(.filled(.warning, height: 44, cornerRadius: 14, fontSize: 14))
        }
    }

    private var leaveCaption: String {
        isOwner
            ? "You\u{2019}ll choose an admin to become the owner. You\u{2019}ll need a new invite to come back."
            : "You\u{2019}ll need a new invite to come back."
    }

    @ViewBuilder private var leaveSheet: some View {
        if let me = app.session.user?.id {
            LeaveGroupSheet(group: group, me: me, api: detail.api, onMemberUpdated: { member in
                var updated = group
                if let index = updated.members.firstIndex(where: { $0.userId == member.userId }) { updated.members[index] = member }
                detail.update(updated)
            }, onLeft: {
                app.router.closeGroup(group.id)
            }, onDeleteInstead: {
                deletesAfterLeaveSheet = true
                confirmsLeave = false
            })
        }
    }

    // MARK: Delete

    /// Only the owner sees it. Its own card, like Leave group, so it reads as a deliberate step rather than a stray link.
    private var deleteCard: some View {
        GlassPanel(cornerRadius: 24) {
            VStack(alignment: .leading, spacing: 4) {
                Text("Delete group").bodyFont(15, .semibold)
                Text("Deletes it for everyone in it, with its chat, plans and photos.")
                    .bodyFont(13, relativeTo: .subheadline)
                    .foregroundStyle(theme.muted)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Button { confirmsDelete = true } label: {
                Label("Delete group", systemImage: "trash")
            }
            .buttonStyle(.filled(.warning, height: 44, cornerRadius: 14, fontSize: 14))
        }
    }

    private var deleteSheet: some View {
        BottomSheet(title: "Delete group?") {
            Text("\(group.name) will be deleted for everyone in it, with its chat, plans and photos. This can\u{2019}t be undone.")
                .bodyFont(15)
                .foregroundStyle(theme.muted)
                .fixedSize(horizontal: false, vertical: true)
            Button {
                Task { await deleteGroup() }
            } label: {
                LoadingLabel(title: "Delete group", systemImage: "trash", isLoading: isDeleting)
            }
            .buttonStyle(.filled(.warning))
            .disabled(isDeleting)
            Button("Cancel") { confirmsDelete = false }
                .buttonStyle(.filled(.secondary))
        }
        .errorAlert($error)
    }

    private func deleteGroup() async {
        isDeleting = true
        defer { isDeleting = false }
        do {
            try await detail.api.send(API.Groups.delete(id: group.id))
            confirmsDelete = false
            app.router.closeGroup(group.id)
        } catch {
            self.error = error.userMessage
        }
    }
}
