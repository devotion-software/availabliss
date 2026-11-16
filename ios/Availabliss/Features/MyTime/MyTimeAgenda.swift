import SwiftUI

/// My time as a list: each day from today with what's on it (your blocks and calendar busy, with
/// where each came from) and its longest free stretch as a row you tap to block.
struct MyTimeAgenda<Footer: View>: View {
    let model: MyTimeViewModel
    let onOpenDay: (CalendarDay) -> Void
    let onOpen: (BusyInterval) -> Void
    let onBlock: (BlockDraft) -> Void
    /// Under the first week (the "Every week" list).
    @ViewBuilder let afterFirstWeek: Footer
    @Environment(\.theme) private var theme

    var body: some View {
        let weeks = model.weeks
        ForEach(Array(weeks.enumerated()), id: \.element.start) { index, week in
            VStack(alignment: .leading, spacing: 12) {
                Text(weekTitle(week.start))
                    .bodyFont(13, .semibold, relativeTo: .footnote)
                    .foregroundStyle(theme.muted)
                    .padding(.leading, 4)
                    .accessibilityAddTraits(.isHeader)
                ForEach(week.days, id: \.self) { day in
                    AgendaDay(model: model, day: day, onOpenDay: onOpenDay, onOpen: onOpen, onBlock: onBlock)
                        .id(day)
                }
            }
            if index == 0 { afterFirstWeek }
        }
        if model.days.count < MyTimeViewModel.maxDays {
            ProgressView()
                .frame(maxWidth: .infinity)
                .padding(.vertical, 16)
                // Re-runs while it stays on screen after each page lands.
                .task(id: model.days.count) { await model.loadMore() }
                .accessibilityLabel("Loading more days")
        }
    }

    /// "This week", "Next week", then "23 – 29 Nov".
    private func weekTitle(_ start: CalendarDay) -> String {
        let thisWeek = CalendarDay.today().startOfWeek()
        if start == thisWeek { return "This week" }
        if start == thisWeek.adding(days: 7) { return "Next week" }
        return Formatting.dayRange(start, start.adding(days: 6))
    }
}

private struct AgendaDay: View {
    let model: MyTimeViewModel
    let day: CalendarDay
    let onOpenDay: (CalendarDay) -> Void
    let onOpen: (BusyInterval) -> Void
    let onBlock: (BlockDraft) -> Void
    @Environment(\.theme) private var theme

    var body: some View {
        let summary = model.summary(for: day)
        let allDay = (model.layout.allDay[day] ?? []).filter { !$0.isIgnored }
        let timed = (model.layout.timed[day] ?? []).filter { !$0.interval.isIgnored }.sorted { $0.from < $1.from }
        VStack(alignment: .leading, spacing: 4) {
            Button { onOpenDay(day) } label: {
                HStack(spacing: 6) {
                    Text("\(Weekday.short(day.weekday())) \(day.day)")
                        .bodyFont(14, .bold)
                        .todayMark(day)
                        .padding(.horizontal, day == .today() ? 6 : 0)
                    if day == .today() { Text("Today").bodyFont(13, .semibold).foregroundStyle(theme.accentInk) }
                    Spacer(minLength: 8)
                    Text(summary.text).bodyFont(12, relativeTo: .caption).foregroundStyle(theme.muted)
                    Image(systemName: "chevron.right").font(.system(size: 10, weight: .bold)).foregroundStyle(theme.muted.opacity(0.7))
                }
                .padding(.horizontal, 4)
                .frame(minHeight: 32)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(summary.accessibilityText)
            .accessibilityHint("Opens the day")
            ForEach(Array(allDay.enumerated()), id: \.offset) { _, interval in
                BusyRow(time: ("All day", nil), interval: interval, tag: tag(interval)) { onOpen(interval) }
            }
            ForEach(timed) { piece in
                BusyRow(time: (DayLayout.clock(piece.from), DayLayout.clock(piece.to)), interval: piece.interval, tag: tag(piece.interval)) { onOpen(piece.interval) }
            }
            if let free = summary.longestFree {
                FreeRow(text: summary.text) {
                    if free.lowerBound <= DaySummary.windowStart && free.upperBound >= DaySummary.windowEnd {
                        onBlock(BlockDraft(day: day, when: .allDay))
                    } else {
                        onBlock(BlockDraft(day: day, range: (Int(free.lowerBound), Int(free.upperBound))))
                    }
                }
            }
        }
    }

    /// Where a busy item came from: "Weekly", "Every other week", "Your block" or "Calendar".
    private func tag(_ interval: BusyInterval) -> String {
        guard interval.source == .manual else { return "Calendar" }
        return model.block(for: interval)?.repeat.tag ?? "Your block"
    }
}

/// "09:00 / 17:30  Work  Weekly": your blocks tinted with the accent, calendar busy plain.
private struct BusyRow: View {
    let time: (String, String?)
    let interval: BusyInterval
    let tag: String
    let action: () -> Void
    @Environment(\.theme) private var theme

    private var isManual: Bool { interval.source == .manual }
    private var title: String { isManual ? (interval.label ?? "Busy") : "Busy" }

    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 0) {
                    Text(time.0)
                    if let end = time.1 { Text(end).foregroundStyle(theme.muted) }
                }
                .bodyFont(12, .semibold, relativeTo: .caption)
                .monospacedDigit()
                .frame(width: 46, alignment: .leading)
                Text(title)
                    .bodyFont(14, .semibold)
                    .foregroundStyle(isManual ? theme.accentInk : theme.ink)
                    .lineLimit(1)
                Spacer(minLength: 6)
                Text(tag).bodyFont(11, relativeTo: .caption2).foregroundStyle(theme.muted)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .frame(minHeight: 48)
            .background {
                let shape = RoundedRectangle(cornerRadius: 14, style: .continuous)
                if isManual {
                    shape.fill(theme.accentSoft)
                } else {
                    shape.fill(theme.surface).overlay(shape.strokeBorder(theme.glassBorder, lineWidth: 1))
                }
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.pressable)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(title), \(time.1.map { "\(time.0) to \($0)" } ?? time.0), \(tag)")
        .accessibilityHint(isManual ? "Edits the block" : "Shows the calendar event")
    }
}

/// The day's longest free stretch as a dashed row: "Free from 17:30   + Block".
private struct FreeRow: View {
    let text: String
    let action: () -> Void
    @Environment(\.theme) private var theme

    var body: some View {
        Button(action: action) {
            HStack {
                Text(text).bodyFont(13, relativeTo: .subheadline).foregroundStyle(theme.muted)
                Spacer()
                Label("Block", systemImage: "plus")
                    .bodyFont(13, .semibold, relativeTo: .subheadline)
                    .foregroundStyle(theme.accentInk)
            }
            .padding(.horizontal, 12)
            .frame(minHeight: 44)
            .background(
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .strokeBorder(theme.line3, style: StrokeStyle(lineWidth: 1.5, dash: [5, 4]))
            )
            .contentShape(Rectangle())
        }
        .buttonStyle(.pressable)
        .accessibilityLabel("\(text). Block time")
    }
}
