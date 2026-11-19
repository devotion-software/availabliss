import SwiftUI

/// "Best options" under the month: the top three upcoming times, fewest missing first, then by the
/// Pro best-date score, each with Propose. Shown to Pro viewers, and to anyone once the group's
/// timing lets a time work without everyone (so who's missing is worth ranking). Shortlist two to five
/// of them (across months too) to ask the group in a date poll.
struct BestOptionsCard: View {
    let model: GroupAvailabilityViewModel
    let group: Group
    let me: String?
    let isPro: Bool
    @Binding var shortlist: [Slot]
    /// "Ask the group": opens the date poll sheet with the shortlist.
    let onAsk: () -> Void
    let propose: (ProposalDraft) -> Void
    @Environment(\.theme) private var theme

    /// A date poll asks about 2–5 times.
    static let pollRange = 2...5

    var body: some View {
        let options = model.bestOptions()
        let hasMissing = model.availability?.slots.contains { $0.missingMemberIds != nil } ?? false
        if !options.isEmpty, isPro || hasMissing {
            VStack(alignment: .leading, spacing: 10) {
                Text("Best options").sectionLabelStyle()
                ForEach(Array(options.enumerated()), id: \.element.id) { index, slot in
                    if index > 0 { Rectangle().fill(theme.line).frame(height: 1) }
                    row(slot, isTop: index == 0)
                }
            }
            .padding(14)
            .glass(cornerRadius: 22)
            .animation(.spring(response: 0.3, dampingFraction: 0.85), value: shortlist.map(\.id))
        }
    }

    private func row(_ slot: Slot, isTop: Bool) -> some View {
        HStack(alignment: .center, spacing: 10) {
            VStack(alignment: .leading, spacing: 3) {
                if isTop, slot.score != nil {
                    StatusPill(text: "Best for the group", tone: .indigo, systemImage: "star.fill")
                }
                Text(slot.label).bodyFont(15, .semibold).lineLimit(2)
                ForEach(notes(slot), id: \.self) { line in
                    Text(line)
                        .bodyFont(12, relativeTo: .caption)
                        .foregroundStyle(theme.muted)
                        .fixedSize(horizontal: false, vertical: true)
                }
                ShortlistToggle(slot: slot, shortlist: $shortlist).padding(.vertical, -6)
            }
            Spacer(minLength: 4)
            Button("Propose") { propose(ProposalDraft(slot: slot, requirement: group.requirement)) }
                .buttonStyle(.filled(slot.missingCount == 0 ? .accent : .secondary, height: 36, cornerRadius: 12, fontSize: 13, fullWidth: false))
                .accessibilityLabel("Propose \(slot.label)")
        }
        .accessibilityElement(children: .contain)
    }

    /// "Fair for Ana, who missed out last time", "Ana can't make it", or "Everyone's free".
    private func notes(_ slot: Slot) -> [String] {
        var lines: [String] = []
        if let fair = Clash.fairFor(slot.fairFor ?? [], group: group, me: me) { lines.append(fair) }
        if let missing = slot.missingMemberIds, !missing.isEmpty {
            lines.append(Clash.cantMake(missing, group: group, me: me))
        } else {
            lines.append("Everyone\u{2019}s free")
        }
        return lines
    }
}

/// "Ask the group": the shortlisted times as a date poll in the chat, with a question to go with them.
struct AskGroupSheet: View {
    let slots: [Slot]
    let requirement: Requirement
    let post: (_ question: String, _ slots: [PollSlot], _ multi: Bool) async -> Bool
    @Environment(\.dismiss) private var dismiss
    @Environment(\.theme) private var theme
    @State private var question = "Which of these works?"
    /// Date polls let people pick every time they can make, unless it's turned off.
    @State private var multi = true
    @State private var isPosting = false

    private var trimmedQuestion: String { question.trimmingCharacters(in: .whitespacesAndNewlines) }

    /// Each time as the poll asks about it: as it would be proposed (a duration's first stretch, all day or not).
    private var pollSlots: [PollSlot] {
        slots.sorted { $0.start < $1.start }.map { slot in
            let draft = ProposalDraft(slot: slot, requirement: requirement)
            return PollSlot(start: draft.start, end: draft.end, allDay: draft.allDay)
        }
    }

    var body: some View {
        BottomSheet(title: "Ask the group") {
            InputField(placeholder: "Ask a question", text: $question)
            Text("Times").sectionLabelStyle()
            VStack(alignment: .leading, spacing: 8) {
                ForEach(slots.sorted { $0.start < $1.start }) { slot in
                    HStack(spacing: 10) {
                        Image(systemName: "calendar").font(.system(size: 13, weight: .semibold)).foregroundStyle(theme.accentInk)
                        Text(slot.label).bodyFont(15, .semibold)
                    }
                    .accessibilityElement(children: .combine)
                }
            }
            Toggle("Allow more than one choice", isOn: $multi)
                .toggleStyle(.glassSwitch)
                .bodyFont(15, .medium)
            Text("It goes in the chat as a poll. Everyone can see who\u{2019}s busy at each time.")
                .bodyFont(13, relativeTo: .subheadline)
                .foregroundStyle(theme.muted)
                .fixedSize(horizontal: false, vertical: true)
            Button {
                Task {
                    isPosting = true
                    defer { isPosting = false }
                    if await post(trimmedQuestion, pollSlots, multi) { dismiss() }
                }
            } label: {
                LoadingLabel(title: "Post poll", isLoading: isPosting)
            }
            .buttonStyle(.accent)
            .disabled(trimmedQuestion.isEmpty || isPosting)
        }
    }
}

/// "Shortlist" / "Shortlisted": picks a time for a date poll (from Best options or a tapped day).
struct ShortlistToggle: View {
    let slot: Slot
    @Binding var shortlist: [Slot]
    @Environment(\.theme) private var theme

    private var isOn: Bool { shortlist.contains { $0.id == slot.id } }

    var body: some View {
        let isFull = !isOn && shortlist.count >= BestOptionsCard.pollRange.upperBound
        Button {
            if isOn {
                shortlist.removeAll { $0.id == slot.id }
            } else if shortlist.count < BestOptionsCard.pollRange.upperBound {
                shortlist.append(slot)
            }
        } label: {
            HStack(spacing: 4) {
                Image(systemName: isOn ? "checkmark.circle.fill" : "plus.circle").font(.system(size: 12, weight: .semibold))
                Text(isOn ? "Shortlisted" : "Shortlist")
            }
            .bodyFont(12, .semibold, relativeTo: .caption)
            .foregroundStyle(isOn ? theme.accentInk : theme.muted)
            .padding(.horizontal, 10)
            .frame(height: 28)
            .background(Capsule().fill(isOn ? theme.accentSoft : theme.line))
            .frame(minHeight: 44)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(isFull)
        .opacity(isFull ? 0.5 : 1)
        .accessibilityLabel("Shortlist \(slot.label)")
        .accessibilityAddTraits(isOn ? .isSelected : [])
        .accessibilityHint(isFull ? "Five are shortlisted already" : "Adds it to a date poll for the group")
    }
}

/// "3 picked · Ask the group" once two or more are shortlisted; "Pick one more to ask the group" before.
/// Under the calendar in either view, so times shortlisted from a tapped day can be asked about too.
struct ShortlistBar: View {
    @Binding var shortlist: [Slot]
    let onAsk: () -> Void
    @Environment(\.theme) private var theme

    var body: some View {
        let ready = BestOptionsCard.pollRange.contains(shortlist.count)
        HStack(spacing: 10) {
            Text(ready ? "\(shortlist.count) picked" : "Pick one more to ask the group")
                .bodyFont(13, .semibold, relativeTo: .subheadline)
                .foregroundStyle(ready ? theme.ink : theme.muted)
            Spacer(minLength: 4)
            Button("Clear") { shortlist = [] }
                .buttonStyle(.textAction)
            if ready {
                Button(action: onAsk) {
                    LoadingLabel(title: "Ask the group", systemImage: "bubble.left")
                }
                .buttonStyle(.filled(.primary, height: 36, cornerRadius: 12, fontSize: 13, fullWidth: false))
            }
        }
        .padding(12)
        .glass(cornerRadius: 20)
        .transition(.opacity.combined(with: .move(edge: .bottom)))
    }
}
