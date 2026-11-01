import SwiftUI

/// Initials on a tile colour (or the person's photo when they have one).
struct Avatar: View {
    let name: String
    var imageURL: String?
    var color: Color
    var size: CGFloat = 34
    var border: Color?
    @Environment(\.theme) private var theme

    var body: some View {
        ZStack {
            Circle().fill(color)
            Text(Self.initials(name))
                .font(AppFont.body(size * 0.38, .bold, relativeTo: .caption))
                .foregroundStyle(theme.ink)
                .minimumScaleFactor(0.5)
            if let imageURL, let url = URL(string: imageURL) {
                AsyncImage(url: url) { image in
                    image.resizable().scaledToFill()
                } placeholder: {
                    Color.clear
                }
                .clipShape(Circle())
            }
        }
        .frame(width: size, height: size)
        .overlay {
            if let border { Circle().strokeBorder(border, lineWidth: 1.5) }
        }
        .accessibilityHidden(true)
    }

    static func initials(_ name: String) -> String {
        let letters = name.split(separator: " ").prefix(1).compactMap(\.first)
        return letters.isEmpty ? "?" : String(letters).uppercased()
    }
}

/// A person shown in avatar stacks: just enough to draw an `Avatar`.
struct AvatarPerson: Identifiable, Hashable {
    let id: String
    let name: String
    var imageURL: String?
}

extension Member {
    var avatarPerson: AvatarPerson { AvatarPerson(id: userId, name: name, imageURL: image) }
}

/// Overlapping avatars ("A P T +3").
struct AvatarStack: View {
    let people: [AvatarPerson]
    var size: CGFloat = 22
    var overlap: CGFloat = 7
    var maxVisible = 5
    var border: Color?
    @Environment(\.theme) private var theme

    var body: some View {
        HStack(spacing: -overlap) {
            ForEach(Array(people.prefix(maxVisible).enumerated()), id: \.element.id) { index, person in
                Avatar(
                    name: person.name,
                    imageURL: person.imageURL,
                    color: theme.tiles[index % theme.tiles.count],
                    size: size,
                    border: border ?? theme.glassBorder
                )
            }
        }
        .padding(.trailing, overlap)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(people.map(\.name).formatted(.list(type: .and)))
    }
}

/// Group monogram on a `t1`–`t5` tile.
struct GroupTile: View {
    let monogram: String
    let colorToken: String
    var size: CGFloat = 48
    var cornerRadius: CGFloat = 16
    @Environment(\.theme) private var theme

    var body: some View {
        Text(monogram)
            .displayFont(size * 0.35, .bold, relativeTo: .headline)
            .foregroundStyle(theme.ink)
            .lineLimit(1)
            .minimumScaleFactor(0.6)
            .frame(width: size, height: size)
            .background(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous).fill(theme.tile(colorToken)))
            .accessibilityHidden(true)
    }
}

/// Status badge: indigo / mint / apricot / neutral.
struct StatusPill: View {
    enum Tone { case indigo, mint, apricot, neutral }

    let text: String
    var tone: Tone = .neutral
    var systemImage: String?
    var fontSize: CGFloat = 11
    @Environment(\.theme) private var theme

    var body: some View {
        HStack(spacing: 5) {
            if let systemImage {
                Image(systemName: systemImage).font(.system(size: fontSize, weight: .heavy)).accessibilityHidden(true)
            }
            Text(text).lineLimit(1)
        }
        .bodyFont(fontSize, .semibold, relativeTo: .caption)
        .foregroundStyle(colors.foreground)
        .padding(.horizontal, fontSize > 11 ? 10 : 8)
        .padding(.vertical, fontSize > 11 ? 5 : 4)
        .background(Capsule().fill(colors.background))
    }

    private var colors: (background: Color, foreground: Color) {
        switch tone {
        case .indigo: (theme.accentSoft, theme.accentInk)
        case .mint: (theme.okBg, theme.okInk)
        case .apricot: (theme.warnBg, theme.warnInk)
        case .neutral: (theme.line, theme.neutralInk)
        }
    }
}

/// Circular progress ring (sync progress, proposal acceptance).
struct ProgressRing: View {
    let progress: Double
    var lineWidth: CGFloat = 3
    var color: Color?
    @Environment(\.theme) private var theme

    var body: some View {
        ZStack {
            Circle().stroke(theme.line2, lineWidth: lineWidth)
            Circle()
                .trim(from: 0, to: max(0, min(1, progress)))
                .stroke(color ?? theme.accent, style: StrokeStyle(lineWidth: lineWidth, lineCap: .round))
                .rotationEffect(.degrees(-90))
                .animation(.spring(response: 0.6, dampingFraction: 0.8), value: progress)
        }
        .padding(lineWidth / 2)
        .accessibilityHidden(true)
    }
}

/// Small ring + "4 of 6 to sync" / "All synced".
struct SyncRing: View {
    let synced: Int
    let total: Int
    @Environment(\.theme) private var theme

    var body: some View {
        HStack(spacing: 6) {
            ProgressRing(progress: total == 0 ? 0 : Double(synced) / Double(total))
                .frame(width: 16, height: 16)
            Text(label)
                .bodyFont(12, .medium, relativeTo: .caption)
                .foregroundStyle(theme.muted)
        }
        .accessibilityElement(children: .combine)
    }

    private var label: String {
        let left = total - synced
        return left <= 0 ? "All synced" : "\(left) of \(total) to sync"
    }
}
