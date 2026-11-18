import SwiftUI

/// "Share a link" (Members and the last onboarding step): choose how many people can join with it
/// (1, 5, 10, 25 or Unlimited), create it, then share it. Once there's a link, New link starts another.
struct InviteLinkSection: View {
    let model: MembersViewModel
    let groupName: String
    @State private var limit: LinkLimit = .standard
    /// Choosing for another link while one is still live.
    @State private var makingAnother = false
    @Environment(\.theme) private var theme

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
                ForEach(LinkLimit.choices, id: \.self) { choice in
                    Chip(label: choice.label, isOn: limit == choice) { limit = choice }
                        .accessibilityLabel(choice.accessibilityLabel)
                }
            }
            .accessibilityElement(children: .contain)
            .accessibilityLabel("People who can join with the link")
            Button {
                Task {
                    if await model.createLink(limit: limit) {
                        makingAnother = false
                        limit = .standard
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
            ShareLink(item: link.url, subject: Text("Join \(groupName) on Availabliss"),
                      message: Text("Join \(groupName) on Availabliss so we can find a time that works.")) {
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
