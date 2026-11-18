import EventKit
import SwiftUI

/// "Which calendars mean you're busy?" — onboarding step 2 and the calendar settings screen.
struct CalendarsView: View {
    enum Mode {
        case onboarding(onBack: () -> Void, onContinue: () -> Void, onSkip: () -> Void)
        case settings
    }

    let mode: Mode
    @Environment(AppModel.self) private var app
    @Environment(\.theme) private var theme
    @State private var model: CalendarsViewModel
    @State private var showsCalDAV = false
    @Environment(\.onboardingSteps) private var onboardingSteps
    @State private var showsProviders = false
    /// Picked in the providers sheet; acted on once it has gone (iCloud opens its own sheet).
    @State private var pickedProvider: ProviderChoice?

    private enum ProviderChoice { case google, microsoft, iCloud }

    init(api: APIClient, mode: Mode) {
        self.mode = mode
        _model = State(initialValue: CalendarsViewModel(api: api))
    }

    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    header
                    intro
                    DeviceCalendarsSection(service: app.deviceCalendar)
                    ForEach(model.remoteConnections) { connection in
                        ConnectionSection(connection: connection, model: model)
                    }
                    connectButton
                    RegionCard(onChange: changeRegion)
                    if case .settings = mode {
                        BusyBufferCard(onError: { model.error = $0 })
                        CalendarFeedCard(model: model)
                    }
                }
                .padding(.horizontal, 20)
                .padding(.top, 20)
                .padding(.bottom, 16)
            }
            .scrollBounceBehavior(.basedOnSize)
            footer
        }
        .screenBackground(OrbLayout.calendars)
        .foregroundStyle(theme.ink)
        .chromeless()
        .task { await model.load() }
        .refreshable { await model.load() }
        .sheet(isPresented: $showsCalDAV) { CalDAVSheet(model: model) }
        .sheet(isPresented: $showsProviders, onDismiss: connectPicked) {
            BottomSheet(title: "Connect a calendar") {
                ProviderRow(title: "Google Calendar", systemImage: "globe") { pick(.google) }
                ProviderRow(title: "Outlook", systemImage: "envelope") { pick(.microsoft) }
                ProviderRow(title: "iCloud", subtitle: "With an app-specific password", systemImage: "icloud") { pick(.iCloud) }
            }
        }
        .errorAlert($model.error)
        .toast($model.toast)
    }

    private var header: some View {
        HStack {
            switch mode {
            case .onboarding(let onBack, _, _):
                IconButton(systemImage: "chevron.left", label: "Back", action: onBack)
                Spacer()
                StepDots(current: 1, total: onboardingSteps)
            case .settings:
                IconButton(systemImage: "chevron.left", label: "Back") { app.router.pop() }
                Spacer()
            }
        }
    }

    private var intro: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("Which calendars mean you're busy?")
                .displayFont(30, .bold)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityAddTraits(.isHeader)
            Text("Events in these calendars block out your time. Only \u{201C}busy\u{201D} is shared, never the details.")
                .bodyFont(14)
                .lineSpacing(2)
                .foregroundStyle(theme.muted)
        }
    }

    /// Opens a sheet of providers. (A Menu here morphed oddly back into the glass button as it closed.)
    private var connectButton: some View {
        Button {
            showsProviders = true
        } label: {
            HStack(spacing: 10) {
                Image(systemName: "plus")
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(theme.accentInk)
                    .frame(width: 30, height: 30)
                    .background(RoundedRectangle(cornerRadius: 10).fill(theme.accentSoft))
                VStack(alignment: .leading, spacing: 1) {
                    Text("Connect another calendar").bodyFont(14, .semibold).lineLimit(1)
                    Text("Google · Outlook · iCloud").bodyFont(12, .medium, relativeTo: .caption).foregroundStyle(theme.muted).lineLimit(1)
                }
                Spacer()
                if model.connectingProvider != nil {
                    ProgressView()
                } else {
                    Image(systemName: "chevron.right").font(.system(size: 12, weight: .semibold)).foregroundStyle(theme.muted)
                }
            }
            .foregroundStyle(theme.ink)
            .padding(.horizontal, 14)
            .frame(minHeight: 58)
            .glass(cornerRadius: 20)
            .contentShape(Rectangle())
        }
        .buttonStyle(.pressable)
        .disabled(model.connectingProvider != nil)
    }

    private func pick(_ choice: ProviderChoice) {
        pickedProvider = choice
        showsProviders = false
    }

    private func connectPicked() {
        guard let choice = pickedProvider else { return }
        pickedProvider = nil
        switch choice {
        case .google: Task { await model.connectOAuth(.google) }
        case .microsoft: Task { await model.connectOAuth(.microsoft) }
        case .iCloud: showsCalDAV = true
        }
    }

    @ViewBuilder
    private var footer: some View {
        if case .onboarding(_, let onContinue, let onSkip) = mode {
            VStack(spacing: 0) {
                Button("Continue") {
                    Task { await app.deviceCalendar.syncIfAuthorized() }
                    onContinue()
                }
                .buttonStyle(.filled(.primary, height: 54, cornerRadius: 18))
                Button("No calendar? Mark busy times yourself", action: onSkip)
                    .bodyFont(14, .semibold)
                    .foregroundStyle(theme.ink)
                    .frame(maxWidth: .infinity, minHeight: 44)
                    .padding(.top, 4)
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 12)
        }
    }

    private func changeRegion() {
        switch mode {
        case .onboarding(let onBack, _, _): onBack()
        case .settings: app.router.push(.region)
        }
    }
}

/// "On this iPhone" — EventKit calendars with busy toggles, or a prompt to allow access.
private struct DeviceCalendarsSection: View {
    let service: DeviceCalendarService
    @Environment(\.theme) private var theme

    var body: some View {
        if service.hasFullAccess {
            ForEach(service.calendarsBySource, id: \.source) { group in
                CalendarToggleSection(
                    title: "On this iPhone",
                    account: group.source,
                    rows: group.calendars.map { calendar in
                        CalendarToggleRow(id: calendar.calendarIdentifier, name: calendar.title, color: Color(cgColor: calendar.cgColor)) {
                            service.isBusy(calendar)
                        } set: { busy in
                            service.setBusy(busy, for: calendar)
                        }
                    }
                )
            }
            DeviceSyncStatus(service: service)
        } else {
            AccessPrompt(denied: service.needsSettingsForFullAccess) {
                Task { await service.requestFullAccess() }
            }
        }
    }
}

/// When this phone's busy time last reached the server, or why it didn't.
private struct DeviceSyncStatus: View {
    let service: DeviceCalendarService
    @Environment(\.theme) private var theme

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            if let error = service.lastError {
                HStack {
                    StatusPill(text: "Sync problem", tone: .apricot, systemImage: "exclamationmark")
                    Spacer()
                    Button(service.isSyncing ? "Syncing…" : "Try again") {
                        Task { await service.syncIfAuthorized() }
                    }
                    .buttonStyle(.textAction)
                    .disabled(service.isSyncing)
                }
                Text(error)
                    .bodyFont(13)
                    .foregroundStyle(theme.muted)
                if let last = service.lastSyncedAt {
                    Text("Last synced \(Formatting.relative(last))")
                        .bodyFont(12)
                        .foregroundStyle(theme.muted)
                }
            } else {
                // Reads this phone's calendars and uploads what changed, straight away.
                SyncNowRow(lastSyncedAt: service.lastSyncedAt, isSyncing: service.isSyncing) {
                    Task { await service.syncIfAuthorized() }
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 6)
    }
}

/// "Synced 5 min. ago" with Sync now (a spinner while it runs), under an account's calendars.
private struct SyncNowRow: View {
    let lastSyncedAt: Date?
    let isSyncing: Bool
    /// "Last synced" when the account has a problem.
    var prefix = "Synced"
    let action: () -> Void
    @Environment(\.theme) private var theme

    var body: some View {
        HStack(spacing: 8) {
            TimelineView(.periodic(from: .now, by: 30)) { context in
                Text(status(now: context.date))
                    .bodyFont(12, relativeTo: .caption)
                    .foregroundStyle(theme.muted)
            }
            Spacer(minLength: 8)
            if isSyncing {
                HStack(spacing: 6) {
                    ProgressView().controlSize(.small)
                    Text("Syncing…").bodyFont(13, .semibold, relativeTo: .subheadline)
                }
                .foregroundStyle(theme.muted)
                .frame(minHeight: 44)
                .accessibilityElement(children: .combine)
            } else {
                Button(action: action) {
                    HStack(spacing: 5) {
                        Image(systemName: "arrow.clockwise").font(.system(size: 12, weight: .bold)).accessibilityHidden(true)
                        Text("Sync now")
                    }
                }
                .buttonStyle(TextActionButtonStyle(fontSize: 13))
            }
        }
        .animation(.easeInOut(duration: 0.2), value: isSyncing)
    }

    private func status(now: Date) -> String {
        guard let lastSyncedAt else { return "Not synced yet" }
        if now.timeIntervalSince(lastSyncedAt) < 60 { return "\(prefix) just now" }
        return "\(prefix) \(Formatting.relative(lastSyncedAt, now: now))"
    }
}

private struct AccessPrompt: View {
    let denied: Bool
    let request: () -> Void
    @Environment(\.theme) private var theme
    @Environment(\.openURL) private var openURL

    var body: some View {
        GlassPanel(cornerRadius: 24, spacing: 10) {
            Label("Calendars on this iPhone", systemImage: "iphone")
                .bodyFont(15, .semibold)
            Text(denied
                 ? "Calendar access is turned off. Allow it in Settings to use the calendars on this phone."
                 : "Use the calendars already on this phone (iCloud, Google, Exchange…). Only start and end times are shared.")
                .bodyFont(13)
                .foregroundStyle(theme.muted)
            Button(denied ? "Open Settings" : "Allow calendar access") {
                if denied, let url = URL(string: UIApplication.openSettingsURLString) { openURL(url) } else { request() }
            }
            .buttonStyle(.filled(.accent, height: 44, cornerRadius: 14, fontSize: 14))
        }
    }
}

/// One account's calendars from the server.
private struct ConnectionSection: View {
    let connection: CalendarConnection
    let model: CalendarsViewModel
    @Environment(\.theme) private var theme

    var body: some View {
        VStack(spacing: 8) {
            CalendarToggleSection(
                title: connection.provider.displayName,
                account: connection.label,
                rows: connection.calendars.map { calendar in
                    CalendarToggleRow(id: calendar.id, name: calendar.name, color: Color(hexString: calendar.color) ?? theme.accent) {
                        calendar.busy
                    } set: { busy in
                        Task { await model.setBusy(busy, calendar: calendar, in: connection) }
                    }
                }
            )
            .contextMenu {
                Button { Task { await model.sync(connection) } } label: { Label("Sync now", systemImage: "arrow.clockwise") }
                Button(role: .destructive) { Task { await model.disconnect(connection) } } label: { Label("Disconnect", systemImage: "trash") }
            }
            VStack(alignment: .leading, spacing: 0) {
                if connection.status != .ok {
                    HStack {
                        StatusPill(text: connection.status == .reauthRequired ? "Needs reconnecting" : "Sync problem", tone: .apricot, systemImage: "exclamationmark")
                        Spacer()
                        if connection.provider == .google || connection.provider == .microsoft {
                            Button("Reconnect") { Task { await model.connectOAuth(connection.provider) } }
                                .buttonStyle(.textAction)
                        }
                    }
                }
                SyncNowRow(
                    lastSyncedAt: connection.lastSyncedAt,
                    isSyncing: model.syncingIds.contains(connection.id),
                    prefix: connection.status == .ok ? "Synced" : "Last synced"
                ) {
                    Task { await model.sync(connection) }
                }
            }
            .padding(.horizontal, 6)
        }
    }
}

struct CalendarToggleRow: Identifiable {
    let id: String
    let name: String
    let color: Color
    let get: @MainActor @Sendable () -> Bool
    let set: @MainActor @Sendable (Bool) -> Void
}

/// Glass section: "Source … account" header, then rows with a colour square and a switch.
private struct CalendarToggleSection: View {
    let title: String
    let account: String
    let rows: [CalendarToggleRow]
    @Environment(\.theme) private var theme

    var body: some View {
        GlassList(data: rows, id: \.id, header: title, trailingHeader: account, rowMinHeight: 50) { row in
            Toggle(isOn: Binding(get: { row.get() }, set: { row.set($0) })) {
                HStack(spacing: 12) {
                    RoundedRectangle(cornerRadius: 4).fill(row.color).frame(width: 12, height: 12).accessibilityHidden(true)
                    Text(row.name).bodyFont(15, .medium)
                }
            }
            .toggleStyle(.glassSwitch)
            .accessibilityLabel("\(row.name) counts as busy")
        }
    }
}

/// Region summary card with "Change".
private struct RegionCard: View {
    let onChange: () -> Void
    @Environment(AppModel.self) private var app
    @Environment(\.theme) private var theme

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "mappin.and.ellipse")
                .font(.system(size: 18, weight: .medium))
                .foregroundStyle(theme.okInk)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 1) {
                Text(regionTitle).bodyFont(14, .semibold)
                Text(regionSubtitle).bodyFont(12, relativeTo: .caption).foregroundStyle(theme.muted)
            }
            Spacer()
            Button("Change", action: onChange).buttonStyle(TextActionButtonStyle(fontSize: 13))
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .glass(cornerRadius: 20)
    }

    private var regionTitle: String {
        guard let user = app.session.user, let country = user.country else { return "Region not set" }
        let name = Locale.current.localizedString(forRegionCode: country) ?? country
        return [name, user.subdivision].compactMap { $0 }.joined(separator: " · ")
    }

    private var regionSubtitle: String {
        app.session.user?.schoolAuthority == nil ? "Used for bank and school holidays" : "School holidays included"
    }
}

/// "Time around busy times": minutes counted as busy before and after each event when groups look
/// for free time, so nobody plans something the minute you're free. Saves to the account at once.
private struct BusyBufferCard: View {
    let onError: (String) -> Void
    @Environment(AppModel.self) private var app
    @Environment(\.theme) private var theme
    @State private var minutes = 0
    @State private var loaded = false

    private static let options: [SegmentOption<Int>] = [
        SegmentOption(value: 0, label: "None"),
        SegmentOption(value: 15, label: "15 min"),
        SegmentOption(value: 30, label: "30 min"),
        SegmentOption(value: 60, label: "1 hr"),
        SegmentOption(value: 120, label: "2 hr"),
    ]

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            VStack(alignment: .leading, spacing: 2) {
                Text("Time around busy times").bodyFont(14, .semibold).accessibilityAddTraits(.isHeader)
                Text("Counts you as busy for a little longer before and after each event, so nobody plans something the minute you\u{2019}re free.")
                    .bodyFont(12, relativeTo: .caption)
                    .foregroundStyle(theme.muted)
                    .fixedSize(horizontal: false, vertical: true)
            }
            SegmentedControl(selection: $minutes, options: Self.options, accessibilityLabel: "Time around busy times")
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .glass(cornerRadius: 20)
        .onAppear {
            minutes = app.session.user?.busyBufferMinutes ?? 0
            loaded = true
        }
        .onChange(of: minutes) { old, new in
            guard loaded, new != (app.session.user?.busyBufferMinutes ?? 0) else { return }
            Task {
                do {
                    _ = try await app.session.update(UserPatch(busyBufferMinutes: new))
                } catch {
                    minutes = old
                    onError(error.userMessage)
                }
            }
        }
    }
}

/// "Subscribe to your plans": a secret webcal link of your plans from every group, for Apple
/// Calendar or another app to subscribe to, so edits and cancellations follow automatically.
private struct CalendarFeedCard: View {
    let model: CalendarsViewModel
    @Environment(\.openURL) private var openURL
    @Environment(\.theme) private var theme
    @State private var confirmsReset = false

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            VStack(alignment: .leading, spacing: 2) {
                Text("Subscribe to your plans").bodyFont(14, .semibold).accessibilityAddTraits(.isHeader)
                Text("Your plans from every group in Apple Calendar or another app. Changes and cancellations follow automatically.")
                    .bodyFont(12, relativeTo: .caption)
                    .foregroundStyle(theme.muted)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Button {
                if let feed = model.calendarFeed { openURL(feed.webcalUrl) }
            } label: {
                LoadingLabel(title: "Subscribe in Calendar", systemImage: "calendar.badge.plus", isLoading: model.calendarFeed == nil)
            }
            .buttonStyle(.accent)
            .disabled(model.calendarFeed == nil || model.isResettingFeed)
            HStack {
                Button("Copy link", systemImage: "doc.on.doc") {
                    guard let feed = model.calendarFeed else { return }
                    UIPasteboard.general.url = feed.url
                    model.toast = "Link copied"
                }
                .buttonStyle(.textAction)
                Spacer()
                Button(model.isResettingFeed ? "Resetting\u{2026}" : "Reset link") { confirmsReset = true }
                    .buttonStyle(.textAction)
            }
            .disabled(model.calendarFeed == nil || model.isResettingFeed)
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .glass(cornerRadius: 20)
        .task { await model.loadCalendarFeed() }
        .confirmSheet(
            "Reset your calendar link?",
            isPresented: $confirmsReset,
            message: "Calendars subscribed with the old link stop updating. You\u{2019}ll need to subscribe again."
        ) {
            [ConfirmOption("Reset link", kind: .destructive) { Task { await model.resetCalendarFeed() } }]
        }
    }
}

/// iCloud via CalDAV with an app-specific password.
private struct CalDAVSheet: View {
    let model: CalendarsViewModel
    @Environment(\.dismiss) private var dismiss
    @Environment(\.theme) private var theme
    @State private var username = ""
    @State private var password = ""

    private static let appleAccountURL = URL(string: "https://account.apple.com/account/manage")!

    var body: some View {
        BottomSheet(title: "Connect iCloud") {
            Text("Apple needs an app-specific password for calendar access. Create one at account.apple.com → Sign-In and Security → App-Specific Passwords.")
                .bodyFont(13)
                .foregroundStyle(theme.muted)
            Link("Open account.apple.com", destination: Self.appleAccountURL)
                .bodyFont(13, .semibold)
                .foregroundStyle(theme.accentInk)
            InputField(placeholder: "Apple ID email", text: $username)
                .textContentType(.username)
                .keyboardType(.emailAddress)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
            SecureField("", text: $password, prompt: Text("xxxx-xxxx-xxxx-xxxx").foregroundStyle(theme.muted))
                .bodyFont(16)
                .textContentType(.password)
                .padding(.horizontal, 16)
                .frame(minHeight: 50)
                .surfaceBackground(cornerRadius: 16, border: theme.line2)
            Button {
                Task { if await model.connectCalDAV(username: username, appPassword: password) { dismiss() } }
            } label: {
                LoadingLabel(title: "Connect", isLoading: model.connectingProvider == .caldav)
            }
            .buttonStyle(.accent)
            .disabled(username.isEmpty || password.count < 8 || model.connectingProvider != nil)
        }
    }
}

/// One provider in the "Connect a calendar" sheet.
private struct ProviderRow: View {
    let title: String
    var subtitle: String?
    let systemImage: String
    let action: () -> Void
    @Environment(\.theme) private var theme

    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Image(systemName: systemImage)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(theme.accentInk)
                    .frame(width: 36, height: 36)
                    .background(RoundedRectangle(cornerRadius: 11).fill(theme.accentSoft))
                VStack(alignment: .leading, spacing: 1) {
                    Text(title).bodyFont(15, .semibold)
                    if let subtitle { Text(subtitle).bodyFont(12, relativeTo: .caption).foregroundStyle(theme.muted) }
                }
                Spacer()
                Image(systemName: "chevron.right").font(.system(size: 12, weight: .semibold)).foregroundStyle(theme.muted)
            }
            .foregroundStyle(theme.ink)
            .padding(.horizontal, 14)
            .frame(minHeight: 60)
            .surfaceBackground(cornerRadius: 18)
            .contentShape(Rectangle())
        }
        .buttonStyle(.pressable)
    }
}
