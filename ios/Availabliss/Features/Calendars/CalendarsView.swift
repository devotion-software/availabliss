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
        .errorAlert($model.error)
        .toast($model.toast)
    }

    private var header: some View {
        HStack {
            switch mode {
            case .onboarding(let onBack, _, _):
                IconButton(systemImage: "chevron.left", label: "Back", action: onBack)
                Spacer()
                StepDots(current: 1, total: 2)
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

    private var connectButton: some View {
        Menu {
            Button { Task { await model.connectOAuth(.google) } } label: { Label("Google Calendar", systemImage: "globe") }
            Button { Task { await model.connectOAuth(.microsoft) } } label: { Label("Outlook", systemImage: "envelope") }
            Button { showsCalDAV = true } label: { Label("iCloud (app-specific password)", systemImage: "icloud") }
        } label: {
            HStack(spacing: 10) {
                Image(systemName: "plus")
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(theme.accentInk)
                    .frame(width: 30, height: 30)
                    .background(RoundedRectangle(cornerRadius: 10).fill(theme.accentSoft))
                Text("Connect another calendar").bodyFont(14, .semibold)
                Spacer()
                if model.connectingProvider != nil {
                    ProgressView()
                } else {
                    Text("Google · Outlook · iCloud").bodyFont(12, .medium, relativeTo: .caption).foregroundStyle(theme.muted)
                }
            }
            .foregroundStyle(theme.ink)
            .padding(.horizontal, 14)
            .frame(minHeight: 52)
            .glass(cornerRadius: 20)
        }
        .disabled(model.connectingProvider != nil)
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
        } else {
            AccessPrompt(denied: service.authorization == .denied || service.authorization == .restricted) {
                Task { await service.requestFullAccess() }
            }
        }
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
            if connection.status != .ok {
                HStack {
                    StatusPill(text: connection.status == .reauthRequired ? "Needs reconnecting" : "Sync problem", tone: .apricot, systemImage: "exclamationmark")
                    Spacer()
                    if connection.provider == .google || connection.provider == .microsoft {
                        Button("Reconnect") { Task { await model.connectOAuth(connection.provider) } }
                            .buttonStyle(.textAction)
                    }
                }
                .padding(.horizontal, 6)
            }
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
            Toggle(isOn: Binding(get: row.get, set: row.set)) {
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
