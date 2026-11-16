import SwiftUI

/// My time: a list of days from today, each with a strip of its busy time and a one-line summary.
/// Calendar busy is faded, blocks you made are in the accent colour. Tap a day for its timeline.
struct MyTimeView: View {
    @Environment(AppModel.self) private var app
    @Environment(\.theme) private var theme
    @State private var model: MyTimeViewModel
    @State private var draft: BlockDraft?
    @State private var openDay: OpenDay?
    @State private var showBlockOptions = false
    @State private var showJump = false
    @State private var jumpTarget: CalendarDay?

    /// The day sheet's starting day.
    private struct OpenDay: Identifiable {
        let id = UUID()
        let day: CalendarDay
    }

    init(api: APIClient) {
        _model = State(initialValue: MyTimeViewModel(api: api))
    }

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 14) {
                    ScreenHeader(title: "My time", subtitle: "What groups see as busy") {
                        HStack(spacing: 8) {
                            IconButton(systemImage: "calendar", label: "Jump to date") { showJump = true }
                            IconButton(icon: .asset("CalendarSync"), label: "Connected calendars") { app.router.push(.calendars) }
                        }
                    }
                    Button {
                        showBlockOptions = true
                    } label: {
                        LoadingLabel(title: "Block time", systemImage: "plus")
                    }
                    .buttonStyle(.filled(.primary, height: 54, cornerRadius: 18))
                    if model.hasCalendar == false {
                        ConnectCalendarHint { app.router.push(.calendars) }
                    }
                    if !model.hasLoaded {
                        ProgressView()
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 60)
                            .accessibilityLabel("Loading your time")
                    } else {
                        let weeks = model.weeks
                        ForEach(Array(weeks.enumerated()), id: \.element.start) { index, week in
                            WeekSection(title: weekTitle(week.start), days: week.days, model: model) { day in
                                openDay = OpenDay(day: day)
                            }
                            if index == 0, !model.recurringBlocks.isEmpty {
                                everyWeek
                            }
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
                }
                .padding(.horizontal, 20)
                .padding(.top, 20)
                .padding(.bottom, 24)
            }
            .onChange(of: jumpTarget) { _, target in
                guard let target else { return }
                Task {
                    await model.ensureLoaded(through: target)
                    // The week first (rows in weeks not built yet can't be found), then the day itself.
                    proxy.scrollTo(target.startOfWeek(), anchor: .top)
                    try? await Task.sleep(for: .milliseconds(150))
                    withAnimation { proxy.scrollTo(target, anchor: .top) }
                    jumpTarget = nil
                }
            }
        }
        .screenBackground(OrbLayout.myTime)
        .foregroundStyle(theme.ink)
        .chromeless()
        .task { await model.load() }
        .refreshable { await model.load() }
        .task {
            for await event in app.live.events() {
                switch event.kind {
                case .availabilityChanged:
                    if event.payload(as: [String: String].self)?["userId"] == app.session.user?.id { await model.load() }
                case .reconnected:
                    await model.load()
                default:
                    break
                }
            }
        }
        .confirmationDialog("Block time", isPresented: $showBlockOptions, titleVisibility: .visible) {
            Button(eveningIsTomorrow ? "Tomorrow evening" : "This evening") {
                let today = CalendarDay.today()
                draft = BlockDraft(day: eveningIsTomorrow ? today.adding(days: 1) : today, range: (18 * 60, DayLayout.dayMinutes))
            }
            Button("All day…") { draft = BlockDraft(day: .today(), when: .allDay, pickDate: true) }
            Button("Away for a few days…") { draft = BlockDraft(day: .today(), when: .days, pickDate: true) }
            Button("Something else…") { draft = BlockDraft(day: .today()) }
        }
        .sheet(item: $draft) { $0.sheet(model: model) }
        .sheet(item: $openDay) { open in
            DaySheet(model: model, day: open.day) { app.router.push(.calendars) }
        }
        .sheet(isPresented: $showJump) {
            JumpToDateSheet { day in jumpTarget = day }
        }
        .confirmationDialog(
            "Remove this block?",
            isPresented: Binding(get: { model.pendingRemoval != nil }, set: { if !$0 { model.pendingRemoval = nil } }),
            titleVisibility: .visible,
            presenting: model.pendingRemoval
        ) { block in
            Button(block.repeat == .none ? "Remove block" : "Remove every occurrence", role: .destructive) {
                Task { await model.remove(block) }
            }
        } message: { block in
            Text("\(block.note ?? "Busy") · \(block.scheduleDescription)")
        }
        .errorAlert($model.error)
    }

    /// Past 18:00 the evening preset means tomorrow's.
    private var eveningIsTomorrow: Bool { Calendar.current.component(.hour, from: Date()) >= 18 }

    /// "This week", "Next week", then "23 – 29 Nov".
    private func weekTitle(_ start: CalendarDay) -> String {
        let thisWeek = CalendarDay.today().startOfWeek()
        if start == thisWeek { return "This week" }
        if start == thisWeek.adding(days: 7) { return "Next week" }
        return Formatting.dayRange(start, start.adding(days: 6))
    }

    /// Your repeating blocks, one row each.
    private var everyWeek: some View {
        VStack(alignment: .leading, spacing: 6) {
            SectionHeading(title: "Every week")
            VStack(spacing: 0) {
                ForEach(Array(model.recurringBlocks.enumerated()), id: \.element.id) { index, block in
                    if index > 0 { theme.line.frame(height: 1) }
                    RecurringBlockRow(block: block) {
                        draft = BlockDraft(day: CalendarDay(block.start), editing: block)
                    } onRemove: {
                        model.pendingRemoval = block
                    }
                }
            }
            .padding(.horizontal, 14)
            .glass(cornerRadius: 24)
        }
    }
}

private struct SectionHeading: View {
    let title: String
    @Environment(\.theme) private var theme

    var body: some View {
        Text(title)
            .bodyFont(13, .semibold, relativeTo: .footnote)
            .foregroundStyle(theme.muted)
            .padding(.leading, 4)
            .padding(.top, 4)
            .accessibilityAddTraits(.isHeader)
    }
}

/// A week's heading and its days in one glass panel.
private struct WeekSection: View {
    let title: String
    let days: [CalendarDay]
    let model: MyTimeViewModel
    let onOpen: (CalendarDay) -> Void
    @Environment(\.theme) private var theme

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            SectionHeading(title: title)
            VStack(spacing: 0) {
                ForEach(Array(days.enumerated()), id: \.element) { index, day in
                    if index > 0 { theme.line.frame(height: 1).padding(.leading, 62) }
                    DayRow(summary: model.summary(for: day)) { onOpen(day) }
                        .id(day)
                }
            }
            .padding(.horizontal, 14)
            .glass(cornerRadius: 24)
        }
    }
}

/// A day: weekday and date, its summary ("Free from 18:00 · Choir") and its busy strip. Tap to open it.
private struct DayRow: View {
    let summary: DaySummary
    let action: () -> Void
    @Environment(\.theme) private var theme

    var body: some View {
        let day = summary.day
        let isToday = day == .today()
        Button(action: action) {
            HStack(spacing: 14) {
                VStack(spacing: 1) {
                    Text(Weekday.short(day.weekday()))
                        .bodyFont(11, .semibold, relativeTo: .caption2)
                        .foregroundStyle(isToday ? theme.accentInk : theme.muted)
                    Text("\(day.day)")
                        .displayFont(17, .semibold, relativeTo: .headline)
                        .monospacedDigit()
                        .foregroundStyle(isToday ? theme.onAccent : theme.ink)
                        .frame(width: 32, height: 32)
                        .background { if isToday { Circle().fill(theme.accent) } }
                }
                .frame(width: 48)
                VStack(alignment: .leading, spacing: 8) {
                    HStack(spacing: 0) {
                        Text(summary.text)
                            .bodyFont(14, .semibold)
                            .foregroundStyle(summary.isBusyAllDay ? theme.muted : theme.ink)
                            .layoutPriority(1)
                        if !summary.names.isEmpty {
                            Text(" · \(summary.names.joined(separator: ", "))")
                                .bodyFont(14)
                                .foregroundStyle(theme.muted)
                        }
                    }
                    .lineLimit(1)
                    DayStrip(segments: summary.segments)
                }
                Image(systemName: "chevron.right")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(theme.muted.opacity(0.7))
            }
            .padding(.vertical, 10)
            .frame(maxWidth: .infinity, minHeight: 64, alignment: .leading)
            .contentShape(Rectangle())
        }
        .buttonStyle(.pressable)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(summary.accessibilityText)
        .accessibilityAddTraits(.isButton)
        .accessibilityHint("Opens the day")
    }
}

/// 06:00 to 24:00 as a thin bar: calendar busy faded, your blocks in the accent colour,
/// faint ticks at 12:00 and 18:00.
private struct DayStrip: View {
    let segments: [DaySummary.Segment]
    @Environment(\.theme) private var theme

    var body: some View {
        GeometryReader { proxy in
            let start = DaySummary.windowStart, span = DaySummary.windowEnd - start
            let x = { (minutes: Double) in CGFloat((min(max(minutes, start), DaySummary.windowEnd) - start) / span) * proxy.size.width }
            ZStack(alignment: .leading) {
                ZStack(alignment: .leading) {
                    theme.line
                    ForEach(Array(segments.enumerated()), id: \.offset) { _, segment in
                        let from = x(segment.from), to = x(segment.to)
                        if to > from {
                            RoundedRectangle(cornerRadius: 2)
                                .fill(segment.manual ? theme.accent : theme.ink.opacity(theme.isDark ? 0.4 : 0.28))
                                .frame(width: max(3, to - from))
                                .offset(x: from)
                        }
                    }
                }
                .frame(height: 8)
                .clipShape(Capsule())
                ForEach([12.0, 18.0], id: \.self) { hour in
                    Rectangle()
                        .fill(theme.muted.opacity(0.45))
                        .frame(width: 1, height: 14)
                        .offset(x: x(hour * 60))
                }
            }
            .frame(height: proxy.size.height)
        }
        .frame(height: 14)
        .accessibilityHidden(true)
    }
}

/// Shown when no calendar is connected.
private struct ConnectCalendarHint: View {
    let action: () -> Void
    @Environment(\.theme) private var theme

    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Image("CalendarSync")
                    .renderingMode(.template)
                    .resizable()
                    .scaledToFit()
                    .frame(width: 20, height: 20)
                    .foregroundStyle(theme.accentInk)
                    .frame(width: 38, height: 38)
                    .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(theme.accentSoft))
                    .accessibilityHidden(true)
                Text("Connect a calendar so your busy time fills in by itself")
                    .bodyFont(14, .semibold)
                    .multilineTextAlignment(.leading)
                    .fixedSize(horizontal: false, vertical: true)
                Spacer(minLength: 0)
                Image(systemName: "chevron.right")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(theme.muted)
            }
            .foregroundStyle(theme.ink)
            .padding(14)
            .glass(cornerRadius: 20)
            .contentShape(Rectangle())
        }
        .buttonStyle(.pressable)
    }
}

/// Pick a day to scroll the list to.
private struct JumpToDateSheet: View {
    let onPick: (CalendarDay) -> Void
    @Environment(\.dismiss) private var dismiss
    @Environment(\.theme) private var theme
    @State private var date = Date()

    var body: some View {
        BottomSheet(title: "Jump to a date") {
            DatePicker("Date", selection: $date, in: range, displayedComponents: .date)
                .datePickerStyle(.graphical)
                .tint(theme.accent)
                .onChange(of: date) { _, picked in pick(CalendarDay(picked)) }
            Button("Today") { pick(.today()) }
                .buttonStyle(.filled(.secondary))
        }
    }

    private func pick(_ day: CalendarDay) {
        onPick(day)
        dismiss()
    }

    private var range: ClosedRange<Date> {
        let today = CalendarDay.today()
        return today.startDate()...today.adding(days: MyTimeViewModel.maxDays - 1).startDate()
    }
}

/// "Choir · Every Tuesday · 19:00 – 21:30", with edit / remove.
private struct RecurringBlockRow: View {
    let block: ManualBlock
    let onEdit: () -> Void
    let onRemove: () -> Void
    @Environment(\.theme) private var theme

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "repeat")
                .font(.system(size: 15, weight: .bold))
                .foregroundStyle(theme.accentInk)
                .frame(width: 34, height: 34)
                .background(RoundedRectangle(cornerRadius: 11).fill(theme.accentSoft))
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 0) {
                Text(block.note ?? "Busy").bodyFont(14, .semibold)
                Text(block.scheduleDescription).bodyFont(12, relativeTo: .caption).foregroundStyle(theme.muted)
            }
            Spacer(minLength: 0)
            Menu {
                Button(action: onEdit) { Label("Edit", systemImage: "pencil") }
                Button(role: .destructive, action: onRemove) { Label("Remove", systemImage: "trash") }
            } label: {
                Image(systemName: "ellipsis")
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(theme.muted)
                    .frame(width: 44, height: 44)
                    .contentShape(Rectangle())
            }
            .accessibilityHidden(true)
        }
        .padding(.vertical, 8)
        .frame(minHeight: 56)
        .contentShape(Rectangle())
        .onTapGesture(perform: onEdit)
        .contextMenu {
            Button(action: onEdit) { Label("Edit", systemImage: "pencil") }
            Button(role: .destructive, action: onRemove) { Label("Remove", systemImage: "trash") }
        }
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isButton)
        .accessibilityHint("Double-tap to edit")
        .accessibilityAction(named: "Remove", onRemove)
    }
}
