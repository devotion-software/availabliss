import SwiftUI

/// "Notifications": which pushes you get, by category, and quiet hours. Each change saves at once,
/// and goes back to what was saved if that fails. Muting a group is in the group's More menu.
struct NotificationsCard: View {
    @Binding var error: String?
    @Environment(AppModel.self) private var app
    @Environment(\.theme) private var theme
    /// What the server last confirmed, to go back to if a save fails.
    @State private var saved: NotificationSettings?
    /// What's on screen: `saved` plus any change still being sent.
    @State private var draft: NotificationSettings?
    @State private var loadError: String?
    /// Counts saves, so only the newest one's reply replaces the draft.
    @State private var saveCount = 0
    /// Quiet hours to bring back when they're turned on again (the defaults until there are some).
    @State private var lastQuietHours = QuietHours.default

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            VStack(alignment: .leading, spacing: 2) {
                Text("Notifications").bodyFont(14, .semibold).accessibilityAddTraits(.isHeader)
                Text("Pushes on this and your other devices. Invites always come through.")
                    .bodyFont(12, relativeTo: .caption)
                    .foregroundStyle(theme.muted)
                    .fixedSize(horizontal: false, vertical: true)
            }
            if let draft {
                ForEach(NotificationCategory.allCases) { category in
                    Toggle(isOn: categoryBinding(category, in: draft)) {
                        Text(category.label).bodyFont(15, .semibold)
                    }
                    .toggleStyle(.glassSwitch)
                    .frame(minHeight: 40)
                }
                Rectangle().fill(theme.line).frame(height: 1).padding(.vertical, 2)
                quietHours(draft)
            } else if let loadError {
                HStack {
                    Text(loadError)
                        .bodyFont(13, relativeTo: .subheadline)
                        .foregroundStyle(theme.muted)
                        .fixedSize(horizontal: false, vertical: true)
                    Spacer()
                    Button("Try again") { Task { await load() } }.buttonStyle(.textAction)
                }
            } else {
                ProgressView().frame(maxWidth: .infinity, minHeight: 44)
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .glass(cornerRadius: 24)
        .task(id: app.accountVersion) { await load() }
    }

    @ViewBuilder
    private func quietHours(_ settings: NotificationSettings) -> some View {
        Toggle(isOn: Binding(get: { settings.quietHours != nil }, set: setQuietHours)) {
            VStack(alignment: .leading, spacing: 2) {
                Text("Quiet hours").bodyFont(15, .semibold)
                Text("No pushes during these hours. They\u{2019}re still in Activity.")
                    .bodyFont(13, relativeTo: .subheadline)
                    .foregroundStyle(theme.muted)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .toggleStyle(.glassSwitch)
        if let hours = settings.quietHours {
            HStack(spacing: 8) {
                timePicker("From", time: hours.from) { QuietHours(from: $0, to: hours.to) }
                Image(systemName: "arrow.right")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(theme.muted)
                    .accessibilityHidden(true)
                timePicker("To", time: hours.to) { QuietHours(from: hours.from, to: $0) }
                Spacer(minLength: 0)
            }
            .transition(.opacity)
        }
    }

    /// A compact time picker for one end of quiet hours; `change` builds the new quiet hours from the picked "HH:MM".
    private func timePicker(_ label: String, time: String, change: @escaping (String) -> QuietHours) -> some View {
        DatePicker(
            label,
            selection: Binding(
                get: { Self.date(from: time) },
                set: { date in
                    let picked = Self.time(from: date)
                    guard picked != time else { return }
                    let hours = change(picked)
                    lastQuietHours = hours
                    save(NotificationSettingsPatch(quietHours: .some(hours))) { $0.quietHours = hours }
                }
            ), displayedComponents: .hourAndMinute
        )
        .datePickerStyle(.compact)
        .labelsHidden()
        .tint(theme.accent)
        .accessibilityLabel(label == "From" ? "Quiet hours start" : "Quiet hours end")
    }

    private func categoryBinding(_ category: NotificationCategory, in settings: NotificationSettings) -> Binding<Bool> {
        Binding(
            get: { !settings.off.contains(category) },
            set: { on in
                var off = settings.off.filter { $0 != category }
                if !on { off.append(category) }
                // In the declared order, so the list doesn't depend on the order they were switched.
                off = NotificationCategory.allCases.filter(off.contains)
                save(NotificationSettingsPatch(off: off, keeping: settings.unknownOff)) { $0.off = off }
            }
        )
    }

    private func setQuietHours(_ on: Bool) {
        let hours: QuietHours? = on ? lastQuietHours : nil
        if let current = draft?.quietHours { lastQuietHours = current }
        save(NotificationSettingsPatch(quietHours: .some(hours))) { $0.quietHours = hours }
    }

    private func load() async {
        do {
            let settings = try await app.api.send(API.Me.notificationSettings())
            saved = settings
            draft = settings
            if let hours = settings.quietHours { lastQuietHours = hours }
            loadError = nil
        } catch  where !error.isCancellation {
            if draft == nil { loadError = error.userMessage }
        } catch {}
    }

    /// Shows `change` at once and sends `patch`; if it fails, goes back to what was last saved.
    private func save(_ patch: NotificationSettingsPatch, _ change: (inout NotificationSettings) -> Void) {
        guard var next = draft else { return }
        change(&next)
        withAnimation(.spring(response: 0.3, dampingFraction: 0.85)) { draft = next }
        saveCount += 1
        let count = saveCount
        Task {
            do {
                let result = try await app.api.send(API.Me.updateNotificationSettings(patch))
                saved = result
                if count == saveCount { draft = result }
            } catch {
                if count == saveCount {
                    withAnimation(.spring(response: 0.3, dampingFraction: 0.85)) { draft = saved }
                }
                self.error = error.userMessage
            }
        }
    }

    // MARK: "HH:MM" ⇄ Date (today, in this device's time zone: only the wall-clock time matters)

    private static func date(from time: String) -> Date {
        let parts = time.split(separator: ":").compactMap { Int($0) }
        let hour = parts.first ?? 0
        let minute = parts.count > 1 ? parts[1] : 0
        return Calendar.current.date(bySettingHour: hour, minute: minute, second: 0, of: Date()) ?? Date()
    }

    private static func time(from date: Date) -> String {
        let parts = Calendar.current.dateComponents([.hour, .minute], from: date)
        return String(format: "%02d:%02d", parts.hour ?? 0, parts.minute ?? 0)
    }
}
