import SwiftUI

/// Busy time synced from a calendar. Only the busy range is ever read (never the event
/// itself), so it can't be edited here: change it in the calendar, or stop that calendar
/// counting as busy.
struct CalendarBusySheet: View {
    let interval: BusyInterval
    let openSettings: () -> Void
    @Environment(\.dismiss) private var dismiss
    @Environment(\.theme) private var theme

    var body: some View {
        BottomSheet(title: "Busy from your calendar") {
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
                    Text(interval.label ?? "A connected calendar").bodyFont(15, .semibold)
                    Text(when).bodyFont(13, relativeTo: .subheadline).foregroundStyle(theme.muted)
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

            Text("Availabliss only reads when you're busy, never what the event is, so it can't be edited here. Change it in your calendar and it updates here automatically, or choose which calendars count as busy.")
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

    /// "Mon 2 Nov · 09:00 – 10:00", or just the day(s) when it covers whole days.
    private var when: String {
        let calendar = Calendar.current
        let allDay = interval.start < interval.end
            && calendar.startOfDay(for: interval.start) == interval.start
            && calendar.startOfDay(for: interval.end) == interval.end
        return Formatting.span(start: interval.start, end: interval.end, allDay: allDay)
    }
}
