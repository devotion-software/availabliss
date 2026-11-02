import SwiftUI

/// "You've made 3 groups": shown instead of the New group form when a free account is at its limit.
struct GroupLimitPanel: View {
    let limit: Int
    let seePro: () -> Void
    @Environment(\.theme) private var theme

    var body: some View {
        VStack(spacing: 10) {
            Image(systemName: "person.3")
                .font(.system(size: 20, weight: .semibold))
                .foregroundStyle(theme.accentInk)
                .frame(width: 52, height: 52)
                .background(RoundedRectangle(cornerRadius: 18, style: .continuous).fill(theme.accentSoft))
                .accessibilityHidden(true)
            Text("You've made \(limit) groups")
                .displayFont(19, .bold, relativeTo: .headline)
                .accessibilityAddTraits(.isHeader)
            Text("Free accounts can create \(limit). Upgrade to Pro for unlimited groups, or delete one you no longer need. You can still join groups other people make.")
                .bodyFont(14)
                .foregroundStyle(theme.muted)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.bottom, 6)
            Button(action: seePro) {
                LoadingLabel(title: "See Pro", systemImage: "sparkles")
            }
            .buttonStyle(.accent)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 8)
    }
}
