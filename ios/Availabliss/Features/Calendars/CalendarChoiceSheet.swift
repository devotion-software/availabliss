import EventKit
import SwiftUI

/// Chooses which of the phone's calendars a plan goes in: writable calendars grouped by account,
/// each with its colour. Picking one closes the sheet and runs `onChoose`.
/// `selectedId` is ticked when it's where the plan is now (moving it). When adding, nothing has been
/// chosen yet, so the last calendar used is only labelled "Last used" (`selectedIsLastUsed`): a tick
/// there read as already chosen, with no button to confirm it.
struct CalendarChoiceSheet: View {
    let title: String
    var message: String?
    let groups: [(source: String, calendars: [EKCalendar])]
    let selectedId: String?
    var selectedIsLastUsed = false
    let onChoose: (EKCalendar) -> Void
    @Environment(\.dismiss) private var dismiss
    @Environment(\.theme) private var theme

    var body: some View {
        BottomSheet(title: title) {
            if let message {
                Text(message)
                    .bodyFont(15)
                    .foregroundStyle(theme.muted)
                    .fixedSize(horizontal: false, vertical: true)
            }
            ForEach(groups, id: \.source) { group in
                GlassList(data: group.calendars, id: \.calendarIdentifier, header: group.source) { calendar in
                    row(calendar)
                }
            }
        }
    }

    private func row(_ calendar: EKCalendar) -> some View {
        let isMarked = calendar.calendarIdentifier == selectedId
        let isSelected = isMarked && !selectedIsLastUsed
        return Button {
            dismiss()
            onChoose(calendar)
        } label: {
            HStack(spacing: 12) {
                Circle().fill(Color(cgColor: calendar.cgColor)).frame(width: 12, height: 12)
                Text(calendar.title).bodyFont(15, isSelected ? .semibold : .regular).lineLimit(1)
                Spacer(minLength: 8)
                if isSelected {
                    Image(systemName: "checkmark").font(.system(size: 15, weight: .bold)).foregroundStyle(theme.accentInk)
                } else if isMarked {
                    Text("Last used").bodyFont(13, relativeTo: .subheadline).foregroundStyle(theme.muted)
                }
            }
            .foregroundStyle(theme.ink)
            .frame(maxWidth: .infinity, minHeight: 52, alignment: .leading)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(calendar.title), \(calendar.source.title)\(isMarked && selectedIsLastUsed ? ", last used" : "")")
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }
}
