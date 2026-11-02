import SwiftUI

/// Wording for proposals that clash with someone's busy time. Mirrors
/// `web/src/lib/components/proposals/clash.ts`.
enum Clash {
    /// "Priya is busy then", "You're busy then", "You and Priya are busy then",
    /// "Priya, Alex, Jo and 2 others are busy then". Nil when nobody is.
    static func text(_ busyIds: [String], group: Group?, me: String?) -> String? {
        guard !busyIds.isEmpty else { return nil }
        let mine = me.map(busyIds.contains) ?? false
        let others = busyIds.filter { $0 != me }.map { firstName($0, group) }
        let names = mine ? ["You"] + others : others
        if names.count == 1 && mine { return "You're busy then" }
        return "\(listNames(names)) \(names.count > 1 ? "are" : "is") busy then"
    }

    /// Short form for compact rows: "Priya busy then" / "3 busy then".
    static func short(_ busyIds: [String], group: Group?) -> String? {
        guard let first = busyIds.first else { return nil }
        if busyIds.count > 1 { return "\(busyIds.count) busy then" }
        return "\(firstName(first, group)) busy then"
    }

    /// "Priya", "Priya and Alex", "Priya, Alex and Jo", "Priya, Alex, Jo and 2 others".
    static func listNames(_ names: [String], max: Int = 3) -> String {
        guard names.count > 1 else { return names.first ?? "" }
        if names.count <= max { return "\(names.dropLast().joined(separator: ", ")) and \(names[names.count - 1])" }
        let rest = names.count - max
        return "\(names.prefix(max).joined(separator: ", ")) and \(rest) other\(rest == 1 ? "" : "s")"
    }

    private static func firstName(_ id: String, _ group: Group?) -> String {
        group?.member(id)?.firstName ?? "Someone"
    }
}

/// Warn-tone panel: "**Priya is busy then.** You can still propose it; …".
struct ClashPanel: View {
    let headline: String
    let detail: String
    var cornerRadius: CGFloat = 16
    @Environment(\.theme) private var theme

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: "exclamationmark.triangle")
                .font(.system(size: 15, weight: .semibold))
                .padding(.top, 1)
                .accessibilityHidden(true)
            (Text("\(headline).").fontWeight(.semibold) + Text(" \(detail)"))
                .bodyFont(13, relativeTo: .subheadline)
                .lineSpacing(2)
                .fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: 0)
        }
        .foregroundStyle(theme.warnInk)
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .background(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous).fill(theme.warnBg))
        .accessibilityElement(children: .combine)
    }
}
