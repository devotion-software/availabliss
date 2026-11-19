import EventKit
import Observation
import SwiftUI

@MainActor
@Observable
final class MyPlansViewModel {
    private(set) var going: [MyPlan] = []
    private(set) var notGoing: [MyPlan] = []
    private(set) var past: [MyPlan] = []
    private(set) var loaded = false
    private(set) var isLoading = false
    private(set) var loadError: String?

    private let api: APIClient

    init(api: APIClient) {
        self.api = api
    }

    var isEmpty: Bool { going.isEmpty && notGoing.isEmpty && past.isEmpty }

    func load() async {
        isLoading = !loaded
        defer { isLoading = false }
        do {
            let response = try await api.send(API.Me.plans())
            going = response.going
            notGoing = response.notGoing
            past = response.past
            WidgetSync.shared.onPlans(response)
            loaded = true
            loadError = nil
        } catch  where !error.isCancellation {
            loadError = error.userMessage
        } catch {}
    }
}

/// My plans (from Activity): every plan across your groups. Coming up (with Add all to calendar),
/// the ones you can't make (only when there are some, quieter), then the last year's. Each row
/// shows your reply when it's Maybe or Not replied, and whether it's in this phone's calendar.
struct MyPlansView: View {
    @Environment(AppModel.self) private var app
    @Environment(\.theme) private var theme
    @State private var model: MyPlansViewModel
    @State private var choosingCalendar = false
    @State private var isAdding = false
    @State private var toast: String?
    @State private var error: String?

    init(api: APIClient) {
        _model = State(initialValue: MyPlansViewModel(api: api))
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                ScreenHeader(title: "My plans", subtitle: "Every plan across your groups", onBack: { app.router.pop() })
                content
                if !model.isLoading {
                    CalendarFeedCard(api: app.api, toast: $toast, error: $error)
                        .padding(.top, 10)
                }
            }
            .padding(.horizontal, 20)
            .padding(.top, 20)
            .padding(.bottom, 24)
        }
        .screenBackground(OrbLayout.proposal)
        .foregroundStyle(theme.ink)
        .chromeless()
        .task { await load() }
        .refreshable { await load() }
        .toast($toast)
        .errorAlert($error)
        .sensoryFeedback(.success, trigger: toast) { _, new in new != nil }
        .sheet(isPresented: $choosingCalendar) {
            let count = notAdded.count
            CalendarChoiceSheet(
                title: "Add all to calendar",
                message: "Which calendar should \(count == 1 ? "this plan" : "these \(count) plans") go in?",
                groups: app.deviceCalendar.writableCalendarsBySource,
                selectedId: app.deviceCalendar.preferredCalendar?.calendarIdentifier
            ) { calendar in
                Task { await addAll(to: calendar) }
            }
        }
    }

    @ViewBuilder
    private var content: some View {
        if model.isLoading {
            StatusView(kind: .loading)
        } else if let error = model.loadError, !model.loaded {
            StatusView(kind: .error(error) { Task { await model.load() } })
        } else if model.isEmpty {
            StatusView(
                kind: .empty(
                    title: "No plans yet", message: "When a time is confirmed or an event is added in one of your groups, it shows up here.", systemImage: "calendar")
            )
            .glass(cornerRadius: 24)
        } else {
            comingUp
            if !model.notGoing.isEmpty {
                sectionTitle("Can\u{2019}t make it", quiet: true)
                ForEach(model.notGoing) { plan in
                    planButton(plan, quiet: true)
                }
            }
            if !model.past.isEmpty {
                sectionTitle("Past")
                ForEach(model.past) { plan in
                    planButton(plan)
                }
            }

        }
    }

    @ViewBuilder
    private var comingUp: some View {
        HStack {
            Text("Coming up").displayFont(19, .semibold).accessibilityAddTraits(.isHeader)
            Spacer()
            // Gone once every plan coming up is in your calendar (as on Android).
            if app.session.user?.showsAddToCalendar ?? true, !notAdded.isEmpty || isAdding {
                Button(action: startAddAll) {
                    if isAdding {
                        ProgressView().controlSize(.small)
                    } else {
                        Label("Add all to calendar", systemImage: "calendar.badge.plus")
                    }
                }
                .buttonStyle(.glassPill)
                .disabled(isAdding)
            }
        }
        .padding(.top, 6)
        if model.going.isEmpty {
            HStack(spacing: 10) {
                Image(systemName: "calendar")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(theme.muted)
                    .accessibilityHidden(true)
                Text("Nothing coming up")
                    .bodyFont(14, relativeTo: .subheadline)
                    .foregroundStyle(theme.muted)
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 16)
            .frame(minHeight: 52)
            .glass(cornerRadius: 18)
        } else {
            ForEach(model.going) { plan in
                planButton(plan)
            }
        }
    }

    private func sectionTitle(_ title: String, quiet: Bool = false) -> some View {
        Text(title)
            .displayFont(quiet ? 16 : 19, .semibold)
            .foregroundStyle(quiet ? theme.muted : theme.ink)
            .padding(.top, 10)
            .accessibilityAddTraits(.isHeader)
    }

    private func planButton(_ plan: MyPlan, quiet: Bool = false) -> some View {
        Button {
            open(plan)
        } label: {
            MyPlanRow(plan: plan, added: app.deviceCalendar.addedState(plan.id) != .notAdded, quiet: quiet)
        }
        .buttonStyle(.pressable)
    }

    /// Coming up plans not in this phone's calendar yet.
    private var notAdded: [MyPlan] {
        model.going.filter { app.deviceCalendar.addedState($0.id) == .notAdded }
    }

    private func open(_ plan: MyPlan) {
        if let proposalId = plan.event.proposalId {
            app.router.push(.proposal(id: proposalId))
        } else {
            app.router.push(.group(id: plan.event.groupId))
        }
    }

    private func load() async {
        await model.load()
        // Same as Add to calendar: a plan that's moved since it was added moves in the calendar too.
        for plan in model.going {
            app.deviceCalendar.updateIfPlanChanged(plan.event)
        }
    }

    /// Asks which calendar once when there's a choice (as Add to calendar does), then adds them all there.
    private func startAddAll() {
        if app.deviceCalendar.canChooseCalendar {
            choosingCalendar = true
        } else {
            Task { await addAll(to: nil) }
        }
    }

    private func addAll(to calendar: EKCalendar?) async {
        guard !isAdding else { return }
        isAdding = true
        defer { isAdding = false }
        var added = 0
        for plan in notAdded {
            do {
                try await app.deviceCalendar.add(plan.event, to: calendar)
                added += 1
            } catch {
                self.error = error.userMessage
                break
            }
        }
        if added > 0 {
            toast = "Added \(Formatting.plural(added, "plan")) to your calendar"
        }
    }
}

/// Date tile, title, time (or All day) · group, a Maybe / Not replied pill, and a mark when it's in
/// this phone's calendar.
private struct MyPlanRow: View {
    let plan: MyPlan
    let added: Bool
    var quiet = false
    @Environment(\.theme) private var theme

    var body: some View {
        HStack(spacing: 12) {
            DateBlock(date: plan.event.start)
                .opacity(quiet ? 0.5 : 1)
            VStack(alignment: .leading, spacing: 2) {
                Text(plan.event.title)
                    .bodyFont(15, .semibold)
                    .foregroundStyle(quiet ? theme.muted : theme.ink)
                    .lineLimit(2)
                Text("\(when) \u{00B7} \(plan.groupName)")
                    .bodyFont(12, relativeTo: .caption)
                    .foregroundStyle(theme.muted)
                    .lineLimit(1)
                if let pill {
                    pill.padding(.top, 2)
                }
            }
            Spacer(minLength: 4)
            if added {
                Image(systemName: "calendar.badge.checkmark")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(theme.okInk)
                    .accessibilityLabel("In your calendar")
            }
            Image(systemName: "chevron.right")
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(theme.muted)
                .accessibilityHidden(true)
        }
        .padding(12)
        .glass(cornerRadius: 20)
        .contentShape(Rectangle())
        .accessibilityElement(children: .combine)
        .accessibilityHint(plan.event.proposalId == nil ? "Opens the group" : "Opens the plan")
    }

    /// "18:30 – 21:00", "All day", or the dates of a plan over several days.
    private var when: String {
        let parts = Formatting.spanParts(start: plan.event.start, end: plan.event.end, allDay: plan.event.allDay)
        if let time = parts.time { return time }
        return parts.date == Formatting.shortDay(plan.event.start) ? "All day" : parts.date
    }

    private var pill: StatusPill? {
        switch plan.myResponse {
        case .maybe: StatusPill(text: "Maybe", tone: .indigo)
        case .pending: StatusPill(text: "Not replied", tone: .apricot)
        default: nil
        }
    }
}
