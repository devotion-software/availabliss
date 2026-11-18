import SwiftUI

/// Busy time synced from a calendar. Only the busy range is ever read (never the event
/// itself), so it can't be edited here. It can be marked not busy (this occurrence, or every
/// occurrence of a repeating event), or a whole calendar can stop counting as busy.
struct CalendarBusySheet: View {
    let interval: BusyInterval
    let openSettings: () -> Void
    /// Marks it not busy; `true` = every occurrence. Returns whether it worked.
    var onIgnore: (Bool) async -> Bool = { _ in false }
    /// Counts it as busy again.
    var onUnignore: () async -> Bool = { false }
    @Environment(\.dismiss) private var dismiss
    @Environment(\.theme) private var theme
    @State private var working: Int?

    var body: some View {
        BottomSheet(title: interval.isIgnored ? "Marked not busy" : "Busy from your calendar") {
            HStack(spacing: 12) {
                Image("CalendarSync")
                    .renderingMode(.template)
                    .resizable()
                    .scaledToFit()
                    .frame(width: 20, height: 20)
                    .foregroundStyle(theme.ink)
                    .frame(width: 38, height: 38)
                    .background {
                        let shape = RoundedRectangle(cornerRadius: 12, style: .continuous)
                        shape.fill(theme.busyBg).overlay(shape.strokeBorder(theme.busyLine, lineWidth: 1))
                    }
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 2) {
                    Text(interval.title ?? interval.label ?? "A connected calendar").bodyFont(15, .semibold)
                    Text(interval.title != nil ? [interval.label, when].compactMap { $0 }.joined(separator: " \u{00B7} ") : when)
                        .bodyFont(13, relativeTo: .subheadline).foregroundStyle(theme.muted)
                }
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 12)
            .background {
                let shape = RoundedRectangle(cornerRadius: 16, style: .continuous)
                shape.fill(theme.surface).overlay(shape.strokeBorder(theme.glassBorder, lineWidth: 1))
            }
            .accessibilityElement(children: .combine)

            if interval.isIgnored {
                Text("You said this doesn\u{2019}t make you busy, so your groups see you as free then.")
                    .bodyFont(13, relativeTo: .subheadline)
                    .foregroundStyle(theme.muted)
                    .fixedSize(horizontal: false, vertical: true)
                Button {
                    Task { await run(0) { await onUnignore() } }
                } label: {
                    LoadingLabel(title: "Count as busy again", isLoading: working == 0)
                }
                .buttonStyle(.filled(.primary))
            } else {
                Text("Doesn\u{2019}t really make you busy? Your groups will see you as free then.")
                    .bodyFont(13, relativeTo: .subheadline)
                    .foregroundStyle(theme.muted)
                    .fixedSize(horizontal: false, vertical: true)
                Button {
                    Task { await run(1) { await onIgnore(false) } }
                } label: {
                    LoadingLabel(title: interval.recurring == true ? "Not busy: just this one" : "Mark as not busy", isLoading: working == 1)
                }
                .buttonStyle(.filled(.primary))
                if interval.recurring == true {
                    Button {
                        Task { await run(2) { await onIgnore(true) } }
                    } label: {
                        LoadingLabel(title: "Not busy: every time it repeats", isLoading: working == 2)
                    }
                    .buttonStyle(.filled(.secondary))
                }
            }

            Text(
                "Availabliss only reads when you're busy, never what the event is, so it can't be edited here. Change it in your calendar and it updates here automatically, or choose which calendars count as busy."
            )
            .bodyFont(13, relativeTo: .subheadline)
            .foregroundStyle(theme.muted)
            .lineSpacing(3)
            .fixedSize(horizontal: false, vertical: true)

            Button {
                dismiss()
                openSettings()
            } label: {
                Label {
                    Text("Calendar settings")
                } icon: {
                    Image("CalendarSync").renderingMode(.template).resizable().scaledToFit().frame(width: 18, height: 18)
                }
            }
            .buttonStyle(.filled(.secondary))
        }
    }

    private func run(_ id: Int, _ action: () async -> Bool) async {
        guard working == nil else { return }
        working = id
        defer { working = nil }
        if await action() { dismiss() }
    }

    /// "Mon 2 Nov · 09:00 – 10:00", or just the day(s) when it covers whole days.
    private var when: String {
        let calendar = Calendar.current
        let allDay =
            interval.start < interval.end
            && calendar.startOfDay(for: interval.start) == interval.start
            && calendar.startOfDay(for: interval.end) == interval.end
        return Formatting.span(start: interval.start, end: interval.end, allDay: allDay)
    }
}
