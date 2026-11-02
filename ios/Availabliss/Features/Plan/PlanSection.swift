import SwiftUI

/// Your plan and how many groups you've made on it, on the You screen.
struct PlanSection: View {
    @Binding var toast: String?
    @Environment(AppModel.self) private var app
    @Environment(\.theme) private var theme
    @State private var plan: Plan?
    @State private var showsUpgrade = false

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("Plan").sectionLabelStyle().accessibilityAddTraits(.isHeader)
                Spacer()
                if let plan { StatusPill(text: plan.isPro ? "Pro" : "Free", tone: plan.isPro ? .indigo : .neutral) }
            }
            Text(detail).bodyFont(14).foregroundStyle(theme.muted)
            if let plan {
                Button {
                    showsUpgrade = true
                } label: {
                    if plan.isPro { Text("Manage Pro") } else { Label("Upgrade to Pro", systemImage: "sparkles") }
                }
                .buttonStyle(.filled(plan.isPro ? .secondary : .accent, height: 44, cornerRadius: 14, fontSize: 15))
            }
        }
        .padding(16)
        .glass(cornerRadius: 24)
        .task { await load() }
        .sheet(isPresented: $showsUpgrade, onDismiss: { Task { await load() } }) {
            UpgradeSheet { _ in toast = "Welcome to Pro" }
        }
    }

    /// "Free · 2 of 3 groups created", "Pro · Unlimited groups · renews 2 Dec".
    private var detail: String {
        guard let plan else { return " " }
        if !plan.isPro { return "Free · \(plan.groupsOwned) of \(plan.groupLimit ?? 3) groups created" }
        if plan.lifetime { return "Pro · Unlimited groups, for life" }
        if let until = plan.proUntil {
            return "Pro · Unlimited groups · \(plan.willRenew ? "renews" : "ends") \(Formatting.dayMonth(CalendarDay(until)))"
        }
        return "Pro · Unlimited groups"
    }

    private func load() async {
        if let plan = try? await app.api.send(API.Me.plan()) { self.plan = plan }
    }
}
