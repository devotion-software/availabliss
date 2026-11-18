import SwiftUI

/// "‹ Fri 25 Dec" or "Fri 5 Feb ›"; "‹ None earlier" / "None later ›" (greyed out) when there's
/// none, or "‹ Earlier" / "Later ›" while it's still looking.
struct FreeJumpButton: View {
    /// -1 = earlier, 1 = later.
    let direction: Int
    let day: CalendarDay?
    let isFinding: Bool
    let action: (CalendarDay) -> Void
    @Environment(\.theme) private var theme

    private var name: String { direction < 0 ? "Previous free" : "Next free" }

    var body: some View {
        Button {
            if let day { action(day) }
        } label: {
            HStack(spacing: 4) {
                if direction < 0 { Image(systemName: "chevron.left").font(.system(size: 10, weight: .bold)) }
                Text(label)
                if direction > 0 { Image(systemName: "chevron.right").font(.system(size: 10, weight: .bold)) }
            }
            .bodyFont(12, .semibold, relativeTo: .caption)
            .lineLimit(1)
            .foregroundStyle(day == nil ? theme.muted : theme.accentInk)
            .padding(.horizontal, 10)
            .frame(height: 30)
            .background(Capsule().fill(day == nil ? theme.line : theme.accentSoft))
            .opacity(day == nil ? 0.7 : 1)
            .frame(minHeight: 44)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(day == nil)
        .animation(.easeInOut(duration: 0.2), value: day)
        .accessibilityLabel(accessibilityText)
    }

    private var label: String {
        if let day { return Formatting.shortDay(day) }
        if isFinding { return direction < 0 ? "Earlier" : "Later" }
        return direction < 0 ? "None earlier" : "None later"
    }

    private var accessibilityText: String {
        if let day { return "\(name) date, \(Formatting.longDay(day.startDate()))" }
        if isFinding { return "\(name) date, looking" }
        return direction < 0 ? "No earlier free dates" : "No later free dates"
    }
}

/// Swipe sideways for the previous / next month or week: the page follows your finger, then slides
/// on (or springs back). The drag lives here so following the finger redraws only this, not the
/// whole screen.
struct SwipePager<Content: View>: View {
    let onSwipe: () -> Void
    let onPage: (Int) -> Void
    @ViewBuilder let content: Content
    @State private var dragX: CGFloat = 0

    var body: some View {
        ZStack { content }
            .offset(x: dragX)
            .simultaneousGesture(
                DragGesture(minimumDistance: 20)
                    .onChanged { drag in
                        let dx = drag.translation.width, dy = drag.translation.height
                        guard abs(dx) > abs(dy) * 1.5 else { return }
                        dragX = dx
                        onSwipe()
                    }
                    .onEnded { drag in
                        let dx = drag.translation.width, dy = drag.translation.height
                        if dragX != 0 { onSwipe() }
                        // A quick flick counts too, not just a long drag.
                        let projected = drag.predictedEndTranslation.width
                        if abs(dx) > abs(dy) * 1.5, abs(dx) > 60 || abs(projected) > 160 {
                            withAnimation(.spring(response: 0.32, dampingFraction: 0.9)) { dragX = 0 }
                            onPage(dx < 0 ? 1 : -1)
                        } else {
                            withAnimation(.spring(response: 0.3, dampingFraction: 0.85)) { dragX = 0 }
                        }
                    }
            )
    }
}

/// "2 people still to sync" with a progress ring and Nudge for admins.
struct SyncBanner: View {
    let group: Group
    let isAdmin: Bool
    let isNudging: Bool
    let nudge: () -> Void
    @Environment(\.theme) private var theme

    var body: some View {
        let pending = group.pendingMembers
        HStack(spacing: 10) {
            ZStack {
                ProgressRing(progress: Double(group.syncedCount) / Double(max(group.memberCount, 1)), lineWidth: 4, color: theme.warn)
                Text("\(group.syncedCount)/\(group.memberCount)").bodyFont(11, .bold, relativeTo: .caption2)
            }
            .frame(width: 38, height: 38)
            VStack(alignment: .leading, spacing: 0) {
                Text("\(Formatting.plural(pending.count, "person", "people")) still to sync").bodyFont(14, .semibold)
                Text("\(Formatting.list(pending.map(\.firstName))) \(pending.count == 1 ? "hasn't" : "haven't") added busy times")
                    .bodyFont(12, relativeTo: .caption)
                    .foregroundStyle(theme.muted)
                    .lineLimit(2)
            }
            Spacer(minLength: 0)
            if isAdmin {
                Button(action: nudge) {
                    LoadingLabel(title: "Nudge", isLoading: isNudging)
                }
                .buttonStyle(.filled(.warning, height: 36, cornerRadius: 12, fontSize: 13, fullWidth: false))
                .disabled(isNudging)
                .frame(minHeight: 44)
            }
        }
        .padding(.leading, 12)
        .padding(.trailing, 10)
        .padding(.vertical, 10)
        .glass(cornerRadius: 20)
        .accessibilityElement(children: .contain)
    }
}

/// "Sam's calendar is only checked up to 4 Mar 2029": days past that may hide clashes.
struct CoverageNote: View {
    let gaps: [Availability.CoverageGap]
    let group: Group
    let me: String?
    @Environment(\.theme) private var theme

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 8) {
            Image(systemName: "info.circle").font(.system(size: 13, weight: .semibold))
            Text(message).bodyFont(13, relativeTo: .footnote).fixedSize(horizontal: false, vertical: true)
        }
        .foregroundStyle(theme.warnInk)
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(theme.warnBg))
        .accessibilityElement(children: .combine)
    }

    private var message: String {
        let mine = gaps.contains { $0.userId == me }
        let others = gaps.filter { $0.userId != me }.map { gap in
            group.members.first { $0.userId == gap.userId }?.firstName ?? "Someone"
        }
        let whose: String
        if others.isEmpty {
            whose = "Your calendar is"
        } else if !mine && others.count == 1 {
            whose = "\(others[0])'s calendar is"
        } else {
            whose = "Calendars for \(Formatting.list((mine ? ["you"] : []) + others)) are"
        }
        let earliest = gaps.map(\.until).min() ?? Date()
        return "\(whose) only checked up to \(Formatting.dayMonthYear(earliest)), so later days may not show every clash yet."
    }
}

/// "A weekend · Fri from 18:00 → Sun · Nov – Jan" — opens the timing editor.
struct TimingChip: View {
    let summary: String
    let action: () -> Void
    @Environment(\.theme) private var theme

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                Image(systemName: "clock").font(.system(size: 14, weight: .medium))
                (Text(lead).fontWeight(.semibold) + Text(rest))
                    .bodyFont(13, relativeTo: .subheadline)
                    .lineLimit(1)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Image(systemName: "pencil").font(.system(size: 13, weight: .medium)).foregroundStyle(theme.muted)
            }
            .foregroundStyle(theme.ink)
            .padding(.horizontal, 12)
            .frame(minHeight: 44)
            .glass(cornerRadius: 14, shadow: false)
        }
        .buttonStyle(.pressable)
        .accessibilityLabel("Timing: \(summary)")
        .accessibilityHint("Opens the timing settings")
    }

    private var parts: [Substring] { summary.split(separator: "·", maxSplits: 1) }
    private var lead: String { parts.first.map { $0.trimmingCharacters(in: .whitespaces) } ?? summary }
    private var rest: String { parts.count > 1 ? " ·\(parts[1])" : "" }
}
