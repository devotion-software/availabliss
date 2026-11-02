import SwiftUI

/// "Availabliss Pro": what it adds and how to get it. Purchases go through RevenueCat with the
/// App Store; Pro bought on the web or Android shows here too.
struct UpgradeSheet: View {
    /// Why it opened, e.g. "You've used your 3 free groups. …".
    var reason: String?
    /// Called with the fresh plan after a purchase or restore made you Pro.
    var onUpgraded: (Plan) -> Void = { _ in }
    @Environment(AppModel.self) private var app
    @Environment(\.dismiss) private var dismiss
    @Environment(\.theme) private var theme
    @State private var plan: Plan?
    @State private var packages: [Billing.ProPackage]?
    @State private var loadError: String?
    @State private var buying: String?
    @State private var isRestoring = false
    @State private var error: String?
    @State private var notice: String?

    var body: some View {
        BottomSheet(title: "Availabliss Pro") {
            Text(reason ?? "Plan with as many groups as you like.")
                .bodyFont(14)
                .foregroundStyle(theme.muted)
                .fixedSize(horizontal: false, vertical: true)
            perks
            content
        }
        .task { await load() }
        .errorAlert($error)
        .toast($notice)
    }

    // MARK: Perks

    private var perks: some View {
        VStack(alignment: .leading, spacing: 12) {
            Perk(systemImage: "person.3", title: "Unlimited groups", text: "Free accounts can create \(plan?.groupLimit ?? 3). Joining other people's groups is always free.")
            if plan?.aiPlanner.available == true {
                Perk(systemImage: "sparkles", title: "AI planning", text: "Picks the best time, suggests things to do and drafts the plan.")
            }
            Perk(systemImage: "heart", title: "Supports Availabliss", text: "Everything else stays free for everyone in your groups.")
        }
        .padding(16)
        .surfaceBackground(cornerRadius: 20)
    }

    // MARK: State

    @ViewBuilder
    private var content: some View {
        if let loadError {
            Note(text: loadError, tone: .warn)
        } else if let plan {
            if plan.isPro {
                proStatus(plan)
            } else if plan.purchase.ios == nil {
                Note(text: "Upgrading in the app isn't available yet. Upgrade on the web: Pro follows your account everywhere.")
            } else if let packages {
                if packages.isEmpty {
                    Note(text: "Pro isn't on sale just now. Check back soon.")
                } else {
                    VStack(spacing: 10) {
                        ForEach(packages) { package in
                            Button {
                                Task { await buy(package) }
                            } label: {
                                HStack {
                                    Text(package.title)
                                    Spacer()
                                    if buying == package.id {
                                        ProgressView().tint(package.isAnnual ? theme.onAccent : theme.ink)
                                    } else {
                                        Text(package.price) + Text(package.period).font(AppFont.body(13, .medium, relativeTo: .caption))
                                    }
                                }
                            }
                            .buttonStyle(.filled(package.isAnnual ? .accent : .secondary, height: 54, cornerRadius: 18))
                            .disabled(buying != nil || isRestoring)
                        }
                    }
                }
                restoreButton
                Text("Cancel any time. Bought on the web or Android instead? It works here automatically.")
                    .bodyFont(12, relativeTo: .caption)
                    .foregroundStyle(theme.muted)
                    .fixedSize(horizontal: false, vertical: true)
            } else {
                ProgressView().frame(maxWidth: .infinity, minHeight: 60)
            }
        } else {
            ProgressView().frame(maxWidth: .infinity, minHeight: 60)
        }
    }

    @ViewBuilder
    private func proStatus(_ plan: Plan) -> some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: "checkmark.circle.fill").font(.system(size: 18)).foregroundStyle(theme.ok)
            (Text("You're on Pro.").fontWeight(.semibold) + Text(plan.renewalText.map { " \($0)." } ?? ""))
                .bodyFont(15)
            Spacer(minLength: 0)
        }
        .padding(14)
        .background(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(theme.okBg))
        switch plan.store {
        case "app_store":
            Button("Manage subscription") { Task { await manage() } }
                .buttonStyle(.secondary)
        case "play_store":
            Note(text: "Manage it in Google Play on the device you bought it on.")
        case "stripe", "rc_billing":
            Note(text: "Manage it on the web.")
        default:
            EmptyView()
        }
    }

    private var restoreButton: some View {
        Button {
            Task { await restore() }
        } label: {
            if isRestoring { ProgressView() } else { Text("Restore purchases") }
        }
        .buttonStyle(.textAction)
        .frame(maxWidth: .infinity)
        .disabled(buying != nil || isRestoring)
    }

    // MARK: Actions

    private func load() async {
        do {
            let plan = try await app.api.send(API.Me.plan())
            self.plan = plan
            guard !plan.isPro, let key = plan.purchase.ios, let userId = app.session.user?.id else { return }
            try await app.billing.prepare(apiKey: key, userId: userId)
            packages = try await app.billing.packages()
        } catch where error.isCancellation {
        } catch {
            if plan == nil { loadError = error.userMessage } else { packages = []; self.error = error.userMessage }
        }
    }

    private func buy(_ package: Billing.ProPackage) async {
        buying = package.id
        defer { buying = nil }
        do {
            guard try await app.billing.purchase(package) else { return }
            await finish(purchased: true)
        } catch {
            self.error = error.userMessage
        }
    }

    private func restore() async {
        isRestoring = true
        defer { isRestoring = false }
        do {
            try await app.billing.restore()
            await finish(purchased: false)
        } catch {
            self.error = error.userMessage
        }
    }

    /// The server re-reads RevenueCat; it's the source of truth for Pro.
    private func finish(purchased: Bool) async {
        do {
            let plan = try await app.api.send(API.Me.refreshPlan())
            self.plan = plan
            if plan.isPro {
                onUpgraded(plan)
                dismiss()
            } else {
                notice = purchased ? "Payment received. Pro will switch on in a moment." : "No Pro purchase found for this Apple ID."
            }
        } catch {
            self.error = error.userMessage
        }
    }

    private func manage() async {
        do { try await app.billing.manageSubscriptions() } catch { self.error = error.userMessage }
    }
}

private struct Perk: View {
    let systemImage: String
    let title: String
    let text: String
    @Environment(\.theme) private var theme

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: systemImage)
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(theme.accentInk)
                .frame(width: 34, height: 34)
                .background(RoundedRectangle(cornerRadius: 11, style: .continuous).fill(theme.accentSoft))
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 2) {
                Text(title).bodyFont(15, .semibold)
                Text(text).bodyFont(13, relativeTo: .subheadline).foregroundStyle(theme.muted).fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 0)
        }
        .accessibilityElement(children: .combine)
    }
}

/// A short muted (or warn-tone) note.
private struct Note: View {
    enum Tone { case muted, warn }
    let text: String
    var tone: Tone = .muted
    @Environment(\.theme) private var theme

    var body: some View {
        Text(text)
            .bodyFont(14)
            .foregroundStyle(tone == .warn ? theme.warnInk : theme.muted)
            .fixedSize(horizontal: false, vertical: true)
            .frame(maxWidth: .infinity, alignment: .leading)
    }
}
