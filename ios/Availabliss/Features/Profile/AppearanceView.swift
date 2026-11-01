import SwiftUI

/// Appearance: Light / Dark / System and five colour themes, applied instantly and saved to the account.
struct AppearanceView: View {
    @Environment(AppModel.self) private var app
    @Environment(\.theme) private var theme
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                ScreenHeader(title: "Appearance", onBack: { app.router.pop() })
                modeSection
                themeSection
                AppearancePreview()
                Label("Saved to your account · syncs to every device", systemImage: "checkmark.icloud")
                    .bodyFont(13, relativeTo: .subheadline)
                    .foregroundStyle(theme.muted)
                    .frame(maxWidth: .infinity)
                    .padding(.top, 8)
            }
            .padding(.horizontal, 20)
            .padding(.top, 20)
            .padding(.bottom, 24)
        }
        .screenBackground(OrbLayout.appearance)
        .foregroundStyle(theme.ink)
        .chromeless()
    }

    private var modeSection: some View {
        GlassPanel(cornerRadius: 24, padding: EdgeInsets(all: 14), spacing: 10) {
            Text("Mode").sectionLabelStyle()
            SegmentedControl(
                selection: Binding(get: { app.appearance.mode }, set: { app.appearance.set(mode: $0, session: app.session) }),
                options: [
                    SegmentOption(value: .light, label: "Light", systemImage: "sun.max"),
                    SegmentOption(value: .dark, label: "Dark", systemImage: "moon"),
                    SegmentOption(value: .system, label: "System", systemImage: "display"),
                ],
                height: 44,
                accessibilityLabel: "Mode"
            )
        }
    }

    private var themeSection: some View {
        GlassPanel(cornerRadius: 24, padding: EdgeInsets(all: 14), spacing: 10) {
            Text("Colour theme").sectionLabelStyle()
            HStack(spacing: 8) {
                ForEach(ColorTheme.allCases, id: \.self) { option in
                    ThemeSwatch(
                        theme: Theme.make(scheme: colorScheme, colorTheme: option),
                        label: option.label,
                        isSelected: app.appearance.colorTheme == option
                    ) {
                        app.appearance.set(colorTheme: option, session: app.session)
                    }
                    .frame(maxWidth: .infinity)
                }
            }
        }
    }
}

/// Mini orb canvas with the accent dot.
private struct ThemeSwatch: View {
    let theme: Theme
    let label: String
    let isSelected: Bool
    let action: () -> Void
    @Environment(\.theme) private var current

    var body: some View {
        Button(action: action) {
            VStack(spacing: 6) {
                ZStack {
                    theme.bg
                    Circle().fill(theme.orbs[0]).frame(width: 40, height: 40).blur(radius: 6).offset(x: -16, y: -16)
                    Circle().fill(theme.orbs[1]).frame(width: 40, height: 40).blur(radius: 6).offset(x: 18, y: -4)
                    Circle().fill(theme.orbs[2]).frame(width: 36, height: 36).blur(radius: 6).offset(x: -6, y: 22)
                    Circle().fill(theme.accent).frame(width: 20, height: 20).overlay(Circle().strokeBorder(.white.opacity(0.85), lineWidth: 2))
                }
                .frame(width: 56, height: 56)
                .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: isSelected ? 21 : 18, style: .continuous)
                        .strokeBorder(isSelected ? current.ink : current.line2, lineWidth: isSelected ? 2.5 : 1)
                        .padding(isSelected ? -5.5 : 0)
                )
                Text(label).bodyFont(12, .semibold, relativeTo: .caption).foregroundStyle(current.ink)
            }
            .frame(minHeight: 44)
        }
        .buttonStyle(.pressable)
        .accessibilityLabel(label)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
        .animation(.easeOut(duration: 0.15), value: isSelected)
    }
}

/// A live sample of the current theme: a matching band, free / busy cells and the two buttons.
private struct AppearancePreview: View {
    @Environment(\.theme) private var theme

    var body: some View {
        GlassPanel(cornerRadius: 24, padding: EdgeInsets(all: 14), spacing: 12) {
            HStack {
                Text("Preview").sectionLabelStyle()
                Spacer()
                StatusPill(text: "Works", tone: .mint)
            }
            Text("Lake District crew").displayFont(18, .semibold)
            Grid(horizontalSpacing: 4, verticalSpacing: 4) {
                GridRow {
                    cell("13", "from 18:00", .match(UnevenRoundedRectangle(topLeadingRadius: 14, bottomLeadingRadius: 14, bottomTrailingRadius: 5, topTrailingRadius: 5)))
                    cell("14", "all free", .match(UnevenRoundedRectangle(topLeadingRadius: 5, bottomLeadingRadius: 5, bottomTrailingRadius: 5, topTrailingRadius: 5)))
                    cell("15", "all free", .match(UnevenRoundedRectangle(topLeadingRadius: 5, bottomLeadingRadius: 5, bottomTrailingRadius: 14, topTrailingRadius: 14)))
                }
                GridRow {
                    cell("20", " ", .free)
                    cell("21", "Tom busy", .busy)
                    cell("22", " ", .free)
                }
            }
            HStack(spacing: 8) {
                sample("Propose", background: theme.accent, foreground: theme.onAccent)
                sample("Add to calendar", background: theme.solid, foreground: theme.onSolid)
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Preview of the selected theme")
    }

    private enum CellKind {
        case match(UnevenRoundedRectangle)
        case free, busy
    }

    @ViewBuilder
    private func cell(_ day: String, _ note: String, _ kind: CellKind) -> some View {
        let label = VStack(alignment: .leading, spacing: 0) {
            Text(day).bodyFont(15, .semibold)
            Text(note).bodyFont(10, .semibold, relativeTo: .caption2)
        }
        .padding(.horizontal, 10)
        .frame(maxWidth: .infinity, minHeight: 48, alignment: .leading)

        switch kind {
        case .match(let shape):
            label.foregroundStyle(theme.onAccent).background(shape.fill(theme.accent))
        case .free:
            label.surfaceBackground(cornerRadius: 14)
        case .busy:
            label.background(RoundedRectangle(cornerRadius: 14).fill(theme.busyBg))
                .overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(theme.glassBorder, lineWidth: 1))
                .opacity(0.5)
        }
    }

    private func sample(_ title: String, background: Color, foreground: Color) -> some View {
        Text(title)
            .bodyFont(14, .semibold)
            .foregroundStyle(foreground)
            .frame(maxWidth: .infinity, minHeight: 42)
            .background(RoundedRectangle(cornerRadius: 13, style: .continuous).fill(background))
    }
}
