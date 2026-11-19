import SwiftUI

/// "Share a link" (Members and the last onboarding step): choose how many people can join with it
/// (any number from 1 to 1,000, or Unlimited), create it, then share it. Once there's a link, New link starts another.
struct InviteLinkSection: View {
    let model: MembersViewModel
    let groupName: String
    /// How many people, as typed (digits only; blank while typing counts as one).
    @State private var peopleText = "1"
    @State private var unlimited = false
    /// Choosing for another link while one is still live.
    @State private var makingAnother = false
    @FocusState private var isPeopleFocused: Bool
    @Environment(\.theme) private var theme

    private var people: Int { min(max(Int(peopleText) ?? 1, 1), LinkLimit.maxPeople) }
    private var limit: LinkLimit { unlimited ? .unlimited : .people(clamping: people) }

    private var choosing: Bool { model.link == nil || makingAnother }

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("Share a link").bodyFont(15, .semibold)
            Text(choosing ? "Choose how many people can join with it." : model.link?.linkSummary ?? "")
                .bodyFont(13, relativeTo: .subheadline)
                .foregroundStyle(theme.muted)
                .fixedSize(horizontal: false, vertical: true)
        }
        if choosing {
            HStack(spacing: 8) {
                TextField("", text: $peopleText, prompt: Text("1").foregroundStyle(theme.muted))
                    .bodyFont(16, .semibold)
                    .foregroundStyle(theme.ink)
                    .multilineTextAlignment(.center)
                    .keyboardType(.numberPad)
                    .focused($isPeopleFocused)
                    .frame(width: 76)
                    .inputChrome(isFocused: isPeopleFocused, height: 44, cornerRadius: 14)
                    .accessibilityLabel("Number of people")
                    .toolbar {
                        if isPeopleFocused {
                            ToolbarItemGroup(placement: .keyboard) {
                                Spacer()
                                Button("Done") { isPeopleFocused = false }
                            }
                        }
                    }
                    .onChange(of: peopleText) { _, new in
                        let digits = String(new.filter { ("0"..."9").contains($0) }.prefix(4))
                        let capped = (Int(digits) ?? 0) > LinkLimit.maxPeople ? String(LinkLimit.maxPeople) : digits
                        if capped != new { peopleText = capped }
                    }
                    .onChange(of: isPeopleFocused) { _, focused in if !focused { peopleText = String(people) } }
                    .disabled(unlimited)
                    .opacity(unlimited ? 0.4 : 1)
                Stepper("People", value: Binding(get: { people }, set: { peopleText = String($0) }), in: 1...LinkLimit.maxPeople)
                    .labelsHidden()
                    .disabled(unlimited)
                    .opacity(unlimited ? 0.4 : 1)
                Spacer(minLength: 4)
                Chip(label: LinkLimit.unlimited.label, isOn: unlimited) {
                    unlimited.toggle()
                    if unlimited { isPeopleFocused = false }
                }
                .accessibilityLabel(LinkLimit.unlimited.accessibilityLabel)
            }
            .disabled(model.isCreatingInvite)
            .accessibilityElement(children: .contain)
            .accessibilityLabel("People who can join with the link")
            Button {
                Task {
                    isPeopleFocused = false
                    if await model.createLink(limit: limit) {
                        makingAnother = false
                        peopleText = "1"
                        unlimited = false
                    }
                }
            } label: {
                LoadingLabel(title: "Create invite link", systemImage: "link", isLoading: model.isCreatingInvite)
            }
            .buttonStyle(.accent)
            .disabled(model.isCreatingInvite)
            if makingAnother {
                Button("Cancel") { makingAnother = false }
                    .buttonStyle(.textAction)
                    .frame(maxWidth: .infinity)
            }
        } else if let link = model.link {
            ShareLink(
                item: link.url, subject: Text("Join \(groupName) on Availabliss"),
                message: Text("Join \(groupName) on Availabliss so we can find a time that works.")
            ) {
                Label("Share invite link", systemImage: "square.and.arrow.up")
            }
            .buttonStyle(.accent)
            Button {
                makingAnother = true
            } label: {
                Label("New link", systemImage: "plus")
            }
            .buttonStyle(.textAction)
            .frame(maxWidth: .infinity)
        }
    }
}
