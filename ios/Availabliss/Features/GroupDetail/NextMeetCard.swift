import SwiftUI

/// With a rhythm, the group's next meet-up: "Fri 4 Dec works for everyone" with Propose, or
/// "Nothing near Fri 4 Dec works yet".
struct NextMeetCard: View {
    let suggestion: NextMeetResponse.Suggestion
    let group: Group
    let me: String?
    let propose: (ProposalDraft) -> Void
    @Environment(\.theme) private var theme

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: "arrow.triangle.2.circlepath")
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(theme.accentInk)
                .frame(width: 34, height: 34)
                .background(RoundedRectangle(cornerRadius: 11, style: .continuous).fill(theme.accentSoft))
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 1) {
                Text(heading).bodyFont(12, .semibold, relativeTo: .caption).foregroundStyle(theme.muted)
                Text(headline).bodyFont(15, .semibold).fixedSize(horizontal: false, vertical: true)
                if let detail {
                    Text(detail).bodyFont(12, relativeTo: .caption).foregroundStyle(theme.muted).fixedSize(horizontal: false, vertical: true)
                }
            }
            Spacer(minLength: 4)
            if let slot = suggestion.slot {
                Button("Propose") { propose(ProposalDraft(slot: slot, requirement: group.requirement)) }
                    .buttonStyle(.filled(slot.missingCount == 0 ? .accent : .secondary, height: 36, cornerRadius: 12, fontSize: 13, fullWidth: false))
                    .accessibilityLabel("Propose \(slot.label)")
            }
        }
        .padding(12)
        .glass(cornerRadius: 22)
        .accessibilityElement(children: .contain)
    }

    /// "Next one · every two weeks".
    private var heading: String {
        guard let every = group.rhythm?.every else { return "Next one" }
        return "Next one \u{00B7} \(every.label.lowercased())"
    }

    /// "Fri 4 Dec works for everyone", "Fri 4 Dec works for most of us", "Nothing near Fri 4 Dec works yet".
    private var headline: String {
        guard let slot = suggestion.slot else { return "Nothing near \(Formatting.shortDay(suggestion.target)) works yet" }
        return "\(slot.label) works for \(slot.missingCount == 0 ? "everyone" : "most of us")"
    }

    /// Who can't make it, when it works for most of the group.
    private var detail: String? {
        guard let missing = suggestion.slot?.missingMemberIds, !missing.isEmpty else { return nil }
        return Clash.cantMake(missing, group: group, me: me)
    }
}
