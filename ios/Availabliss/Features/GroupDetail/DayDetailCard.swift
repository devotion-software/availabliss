import SwiftUI

/// The card under the month / week: what the selected day means, who's free when, and Propose.
struct DayDetailCard: View {
    let model: GroupAvailabilityViewModel
    /// The day tapped.
    let day: CalendarDay
    /// Times picked for a date poll; a day that works can be added beside Propose.
    @Binding var shortlist: [Slot]
    let group: Group
    let me: String?
    let propose: (ProposalDraft) -> Void
    @Environment(\.theme) private var theme

    private var isPast: Bool { day < .today() }

    var body: some View {
        let slot = model.slot(for: day)
        let works = !isPast && slot != nil && model.day(day)?.status == .match
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .center, spacing: 8) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(works ? (slot?.label ?? "") : Formatting.shortDay(day)).displayFont(16, .semibold, relativeTo: .headline)
                    Text(subtitle(works: works, slot: slot)).bodyFont(12, relativeTo: .caption).foregroundStyle(theme.muted)
                }
                Spacer()
                StatusPill(
                    text: works ? (slot?.missingCount ?? 0 > 0 ? "Most of us" : "Works") : (isPast ? "Past" : "Doesn\u{2019}t fit"), tone: works ? .mint : .neutral)
            }
            if works, let who = model.day(day)?.preferredBy, !who.isEmpty {
                Label {
                    Text(
                        "\(Formatting.list(who.map { $0 == me ? "You" : (group.member($0)?.firstName ?? "Someone") })) \(who.count == 1 && who.first != me ? "prefers" : "prefer") this"
                    )
                } icon: {
                    Image(systemName: "heart.fill")
                }
                .bodyFont(13, .semibold, relativeTo: .subheadline)
                .foregroundStyle(theme.accentInk)
            }
            if isPast {
                // Nothing to plan on a day that's gone.
            } else if let detail = model.dayDetails[day] {
                DayTimeline(detail: detail, date: day, group: group, me: me)
                    .padding(.vertical, 2)
            } else {
                ProgressView().frame(maxWidth: .infinity, minHeight: 90)
            }
            if !isPast { proposeButton(works: works, slot: slot) }
        }
        .padding(14)
        .glass(cornerRadius: 22)
        .animation(.easeInOut(duration: 0.2), value: day)
    }

    @ViewBuilder
    private func proposeButton(works: Bool, slot: Slot?) -> some View {
        if works, let slot {
            HStack(spacing: 8) {
                Button(
                    group.requirement.slotNoun.proposeLabel(
                        multiDay: !Calendar.current.isDate(slot.start, inSameDayAs: slot.end.addingTimeInterval(-1)))
                ) {
                    propose(ProposalDraft(slot: slot, requirement: group.requirement))
                }
                .buttonStyle(.filled(.accent, height: 44, cornerRadius: 14, fontSize: 14))
                ShortlistToggle(slot: slot, shortlist: $shortlist)
            }
        } else if let free = model.dayDetails[day]?.everyoneFree.first {
            let nextDay = day.adding(days: 1).startDate()
            let allDay = free.start <= day.startDate() && free.end >= nextDay
            let end = free.end >= nextDay ? "midnight" : Formatting.time(free.end)
            Button(allDay ? "Propose all day" : "Propose \(Formatting.time(free.start)) – \(end)") {
                propose(
                    allDay
                        ? ProposalDraft(start: day.startDate(), end: nextDay, allDay: true)
                        : ProposalDraft(start: free.start, end: free.end, allDay: false))
            }
            .buttonStyle(.filled(.accent, height: 44, cornerRadius: 14, fontSize: 14))
        } else if let status = model.day(day)?.status, status == .busy || status == .free {
            // Doesn't work for everyone: the proposal sheet warns who's busy.
            Button("Propose anyway") {
                propose(ProposalDraft(start: WallTime(hour: 18, minute: 0).date(on: day), end: WallTime(hour: 21, minute: 0).date(on: day), allDay: false))
            }
            .buttonStyle(.filled(.secondary, height: 44, cornerRadius: 14, fontSize: 14))
        }
    }

    private func subtitle(works: Bool, slot: Slot?) -> String {
        if isPast { return "This day is in the past" }
        guard let info = model.day(day) else { return "Outside the search range" }
        if works, let slot {
            let from = "\(Formatting.shortDay(slot.start)) \(Formatting.time(slot.start))"
            guard let missing = slot.missingMemberIds, !missing.isEmpty else { return "Everyone synced is free from \(from)" }
            return "Most of us are free from \(from) · \(Clash.cantMake(missing, group: group, me: me))"
        }
        switch info.status {
        case .busy:
            let names = info.busyMemberIds.map { $0 == me ? "You" : (group.member($0)?.firstName ?? "Someone") }
            return "\(Formatting.list(names)) \(names == ["You"] || names.count > 1 ? "are" : "is") busy"
        case .hidden: return info.holiday.map { "\($0) · outside the group's timing" } ?? "Outside the group's timing"
        default: return info.holiday ?? "Free, but no full slot fits here"
        }
    }
}
