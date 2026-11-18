import SwiftUI

/// The month on the group's calendar: a cell per day, coloured by who's free, with the legend under it.
struct MonthGrid: View {
    let model: GroupAvailabilityViewModel
    let group: Group
    let me: String?
    @Environment(\.theme) private var theme

    var body: some View {
        let columns = Array(repeating: GridItem(.flexible(), spacing: 4), count: model.columns.count)
        VStack(alignment: .leading, spacing: 6) {
            LazyVGrid(columns: columns, spacing: 4) {
                ForEach(model.columns, id: \.self) { column in
                    Text(Weekday.short(column))
                        .bodyFont(11, .semibold, relativeTo: .caption2)
                        .foregroundStyle(theme.muted)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.leading, model.columns.count > 4 ? 4 : 10)
                }
                ForEach(Array(model.monthRows.enumerated()), id: \.offset) { _, row in
                    ForEach(Array(row.enumerated()), id: \.offset) { _, date in
                        if let date {
                            DayCell(
                                date: date,
                                info: model.day(date),
                                note: note(for: date),
                                compact: model.columns.count > 4,
                                isSelected: date == model.selectedDay
                            ) {
                                withAnimation(.spring(response: 0.3)) { model.pick(date) }
                            }
                            // Above its neighbours, so their cells don't cover the selection ring.
                            .zIndex(date == model.selectedDay ? 1 : 0)
                        } else {
                            Color.clear.frame(height: 54).accessibilityHidden(true)
                        }
                    }
                }
            }
            legend
        }
    }

    private var legend: some View {
        HStack(spacing: 14) {
            LegendItem(label: "Works for everyone") { theme.accent }
            if showsPartial {
                LegendItem(label: "Most of us free") {
                    theme.accentSoft.overlay(RoundedRectangle(cornerRadius: 3).strokeBorder(theme.accent, lineWidth: 1))
                }
            }
            LegendItem(label: "Someone's busy") {
                theme.busyBg.opacity(0.5).overlay(RoundedRectangle(cornerRadius: 3).strokeBorder(theme.busyLine, lineWidth: 1))
            }
            if model.columns.count < 7, let first = model.columns.first, let last = model.columns.last {
                Text("\(Weekday.short(first))–\(Weekday.short(last)) only")
            }
        }
        .bodyFont(11, relativeTo: .caption2)
        .foregroundStyle(theme.muted)
        .padding(.top, 4)
        .padding(.horizontal, 2)
    }

    /// A day this month works only because of the group's quorum or optional members.
    private var showsPartial: Bool {
        model.monthRows.joined().contains { date in
            guard let date, date >= .today() else { return false }
            return model.day(date).map { $0.status == .match && $0.isPartial } ?? false
        }
    }

    private func note(for date: CalendarDay) -> String {
        if date < .today() { return "Past" }
        guard let info = model.day(date) else { return "" }
        switch info.status {
        case .match where info.isPartial:
            // "2 missing": the slot names who's busy then; the day's busy list stands in without it.
            let missing = model.slot(for: date)?.missingMemberIds ?? info.busyMemberIds
            if missing.count == 1, missing.first == me { return "You're out" }
            return missing.isEmpty ? "most free" : "\(missing.count) missing"
        case .match:
            if let slot = model.slot(for: date), info.slotPosition == .start || info.slotPosition == .single {
                let startsMidday = !Calendar.current.isDate(slot.start, equalTo: date.startDate(), toGranularity: .minute)
                return startsMidday ? Formatting.time(slot.start) : "all free"
            }
            return "all free"
        case .busy:
            let names = info.busyMemberIds.map { group.member($0)?.firstName ?? "Someone" }
            if names.count == 1 { return info.busyMemberIds.first == me ? "You're busy" : "\(names[0]) busy" }
            return "\(names.count) busy"
        case .free, .hidden:
            if date == .today() { return "Today" }
            return info.holiday ?? ""
        }
    }
}

struct DayCell: View {
    let date: CalendarDay
    let info: AvailabilityDay?
    let note: String
    let compact: Bool
    let isSelected: Bool
    let action: () -> Void
    @Environment(\.theme) private var theme

    /// Past days look like days outside the group's timing.
    private var isPast: Bool { date < .today() }
    private var isToday: Bool { date == .today() }

    /// A day that works only for most of the group (quorum or optional members): drawn lighter.
    private var isPartial: Bool { !isPast && info?.status == .match && info?.isPartial == true }

    var body: some View {
        let status = isPast ? .hidden : (info?.status ?? .hidden)
        // Days that don't work fade; today's doesn't, so it never turns into a washed-out pill.
        let fade = status == .busy ? 0.5 : (status == .hidden ? 0.35 : 1)
        // Today on a day that doesn't work: an accent outline and accent date instead of a pill.
        let plainToday = isToday && status != .match
        // Filled with the accent: a day that works for everyone. Partial days are soft, with accent text.
        let onAccent = status == .match && !isPartial
        Button(action: action) {
            VStack(alignment: .leading, spacing: 4) {
                if plainToday {
                    Text("\(date.day)").font(AppFont.body(compact ? 15 : 17, .bold, relativeTo: .body))
                        .foregroundStyle(theme.accentInk)
                } else {
                    Text("\(date.day)").font(AppFont.body(compact ? 15 : 17, .semibold, relativeTo: .body))
                        .todayMark(date, onAccent: onAccent)
                }
                if !compact || status == .match || isPast {
                    Text(note).bodyFont(compact ? 9 : 11, .semibold, relativeTo: .caption2).lineLimit(1)
                        .foregroundStyle(plainToday ? theme.muted : (onAccent ? theme.onAccent : (isPartial ? theme.accentInk : theme.ink)))
                }
            }
            .foregroundStyle(onAccent ? theme.onAccent : (isPartial ? theme.accentInk : theme.ink))
            .opacity(plainToday ? 1 : fade)
            .padding(.horizontal, compact ? 6 : 10)
            .frame(maxWidth: .infinity, minHeight: 54, alignment: .leading)
            .background(background(status).opacity(fade))
            .overlay {
                // The selected day's ring takes over from today's outline, so there's only ever one.
                if plainToday && !isSelected { shape.strokeBorder(theme.accent, lineWidth: 1.5) }
            }
            .overlay(alignment: .topTrailing) {
                if status == .match, let count = info?.preferredBy?.count, count > 0 {
                    HStack(spacing: 1) {
                        Image(systemName: "heart.fill").font(.system(size: 8, weight: .bold))
                        if !compact { Text("\(count)").font(.system(size: 9, weight: .bold)) }
                    }
                    .foregroundStyle(onAccent ? theme.onAccent : theme.accentInk)
                    .padding(.top, 5)
                    .padding(.trailing, compact ? 5 : 7)
                    .accessibilityHidden(true)
                }
            }
            .overlay {
                // The selected day: a ring inside the cell, so it never reaches the neighbouring days.
                // On a day that works (filled with the accent), a light inner ring reads on the fill.
                if isSelected {
                    if onAccent {
                        shape.inset(by: 2).strokeBorder(theme.onAccent, lineWidth: 2)
                    } else {
                        shape.strokeBorder(theme.ink, lineWidth: 2)
                    }
                }
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(PressableButtonStyle(scale: 0.97))
        .accessibilityLabel(accessibilityText(status))
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }

    private var shape: UnevenRoundedRectangle {
        let big: CGFloat = 16, small: CGFloat = 5
        switch info?.slotPosition {
        case .start:
            return UnevenRoundedRectangle(topLeadingRadius: big, bottomLeadingRadius: big, bottomTrailingRadius: small, topTrailingRadius: small, style: .continuous)
        case .middle:
            return UnevenRoundedRectangle(topLeadingRadius: small, bottomLeadingRadius: small, bottomTrailingRadius: small, topTrailingRadius: small, style: .continuous)
        case .end:
            return UnevenRoundedRectangle(topLeadingRadius: small, bottomLeadingRadius: small, bottomTrailingRadius: big, topTrailingRadius: big, style: .continuous)
        default: return UnevenRoundedRectangle(topLeadingRadius: big, bottomLeadingRadius: big, bottomTrailingRadius: big, topTrailingRadius: big, style: .continuous)
        }
    }

    @ViewBuilder
    private func background(_ status: AvailabilityDay.Status) -> some View {
        switch status {
        case .match where isPartial:
            shape.fill(theme.accentSoft).overlay(shape.strokeBorder(theme.accent, lineWidth: 1.5))
        case .match:
            shape.fill(theme.accent).shadow(color: theme.accent.opacity(0.55), radius: 10, y: 10)
        case .busy:
            shape.fill(theme.busyBg).overlay(shape.strokeBorder(theme.glassBorder, lineWidth: 1))
        case .free, .hidden:
            shape.fill(theme.surface).overlay(shape.strokeBorder(theme.glassBorder, lineWidth: 1))
        }
    }

    private func accessibilityText(_ status: AvailabilityDay.Status) -> String {
        let base = (date == .today() ? "Today, " : "") + Formatting.longDay(date.startDate())
        switch status {
        case .match:
            let prefers = (info?.preferredBy?.count ?? 0) > 0 ? ", \(Formatting.plural(info?.preferredBy?.count ?? 0, "person", "people")) prefer it" : ""
            if isPartial { return "\(base), works for most of the group, \(note)\(prefers)" }
            return "\(base), works for everyone\(prefers)"
        case .busy: return "\(base), \(note)"
        case .free: return note.isEmpty ? base : "\(base), \(note)"
        case .hidden: return isPast ? "\(base), in the past" : "\(base), outside the group's timing"
        }
    }
}

extension View {
    /// Today's date, picked out with a pill behind it in the accent colour (inverted on a cell
    /// that's already filled with it). Drawn outside the layout, so cells keep their size.
    func todayMark(_ date: CalendarDay, onAccent: Bool = false) -> some View {
        modifier(TodayMark(isToday: date == .today(), onAccent: onAccent))
    }
}

private struct TodayMark: ViewModifier {
    let isToday: Bool
    let onAccent: Bool
    @Environment(\.theme) private var theme

    func body(content: Content) -> some View {
        if isToday {
            content
                .foregroundStyle(onAccent ? theme.accent : theme.onAccent)
                .background {
                    Capsule().fill(onAccent ? theme.onAccent : theme.accent)
                        .padding(.horizontal, -6)
                        .padding(.vertical, -2)
                }
        } else {
            content
        }
    }
}
