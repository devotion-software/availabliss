import SwiftUI

/// My time: a month shaded by how free each day is (or, switched, a list of what's on each day).
/// Tap a day for its week, with that day's timeline to block time on.
struct MyTimeView: View {
    @Environment(AppModel.self) private var app
    @Environment(\.theme) private var theme
    @State private var model: MyTimeViewModel
    @State private var draft: BlockDraft?
    @State private var calendarBusy: CalendarBusyItem?
    @State private var toast: String?
    /// Month (the default) or the agenda list, remembered on the device.
    @AppStorage("myTime.view") private var view: ViewMode = .month
    @State private var month = CalendarDay.today().firstOfMonth
    /// The day whose week is open, if one is.
    @State private var openDay: OpenDay?

    enum ViewMode: String { case month, agenda }

    private struct OpenDay: Equatable {
        let id = UUID()
        let day: CalendarDay
    }

    init(api: APIClient) {
        _model = State(initialValue: MyTimeViewModel(api: api))
    }

    var body: some View {
        ZStack {
            if let openDay {
                WeekDayView(model: model, day: openDay.day, backTitle: view == .month ? "Month" : "Agenda") {
                    withAnimation(.spring(response: 0.35, dampingFraction: 0.9)) { self.openDay = nil }
                } openCalendars: {
                    app.router.push(.calendars)
                }
                .id(openDay.id)
                .transition(.move(edge: .trailing))
            } else {
                main.transition(.move(edge: .leading))
            }
        }
        .screenBackground(OrbLayout.myTime)
        .foregroundStyle(theme.ink)
        .chromeless()
        .task { await model.load() }
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
        .sheet(item: $draft) { $0.sheet(model: model) }
        .sheet(item: $calendarBusy) { busy in
            CalendarBusySheet(interval: busy.interval) {
                calendarBusy = nil
                app.router.push(.calendars)
            } onIgnore: { series in
                let ok = await model.ignore(busy.interval, series: series)
                if ok { toast = series ? "Not busy every time it repeats" : "Marked not busy" }
                return ok
            } onUnignore: {
                let ok = await model.unignore(busy.interval)
                if ok { toast = "Counts as busy again" }
                return ok
            }
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
        .toast($toast)
        .errorAlert($model.error)
    }

    private var main: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 14) {
                ScreenHeader(title: "My time", subtitle: "What groups see as busy") {
                    HStack(spacing: 8) {
                        IconButton(
                            systemImage: view == .month ? "list.bullet" : "calendar",
                            label: view == .month ? "Show as a list" : "Show as a month"
                        ) {
                            withAnimation(.easeInOut(duration: 0.2)) { view = view == .month ? .agenda : .month }
                        }
                        IconButton(icon: .asset("CalendarSync"), label: "Connected calendars") { app.router.push(.calendars) }
                    }
                }
                Button {
                    draft = BlockDraft(day: .today())
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
                } else if view == .month {
                    MyTimeMonth(model: model, month: $month) { day in open(day) }
                    everyWeek
                } else {
                    MyTimeAgenda(model: model, onOpenDay: { day in open(day) }, onOpen: open(interval:), onBlock: { draft = $0 }) {
                        everyWeek
                    }
                }
            }
            .padding(.horizontal, 20)
            .padding(.top, 20)
            .padding(.bottom, 24)
        }
        .tabBarScrollInset()
        .refreshable { await model.load() }
    }

    private func open(_ day: CalendarDay) {
        withAnimation(.spring(response: 0.35, dampingFraction: 0.9)) { openDay = OpenDay(day: max(day, .today())) }
    }

    /// A tap on busy time in the agenda: edit your own block (the whole series if it repeats), or explain calendar busy.
    private func open(interval: BusyInterval) {
        switch interval.source {
        case .calendar:
            calendarBusy = CalendarBusyItem(interval: interval)
        case .manual:
            if let block = model.block(for: interval) {
                draft = BlockDraft(day: CalendarDay(block.start), editing: block)
            } else {
                toast = "That block was just removed."
            }
        }
    }

    /// Your repeating blocks, one row each.
    @ViewBuilder
    private var everyWeek: some View {
        if !model.recurringBlocks.isEmpty {
        VStack(alignment: .leading, spacing: 6) {
            SectionHeading(title: "Repeating")
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
